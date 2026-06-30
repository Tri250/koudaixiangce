package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.rapidraw.R
import com.rapidraw.data.model.Adjustments
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 based GPU processing pipeline for real-time preview.
 * Uses EGL14 for context management and GLES30 for rendering.
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
    private var readBackBuffer: java.nio.ByteBuffer? = null
    private var readBackBitmap: android.graphics.Bitmap? = null
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

    // ── Shader Compilation ─────────────────────────────────────────

    private fun compileShaders() {
        // Compile vertex shader
        vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)

        // Load fragment shader from resources
        val fragSource = context.resources.openRawResource(R.raw.image_adjustment)
            .bufferedReader().use { it.readText() }
        fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragSource)

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
    }

    private fun loadShader(type: Int, source: String): Int {
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

        return shader
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
            "uColorScienceMode",
            "uAgXEnabled", "uAgXContrast", "uAgXPedestal",
            "uAces2DisplayColorSpace", "uAces2Eotf", "uAces2PeakLuminance",
            "uOpenDrtDisplayColorSpace", "uOpenDrtEotf", "uOpenDrtPeakLuminance",
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
            // Channel Mixer
            "uChannelMixerRR", "uChannelMixerRG", "uChannelMixerRB",
            "uChannelMixerGR", "uChannelMixerGG", "uChannelMixerGB",
            "uChannelMixerBR", "uChannelMixerBG", "uChannelMixerBB",
            "uChannelMixerMono",
            // Split Toning
            "uSplitToneHLHue", "uSplitToneHLSat",
            "uSplitToneSHHue", "uSplitToneSHSat",
            "uSplitToneBalance",
            // Local Tint
            "uShadowsTintHue", "uShadowsTintSat",
            "uHighlightsTintHue", "uHighlightsTintSat",
            // Edge Light
            "uEdgeLightAmount", "uEdgeLightHue", "uEdgeLightSat",
            // Color Range Selector
            "uColorRangeHue", "uColorRangeWidth",
            "uColorRangeSatAdjust", "uColorRangeLumAdjust",
            // Blur-based creative effects
            "uBlurGlow", "uBlurHalation",
            "uRotation", "uOrientationSteps",
            "uFlipHorizontal", "uFlipVertical",
            "uCropAspectRatio",
            "uTransformDistortion", "uTransformVertical", "uTransformHorizontal",
            "uTransformRotate", "uTransformAspect", "uTransformScale",
            "uTransformXOffset", "uTransformYOffset",
            "uLensDistortion", "uLensVignette", "uLensTca", "uLensFocalLength",
            "uRedCurve[0]", "uRedCurve[1]", "uRedCurve[2]", "uRedCurve[3]", "uRedCurve[4]", "uRedCurve[5]",
            "uGreenCurve[0]", "uGreenCurve[1]", "uGreenCurve[2]", "uGreenCurve[3]", "uGreenCurve[4]", "uGreenCurve[5]",
            "uBlueCurve[0]", "uBlueCurve[1]", "uBlueCurve[2]", "uBlueCurve[3]", "uBlueCurve[4]", "uBlueCurve[5]",
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

        try {
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

        // ── Tone Mapping / Color Science ──
        // ColorScience.Mode ordinal: 0=AGX, 1=ACES_2, 2=OPEN_DRT, 3=STANDARD
        val csMode = adjustments.colorScienceMode.coerceIn(0, 3)
        setUniform1i("uColorScienceMode", csMode)
        val agxEnabled = adjustments.toneMapper == "agx" || csMode == 0
        setUniform1f("uAgXEnabled", if (agxEnabled) 1f else 0f)
        setUniform1f("uAgXContrast", adjustments.agxContrast.coerceIn(0f, 1f))
        setUniform1f("uAgXPedestal", adjustments.agxPedestal.coerceIn(0f, 0.5f))
        // ACES 2.0 display parameters
        setUniform1i("uAces2DisplayColorSpace", adjustments.displayColorSpace.coerceIn(0, 2))
        setUniform1i("uAces2Eotf", adjustments.eotf.coerceIn(0, 2))
        setUniform1f("uAces2PeakLuminance", adjustments.peakLuminanceNits.coerceIn(100f, 10000f))
        // OpenDRT display parameters
        setUniform1i("uOpenDrtDisplayColorSpace", adjustments.displayColorSpace.coerceIn(0, 2))
        setUniform1i("uOpenDrtEotf", adjustments.eotf.coerceIn(0, 2))
        setUniform1f("uOpenDrtPeakLuminance", adjustments.peakLuminanceNits.coerceIn(100f, 10000f))

        // ── LUT Intensity ──
        setUniform1f("uLutIntensity", adjustments.activeLutBlend.coerceIn(0f, 1f))

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
        // Channel Mixer (0-200% range → 0.0-2.0)
        setUniform1f("uChannelMixerRR", adjustments.channelMixerRedOutRed / 100f)
        setUniform1f("uChannelMixerRG", adjustments.channelMixerRedOutGreen / 100f)
        setUniform1f("uChannelMixerRB", adjustments.channelMixerRedOutBlue / 100f)
        setUniform1f("uChannelMixerGR", adjustments.channelMixerGreenOutRed / 100f)
        setUniform1f("uChannelMixerGG", adjustments.channelMixerGreenOutGreen / 100f)
        setUniform1f("uChannelMixerGB", adjustments.channelMixerGreenOutBlue / 100f)
        setUniform1f("uChannelMixerBR", adjustments.channelMixerBlueOutRed / 100f)
        setUniform1f("uChannelMixerBG", adjustments.channelMixerBlueOutGreen / 100f)
        setUniform1f("uChannelMixerBB", adjustments.channelMixerBlueOutBlue / 100f)
        setUniform1f("uChannelMixerMono", if (adjustments.channelMixerMonochrome) 1f else 0f)
        // Split Toning
        setUniform1f("uSplitToneHLHue", adjustments.splitToningHighlightHue)
        setUniform1f("uSplitToneHLSat", adjustments.splitToningHighlightSaturation / 100f)
        setUniform1f("uSplitToneSHHue", adjustments.splitToningShadowHue)
        setUniform1f("uSplitToneSHSat", adjustments.splitToningShadowSaturation / 100f)
        setUniform1f("uSplitToneBalance", adjustments.splitToningBalance / 100f)
        // Local Tint
        setUniform1f("uShadowsTintHue", adjustments.shadowsTintHue)
        setUniform1f("uShadowsTintSat", adjustments.shadowsTintSaturation / 100f)
        setUniform1f("uHighlightsTintHue", adjustments.highlightsTintHue)
        setUniform1f("uHighlightsTintSat", adjustments.highlightsTintSaturation / 100f)
        // Edge Light
        setUniform1f("uEdgeLightAmount", adjustments.edgeLightAmount / 100f)
        setUniform1f("uEdgeLightHue", adjustments.edgeLightHue)
        setUniform1f("uEdgeLightSat", adjustments.edgeLightSaturation)
        // Color Range Selector
        setUniform1f("uColorRangeHue", adjustments.colorRangeHue)
        setUniform1f("uColorRangeWidth", adjustments.colorRangeWidth)
        setUniform1f("uColorRangeSatAdjust", adjustments.colorRangeSatAdjust / 100f)
        setUniform1f("uColorRangeLumAdjust", adjustments.colorRangeLumAdjust / 100f)
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

        // ── Debug ──
        setUniform1f("uClippingPreview", if (adjustments.showClipping) 1f else 0f)
        } catch (e: RuntimeException) {
            Log.e(TAG, "updateAdjustments failed", e)
            initialized = false
        } catch (e: Exception) {
            Log.e(TAG, "updateAdjustments failed", e)
            initialized = false
        }
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
     */
    fun renderFrame(inputBitmap: Bitmap) {
        if (!initialized) return

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
        } catch (e: RuntimeException) {
            Log.e(TAG, "renderFrame failed", e)
            initialized = false
        } catch (e: Exception) {
            Log.e(TAG, "renderFrame failed", e)
            initialized = false
        }
    }

    /**
     * Read back pixels from the FBO (offscreen) and return as Bitmap.
     */
    fun getProcessedBitmap(): Bitmap {
        if (!initialized) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        try {
            makeCurrent()

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

            // 复用 ByteBuffer 避免频繁分配
            var buffer = readBackBuffer
            if (buffer == null || buffer.capacity() < width * height * 4) {
                buffer = ByteBuffer.allocateDirect(width * height * 4)
                    .order(ByteOrder.nativeOrder())
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
        } catch (e: RuntimeException) {
            Log.e(TAG, "getProcessedBitmap failed", e)
            initialized = false
        } catch (e: Exception) {
            Log.e(TAG, "getProcessedBitmap failed", e)
            initialized = false
        }
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
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
