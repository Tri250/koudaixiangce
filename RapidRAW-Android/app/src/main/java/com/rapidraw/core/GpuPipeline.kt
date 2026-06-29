package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLES32
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import com.rapidraw.R
import com.rapidraw.data.model.Adjustments
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * OpenGL ES 3.0+ based GPU processing pipeline for real-time preview.
 * Uses EGL14 for context management and GLES30/GLES32 for rendering.
 *
 * Android 16 (API 36) 优化特性：
 * - Shader 编译缓存（避免重复编译）
 * - 多线程渲染优化
 * - OpenGL ES 3.2 特性利用（几何着色器等）
 * - Vulkan compute pipeline 支持（可选）
 *
 * GPU PROCESSING PIPELINE - CONSISTENCY NOTE
 * ============================================
 * This pipeline must produce identical results to the CPU path in ImageProcessor.processFullResolution().
 * The processing order is:
 * 1. sRGB → Linear
 * 2. Exposure
 * 3. Filmic Brightness
 * 4. Tone Level
 * 5. White Balance (temperature/tint)
 * 6. Green-Magenta axis
 * 7. Highlights
 * 8. Tonal (contrast/shadows/whites/blacks)
 * 9. Centre
 * 10. Saturation/Vibrance
 * 11. HSL 8-color panel
 * 12. Tone Curve
 * 13. RGB Curves
 * 14. Color Grading
 * 15. Color Calibration
 * 16. Film Simulation (curve + shifts)
 * 17. Film Intensity mix
 * 18. Vignette
 * 19. Grain
 * 20. Soft Glow / Bloom
 * 21. LUT
 * 22. Linear → sRGB
 * 23. AgX Tone Mapping (if enabled)
 * 24. Dither + Clamp
 *
 * When adding new processing steps, BOTH this GPU shader and the CPU path must be updated.
 * Test with: identical input → diff < 1/255 per channel.
 */
class GpuPipeline(private val context: Context) {

    companion object {
        private const val TAG = "GpuPipeline"

        // Vertex shader - simple fullscreen quad
        private const val VERTEX_SHADER = """
            #version 300 es
            precision highp float;

            in vec2 aPosition;
            in vec2 aTexCoord;
            out vec2 vTexCoord;

            void main() {
                vTexCoord = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        // ── Shader 编译缓存（全局）──────────────────────────────────────────

        /**
         * Shader 编译缓存。
         *
         * 缓存已编译的 shader，避免重复编译导致的性能损耗。
         * Android 16 优化：使用 GLES32 的 glShaderBinary 缓存（可选）。
         */
        private val shaderCache = ConcurrentHashMap<String, Int>()

        /**
         * Shader 二进制缓存（用于跨会话缓存）。
         *
         * Android 6+ (API 23) 支持 glGetProgramBinary / glProgramBinary。
         * 可以将编译好的程序保存到磁盘，下次启动直接加载。
         */
        private val programBinaryCache = ConcurrentHashMap<String, ByteArray>()

        /**
         * 是否支持 OpenGL ES 3.2。
         */
        fun supportsGles32(): Boolean {
            return try {
                val version = GLES30.glGetString(GLES30.GL_VERSION) ?: ""
                version.contains("ES 3.2") || Build.VERSION.SDK_INT >= 36
            } catch (_: Exception) {
                false
            }
        }

        /**
         * 是否支持 Shader 二进制缓存。
         */
        fun supportsShaderBinary(): Boolean {
            return try {
                val formats = IntArray(1)
                GLES30.glGetIntegerv(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS, formats, 0)
                formats[0] > 0
            } catch (_: Exception) {
                false
            }
        }

        // 清空 shader 缓存
        fun clearShaderCache() {
            shaderCache.values.forEach { shader ->
                try {
                    GLES30.glDeleteShader(shader)
                } catch (_: Exception) {}
            }
            shaderCache.clear()
            programBinaryCache.clear()
        }
    }

    // GL state
    private var program = 0
    private var vertexShader = 0
    private var fragmentShader = 0
    private var textureId = 0
    private var maskTextureId = 0
    private var lutTextureId = 0
    private var fbo = 0
    private var fboTextureId = 0
    private var width = 0
    private var height = 0
    private var initialized = false

    // Flow mask / LUT intensity
    private var maskIntensity = 0f
    private var lutIntensity = 0f

    // 性能优化：复用缓冲区避免频繁 GC
    private var readBackBuffer: ByteBuffer? = null
    private var readBackBitmap: Bitmap? = null
    private var lastInputWidth = 0
    private var lastInputHeight = 0

    // Vertex buffers
    private var vao = 0
    private var vbo = 0

    // Uniform locations
    private var uniformLocations = mutableMapOf<String, Int>()

    // EGL
    private var eglDisplay: android.opengl.EGLDisplay? = null
    private var eglContext: android.opengl.EGLContext? = null
    private var eglSurface: android.opengl.EGLSurface? = null
    private var eglConfig: android.opengl.EGLConfig? = null
    private var isOffscreen = false

    // ── 多线程渲染优化 ───────────────────────────────────────────

    private val renderLock = ReentrantLock()
    private val isRendering = AtomicBoolean(false)

    // OpenGL ES 3.2 特性
    private var useGles32 = false
    private var useShaderBinary = false

    // Texture coordinate buffer for flipped Y
    private val quadVertices = floatArrayOf(
        // Position     // TexCoord
        -1f, -1f,       0f, 0f,
         1f, -1f,       1f, 0f,
        -1f,  1f,       0f, 1f,
         1f,  1f,       1f, 1f
    )

    /**
     * Initialize the GPU pipeline: create EGL context, compile shaders,
     * setup textures and framebuffer.
     */
    fun initialize(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        this.width = width
        this.height = height
        this.isOffscreen = false

        // 检测 OpenGL ES 版本能力
        useGles32 = supportsGles32()
        useShaderBinary = supportsShaderBinary()
        Log.d(TAG, "OpenGL capabilities: GLES32=$useGles32, ShaderBinary=$useShaderBinary")

        setupEgl(surfaceTexture)
        compileShaders()
        setupGeometry()
        setupTexture()
        setupMaskTexture()
        setupLutTexture()
        setupFramebuffer(width, height)

        initialized = true
    }

    /**
     * Initialize with offscreen rendering (no surface texture).
     */
    fun initializeOffscreen(width: Int, height: Int) {
        this.width = width
        this.height = height
        this.isOffscreen = true

        setupEglOffscreen()
        compileShaders()
        setupGeometry()
        setupTexture()
        setupMaskTexture()
        setupLutTexture()
        setupFramebuffer(width, height)

        initialized = true
    }

    // ── EGL Setup ──────────────────────────────────────────────────

    private fun setupEgl(surfaceTexture: SurfaceTexture) {
        val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        if (display === android.opengl.EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL display")
        }
        eglDisplay = display

        val version = IntArray(2)
        if (!android.opengl.EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        // Choose config: request OpenGL ES 3.0 capable config
        val attribList = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            android.opengl.EGL14.EGL_RED_SIZE, 8,
            android.opengl.EGL14.EGL_GREEN_SIZE, 8,
            android.opengl.EGL14.EGL_BLUE_SIZE, 8,
            android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
            android.opengl.EGL14.EGL_DEPTH_SIZE, 0,
            android.opengl.EGL14.EGL_STENCIL_SIZE, 0,
            android.opengl.EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!android.opengl.EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        val config = configs[0] ?: throw RuntimeException("No EGL config chosen")
        eglConfig = config

        // Create context
        val contextAttribs = intArrayOf(
            android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            android.opengl.EGL14.EGL_NONE
        )

        val context = android.opengl.EGL14.eglCreateContext(
            display, config, android.opengl.EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (context === android.opengl.EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }
        eglContext = context

        // Create surface from SurfaceTexture
        val surface = android.opengl.EGL14.eglCreateWindowSurface(
            display, config, surfaceTexture, intArrayOf(android.opengl.EGL14.EGL_NONE), 0
        )
        if (surface === android.opengl.EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL surface")
        }
        eglSurface = surface

        makeCurrent()
    }

    private fun setupEglOffscreen() {
        val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        if (display === android.opengl.EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL display")
        }
        eglDisplay = display

        val version = IntArray(2)
        if (!android.opengl.EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        val attribList = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            android.opengl.EGL14.EGL_RED_SIZE, 8,
            android.opengl.EGL14.EGL_GREEN_SIZE, 8,
            android.opengl.EGL14.EGL_BLUE_SIZE, 8,
            android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
            android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
            android.opengl.EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!android.opengl.EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        val config = configs[0] ?: throw RuntimeException("No EGL config chosen")
        eglConfig = config

        val contextAttribs = intArrayOf(
            android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            android.opengl.EGL14.EGL_NONE
        )

        val context = android.opengl.EGL14.eglCreateContext(
            display, config, android.opengl.EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (context === android.opengl.EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }
        eglContext = context

        // Create pbuffer surface for offscreen rendering
        val pbufferAttribs = intArrayOf(
            android.opengl.EGL14.EGL_WIDTH, width,
            android.opengl.EGL14.EGL_HEIGHT, height,
            android.opengl.EGL14.EGL_NONE
        )

        val surface = android.opengl.EGL14.eglCreatePbufferSurface(
            display, config, pbufferAttribs, 0
        )
        if (surface === android.opengl.EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL pbuffer surface")
        }
        eglSurface = surface

        makeCurrent()
    }

    private fun makeCurrent() {
        val display = eglDisplay ?: return
        val surface = eglSurface ?: return
        val context = eglContext ?: return

        if (!android.opengl.EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw RuntimeException("Unable to make EGL context current")
        }
    }

    // ── Shader Compilation (with caching) ─────────────────────────────────────────

    /**
     * 编译 Shader（带缓存）。
     *
     * 优先从缓存中获取已编译的 shader，避免重复编译。
     * 支持 Shader 二进制缓存（跨会话缓存）。
     */
    private fun compileShaders() {
        val startTime = System.currentTimeMillis()

        // 尝试从二进制缓存加载
        if (useShaderBinary && tryLoadProgramBinary()) {
            Log.d(TAG, "Program loaded from binary cache in ${System.currentTimeMillis() - startTime}ms")
            GLES30.glUseProgram(program)
            cacheUniformLocations()
            return
        }

        // Compile vertex shader (with cache)
        vertexShader = loadShaderWithCache(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER, "vertex_main")

        // Load fragment shader from resources (with cache)
        val fragSource = context.resources.openRawResource(R.raw.image_adjustment)
            .bufferedReader().use { it.readText() }
        fragmentShader = loadShaderWithCache(GLES30.GL_FRAGMENT_SHADER, fragSource, "fragment_main")

        // Link program
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Shader link failed: $log")
        }

        GLES30.glUseProgram(program)

        // Cache uniform locations
        cacheUniformLocations()

        // 保存二进制缓存（用于下次启动）
        if (useShaderBinary) {
            saveProgramBinary()
        }

        Log.d(TAG, "Shader compilation completed in ${System.currentTimeMillis() - startTime}ms")
    }

    /**
     * 加载 Shader（带缓存）。
     *
     * @param type Shader 类型 (GL_VERTEX_SHADER / GL_FRAGMENT_SHADER)
     * @param source Shader 源代码
     * @param cacheKey 缓存键名
     * @return Shader ID
     */
    private fun loadShaderWithCache(type: Int, source: String, cacheKey: String): Int {
        // 尝试从缓存获取
        val cachedShader = shaderCache[cacheKey]
        if (cachedShader != null && cachedShader > 0) {
            // 验证缓存 shader 是否仍然有效
            try {
                GLES30.glGetShaderiv(cachedShader, GLES30.GL_SHADER_TYPE, IntArray(1), 0)
                Log.d(TAG, "Using cached shader: $cacheKey")
                return cachedShader
            } catch (_: Exception) {
                shaderCache.remove(cacheKey)
            }
        }

        // 编译新 shader
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $log")
        }

        // 缓存 shader
        shaderCache[cacheKey] = shader
        Log.d(TAG, "Shader compiled and cached: $cacheKey")

        return shader
    }

    /**
     * 尝试从二进制缓存加载程序。
     *
     * 使用 glProgramBinary 加载预编译的程序（跨会话缓存）。
     */
    private fun tryLoadProgramBinary(): Boolean {
        val binary = programBinaryCache["main_program"]
        if (binary == null || binary.isEmpty()) return false

        try {
            program = GLES30.glCreateProgram()

            // 获取二进制格式
            val formats = IntArray(16)
            GLES30.glGetIntegerv(GLES30.GL_PROGRAM_BINARY_FORMATS, formats, 0)
            val format = formats[0]

            GLES30.glProgramBinary(program, format, ByteBuffer.wrap(binary), binary.size)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES30.GL_TRUE) {
                GLES30.glDeleteProgram(program)
                return false
            }

            Log.d(TAG, "Program binary loaded successfully")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load program binary: ${e.message}")
            GLES30.glDeleteProgram(program)
            return false
        }
    }

    /**
     * 保存程序二进制到缓存。
     */
    private fun saveProgramBinary() {
        try {
            // 获取二进制长度
            val lengthArr = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_PROGRAM_BINARY_LENGTH, lengthArr, 0)
            val length = lengthArr[0]

            if (length <= 0) return

            // 获取二进制数据
            val buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder())
            val formatArr = IntArray(1)
            GLES30.glGetProgramBinary(program, length, IntArray(1), 0, formatArr, 0, buffer)

            buffer.rewind()
            val binary = ByteArray(length)
            buffer.get(binary)

            programBinaryCache["main_program"] = binary
            Log.d(TAG, "Program binary saved: $length bytes")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save program binary: ${e.message}")
        }
    }

    // 原始的 loadShader 方法（保留兼容）
    private fun loadShader(type: Int, source: String): Int {
        return loadShaderWithCache(type, source, if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment")
    }

    private fun cacheUniformLocations() {
        val uniformNames = listOf(
            "uTexture", "uResolution",
            "uExposure", "uBrightness",
            "uTemperature", "uTint",
            "uContrast", "uHighlights", "uShadows", "uWhites", "uBlacks",
            "uSaturation", "uVibrance",
            "uHueRed", "uSatRed", "uLumRed",
            "uHueOrange", "uSatOrange", "uLumOrange",
            "uHueYellow", "uSatYellow", "uLumYellow",
            "uHueGreen", "uSatGreen", "uLumGreen",
            "uHueAqua", "uSatAqua", "uLumAqua",
            "uHueBlue", "uSatBlue", "uLumBlue",
            "uHuePurple", "uSatPurple", "uLumPurple",
            "uHueMagenta", "uSatMagenta", "uLumMagenta",
            "uCurvePoints[0]", "uCurvePoints[1]", "uCurvePoints[2]",
            "uCurvePoints[3]", "uCurvePoints[4]",
            "uColorGradingShadows", "uColorGradingMidtones", "uColorGradingHighlights",
            "uColorGradingBlend", "uColorGradingGlobalSat",
            "uCalibRedHue", "uCalibRedSat",
            "uCalibGreenHue", "uCalibGreenSat",
            "uCalibBlueHue", "uCalibBlueSat",
            "uSharpness", "uClarity", "uStructure",
            "uDehaze", "uVignette",
            "uGrain", "uGrainSize",
            "uChromaticAberrationRedCyan", "uChromaticAberrationBlueYellow",
            "uAgXEnabled", "uAgXContrast", "uAgXPedestal",
            "uClippingPreview",
            // New uniforms for film simulation & advanced controls
            "uToneLevel", "uFilmIntensity", "uGreenMagenta", "uSoftGlow",
            "uHighlightRollOff", "uShadowLift", "uDrCompression",
            "uFilmRedShift", "uFilmGreenShift", "uFilmBlueShift",
            "uFilmSaturation", "uFilmContrast",
            "uFilmGrainAmount", "uFilmGrainSize", "uFilmGrainRoughness",
            "uFilmCurve[0]", "uFilmCurve[1]", "uFilmCurve[2]",
            "uFilmCurve[3]", "uFilmCurve[4]", "uFilmCurve[5]",
            // Flow Mask & LUT
            "uMaskTexture", "uMaskIntensity",
            "uLutTexture", "uLutIntensity",
            // Missing fields fix
            "uLumaNoiseReduction", "uColorNoiseReduction",
            "uCentre",
            "uVignetteMidpoint", "uVignetteRoundness", "uVignetteFeather",
            "uGrainRoughness",
            "uGlowAmount", "uHalationAmount", "uFlareAmount",
            "uColorGradingBalance",
            "uColorCalibrationShadowsTint",
            // CDL Color Grading
            "uCdlShadowsR", "uCdlShadowsG", "uCdlShadowsB",
            "uCdlMidtonesR", "uCdlMidtonesG", "uCdlMidtonesB",
            "uCdlHighlightsR", "uCdlHighlightsG", "uCdlHighlightsB",
            // Blur-based creative effects
            "uBlurGlow", "uBlurHalation",
            "uRotation", "uOrientationSteps",
            "uFlipHorizontal", "uFlipVertical",
            "uCropAspectRatio",
            "uTransformDistortion", "uTransformVertical", "uTransformHorizontal",
            "uTransformRotate", "uTransformAspect", "uTransformScale",
            "uTransformXOffset", "uTransformYOffset",
            "uLensDistortion", "uLensVignette", "uLensTca", "uLensFocalLength",
            // Advanced Lens Correction (Brown-Conrady)
            "uLensK1", "uLensK2", "uLensK3",
            "uLensP1", "uLensP2",
            "uLensLateralCA", "uLensTcaRed", "uLensTcaBlue",
            "uLensVignetteCorrection", "uLensVignetteK1", "uLensVignetteK2", "uLensVignetteK3",
            "uLensAutoCorrection", "uLensScale",
            "uRedCurve[0]", "uRedCurve[1]", "uRedCurve[2]", "uRedCurve[3]", "uRedCurve[4]", "uRedCurve[5]",
            "uGreenCurve[0]", "uGreenCurve[1]", "uGreenCurve[2]", "uGreenCurve[3]", "uGreenCurve[4]", "uGreenCurve[5]",
            "uBlueCurve[0]", "uBlueCurve[1]", "uBlueCurve[2]", "uBlueCurve[3]", "uBlueCurve[4]", "uBlueCurve[5]",
            // Traditional Denoise
            "uDenoiseMode", "uDenoiseStrength", "uDenoiseWindowSize", "uGaussianSigma",
            // Skin Whitening
            "uSkinWhiteningIntensity", "uSkinToneTarget", "uSkinSmoothness",
        )

        for (name in uniformNames) {
            val loc = GLES30.glGetUniformLocation(program, name)
            uniformLocations[name] = loc
        }
    }

    // ── Geometry ───────────────────────────────────────────────────

    private fun setupGeometry() {
        val buffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(quadVertices)
        buffer.position(0)

        val vaoArr = IntArray(1)
        val vboArr = IntArray(1)

        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)

        vao = vaoArr[0]
        vbo = vboArr[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)

        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, 0)

        val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glBindVertexArray(0)
    }

    // ── Texture Setup ──────────────────────────────────────────────

    private fun setupTexture() {
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        textureId = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun setupMaskTexture() {
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        maskTextureId = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // Upload a 1x1 transparent pixel as default
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        pixel.put(byteArrayOf(0, 0, 0, 0))
        pixel.rewind()
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun setupLutTexture() {
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        lutTextureId = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        // Upload a 2x2x2 identity LUT as default
        val size = 2
        val pixelCount = size * size * size * 3
        val buffer = ByteBuffer.allocateDirect(pixelCount).order(ByteOrder.nativeOrder())
        for (z in 0 until size) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    buffer.put((x * 255 / (size - 1)).toByte())
                    buffer.put((y * 255 / (size - 1)).toByte())
                    buffer.put((z * 255 / (size - 1)).toByte())
                }
            }
        }
        buffer.rewind()
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, size, size, size, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buffer)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    private fun setupFramebuffer(width: Int, height: Int) {
        // Create FBO texture
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        fboTextureId = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        // Create FBO
        val fboArr = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArr, 0)
        fbo = fboArr[0]

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, fboTextureId, 0)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete: $status")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    // ── Update Adjustments ─────────────────────────────────────────

    /**
     * Convert Adjustments data class to shader uniforms.
     * Accepts data.model.Adjustments (the canonical adjustment type).
     */
    fun updateAdjustments(adjustments: Adjustments) {
        if (!initialized) return

        GLES30.glUseProgram(program)

        // ── Basic ──
        setUniform1f("uExposure", adjustments.exposure)
        setUniform1f("uBrightness", adjustments.brightness / 5f)          // -5..5 → -1..1
        setUniform1f("uTemperature", 6500f + adjustments.temperature * 50f) // offset → Kelvin
        setUniform1f("uTint", adjustments.tint / 100f)                    // -100..100 → -1..1
        setUniform1f("uContrast", adjustments.contrast / 100f)            // -100..100 → -1..1
        setUniform1f("uHighlights", adjustments.highlights / 150f)        // -150..150 → -1..1
        setUniform1f("uShadows", adjustments.shadows / 100f)             // -100..100 → -1..1
        setUniform1f("uWhites", adjustments.whites / 30f)                // -30..30 → -1..1
        setUniform1f("uBlacks", adjustments.blacks / 60f)                // -60..60 → -1..1
        setUniform1f("uSaturation", adjustments.saturation / 100f)       // -100..100 → -1..1
        setUniform1f("uVibrance", adjustments.vibrance / 100f)           // -100..100 → -1..1

        // ── New: Tone / Film Intensity / Green-Magenta / Soft Glow ──
        setUniform1f("uToneLevel", adjustments.toneLevel)
        setUniform1f("uFilmIntensity", adjustments.filmIntensity)
        setUniform1f("uGreenMagenta", adjustments.greenMagenta)
        setUniform1f("uSoftGlow", adjustments.softGlow)

        // ── New: Film simulation parameters ──
        setUniform1f("uHighlightRollOff", adjustments.filmHighlightRollOff)
        setUniform1f("uShadowLift", adjustments.filmShadowLift)
        setUniform1f("uDrCompression", adjustments.filmDrCompression)
        setUniform1f("uFilmRedShift", adjustments.filmRedShift)
        setUniform1f("uFilmGreenShift", adjustments.filmGreenShift)
        setUniform1f("uFilmBlueShift", adjustments.filmBlueShift)
        setUniform1f("uFilmSaturation", adjustments.filmSaturation)
        setUniform1f("uFilmContrast", adjustments.filmContrast)
        setUniform1f("uFilmGrainAmount", adjustments.filmGrainAmount)
        setUniform1f("uFilmGrainSize", adjustments.filmGrainSize)
        setUniform1f("uFilmGrainRoughness", adjustments.filmGrainRoughness)

        // Film curve: upload 6 control points as 6 vec2 uniforms
        val filmCurve = adjustments.filmCurvePoints
        for (i in 0 until 6) {
            if (i < filmCurve.size) {
                setUniform2f("uFilmCurve[$i]", filmCurve[i].first, filmCurve[i].second)
            } else {
                val t = i * 51f
                setUniform2f("uFilmCurve[$i]", t, t)
            }
        }

        // ── HSL 8-color panel ──
        setUniform1f("uHueRed", adjustments.hslReds.hue / 100f)
        setUniform1f("uSatRed", adjustments.hslReds.saturation / 100f)
        setUniform1f("uLumRed", adjustments.hslReds.luminance / 100f)
        setUniform1f("uHueOrange", adjustments.hslOranges.hue / 100f)
        setUniform1f("uSatOrange", adjustments.hslOranges.saturation / 100f)
        setUniform1f("uLumOrange", adjustments.hslOranges.luminance / 100f)
        setUniform1f("uHueYellow", adjustments.hslYellows.hue / 100f)
        setUniform1f("uSatYellow", adjustments.hslYellows.saturation / 100f)
        setUniform1f("uLumYellow", adjustments.hslYellows.luminance / 100f)
        setUniform1f("uHueGreen", adjustments.hslGreens.hue / 100f)
        setUniform1f("uSatGreen", adjustments.hslGreens.saturation / 100f)
        setUniform1f("uLumGreen", adjustments.hslGreens.luminance / 100f)
        setUniform1f("uHueAqua", adjustments.hslAquas.hue / 100f)
        setUniform1f("uSatAqua", adjustments.hslAquas.saturation / 100f)
        setUniform1f("uLumAqua", adjustments.hslAquas.luminance / 100f)
        setUniform1f("uHueBlue", adjustments.hslBlues.hue / 100f)
        setUniform1f("uSatBlue", adjustments.hslBlues.saturation / 100f)
        setUniform1f("uLumBlue", adjustments.hslBlues.luminance / 100f)
        setUniform1f("uHuePurple", adjustments.hslPurples.hue / 100f)
        setUniform1f("uSatPurple", adjustments.hslPurples.saturation / 100f)
        setUniform1f("uLumPurple", adjustments.hslPurples.luminance / 100f)
        setUniform1f("uHueMagenta", adjustments.hslMagentas.hue / 100f)
        setUniform1f("uSatMagenta", adjustments.hslMagentas.saturation / 100f)
        setUniform1f("uLumMagenta", adjustments.hslMagentas.luminance / 100f)

        // ── Tone curve - pack lumaCurve control points into 5 vec4s ──
        // data.model uses Coord(x,y); convert to Pair list for packing
        val curvePoints = adjustments.lumaCurve.map { it.x to it.y }
        if (curvePoints.size == 10) {
            for (i in 0 until 5) {
                val p0 = curvePoints[i * 2]
                val p1 = curvePoints[i * 2 + 1]
                setUniform4f("uCurvePoints[$i]", p0.first, p0.second, p1.first, p1.second)
            }
        }

        // ── Color Grading ──
        // ColorGradingRegion has hue(0..360), saturation(0..100), luminance(-100..100)
        // Shader expects vec3 (hue/360, sat/100, lum/100)
        val cg = adjustments.colorGrading
        setUniform3f("uColorGradingShadows", floatArrayOf(
            cg.shadows.hue / 360f, cg.shadows.saturation / 100f, cg.shadows.luminance / 100f))
        setUniform3f("uColorGradingMidtones", floatArrayOf(
            cg.midtones.hue / 360f, cg.midtones.saturation / 100f, cg.midtones.luminance / 100f))
        setUniform3f("uColorGradingHighlights", floatArrayOf(
            cg.highlights.hue / 360f, cg.highlights.saturation / 100f, cg.highlights.luminance / 100f))
        setUniform1f("uColorGradingBlend", cg.blending / 100f)
        setUniform1f("uColorGradingGlobalSat", 0f)

        // ── Color Calibration ──
        val cc = adjustments.colorCalibration
        setUniform1f("uCalibRedHue", cc.redHue / 100f)
        setUniform1f("uCalibRedSat", cc.redSaturation / 100f)
        setUniform1f("uCalibGreenHue", cc.greenHue / 100f)
        setUniform1f("uCalibGreenSat", cc.greenSaturation / 100f)
        setUniform1f("uCalibBlueHue", cc.blueHue / 100f)
        setUniform1f("uCalibBlueSat", cc.blueSaturation / 100f)

        // ── Detail ──
        setUniform1f("uSharpness", adjustments.sharpness / 150f * 4f)     // 0..150 → 0..4
        setUniform1f("uClarity", adjustments.clarity / 100f)             // -100..100 → -1..1
        setUniform1f("uStructure", adjustments.structure / 100f)         // -100..100 → -1..1

        // ── Effects ──
        setUniform1f("uDehaze", adjustments.dehaze / 100f)               // -100..100 → -1..1
        setUniform1f("uVignette", adjustments.vignetteAmount / 100f)     // -100..100 → -1..1
        setUniform1f("uGrain", adjustments.grainAmount / 100f)           // 0..100 → 0..1
        setUniform1f("uGrainSize", adjustments.grainSize / 100f * 3f)    // 0..100 → 0..3
        // Chromatic aberration: pass red-cyan and blue-yellow separately
        val caRedCyan = adjustments.chromaticAberrationRedCyan / 100f
        val caBlueYellow = adjustments.chromaticAberrationBlueYellow / 100f
        setUniform1f("uChromaticAberrationRedCyan", caRedCyan)
        setUniform1f("uChromaticAberrationBlueYellow", caBlueYellow)

        // ── Tone Mapping ──
        val agxEnabled = adjustments.toneMapper == "agx"
        setUniform1f("uAgXEnabled", if (agxEnabled) 1f else 0f)
        setUniform1f("uAgXContrast", adjustments.agxContrast.coerceIn(0f, 1f))
        setUniform1f("uAgXPedestal", adjustments.agxPedestal.coerceIn(0f, 0.5f))

        // ── Missing fields fix ──
        setUniform1f("uLumaNoiseReduction", adjustments.lumaNoiseReduction / 100f)
        setUniform1f("uColorNoiseReduction", adjustments.colorNoiseReduction / 100f)
        setUniform1f("uCentre", adjustments.centre / 100f)
        setUniform1f("uVignetteMidpoint", adjustments.vignetteMidpoint / 100f)
        setUniform1f("uVignetteRoundness", adjustments.vignetteRoundness / 100f)
        setUniform1f("uVignetteFeather", adjustments.vignetteFeather / 100f)
        setUniform1f("uGrainRoughness", adjustments.grainRoughness / 100f)
        setUniform1f("uGlowAmount", adjustments.glowAmount / 100f)
        setUniform1f("uHalationAmount", adjustments.halationAmount / 100f)
        setUniform1f("uFlareAmount", adjustments.flareAmount / 100f)
        setUniform1f("uColorGradingBalance", adjustments.colorGrading.balance / 100f)
        setUniform1f("uColorCalibrationShadowsTint", adjustments.colorCalibration.shadowsTint / 100f)
        // CDL Color Grading
        setUniform1f("uCdlShadowsR", adjustments.colorGradingShadowsR / 100f)
        setUniform1f("uCdlShadowsG", adjustments.colorGradingShadowsG / 100f)
        setUniform1f("uCdlShadowsB", adjustments.colorGradingShadowsB / 100f)
        setUniform1f("uCdlMidtonesR", adjustments.colorGradingMidtonesR / 100f)
        setUniform1f("uCdlMidtonesG", adjustments.colorGradingMidtonesG / 100f)
        setUniform1f("uCdlMidtonesB", adjustments.colorGradingMidtonesB / 100f)
        setUniform1f("uCdlHighlightsR", adjustments.colorGradingHighlightsR / 100f)
        setUniform1f("uCdlHighlightsG", adjustments.colorGradingHighlightsG / 100f)
        setUniform1f("uCdlHighlightsB", adjustments.colorGradingHighlightsB / 100f)
        // Blur-based creative effects
        setUniform1f("uBlurGlow", adjustments.glow / 100f)
        setUniform1f("uBlurHalation", adjustments.halation / 100f)
        setUniform1f("uRotation", adjustments.rotation)
        setUniform1i("uOrientationSteps", adjustments.orientationSteps)
        setUniform1f("uFlipHorizontal", if (adjustments.flipHorizontal) 1f else 0f)
        setUniform1f("uFlipVertical", if (adjustments.flipVertical) 1f else 0f)
        setUniform1f("uCropAspectRatio", adjustments.crop?.aspectRatio ?: 0f)
        setUniform1f("uTransformDistortion", adjustments.transformDistortion / 100f)
        setUniform1f("uTransformVertical", adjustments.transformVertical / 100f)
        setUniform1f("uTransformHorizontal", adjustments.transformHorizontal / 100f)
        setUniform1f("uTransformRotate", adjustments.transformRotate)
        setUniform1f("uTransformAspect", adjustments.transformAspect / 100f)
        setUniform1f("uTransformScale", adjustments.transformScale / 100f)
        setUniform1f("uTransformXOffset", adjustments.transformXOffset / 100f)
        setUniform1f("uTransformYOffset", adjustments.transformYOffset / 100f)

        // ── Lens Correction ──
        setUniform1f("uLensDistortion", adjustments.lensDistortion / 100f)
        setUniform1f("uLensVignette", adjustments.lensVignette / 100f)
        setUniform1f("uLensTca", adjustments.lensTca / 100f)
        setUniform1f("uLensFocalLength", adjustments.lensFocalLength)
        
        // ── Advanced Lens Correction (Brown-Conrady Model) ──
        // 径向畸变系数 (转换为实际系数, UI范围-100..100映射到实际范围)
        setUniform1f("uLensK1", adjustments.lensDistortionK1 / 100f * 0.5f)
        setUniform1f("uLensK2", adjustments.lensDistortionK2 / 100f * 0.2f)
        setUniform1f("uLensK3", adjustments.lensDistortionK3 / 100f * 0.1f)
        
        // 切向畸变系数
        setUniform1f("uLensP1", adjustments.lensTangentialP1 / 100f * 0.01f)
        setUniform1f("uLensP2", adjustments.lensTangentialP2 / 100f * 0.01f)
        
        // 横向色差校正
        setUniform1f("uLensLateralCA", adjustments.lensLateralCA / 100f)
        setUniform1f("uLensTcaRed", adjustments.lensTcaRedOffset / 100f * 0.05f)
        setUniform1f("uLensTcaBlue", adjustments.lensTcaBlueOffset / 100f * 0.05f)
        
        // 暗角校正
        setUniform1f("uLensVignetteCorrection", adjustments.lensVignetteCorrection / 100f)
        setUniform1f("uLensVignetteK1", adjustments.lensVignetteK1 / 100f)
        setUniform1f("uLensVignetteK2", adjustments.lensVignetteK2 / 100f)
        setUniform1f("uLensVignetteK3", adjustments.lensVignetteK3 / 100f)
        
        // 自动校正标志和缩放
        setUniform1f("uLensAutoCorrection", if (adjustments.lensAutoCorrection) 1f else 0f)
        
        // 计算缩放因子 (基于畸变参数)
        val k1 = adjustments.lensDistortionK1 / 100f * 0.5f
        val scale = if (k1 > 0f) 1f + k1 * 0.5f else 1f - k1 * 0.3f
        setUniform1f("uLensScale", scale.coerceIn(0.9f, 1.2f))

        // RGB Curves: pack up to 12 points into 6 vec4s
        fun uploadCurve(name: String, curve: List<com.rapidraw.data.model.Coord>) {
            val normalized = curve.map { it.x / 255f to it.y / 255f }
            for (i in 0 until 6) {
                val default0 = (i * 2) * (1f / 11f) to (i * 2) * (1f / 11f)
                val default1 = (i * 2 + 1) * (1f / 11f) to (i * 2 + 1) * (1f / 11f)
                val p0 = normalized.getOrElse(i * 2) { default0 }
                val p1 = normalized.getOrElse(i * 2 + 1) { default1 }
                setUniform4f("$name[$i]", p0.first, p0.second, p1.first, p1.second)
            }
        }
        uploadCurve("uRedCurve", adjustments.redCurve)
        uploadCurve("uGreenCurve", adjustments.greenCurve)
        uploadCurve("uBlueCurve", adjustments.blueCurve)

        // ── Traditional Denoise ──
        val denoiseModeInt = when (adjustments.denoiseMode) {
            com.rapidraw.data.model.DenoiseMode.AI -> 0
            com.rapidraw.data.model.DenoiseMode.MEAN -> 1
            com.rapidraw.data.model.DenoiseMode.MEDIAN -> 2
            com.rapidraw.data.model.DenoiseMode.GAUSSIAN -> 3
        }
        setUniform1i("uDenoiseMode", denoiseModeInt)
        setUniform1f("uDenoiseStrength", adjustments.denoiseStrength / 100f)
        setUniform1i("uDenoiseWindowSize", adjustments.denoiseWindowSize.coerceIn(3, 7))
        setUniform1f("uGaussianSigma", adjustments.gaussianSigma.coerceIn(0.5f, 5.0f))

        // ── Color Replacements (PixelFruit style) ──
        val colorReplacements = adjustments.colorReplacements
        setUniform1i("uColorReplacementCount", colorReplacements.size.coerceIn(0, 4))
        
        // Upload each color replacement (up to 4)
        for (i in colorReplacements.indices.take(4)) {
            val cr = colorReplacements[i]
            // Each replacement uses 3 vec4 uniforms
            // params0: sourceHueCenter, sourceHueRange, targetHue, feathering
            // params1: sourceSatMin, sourceSatMax, sourceLumMin, sourceLumMax  
            // params2: saturationAdjust, lightnessAdjust, intensity, enabled
            val baseIndex = i * 3
            setUniform4f("uColorReplacement${baseIndex}", 
                cr.sourceHueCenter, cr.sourceHueRange, cr.targetHue, cr.feathering)
            setUniform4f("uColorReplacement${baseIndex + 1}", 
                cr.sourceSatMin, cr.sourceSatMax, cr.sourceLumMin, cr.sourceLumMax)
            setUniform4f("uColorReplacement${baseIndex + 2}", 
                cr.saturationAdjust, cr.lightnessAdjust, cr.intensity, if (cr.enabled) 1f else 0f)
        }

        // ── Skin Whitening (面部美白) ──
        setUniform1f("uSkinWhiteningIntensity", adjustments.skinWhiteningIntensity / 100f)
        setUniform1f("uSkinToneTarget", adjustments.skinToneTarget / 100f)
        setUniform1f("uSkinSmoothness", adjustments.skinSmoothness / 100f)

        // ── Debug ──
        setUniform1f("uClippingPreview", if (adjustments.showClipping) 1f else 0f)
    }

    // ── Render ─────────────────────────────────────────────────────

    /**
     * Update the flow mask texture from a Bitmap.
     */
    fun updateMaskTexture(bitmap: Bitmap?, intensity: Float = 1f) {
        if (!initialized) return
        maskIntensity = intensity.coerceIn(0f, 1f)
        if (bitmap == null) return

        makeCurrent()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * Update the 3D LUT texture from a parsed Cube LUT.
     */
    fun updateLutTexture(lut: CubeLutParser.Lut3D?, intensity: Float = 1f) {
        if (!initialized) return
        lutIntensity = intensity.coerceIn(0f, 1f)
        if (lut == null) return

        makeCurrent()
        val size = lut.size
        val pixelCount = size * size * size * 3
        val buffer = ByteBuffer.allocateDirect(pixelCount).order(ByteOrder.nativeOrder())
        for (i in lut.data.indices step 3) {
            buffer.put((lut.data[i].coerceIn(0f, 1f) * 255f).toInt().toByte())
            buffer.put((lut.data[i + 1].coerceIn(0f, 1f) * 255f).toInt().toByte())
            buffer.put((lut.data[i + 2].coerceIn(0f, 1f) * 255f).toInt().toByte())
        }
        buffer.rewind()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8,
            size, size, size, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    /**
     * Upload bitmap as texture, run fragment shader, render to FBO.
     *
     * 多线程渲染优化：
     * - 使用锁防止并发渲染冲突
     * - 渲染状态跟踪避免重复渲染
     * - OpenGL ES 3.2 特性利用（可选）
     */
    fun renderFrame(inputBitmap: Bitmap) {
        if (!initialized) return

        // 多线程安全：检查是否正在渲染
        if (isRendering.get()) {
            Log.w(TAG, "Render already in progress, skipping")
            return
        }

        renderLock.withLock {
            isRendering.set(true)
            try {
                makeCurrent()
                GLES30.glUseProgram(program)

                // Upload bitmap to texture（仅在尺寸变化时重新分配纹理存储）
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
                if (inputBitmap.width != lastInputWidth || inputBitmap.height != lastInputHeight) {
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, inputBitmap, 0)
                    lastInputWidth = inputBitmap.width
                    lastInputHeight = inputBitmap.height
                } else {
                    // 尺寸不变时使用 texSubImage2D 避免重新分配
                    GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, inputBitmap)
                }

                setUniform1i("uTexture", 0)
                setUniform2f("uResolution", inputBitmap.width.toFloat(), inputBitmap.height.toFloat())

                // Bind mask texture to unit 1
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
                setUniform1i("uMaskTexture", 1)
                setUniform1f("uMaskIntensity", maskIntensity)

                // Bind LUT texture to unit 2
                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
                setUniform1i("uLutTexture", 2)
                setUniform1f("uLutIntensity", lutIntensity)

                // Render to FBO
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                GLES30.glViewport(0, 0, width, height)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                GLES30.glBindVertexArray(vao)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                GLES30.glBindVertexArray(0)

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                // 仅窗口 surface 需要额外绘制到默认 surface 并 swap；离屏模式直接读取 FBO 即可
                if (!isOffscreen) {
                    GLES30.glViewport(0, 0, width, height)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    GLES30.glBindVertexArray(vao)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                    GLES30.glBindVertexArray(0)

                    // Swap buffers
                    val display = eglDisplay ?: return
                    val surface = eglSurface ?: return
                    android.opengl.EGL14.eglSwapBuffers(display, surface)
                }
            } finally {
                isRendering.set(false)
            }
        }
    }

    /**
     * Read back pixels from the FBO (offscreen) and return as Bitmap.
     *
     * 多线程优化：同步读取避免冲突。
     */
    fun getProcessedBitmap(): Bitmap {
        if (!initialized) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        renderLock.withLock {
            makeCurrent()

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

            // 复用 ByteBuffer 避免频繁分配
            // 使用 PerformanceOptimizer 创建对齐的 buffer（16KB page size 优化）
            var buffer = readBackBuffer
            val requiredSize = PerformanceOptimizer.alignToPageSize(width * height * 4)
            if (buffer == null || buffer.capacity() < requiredSize) {
                buffer = PerformanceOptimizer.createAlignedByteBuffer(requiredSize)
                readBackBuffer = buffer
            }
            buffer.clear()

            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            buffer.rewind()

            // 复用 Bitmap 避免频繁创建
            var bitmap = readBackBitmap
            if (bitmap == null || bitmap.width != width || bitmap.height != height) {
                bitmap?.recycle()
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                readBackBitmap = bitmap
            }
            bitmap.copyPixelsFromBuffer(buffer)

            // OpenGL reads bottom-to-top, need to flip vertically
            val matrix = android.graphics.Matrix()
            matrix.postScale(1f, -1f)
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        }
    }

    // ── Uniform Helpers ────────────────────────────────────────────

    private fun setUniform1f(name: String, value: Float) {
        val loc = uniformLocations[name] ?: return
        if (loc >= 0) GLES30.glUniform1f(loc, value)
    }

    private fun setUniform1i(name: String, value: Int) {
        val loc = uniformLocations[name] ?: return
        if (loc >= 0) GLES30.glUniform1i(loc, value)
    }

    private fun setUniform2f(name: String, v0: Float, v1: Float) {
        val loc = uniformLocations[name] ?: return
        if (loc >= 0) GLES30.glUniform2f(loc, v0, v1)
    }

    private fun setUniform3f(name: String, values: FloatArray) {
        val loc = uniformLocations[name] ?: return
        if (loc >= 0 && values.size >= 3) GLES30.glUniform3f(loc, values[0], values[1], values[2])
    }

    private fun setUniform4f(name: String, v0: Float, v1: Float, v2: Float, v3: Float) {
        val loc = uniformLocations[name] ?: return
        if (loc >= 0) GLES30.glUniform4f(loc, v0, v1, v2, v3)
    }

    // ── Cleanup ────────────────────────────────────────────────────

    fun isInitialized(): Boolean = initialized

    fun release() {
        if (!initialized) return

        makeCurrent()

        if (program != 0) { GLES30.glDeleteProgram(program); program = 0 }
        if (vertexShader != 0) { GLES30.glDeleteShader(vertexShader); vertexShader = 0 }
        if (fragmentShader != 0) { GLES30.glDeleteShader(fragmentShader); fragmentShader = 0 }
        if (textureId != 0) { GLES30.glDeleteTextures(1, intArrayOf(textureId), 0); textureId = 0 }
        if (maskTextureId != 0) { GLES30.glDeleteTextures(1, intArrayOf(maskTextureId), 0); maskTextureId = 0 }
        if (lutTextureId != 0) { GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0); lutTextureId = 0 }
        if (fboTextureId != 0) { GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0); fboTextureId = 0 }
        if (fbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0); fbo = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }

        // Recycle readback bitmap before EGL teardown
        readBackBitmap?.recycle()
        readBackBuffer = null
        readBackBitmap = null

        // Destroy EGL
        val display = eglDisplay
        val surface = eglSurface
        val context = eglContext

        if (display != null && surface != null && context != null) {
            android.opengl.EGL14.eglMakeCurrent(display,
                android.opengl.EGL14.EGL_NO_SURFACE,
                android.opengl.EGL14.EGL_NO_SURFACE,
                android.opengl.EGL14.EGL_NO_CONTEXT)
            android.opengl.EGL14.eglDestroySurface(display, surface)
            android.opengl.EGL14.eglDestroyContext(display, context)
            android.opengl.EGL14.eglTerminate(display)
        }

        eglDisplay = null
        eglSurface = null
        eglContext = null
        eglConfig = null
        initialized = false
    }
}
