package com.rawlab.android

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class EditSettings(
    val film: String = "neutral",
    val strength: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val contrast: Float = 0f,
    val toneCurve: Float = 0f,
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
    val lens: LensSettings = LensSettings(),
    // v0.7.0 schema: null keeps the v0.6.3 behavior; the legacy scalar
    // toneCurve stays alongside curve for rollback safety.
    val crop: CropSettings? = null,
    val curve: CurveSettings? = null,
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
    // JNI curve payload: flattened (x, y) pairs; empty clears the session curve.
    fun curvePacked(): FloatArray {
        val curve = curve ?: return FloatArray(0)
        if (curve.isIdentity) return FloatArray(0)
        return FloatArray(curve.points.size * 2) { i ->
            if (i % 2 == 0) curve.points[i / 2].x else curve.points[i / 2].y
        }
    }
    // JNI crop payload: (x, y, w, h, angle); null clears the session crop.
    // A full-frame rectangle renders identically to no crop, so it stays inactive.
    fun cropPacked(): FloatArray? {
        val crop = crop ?: return null
        if (crop.isFullFrame) return null
        return floatArrayOf(crop.x, crop.y, crop.w, crop.h, crop.angle)
    }
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

data class LensSettings(val distortion: Boolean = false, val tca: Boolean = false,
    val autoCrop: Boolean = true, val manual: Boolean = false, val profileId: String? = null) {
    val enabled: Boolean get() = distortion || tca
    val coreProfileId: String get() = if (manual) profileId ?: "__rawlab_manual_selection_required__" else ""
    fun encode(): String = listOf(if (distortion) 1 else 0, if (tca) 1 else 0,
        if (autoCrop) 1 else 0, if (manual) 1 else 0,
        profileId?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "")
        .joinToString("|")
    companion object {
        fun decode(value: String?): LensSettings {
            if (value == null) return LensSettings()
            val fields = value.split('|')
            require(fields.size == 5 && fields.take(4).all { it == "0" || it == "1" })
            return LensSettings(fields[0] == "1", fields[1] == "1", fields[2] == "1", fields[3] == "1",
                fields[4].takeIf { it.isNotEmpty() }?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) })
        }
    }
}

/** v0.7.0 schema: normalized source rectangle; invalid records fall back to null. */
data class CropSettings(
    val aspect: String = "original",
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f,
    val angle: Float = 0f,
) {
    init {
        require(aspect in ASPECTS)
        require(listOf(x, y, w, h, angle).all { it.isFinite() })
        require(x >= 0f && y >= 0f && w > 0f && h > 0f && x + w <= 1f && y + h <= 1f && abs(angle) <= 45f)
    }

    val isFullFrame: Boolean get() = x == 0f && y == 0f && w == 1f && h == 1f && angle == 0f

    fun encode(): String = listOf(aspect, x, y, w, h, angle).joinToString("|")

    companion object {
        val ASPECTS = setOf("original", "free", "1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16")

        fun decode(value: String?): CropSettings? {
            if (value == null) return null
            val fields = value.split('|')
            if (fields.size != 6) return null
            val angle = fields[5].toFloatOrNull() ?: return null
            return runCatching {
                CropSettings(fields[0], fields[1].toFloatOrNull() ?: return null,
                    fields[2].toFloatOrNull() ?: return null, fields[3].toFloatOrNull() ?: return null,
                    fields[4].toFloatOrNull() ?: return null, angle)
            }.getOrNull()
        }
    }
}

data class CurvePoint(val x: Float, val y: Float)

class CurveSettings(val points: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))) {
    init {
        require(points.size in 2..MAX_POINTS)
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f })
        require(points.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    val isIdentity: Boolean get() = points == listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

    override fun equals(other: Any?) = other is CurveSettings && points == other.points
    override fun hashCode() = points.hashCode()

    fun encode(): String = points.joinToString(";") { "${it.x},${it.y}" }

    companion object {
        const val MAX_POINTS = 64

        fun decode(value: String?): CurveSettings? {
            if (value.isNullOrBlank()) return null
            val parsed = value.split(';').map {
                val parts = it.split(',')
                if (parts.size != 2) return null
                val x = parts[0].toFloatOrNull() ?: return null
                val y = parts[1].toFloatOrNull() ?: return null
                CurvePoint(x, y)
            }
            return runCatching { CurveSettings(parsed) }.getOrNull()
        }
    }
}

enum class DenoisePreset { DETAIL, CLEAN, CUSTOM }

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
