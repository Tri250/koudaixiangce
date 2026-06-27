package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.rapidraw.data.model.Adjustments
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 based GPU processing pipeline for real-time preview.
 * Uses EGL14 for context management and GLES30 for rendering.
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
    private var fbo = 0
    private var fboTextureId = 0
    private var width = 0
    private var height = 0
    private var initialized = false

    // Vertex buffers
    private var vao = 0
    private var vbo = 0

    // Uniform locations
    private var uniformLocations = mutableMapOf<String, Int>()

    // EGL
    private var eglDisplay = 0L
    private var eglContext = 0L
    private var eglSurface = 0L
    private var eglConfig = 0L

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

        setupEgl(surfaceTexture)
        compileShaders()
        setupGeometry()
        setupTexture()
        setupFramebuffer(width, height)

        initialized = true
    }

    /**
     * Initialize with offscreen rendering (no surface texture).
     */
    fun initializeOffscreen(width: Int, height: Int) {
        this.width = width
        this.height = height

        setupEglOffscreen()
        compileShaders()
        setupGeometry()
        setupTexture()
        setupFramebuffer(width, height)

        initialized = true
    }

    // ── EGL Setup ──────────────────────────────────────────────────

    private fun setupEgl(surfaceTexture: SurfaceTexture) {
        eglDisplay = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == 0L) {
            throw RuntimeException("Unable to get EGL display")
        }

        val version = IntArray(2)
        if (!android.opengl.EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        // Choose config
        val attribList = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
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
        if (!android.opengl.EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        eglConfig = configs[0]?.nativeHandle ?: 0L

        // Create context
        val contextAttribs = intArrayOf(
            android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            android.opengl.EGL14.EGL_NONE
        )

        eglContext = android.opengl.EGL14.eglCreateContext(
            eglDisplay, configs[0], 0L, contextAttribs, 0
        )?.nativeHandle ?: throw RuntimeException("Unable to create EGL context")

        // Create surface from SurfaceTexture
        val eglSurfaceObj = android.opengl.EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], surfaceTexture, intArrayOf(android.opengl.EGL14.EGL_NONE), 0
        )
        eglSurface = eglSurfaceObj?.nativeHandle ?: throw RuntimeException("Unable to create EGL surface")

        makeCurrent()
    }

    private fun setupEglOffscreen() {
        eglDisplay = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == 0L) {
            throw RuntimeException("Unable to get EGL display")
        }

        val version = IntArray(2)
        if (!android.opengl.EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        val attribList = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
            android.opengl.EGL14.EGL_RED_SIZE, 8,
            android.opengl.EGL14.EGL_GREEN_SIZE, 8,
            android.opengl.EGL14.EGL_BLUE_SIZE, 8,
            android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
            android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
            android.opengl.EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!android.opengl.EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        eglConfig = configs[0]?.nativeHandle ?: 0L

        val contextAttribs = intArrayOf(
            android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            android.opengl.EGL14.EGL_NONE
        )

        eglContext = android.opengl.EGL14.eglCreateContext(
            eglDisplay, configs[0], 0L, contextAttribs, 0
        )?.nativeHandle ?: throw RuntimeException("Unable to create EGL context")

        // Create pbuffer surface for offscreen rendering
        val pbufferAttribs = intArrayOf(
            android.opengl.EGL14.EGL_WIDTH, width,
            android.opengl.EGL14.EGL_HEIGHT, height,
            android.opengl.EGL14.EGL_NONE
        )

        val eglSurfaceObj = android.opengl.EGL14.eglCreatePbufferSurface(
            eglDisplay, configs[0], pbufferAttribs, 0
        )
        eglSurface = eglSurfaceObj?.nativeHandle ?: throw RuntimeException("Unable to create EGL pbuffer surface")

        makeCurrent()
    }

    private fun makeCurrent() {
        val displayObj = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)

        // Reconstruct EGL objects from handles
        val surfaceObj = android.opengl.EGLSurface.create(eglSurface)
        val contextObj = android.opengl.EGLContext.create(eglContext)

        if (!android.opengl.EGL14.eglMakeCurrent(displayObj, surfaceObj, surfaceObj, contextObj)) {
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
            "uChromaticAberration",
            "uAgXEnabled", "uAgXContrast", "uAgXPedestal",
            "uClippingPreview",
            // New uniforms for film simulation & advanced controls
            "uToneLevel", "uFilmIntensity", "uGreenMagenta", "uSoftGlow",
            "uFilmHighlightRollOff", "uFilmShadowLift", "uFilmDrCompression",
            "uFilmRedShift", "uFilmGreenShift", "uFilmBlueShift",
            "uFilmSaturation", "uFilmContrast",
            "uFilmGrainAmount", "uFilmGrainSize", "uFilmGrainRoughness",
            "uFilmCurve[0]", "uFilmCurve[1]", "uFilmCurve[2]",
            "uFilmCurve[3]", "uFilmCurve[4]", "uFilmCurve[5]"
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
        setUniform1f("uFilmHighlightRollOff", adjustments.filmHighlightRollOff)
        setUniform1f("uFilmShadowLift", adjustments.filmShadowLift)
        setUniform1f("uFilmDrCompression", adjustments.filmDrCompression)
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
        val chromaticAberration = (adjustments.chromaticAberrationRedCyan +
            adjustments.chromaticAberrationBlueYellow) / 200f            // combined → -1..1
        setUniform1f("uChromaticAberration", chromaticAberration)

        // ── Tone Mapping ──
        val agxEnabled = adjustments.toneMapper == "agx"
        setUniform1f("uAgXEnabled", if (agxEnabled) 1f else 0f)
        setUniform1f("uAgXContrast", 0f)
        setUniform1f("uAgXPedestal", 0f)

        // ── Debug ──
        setUniform1f("uClippingPreview", if (adjustments.showClipping) 1f else 0f)
    }

    // ── Render ─────────────────────────────────────────────────────

    /**
     * Upload bitmap as texture, run fragment shader, render to FBO.
     */
    fun renderFrame(inputBitmap: Bitmap) {
        if (!initialized) return

        makeCurrent()
        GLES30.glUseProgram(program)

        // Upload bitmap to texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, inputBitmap, 0)

        setUniform1i("uTexture", 0)
        setUniform2f("uResolution", inputBitmap.width.toFloat(), inputBitmap.height.toFloat())

        // Render to FBO
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // Also render to default surface (eglSurface) for display
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap buffers
        val displayObj = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        val surfaceObj = android.opengl.EGLSurface.create(eglSurface)
        android.opengl.EGL14.eglSwapBuffers(displayObj, surfaceObj)
    }

    /**
     * Read back pixels from the FBO (offscreen) and return as Bitmap.
     */
    fun getProcessedBitmap(): Bitmap {
        if (!initialized) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        makeCurrent()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

        val buffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        buffer.rewind()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // OpenGL reads bottom-to-top, need to flip vertically
        val matrix = android.graphics.Matrix()
        matrix.postScale(1f, -1f)
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        bitmap.recycle()

        return flipped
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

        if (program != 0) GLES30.glDeleteProgram(program)
        if (vertexShader != 0) GLES30.glDeleteShader(vertexShader)
        if (fragmentShader != 0) GLES30.glDeleteShader(fragmentShader)
        if (textureId != 0) GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (fboTextureId != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)

        // Destroy EGL
        val displayObj = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        val surfaceObj = android.opengl.EGLSurface.create(eglSurface)
        val contextObj = android.opengl.EGLContext.create(eglContext)

        android.opengl.EGL14.eglMakeCurrent(displayObj,
            android.opengl.EGL14.EGL_NO_SURFACE,
            android.opengl.EGL14.EGL_NO_SURFACE,
            android.opengl.EGL14.EGL_NO_CONTEXT)
        android.opengl.EGL14.eglDestroySurface(displayObj, surfaceObj)
        android.opengl.EGL14.eglDestroyContext(displayObj, contextObj)
        android.opengl.EGL14.eglTerminate(displayObj)

        initialized = false
    }
}
