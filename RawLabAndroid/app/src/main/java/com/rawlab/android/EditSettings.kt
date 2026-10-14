package com.rawlab.android

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

data class EditSettings(
    val film: String = "neutral",
    val strength: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val contrast: Float = 0f,
    val toneCurve: Float = 0f,
    val curve: ToneCurveSettings = ToneCurveSettings(),
    val saturation: Float = 0f,
    val customWb: Boolean = false,
    val temperature: Float = 6500f,
    val tint: Float = 0f,
    val denoise: DenoiseSettings = DenoiseSettings(),
    val sharpening: Float = 0f,
    val effects: PhotoEffectsSettings = PhotoEffectsSettings(),
    val displayChromaDenoise: Int = 0,
    val exposureMode: Int = 1,
    val hsl: HslSettings = HslSettings(),
    val recipeID: String? = null,
) {
    init {
        require(exposureMode in 0..2)
        require(strength.isFinite() && strength in 0f..2f)
        require(exposure.isFinite() && exposure in -4f..4f)
        require(highlights.isFinite() && highlights in -1f..1f)
        require(shadows.isFinite() && shadows in -1f..1f)
        require(contrast.isFinite() && contrast in -1f..1f)
        require(toneCurve.isFinite() && toneCurve in -1f..1f)
        require(saturation.isFinite() && saturation in -1f..1f)
        require(temperature.isFinite() && temperature in 2000f..50000f)
        require(tint.isFinite() && tint in -150f..150f)
        require(sharpening.isFinite() && sharpening in 0f..2f)
        require(displayChromaDenoise in 0..2)
    }
    fun reset() = EditSettings(film = film)
    val recipeScale get() = if (recipeID == null) 1f else strength
    val renderHsl get() = hsl.scaled(recipeScale)
    val renderCurveLut: FloatArray? get() = if (curve.isDefault) null else curve.bakeLut()
    fun applyRecipe(recipe: MasterRecipe) = copy(film = "neutral", recipeID = recipe.id, strength = 1f,
        exposure = recipe.exposure, shadows = recipe.shadows, highlights = recipe.highlights,
        contrast = recipe.contrast, saturation = recipe.saturation, tint = recipe.tint, hsl = recipe.hsl)
    fun selectFilm(id: String, cameraTint: Float? = null): EditSettings {
        if (recipeID == null) return copy(film = id)
        val independent = if (!customWb && (tint == 0f || cameraTint == null))
            copy(recipeID = null, tint = cameraTint ?: 0f)
        else copy(recipeID = null, customWb = true, tint = ((cameraTint ?: 0f) + tint).coerceIn(-150f, 150f))
        return independent.copy(film = id)
    }
}

enum class DenoisePreset { DETAIL, CLEAN, CUSTOM }

/** 曲线控制点：x 为输入亮度（首点 0、尾点 1、严格递增），y 为映射输出，均在 [0,1]。 */
data class CurvePoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && x in 0f..1f && y.isFinite() && y in 0f..1f)
    }
}

/**
 * 交互式色调曲线：2..8 个控制点，经 Fritsch–Carlson 单调三次 Hermite 样条烘焙为
 * 256 桶 LUT，交由核心 sony2fuji_session_set_tone_curve_lut 渲染（CPU/GLES 同一 LUT）。
 */
class ToneCurveSettings(private val controlPoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))) {
    init {
        require(controlPoints.size in 2..8)
        require(controlPoints.first().x == 0f && controlPoints.last().x == 1f)
        require(controlPoints.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    val isDefault: Boolean get() = controlPoints.size == 2 &&
        controlPoints[0].y == 0f && controlPoints[1].y == 1f
    fun points(): List<CurvePoint> = controlPoints

    /** 与核心 buildToneCurve 折线等效的控制点，用于把旧 S 曲线标量迁入编辑器。 */
    fun withScalar(scalar: Float): ToneCurveSettings {
        if (!scalar.isFinite() || scalar == 0f) return this
        // 与核心 buildToneCurve 的 signedPow(scalar, 1.2) 一致（double 精度）。
        val strength = (sign(scalar) * abs(scalar).toDouble().pow(1.2)).toFloat()
        fun clamp01(v: Float) = v.coerceIn(0f, 1f)
        return ToneCurveSettings(listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, clamp01(0.25f - strength * 0.2f)),
            CurvePoint(0.5f, clamp01(0.5f + strength * 0.05f)),
            CurvePoint(0.75f, clamp01(0.75f + strength * 0.2f)),
            CurvePoint(1f, 1f)))
    }

    /** 拖动已有控制点：端点 x 固定，中间点 x 在相邻点之间留出间隙。 */
    fun movePoint(index: Int, x: Float, y: Float): ToneCurveSettings {
        if (index !in controlPoints.indices || !x.isFinite() || !y.isFinite()) return this
        val bound = if (controlPoints.size > 2) 1f / (controlPoints.size - 1) * 0.25f else 0f
        val newX = when (index) {
            0 -> 0f
            controlPoints.size - 1 -> 1f
            else -> x.coerceIn(controlPoints[index - 1].x + bound, controlPoints[index + 1].x - bound)
        }
        val moved = CurvePoint(newX, y.coerceIn(0f, 1f))
        if (moved == controlPoints[index]) return this
        return ToneCurveSettings(controlPoints.toMutableList().also { it[index] = moved })
    }

    /** 在指定输入亮度处加点（取当前曲线值），超过 8 个点时返回原曲线。 */
    fun addPoint(x: Float): ToneCurveSettings {
        if (!x.isFinite() || x <= 0f || x >= 1f || controlPoints.size >= 8) return this
        val y = sample(x)
        val position = controlPoints.indexOfFirst { it.x > x }.let { if (it == -1) controlPoints.size - 1 else it }
        if (controlPoints.any { abs(it.x - x) < 1f / 255f }) return this
        return ToneCurveSettings(controlPoints.toMutableList().also { it.add(position, CurvePoint(x, y)) })
    }

    /** 删除中间控制点；仅剩两端点或索引无效时返回原曲线。 */
    fun removePoint(index: Int): ToneCurveSettings {
        if (index !in 1 until controlPoints.size - 1) return this
        return ToneCurveSettings(controlPoints.filterIndexed { i, _ -> i != index })
    }

    /** 曲线在任意输入亮度处的样条取值。 */
    fun sample(x: Float): Float {
        val lut = bakeLut()
        val p = x.coerceIn(0f, 1f) * 255f
        val lo = p.toInt().coerceIn(0, 255)
        val t = p - lo
        val a = lut[lo]
        val b = lut[minOf(lo + 1, 255)]
        return a + (b - a) * t
    }

    /**
     * Fritsch–Carlson 单调三次样条烘焙 256 桶 LUT；两端点边界用单侧斜率。
     * 单调段间斜率取调和平均，避免曲线过冲（与核心 sampleToneCurveLut 线性插值一致）。
     */
    fun bakeLut(): FloatArray {
        val points = controlPoints
        val n = points.size
        val deltas = FloatArray(n - 1) { i ->
            (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x)
        }
        val slopes = FloatArray(n)
        slopes[0] = deltas[0]
        slopes[n - 1] = deltas[n - 2]
        for (i in 1 until n - 1) {
            slopes[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f
            else 2f * deltas[i - 1] * deltas[i] / (deltas[i - 1] + deltas[i])
        }
        val lut = FloatArray(256)
        var segment = 0
        for (bin in 0..255) {
            val x = bin / 255f
            while (segment < n - 2 && x > points[segment + 1].x) ++segment
            val x0 = points[segment].x
            val x1 = points[segment + 1].x
            val span = (x1 - x0).coerceAtLeast(1e-6f)
            val t = ((x - x0) / span).coerceIn(0f, 1f)
            val y0 = points[segment].y
            val y1 = points[segment + 1].y
            val m0 = slopes[segment] * span
            val m1 = slopes[segment + 1] * span
            val t2 = t * t
            val t3 = t2 * t
            lut[bin] = ((2f * t3 - 3f * t2 + 1f) * y0 + (t3 - 2f * t2 + t) * m0 +
                (-2f * t3 + 3f * t2) * y1 + (t3 - t2) * m1).coerceIn(0f, 1f)
        }
        return lut
    }

    /** "x:y;x:y;..." 三位小数编码，用于持久化第 50 字段。 */
    fun encode(): String = controlPoints.joinToString(";") { "%.3f:%.3f".format(Locale.ROOT, it.x, it.y) }

    override fun equals(other: Any?) = other is ToneCurveSettings && controlPoints == other.controlPoints
    override fun hashCode() = controlPoints.hashCode()

    companion object {
        fun decode(encoded: String): ToneCurveSettings {
            val points = encoded.split(';').filter { it.isNotBlank() }.map { pair ->
                val parts = pair.split(':')
                require(parts.size == 2)
                CurvePoint(parts[0].toFloat(), parts[1].toFloat())
            }
            return ToneCurveSettings(points)
        }
    }
}

class HslSettings(values: List<Float> = List(24) { 0f }) {
    private val values = values.toList()
    init { require(this.values.size == 24 && this.values.all { it.isFinite() && it in -100f..100f }) }
    val isIdentity get() = values.all { it == 0f }
    fun packed() = values.toFloatArray()
    fun scaled(strength: Float) = HslSettings(values.map { (it * strength).coerceIn(-100f, 100f) })
    fun value(axis: Int, channel: Int) = values[axis * 8 + channel]
    fun withValue(axis: Int, channel: Int, value: Float): HslSettings {
        require(axis in 0..2 && channel in 0..7)
        if (!value.isFinite()) return this
        return HslSettings(values.toMutableList().also { it[axis * 8 + channel] = value.coerceIn(-100f, 100f) })
    }
    override fun equals(other: Any?) = other is HslSettings && values == other.values
    override fun hashCode() = values.hashCode()
}

data class DenoiseSettings(val enabled: Boolean = false, val luma: Float = 0f,
    val chroma: Float = 46f, val coarse: Float = 50f) {
    init { require(listOf(luma, chroma, coarse).all { it.isFinite() && it in 0f..100f }) }
    val preset: DenoisePreset get() = when {
        luma == 0f && chroma == 46f && coarse == 50f -> DenoisePreset.DETAIL
        luma == 10f && chroma == 72f && coarse == 100f -> DenoisePreset.CLEAN
        else -> DenoisePreset.CUSTOM
    }
    fun withPreset(value: DenoisePreset) = when (value) {
        DenoisePreset.DETAIL -> DenoiseSettings(enabled = true)
        DenoisePreset.CLEAN -> DenoiseSettings(true, 10f, 72f, 100f)
        DenoisePreset.CUSTOM -> this
    }
}

enum class PhotoEffectParameter(
    val isVignette: Boolean,
    val range: ClosedFloatingPointRange<Float>,
    val defaultValue: Float,
) {
    VIGNETTE_AMOUNT(true, -100f..100f, 0f),
    VIGNETTE_MIDPOINT(true, 0f..100f, 50f),
    VIGNETTE_ROUNDNESS(true, -100f..100f, 0f),
    VIGNETTE_FEATHER(true, 0f..100f, 50f),
    VIGNETTE_HIGHLIGHTS(true, 0f..100f, 0f),
    GRAIN_AMOUNT(false, 0f..100f, 0f),
    GRAIN_SIZE(false, 0f..100f, 25f),
    GRAIN_ROUGHNESS(false, 0f..100f, 50f),
}

data class PhotoEffectsSettings(
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val vignetteHighlights: Float = 0f,
    val grainAmount: Float = 0f,
    val grainSize: Float = 25f,
    val grainRoughness: Float = 50f,
) {
    init {
        require(vignetteAmount.isFinite() && vignetteAmount in -100f..100f)
        require(vignetteMidpoint.isFinite() && vignetteMidpoint in 0f..100f)
        require(vignetteRoundness.isFinite() && vignetteRoundness in -100f..100f)
        require(vignetteFeather.isFinite() && vignetteFeather in 0f..100f)
        require(vignetteHighlights.isFinite() && vignetteHighlights in 0f..100f)
        require(grainAmount.isFinite() && grainAmount in 0f..100f)
        require(grainSize.isFinite() && grainSize in 0f..100f)
        require(grainRoughness.isFinite() && grainRoughness in 0f..100f)
    }

    /** Numeric controls clamp finite input and ignore non-finite drafts. */
    fun withEffect(parameter: PhotoEffectParameter, value: Float): PhotoEffectsSettings {
        if (!value.isFinite()) return this
        val rounded = if (value >= 0f) floor(value + .5f) else ceil(value - .5f)
        val normalized = rounded.coerceIn(parameter.range.start, parameter.range.endInclusive)
        return when (parameter) {
            PhotoEffectParameter.VIGNETTE_AMOUNT -> copy(vignetteAmount = normalized)
            PhotoEffectParameter.VIGNETTE_MIDPOINT -> copy(vignetteMidpoint = normalized)
            PhotoEffectParameter.VIGNETTE_ROUNDNESS -> copy(vignetteRoundness = normalized)
            PhotoEffectParameter.VIGNETTE_FEATHER -> copy(vignetteFeather = normalized)
            PhotoEffectParameter.VIGNETTE_HIGHLIGHTS -> copy(vignetteHighlights = normalized)
            PhotoEffectParameter.GRAIN_AMOUNT -> copy(grainAmount = normalized)
            PhotoEffectParameter.GRAIN_SIZE -> copy(grainSize = normalized)
            PhotoEffectParameter.GRAIN_ROUGHNESS -> copy(grainRoughness = normalized)
        }
    }

    fun value(parameter: PhotoEffectParameter): Float = when (parameter) {
        PhotoEffectParameter.VIGNETTE_AMOUNT -> vignetteAmount
        PhotoEffectParameter.VIGNETTE_MIDPOINT -> vignetteMidpoint
        PhotoEffectParameter.VIGNETTE_ROUNDNESS -> vignetteRoundness
        PhotoEffectParameter.VIGNETTE_FEATHER -> vignetteFeather
        PhotoEffectParameter.VIGNETTE_HIGHLIGHTS -> vignetteHighlights
        PhotoEffectParameter.GRAIN_AMOUNT -> grainAmount
        PhotoEffectParameter.GRAIN_SIZE -> grainSize
        PhotoEffectParameter.GRAIN_ROUGHNESS -> grainRoughness
    }

    fun resetEffects(vignette: Boolean): PhotoEffectsSettings =
        PhotoEffectParameter.entries.filter { it.isVignette == vignette }
            .fold(this) { state, parameter -> state.withEffect(parameter, parameter.defaultValue) }

    fun isEffectDefault(vignette: Boolean): Boolean =
        PhotoEffectParameter.entries.filter { it.isVignette == vignette }
            .all { value(it) == it.defaultValue }
}

data class Film(val id: String, val name: String, val file: String?) {
    companion object {
        val all = listOf(
            Film("neutral", "中性", null),
            Film("provia", "标准", "PROVIA"), Film("velvia", "鲜艳", "Velvia"),
            Film("astia", "柔和", "ASTIA"), Film("classic-chrome", "经典铬", "CLASSIC-CHROME"),
            Film("classic-neg", "经典负片", "CLASSIC-Neg."), Film("reala-ace", "真实", "REALA-ACE"),
            Film("pro-neg-std", "专业负片", "PRO-Neg.Std"), Film("eterna", "永恒", "ETERNA"),
            Film("eterna-bb", "漂白", "ETERNA-BB"), Film("acros", "细腻", "ACROS"),
        )
    }
}

data class LookChoice(val id: String, val name: String, val managed: Boolean, val available: Boolean = true) {
    companion object {
        val builtIns = Film.all.map { Film(it.id, it.name, it.file).toChoice() }

        private fun Film.toChoice() = LookChoice(id, name, managed = false)
    }
}
