package com.alcedo.studio.core

import android.content.Context
import com.alcedo.studio.data.model.ExifData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LensfunDatabase(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val builtInLenses = listOf(
        LensProfile(
            id = "canon_24_70_f28",
            brand = "Canon",
            model = "EF 24-70mm f/2.8L II USM",
            focalLengthMin = 24f,
            focalLengthMax = 70f,
            apertureMin = 2.8f,
            distortionCoefficients = mapOf(
                24f to DistortionParams(k1 = 0.001f, k2 = 0.0005f, k3 = 0.0001f),
                50f to DistortionParams(k1 = 0.0008f, k2 = 0.0003f, k3 = 0.00005f),
                70f to DistortionParams(k1 = 0.0005f, k2 = 0.0002f, k3 = 0.00001f)
            ),
            tcaCoefficients = mapOf(
                24f to TcaParams(vr = 1.001f, vb = 0.999f),
                50f to TcaParams(vr = 1.0005f, vb = 0.9995f),
                70f to TcaParams(vr = 1.0002f, vb = 0.9998f)
            ),
            vignettingCoefficients = mapOf(
                24f to VignettingParams(v1 = 0.05f, v2 = 0.02f, v3 = 0.01f),
                50f to VignettingParams(v1 = 0.03f, v2 = 0.015f, v3 = 0.005f),
                70f to VignettingParams(v1 = 0.02f, v2 = 0.01f, v3 = 0.003f)
            )
        ),
        LensProfile(
            id = "nikon_24_70_f28",
            brand = "Nikon",
            model = "AF-S NIKKOR 24-70mm f/2.8E ED VR",
            focalLengthMin = 24f,
            focalLengthMax = 70f,
            apertureMin = 2.8f,
            distortionCoefficients = mapOf(
                24f to DistortionParams(k1 = -0.002f, k2 = 0.001f, k3 = -0.0005f),
                50f to DistortionParams(k1 = -0.001f, k2 = 0.0005f, k3 = -0.0002f),
                70f to DistortionParams(k1 = -0.0005f, k2 = 0.0002f, k3 = -0.0001f)
            ),
            tcaCoefficients = mapOf(
                24f to TcaParams(vr = 1.0008f, vb = 0.9992f),
                50f to TcaParams(vr = 1.0004f, vb = 0.9996f),
                70f to TcaParams(vr = 1.0002f, vb = 0.9998f)
            ),
            vignettingCoefficients = mapOf(
                24f to VignettingParams(v1 = 0.04f, v2 = 0.015f, v3 = 0.008f),
                50f to VignettingParams(v1 = 0.025f, v2 = 0.01f, v3 = 0.004f),
                70f to VignettingParams(v1 = 0.015f, v2 = 0.008f, v3 = 0.002f)
            )
        ),
        LensProfile(
            id = "sony_24_70_f28_gm",
            brand = "Sony",
            model = "FE 24-70mm F2.8 GM",
            focalLengthMin = 24f,
            focalLengthMax = 70f,
            apertureMin = 2.8f,
            distortionCoefficients = mapOf(
                24f to DistortionParams(k1 = 0.003f, k2 = -0.001f, k3 = 0.0005f),
                50f to DistortionParams(k1 = 0.0015f, k2 = -0.0005f, k3 = 0.0002f),
                70f to DistortionParams(k1 = 0.0008f, k2 = -0.0002f, k3 = 0.0001f)
            ),
            tcaCoefficients = mapOf(
                24f to TcaParams(vr = 1.0012f, vb = 0.9988f),
                50f to TcaParams(vr = 1.0006f, vb = 0.9994f),
                70f to TcaParams(vr = 1.0003f, vb = 0.9997f)
            ),
            vignettingCoefficients = mapOf(
                24f to VignettingParams(v1 = 0.06f, v2 = 0.025f, v3 = 0.01f),
                50f to VignettingParams(v1 = 0.035f, v2 = 0.015f, v3 = 0.006f),
                70f to VignettingParams(v1 = 0.02f, v2 = 0.01f, v3 = 0.003f)
            )
        ),
        LensProfile(
            id = "fuji_16_55_f28",
            brand = "Fujifilm",
            model = "XF 16-55mm F2.8 R LM WR",
            focalLengthMin = 16f,
            focalLengthMax = 55f,
            apertureMin = 2.8f,
            distortionCoefficients = mapOf(
                16f to DistortionParams(k1 = 0.004f, k2 = -0.002f, k3 = 0.001f),
                35f to DistortionParams(k1 = 0.002f, k2 = -0.001f, k3 = 0.0005f),
                55f to DistortionParams(k1 = 0.001f, k2 = -0.0005f, k3 = 0.0002f)
            ),
            tcaCoefficients = mapOf(
                16f to TcaParams(vr = 1.0015f, vb = 0.9985f),
                35f to TcaParams(vr = 1.0008f, vb = 0.9992f),
                55f to TcaParams(vr = 1.0004f, vb = 0.9996f)
            ),
            vignettingCoefficients = mapOf(
                16f to VignettingParams(v1 = 0.07f, v2 = 0.03f, v3 = 0.012f),
                35f to VignettingParams(v1 = 0.04f, v2 = 0.018f, v3 = 0.007f),
                55f to VignettingParams(v1 = 0.025f, v2 = 0.01f, v3 = 0.004f)
            )
        ),
        LensProfile(
            id = "sigma_24_70_f28_dgdn",
            brand = "Sigma",
            model = "24-70mm F2.8 DG DN | Art",
            focalLengthMin = 24f,
            focalLengthMax = 70f,
            apertureMin = 2.8f,
            distortionCoefficients = mapOf(
                24f to DistortionParams(k1 = 0.002f, k2 = -0.0008f, k3 = 0.0004f),
                50f to DistortionParams(k1 = 0.001f, k2 = -0.0004f, k3 = 0.0002f),
                70f to DistortionParams(k1 = 0.0005f, k2 = -0.0002f, k3 = 0.0001f)
            ),
            tcaCoefficients = mapOf(
                24f to TcaParams(vr = 1.001f, vb = 0.999f),
                50f to TcaParams(vr = 1.0005f, vb = 0.9995f),
                70f to TcaParams(vr = 1.0002f, vb = 0.9998f)
            ),
            vignettingCoefficients = mapOf(
                24f to VignettingParams(v1 = 0.05f, v2 = 0.02f, v3 = 0.008f),
                50f to VignettingParams(v1 = 0.03f, v2 = 0.012f, v3 = 0.005f),
                70f to VignettingParams(v1 = 0.018f, v2 = 0.008f, v3 = 0.003f)
            )
        ),
        LensProfile(
            id = "generic_50mm",
            brand = "Generic",
            model = "50mm Standard",
            focalLengthMin = 50f,
            focalLengthMax = 50f,
            apertureMin = 1.8f,
            distortionCoefficients = mapOf(
                50f to DistortionParams(k1 = 0.005f, k2 = -0.002f, k3 = 0.001f)
            ),
            tcaCoefficients = mapOf(
                50f to TcaParams(vr = 1.001f, vb = 0.999f)
            ),
            vignettingCoefficients = mapOf(
                50f to VignettingParams(v1 = 0.05f, v2 = 0.02f, v3 = 0.01f)
            )
        )
    )

    fun findLens(exifData: ExifData): LensProfile? {
        val lensModel = exifData.lensModel.lowercase()
        val lensMake = exifData.lensMake.lowercase()
        val cameraMake = exifData.cameraMake.lowercase()

        return builtInLenses.firstOrNull { lens ->
            val brandMatch = lens.brand.lowercase() in lensMake ||
                lens.brand.lowercase() in cameraMake
            val modelMatch = lens.model.lowercase().contains(lensModel) ||
                lensModel.contains(lens.model.lowercase())
            brandMatch && modelMatch
        }
    }

    fun findLensByBrandAndModel(brand: String, model: String): LensProfile? {
        val brandLower = brand.lowercase()
        val modelLower = model.lowercase()
        return builtInLenses.firstOrNull {
            it.brand.lowercase() == brandLower ||
                it.model.lowercase().contains(modelLower)
        }
    }

    fun getAllLenses(): List<LensProfile> = builtInLenses

    fun getLensesByBrand(brand: String): List<LensProfile> {
        return builtInLenses.filter { it.brand.lowercase() == brand.lowercase() }
    }

    fun getBrands(): List<String> = builtInLenses.map { it.brand }.distinct()

    fun getDistortionParams(lens: LensProfile, focalLength: Float): DistortionParams? {
        val focalKeys = lens.distortionCoefficients.keys.sorted()
        if (focalKeys.isEmpty()) return null

        if (focalLength <= focalKeys.first()) {
            return lens.distortionCoefficients[focalKeys.first()]
        }
        if (focalLength >= focalKeys.last()) {
            return lens.distortionCoefficients[focalKeys.last()]
        }

        for (i in 0 until focalKeys.size - 1) {
            if (focalLength >= focalKeys[i] && focalLength <= focalKeys[i + 1]) {
                val t = (focalLength - focalKeys[i]) / (focalKeys[i + 1] - focalKeys[i])
                val p1 = lens.distortionCoefficients[focalKeys[i]]!!
                val p2 = lens.distortionCoefficients[focalKeys[i + 1]]!!
                return DistortionParams(
                    k1 = ColorMath.lerp(p1.k1, p2.k1, t),
                    k2 = ColorMath.lerp(p1.k2, p2.k2, t),
                    k3 = ColorMath.lerp(p1.k3, p2.k3, t)
                )
            }
        }

        return lens.distortionCoefficients[focalKeys.first()]
    }

    fun getTcaParams(lens: LensProfile, focalLength: Float): TcaParams? {
        val focalKeys = lens.tcaCoefficients.keys.sorted()
        if (focalKeys.isEmpty()) return null

        if (focalLength <= focalKeys.first()) {
            return lens.tcaCoefficients[focalKeys.first()]
        }
        if (focalLength >= focalKeys.last()) {
            return lens.tcaCoefficients[focalKeys.last()]
        }

        for (i in 0 until focalKeys.size - 1) {
            if (focalLength >= focalKeys[i] && focalLength <= focalKeys[i + 1]) {
                val t = (focalLength - focalKeys[i]) / (focalKeys[i + 1] - focalKeys[i])
                val p1 = lens.tcaCoefficients[focalKeys[i]]!!
                val p2 = lens.tcaCoefficients[focalKeys[i + 1]]!!
                return TcaParams(
                    vr = ColorMath.lerp(p1.vr, p2.vr, t),
                    vb = ColorMath.lerp(p1.vb, p2.vb, t)
                )
            }
        }

        return lens.tcaCoefficients[focalKeys.first()]
    }

    fun getVignettingParams(lens: LensProfile, focalLength: Float): VignettingParams? {
        val focalKeys = lens.vignettingCoefficients.keys.sorted()
        if (focalKeys.isEmpty()) return null

        if (focalLength <= focalKeys.first()) {
            return lens.vignettingCoefficients[focalKeys.first()]
        }
        if (focalLength >= focalKeys.last()) {
            return lens.vignettingCoefficients[focalKeys.last()]
        }

        for (i in 0 until focalKeys.size - 1) {
            if (focalLength >= focalKeys[i] && focalLength <= focalKeys[i + 1]) {
                val t = (focalLength - focalKeys[i]) / (focalKeys[i + 1] - focalKeys[i])
                val p1 = lens.vignettingCoefficients[focalKeys[i]]!!
                val p2 = lens.vignettingCoefficients[focalKeys[i + 1]]!!
                return VignettingParams(
                    v1 = ColorMath.lerp(p1.v1, p2.v1, t),
                    v2 = ColorMath.lerp(p1.v2, p2.v2, t),
                    v3 = ColorMath.lerp(p1.v3, p2.v3, t)
                )
            }
        }

        return lens.vignettingCoefficients[focalKeys.first()]
    }
}

@Serializable
data class LensProfile(
    val id: String,
    val brand: String,
    val model: String,
    val focalLengthMin: Float,
    val focalLengthMax: Float,
    val apertureMin: Float,
    val distortionCoefficients: Map<Float, DistortionParams> = emptyMap(),
    val tcaCoefficients: Map<Float, TcaParams> = emptyMap(),
    val vignettingCoefficients: Map<Float, VignettingParams> = emptyMap(),
)

@Serializable
data class DistortionParams(
    val k1: Float = 0f,
    val k2: Float = 0f,
    val k3: Float = 0f
)

@Serializable
data class TcaParams(
    val vr: Float = 1f,
    val vb: Float = 1f
)

@Serializable
data class VignettingParams(
    val v1: Float = 0f,
    val v2: Float = 0f,
    val v3: Float = 0f
)
