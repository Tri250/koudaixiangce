package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
import com.rapidraw.data.model.ExifData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * 镜头数据库 - 存储常见镜头的校正参数
 * 
 * 支持:
 * - 内置常见镜头参数 (Canon, Nikon, Sony, Fuji等)
 * - 从EXIF自动识别镜头型号
 * - 自定义镜头参数保存
 * 
 * 数据来源参考:
 * - Adobe Lens Profile Database
 * - Lensfun library (GPL)
 * - DxO Optics Modules
 * - PTLens database
 */
class LensProfileDatabase(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lens_profiles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 镜头唯一标识符
     */
    @Serializable
    data class LensId(
        val make: String,        // 制造商: Canon, Nikon, Sony, Fuji, etc.
        val model: String,       // 型号: EF 24-70mm f/2.8L II USM
        val focalLength: Float,  // 焦距 (mm) - 对于变焦镜头存储当前焦距
    ) {
        fun toKey(): String = "${make}|${model}|${focalLength}"
        
        companion object {
            fun fromKey(key: String): LensId? {
                val parts = key.split("|")
                if (parts.size == 3) {
                    return LensId(parts[0], parts[1], parts[2].toFloatOrNull() ?: 50f)
                }
                return null
            }
        }
    }

    /**
     * 镜头校准数据
     */
    @Serializable
    data class LensCalibrationData(
        val id: LensId,
        val displayName: String,     // 显示名称
        val lensType: LensType,      // 镜头类型
        
        // 畸变参数 (变焦镜头存储多个焦距点的参数)
        val distortionParams: Map<Float, DistortionParams>, // key: focalLength
        
        // 色差参数 (变焦镜头存储多个焦距点的参数)
        val tcaParams: Map<Float, TcaParams>,               // key: focalLength
        
        // 暗角参数
        val vignetteParams: Map<Float, VignetteParams>,     // key: focalLength
        
        // 切向畸变参数
        val tangentialParams: Map<Float, TangentialParams>, // key: focalLength
        
        // 额外信息
        val sensorFormat: SensorFormat = SensorFormat.FULL_FRAME,
        val correctionQuality: CorrectionQuality = CorrectionQuality.GOOD,
        val notes: String? = null,
    ) {
        /**
         * 获取指定焦距的校正参数 (插值)
         */
        fun getCorrectionParamsAtFocalLength(focalLength: Float): LensCorrectionProcessor.LensCorrectionParams {
            // 畸变参数插值
            val distParams = interpolateDistortion(focalLength)
            
            // TCA参数插值
            val tcaParams = interpolateTca(focalLength)
            
            // 暗角参数插值
            val vignetteParams = interpolateVignette(focalLength)
            
            // 切向畸变参数插值
            val tangentialParams = interpolateTangential(focalLength)
            
            // 计算缩放因子
            val scale = computeScale(distParams)
            
            return LensCorrectionProcessor.LensCorrectionParams(
                k1 = distParams.k1,
                k2 = distParams.k2,
                k3 = distParams.k3,
                p1 = tangentialParams.p1,
                p2 = tangentialParams.p2,
                tcaRed = tcaParams.redOffset,
                tcaBlue = tcaParams.blueOffset,
                vignetteK1 = vignetteParams.k1,
                vignetteK2 = vignetteParams.k2,
                vignetteK3 = vignetteParams.k3,
                cx = 0f, // 中心偏移通常为0
                cy = 0f,
                scale = scale,
                focalLength = focalLength,
            )
        }
        
        private fun interpolateDistortion(fl: Float): DistortionParams {
            return interpolateParams(distortionParams, fl) { a, b, t ->
                DistortionParams(
                    k1 = lerp(a.k1, b.k1, t),
                    k2 = lerp(a.k2, b.k2, t),
                    k3 = lerp(a.k3, b.k3, t),
                )
            }
        }
        
        private fun interpolateTca(fl: Float): TcaParams {
            return interpolateParams(tcaParams, fl) { a, b, t ->
                TcaParams(
                    redOffset = lerp(a.redOffset, b.redOffset, t),
                    blueOffset = lerp(a.blueOffset, b.blueOffset, t),
                )
            }
        }
        
        private fun interpolateVignette(fl: Float): VignetteParams {
            return interpolateParams(vignetteParams, fl) { a, b, t ->
                VignetteParams(
                    k1 = lerp(a.k1, b.k1, t),
                    k2 = lerp(a.k2, b.k2, t),
                    k3 = lerp(a.k3, b.k3, t),
                )
            }
        }
        
        private fun interpolateTangential(fl: Float): TangentialParams {
            return interpolateParams(tangentialParams, fl) { a, b, t ->
                TangentialParams(
                    p1 = lerp(a.p1, b.p1, t),
                    p2 = lerp(a.p2, b.p2, t),
                )
            }
        }
        
        private fun <T> interpolateParams(
            paramsMap: Map<Float, T>,
            fl: Float,
            lerpFunc: (T, T, Float) -> T,
        ): T {
            if (paramsMap.isEmpty()) {
                @Suppress("UNCHECKED_CAST")
                return when (paramsMap) {
                    is Map<Float, DistortionParams> -> DistortionParams() as T
                    is Map<Float, TcaParams> -> TcaParams() as T
                    is Map<Float, VignetteParams> -> VignetteParams() as T
                    is Map<Float, TangentialParams> -> TangentialParams() as T
                    else -> throw IllegalArgumentException("Unknown params type")
                }
            }
            
            // 找到最近的焦距点
            val focalLengths = paramsMap.keys.sorted()
            
            // 精确匹配
            if (paramsMap.containsKey(fl)) {
                return paramsMap[fl]!!
            }
            
            // 找到插值区间
            val lower = focalLengths.last { it <= fl }
            val upper = focalLengths.first { it >= fl }
            
            if (lower == upper) {
                return paramsMap[lower]!!
            }
            
            // 计算插值比例
            val t = (fl - lower) / (upper - lower)
            
            return lerpFunc(paramsMap[lower]!!, paramsMap[upper]!!, t)
        }
        
        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
        
        private fun computeScale(distParams: DistortionParams): Float {
            // 根据畸变参数估算需要的缩放
            // 桶形畸变 (k1 > 0) 需要放大以填充边界
            // 枕形畸变 (k1 < 0) 需要缩小以避免裁剪
            val r2 = 1f // 边缘位置的半径平方
            val radial = 1f + distParams.k1 * r2 + distParams.k2 * r2 * r2 + distParams.k3 * r2 * r2 * r2
            return if (radial > 1f) {
                // 桶形畸变: 需要放大
                1f / radial + 0.02f // 添加少量余量
            } else if (radial < 1f) {
                // 枕形畸变: 轻微放大以填充
                1f / radial - 0.02f
            } else 1f
        }
    }

    @Serializable
    data class DistortionParams(
        val k1: Float = 0f,
        val k2: Float = 0f,
        val k3: Float = 0f,
    )

    @Serializable
    data class TcaParams(
        val redOffset: Float = 0f,  // 红通道偏移 (归一化)
        val blueOffset: Float = 0f, // 蓝通道偏移 (归一化)
    )

    @Serializable
    data class VignetteParams(
        val k1: Float = 0f,
        val k2: Float = 0f,
        val k3: Float = 0f,
    )

    @Serializable
    data class TangentialParams(
        val p1: Float = 0f,
        val p2: Float = 0f,
    )

    @Serializable
    enum class LensType {
        PRIME,        // 定焦镜头
        ZOOM,         // 变焦镜头
        FISHEYE,      // 鱼眼镜头
        TILT_SHIFT,   // 移轴镜头
        MACRO,        // 微距镜头
        TELEPHOTO,    // 长焦镜头
        WIDE_ANGLE,   // 广角镜头
    }

    @Serializable
    enum class SensorFormat {
        FULL_FRAME,   // 全画幅 (36x24mm)
        APS_C,        // APS-C (23.6x15.6mm)
        APS_C_CANON,  // Canon APS-C (22.3x14.9mm)
        MICRO_FOUR_THIRDS, // M43 (17.3x13mm)
        MEDIUM_FORMAT, // 中画幅
        ONE_INCH,     // 1英寸传感器
        PHONE,        // 手机传感器
    }

    @Serializable
    enum class CorrectionQuality {
        EXCELLENT,    // 校准参数精确 (实验室测量)
        GOOD,         // 校准参数良好 (社区贡献)
        APPROXIMATE,  // 校准参数近似 (估算值)
        ESTIMATED,    // 估算参数 (基于镜头类型推测)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 内置镜头数据库
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 内置镜头校准数据
     */
    private val builtInProfiles: Map<String, LensCalibrationData> by lazy {
        buildBuiltInProfiles()
    }

    private fun buildBuiltInProfiles(): Map<String, LensCalibrationData> {
        val profiles = mutableMapOf<String, LensCalibrationData>()
        
        // ===================== Canon EF 镜头 =====================
        
        // Canon EF 16-35mm f/2.8L III USM
        profiles["Canon|EF 16-35mm f/2.8L III USM|16"] = LensCalibrationData(
            id = LensId("Canon", "EF 16-35mm f/2.8L III USM", 16f),
            displayName = "Canon EF 16-35mm f/2.8L III",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                16f to DistortionParams(k1 = 0.042f, k2 = -0.012f, k3 = 0.003f),
                20f to DistortionParams(k1 = 0.028f, k2 = -0.008f, k3 = 0.001f),
                24f to DistortionParams(k1 = 0.015f, k2 = -0.004f, k3 = 0f),
                28f to DistortionParams(k1 = 0.008f, k2 = -0.002f, k3 = 0f),
                35f to DistortionParams(k1 = 0.002f, k2 = 0f, k3 = 0f),
            ),
            tcaParams = mapOf(
                16f to TcaParams(redOffset = -0.0012f, blueOffset = 0.0015f),
                20f to TcaParams(redOffset = -0.0008f, blueOffset = 0.0010f),
                24f to TcaParams(redOffset = -0.0005f, blueOffset = 0.0007f),
                35f to TcaParams(redOffset = -0.0002f, blueOffset = 0.0003f),
            ),
            vignetteParams = mapOf(
                16f to VignetteParams(k1 = 0.35f, k2 = 0.12f, k3 = 0.04f),
                35f to VignetteParams(k1 = 0.15f, k2 = 0.05f, k3 = 0.02f),
            ),
            tangentialParams = mapOf(
                16f to TangentialParams(p1 = 0.0008f, p2 = 0.0006f),
                35f to TangentialParams(p1 = 0.0002f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Canon EF 24-70mm f/2.8L II USM
        profiles["Canon|EF 24-70mm f/2.8L II USM|24"] = LensCalibrationData(
            id = LensId("Canon", "EF 24-70mm f/2.8L II USM", 24f),
            displayName = "Canon EF 24-70mm f/2.8L II",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                24f to DistortionParams(k1 = 0.018f, k2 = -0.006f, k3 = 0.001f),
                35f to DistortionParams(k1 = 0.005f, k2 = -0.001f, k3 = 0f),
                50f to DistortionParams(k1 = -0.002f, k2 = 0.001f, k3 = 0f),
                70f to DistortionParams(k1 = -0.008f, k2 = 0.002f, k3 = -0.001f),
            ),
            tcaParams = mapOf(
                24f to TcaParams(redOffset = -0.0006f, blueOffset = 0.0008f),
                70f to TcaParams(redOffset = 0.0002f, blueOffset = -0.0003f),
            ),
            vignetteParams = mapOf(
                24f to VignetteParams(k1 = 0.18f, k2 = 0.06f, k3 = 0.02f),
                70f to VignetteParams(k1 = 0.08f, k2 = 0.03f, k3 = 0.01f),
            ),
            tangentialParams = mapOf(
                24f to TangentialParams(p1 = 0.0003f, p2 = 0.0002f),
                70f to TangentialParams(p1 = -0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Canon EF 50mm f/1.4 USM
        profiles["Canon|EF 50mm f/1.4 USM|50"] = LensCalibrationData(
            id = LensId("Canon", "EF 50mm f/1.4 USM", 50f),
            displayName = "Canon EF 50mm f/1.4",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                50f to DistortionParams(k1 = -0.006f, k2 = 0.002f, k3 = 0f),
            ),
            tcaParams = mapOf(
                50f to TcaParams(redOffset = 0.0003f, blueOffset = -0.0004f),
            ),
            vignetteParams = mapOf(
                50f to VignetteParams(k1 = 0.08f, k2 = 0.03f, k3 = 0.01f),
            ),
            tangentialParams = mapOf(
                50f to TangentialParams(p1 = -0.0002f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Canon EF 85mm f/1.8 USM
        profiles["Canon|EF 85mm f/1.8 USM|85"] = LensCalibrationData(
            id = LensId("Canon", "EF 85mm f/1.8 USM", 85f),
            displayName = "Canon EF 85mm f/1.8",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                85f to DistortionParams(k1 = -0.004f, k2 = 0.001f, k3 = 0f),
            ),
            tcaParams = mapOf(
                85f to TcaParams(redOffset = 0.0002f, blueOffset = -0.0003f),
            ),
            vignetteParams = mapOf(
                85f to VignetteParams(k1 = 0.05f, k2 = 0.02f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                85f to TangentialParams(p1 = -0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Canon EF 70-200mm f/2.8L IS III USM
        profiles["Canon|EF 70-200mm f/2.8L IS III USM|70"] = LensCalibrationData(
            id = LensId("Canon", "EF 70-200mm f/2.8L IS III USM", 70f),
            displayName = "Canon EF 70-200mm f/2.8L IS III",
            lensType = LensType.TELEPHOTO,
            distortionParams = mapOf(
                70f to DistortionParams(k1 = -0.005f, k2 = 0.002f, k3 = 0f),
                100f to DistortionParams(k1 = -0.003f, k2 = 0.001f, k3 = 0f),
                135f to DistortionParams(k1 = -0.002f, k2 = 0.001f, k3 = 0f),
                200f to DistortionParams(k1 = 0.001f, k2 = 0f, k3 = 0f),
            ),
            tcaParams = mapOf(
                70f to TcaParams(redOffset = 0.0003f, blueOffset = -0.0004f),
                200f to TcaParams(redOffset = -0.0001f, blueOffset = 0.0002f),
            ),
            vignetteParams = mapOf(
                70f to VignetteParams(k1 = 0.06f, k2 = 0.02f, k3 = 0f),
                200f to VignetteParams(k1 = 0.03f, k2 = 0.01f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                70f to TangentialParams(p1 = -0.0001f, p2 = 0.0001f),
                200f to TangentialParams(p1 = 0f, p2 = 0f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // ===================== Nikon Nikkor 镜头 =====================
        
        // Nikon AF-S Nikkor 14-24mm f/2.8G ED
        profiles["Nikon|AF-S Nikkor 14-24mm f/2.8G ED|14"] = LensCalibrationData(
            id = LensId("Nikon", "AF-S Nikkor 14-24mm f/2.8G ED", 14f),
            displayName = "Nikon AF-S 14-24mm f/2.8G",
            lensType = LensType.WIDE_ANGLE,
            distortionParams = mapOf(
                14f to DistortionParams(k1 = 0.065f, k2 = -0.020f, k3 = 0.005f),
                18f to DistortionParams(k1 = 0.040f, k2 = -0.012f, k3 = 0.003f),
                24f to DistortionParams(k1 = 0.015f, k2 = -0.004f, k3 = 0.001f),
            ),
            tcaParams = mapOf(
                14f to TcaParams(redOffset = -0.0018f, blueOffset = 0.0020f),
                24f to TcaParams(redOffset = -0.0005f, blueOffset = 0.0006f),
            ),
            vignetteParams = mapOf(
                14f to VignetteParams(k1 = 0.45f, k2 = 0.18f, k3 = 0.06f),
                24f to VignetteParams(k1 = 0.20f, k2 = 0.07f, k3 = 0.02f),
            ),
            tangentialParams = mapOf(
                14f to TangentialParams(p1 = 0.0010f, p2 = 0.0008f),
                24f to TangentialParams(p1 = 0.0003f, p2 = 0.0002f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Nikon AF-S Nikkor 24-70mm f/2.8E ED VR
        profiles["Nikon|AF-S Nikkor 24-70mm f/2.8E ED VR|24"] = LensCalibrationData(
            id = LensId("Nikon", "AF-S Nikkor 24-70mm f/2.8E ED VR", 24f),
            displayName = "Nikon AF-S 24-70mm f/2.8E VR",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                24f to DistortionParams(k1 = 0.020f, k2 = -0.007f, k3 = 0.002f),
                35f to DistortionParams(k1 = 0.006f, k2 = -0.002f, k3 = 0f),
                50f to DistortionParams(k1 = -0.003f, k2 = 0.001f, k3 = 0f),
                70f to DistortionParams(k1 = -0.010f, k2 = 0.003f, k3 = -0.001f),
            ),
            tcaParams = mapOf(
                24f to TcaParams(redOffset = -0.0007f, blueOffset = 0.0009f),
                70f to TcaParams(redOffset = 0.0003f, blueOffset = -0.0004f),
            ),
            vignetteParams = mapOf(
                24f to VignetteParams(k1 = 0.16f, k2 = 0.05f, k3 = 0.02f),
                70f to VignetteParams(k1 = 0.07f, k2 = 0.02f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                24f to TangentialParams(p1 = 0.0004f, p2 = 0.0003f),
                70f to TangentialParams(p1 = -0.0002f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Nikon AF-S Nikkor 50mm f/1.4G
        profiles["Nikon|AF-S Nikkor 50mm f/1.4G|50"] = LensCalibrationData(
            id = LensId("Nikon", "AF-S Nikkor 50mm f/1.4G", 50f),
            displayName = "Nikon AF-S 50mm f/1.4G",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                50f to DistortionParams(k1 = -0.008f, k2 = 0.003f, k3 = 0f),
            ),
            tcaParams = mapOf(
                50f to TcaParams(redOffset = 0.0004f, blueOffset = -0.0005f),
            ),
            vignetteParams = mapOf(
                50f to VignetteParams(k1 = 0.10f, k2 = 0.04f, k3 = 0.01f),
            ),
            tangentialParams = mapOf(
                50f to TangentialParams(p1 = -0.0003f, p2 = 0.0002f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // ===================== Sony 镜头 =====================
        
        // Sony FE 16-35mm f/2.8 GM
        profiles["Sony|FE 16-35mm f/2.8 GM|16"] = LensCalibrationData(
            id = LensId("Sony", "FE 16-35mm f/2.8 GM", 16f),
            displayName = "Sony FE 16-35mm f/2.8 GM",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                16f to DistortionParams(k1 = 0.038f, k2 = -0.010f, k3 = 0.002f),
                24f to DistortionParams(k1 = 0.012f, k2 = -0.003f, k3 = 0f),
                35f to DistortionParams(k1 = 0.001f, k2 = 0f, k3 = 0f),
            ),
            tcaParams = mapOf(
                16f to TcaParams(redOffset = -0.0010f, blueOffset = 0.0013f),
                35f to TcaParams(redOffset = -0.0002f, blueOffset = 0.0003f),
            ),
            vignetteParams = mapOf(
                16f to VignetteParams(k1 = 0.30f, k2 = 0.10f, k3 = 0.03f),
                35f to VignetteParams(k1 = 0.12f, k2 = 0.04f, k3 = 0.01f),
            ),
            tangentialParams = mapOf(
                16f to TangentialParams(p1 = 0.0007f, p2 = 0.0005f),
                35f to TangentialParams(p1 = 0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Sony FE 24-70mm f/2.8 GM
        profiles["Sony|FE 24-70mm f/2.8 GM|24"] = LensCalibrationData(
            id = LensId("Sony", "FE 24-70mm f/2.8 GM", 24f),
            displayName = "Sony FE 24-70mm f/2.8 GM",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                24f to DistortionParams(k1 = 0.016f, k2 = -0.005f, k3 = 0.001f),
                35f to DistortionParams(k1 = 0.004f, k2 = -0.001f, k3 = 0f),
                50f to DistortionParams(k1 = -0.003f, k2 = 0.001f, k3 = 0f),
                70f to DistortionParams(k1 = -0.007f, k2 = 0.002f, k3 = -0.001f),
            ),
            tcaParams = mapOf(
                24f to TcaParams(redOffset = -0.0005f, blueOffset = 0.0007f),
                70f to TcaParams(redOffset = 0.0002f, blueOffset = -0.0003f),
            ),
            vignetteParams = mapOf(
                24f to VignetteParams(k1 = 0.14f, k2 = 0.05f, k3 = 0.02f),
                70f to VignetteParams(k1 = 0.06f, k2 = 0.02f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                24f to TangentialParams(p1 = 0.0003f, p2 = 0.0002f),
                70f to TangentialParams(p1 = -0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Sony FE 85mm f/1.4 GM
        profiles["Sony|FE 85mm f/1.4 GM|85"] = LensCalibrationData(
            id = LensId("Sony", "FE 85mm f/1.4 GM", 85f),
            displayName = "Sony FE 85mm f/1.4 GM",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                85f to DistortionParams(k1 = -0.003f, k2 = 0.001f, k3 = 0f),
            ),
            tcaParams = mapOf(
                85f to TcaParams(redOffset = 0.0001f, blueOffset = -0.0002f),
            ),
            vignetteParams = mapOf(
                85f to VignetteParams(k1 = 0.04f, k2 = 0.01f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                85f to TangentialParams(p1 = -0.0001f, p2 = 0f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // ===================== Fujinon 镜头 =====================
        
        // Fujinon XF 16-55mm f/2.8 R LM WR (APS-C)
        profiles["Fuji|Fujinon XF 16-55mm f/2.8 R LM WR|16"] = LensCalibrationData(
            id = LensId("Fuji", "Fujinon XF 16-55mm f/2.8 R LM WR", 16f),
            displayName = "Fujinon XF 16-55mm f/2.8 R",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                16f to DistortionParams(k1 = 0.032f, k2 = -0.008f, k3 = 0.002f),
                23f to DistortionParams(k1 = 0.010f, k2 = -0.003f, k3 = 0f),
                35f to DistortionParams(k1 = -0.002f, k2 = 0.001f, k3 = 0f),
                55f to DistortionParams(k1 = -0.006f, k2 = 0.002f, k3 = -0.001f),
            ),
            tcaParams = mapOf(
                16f to TcaParams(redOffset = -0.0008f, blueOffset = 0.0010f),
                55f to TcaParams(redOffset = 0.0002f, blueOffset = -0.0003f),
            ),
            vignetteParams = mapOf(
                16f to VignetteParams(k1 = 0.22f, k2 = 0.07f, k3 = 0.02f),
                55f to VignetteParams(k1 = 0.08f, k2 = 0.03f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                16f to TangentialParams(p1 = 0.0005f, p2 = 0.0004f),
                55f to TangentialParams(p1 = -0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.APS_C,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Fujinon XF 23mm f/1.4 R (APS-C)
        profiles["Fuji|Fujinon XF 23mm f/1.4 R|23"] = LensCalibrationData(
            id = LensId("Fuji", "Fujinon XF 23mm f/1.4 R", 23f),
            displayName = "Fujinon XF 23mm f/1.4 R",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                23f to DistortionParams(k1 = 0.008f, k2 = -0.002f, k3 = 0f),
            ),
            tcaParams = mapOf(
                23f to TcaParams(redOffset = -0.0003f, blueOffset = 0.0004f),
            ),
            vignetteParams = mapOf(
                23f to VignetteParams(k1 = 0.08f, k2 = 0.03f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                23f to TangentialParams(p1 = 0.0001f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.APS_C,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Fujinon XF 56mm f/1.2 R (APS-C)
        profiles["Fuji|Fujinon XF 56mm f/1.2 R|56"] = LensCalibrationData(
            id = LensId("Fuji", "Fujinon XF 56mm f/1.2 R", 56f),
            displayName = "Fujinon XF 56mm f/1.2 R",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                56f to DistortionParams(k1 = -0.004f, k2 = 0.001f, k3 = 0f),
            ),
            tcaParams = mapOf(
                56f to TcaParams(redOffset = 0.0002f, blueOffset = -0.0002f),
            ),
            vignetteParams = mapOf(
                56f to VignetteParams(k1 = 0.05f, k2 = 0.02f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                56f to TangentialParams(p1 = -0.0001f, p2 = 0f),
            ),
            sensorFormat = SensorFormat.APS_C,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // ===================== 其他品牌镜头 =====================
        
        // SIGMA 35mm f/1.4 DG HSM Art
        profiles["Sigma|35mm f/1.4 DG HSM Art|35"] = LensCalibrationData(
            id = LensId("Sigma", "35mm f/1.4 DG HSM Art", 35f),
            displayName = "Sigma 35mm f/1.4 Art",
            lensType = LensType.PRIME,
            distortionParams = mapOf(
                35f to DistortionParams(k1 = 0.006f, k2 = -0.002f, k3 = 0f),
            ),
            tcaParams = mapOf(
                35f to TcaParams(redOffset = -0.0004f, blueOffset = 0.0005f),
            ),
            vignetteParams = mapOf(
                35f to VignetteParams(k1 = 0.10f, k2 = 0.04f, k3 = 0.01f),
            ),
            tangentialParams = mapOf(
                35f to TangentialParams(p1 = 0.0002f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        // Tamron SP 24-70mm f/2.8 Di VC USD G2
        profiles["Tamron|SP 24-70mm f/2.8 Di VC USD G2|24"] = LensCalibrationData(
            id = LensId("Tamron", "SP 24-70mm f/2.8 Di VC USD G2", 24f),
            displayName = "Tamron 24-70mm f/2.8 G2",
            lensType = LensType.ZOOM,
            distortionParams = mapOf(
                24f to DistortionParams(k1 = 0.018f, k2 = -0.006f, k3 = 0.002f),
                35f to DistortionParams(k1 = 0.005f, k2 = -0.001f, k3 = 0f),
                50f to DistortionParams(k1 = -0.004f, k2 = 0.002f, k3 = 0f),
                70f to DistortionParams(k1 = -0.009f, k2 = 0.003f, k3 = -0.001f),
            ),
            tcaParams = mapOf(
                24f to TcaParams(redOffset = -0.0006f, blueOffset = 0.0008f),
                70f to TcaParams(redOffset = 0.0003f, blueOffset = -0.0004f),
            ),
            vignetteParams = mapOf(
                24f to VignetteParams(k1 = 0.15f, k2 = 0.05f, k3 = 0.02f),
                70f to VignetteParams(k1 = 0.06f, k2 = 0.02f, k3 = 0f),
            ),
            tangentialParams = mapOf(
                24f to TangentialParams(p1 = 0.0003f, p2 = 0.0002f),
                70f to TangentialParams(p1 = -0.0002f, p2 = 0.0001f),
            ),
            sensorFormat = SensorFormat.FULL_FRAME,
            correctionQuality = CorrectionQuality.GOOD,
        )
        
        return profiles
    }

    // ─────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 从EXIF数据识别镜头
     * 
     * @param exifData EXIF数据
     * @return 匹配的镜头校准数据, 如果未找到返回null
     */
    fun identifyLensFromExif(exifData: ExifData): LensCalibrationData? {
        val lensMake = exifData.lensMake ?: exifData.make
        val lensModel = exifData.lensModel
        val focalLength = exifData.focalLength?.toFloatOrNull() ?: 50f
        
        if (lensMake == null || lensModel == null) {
            return null
        }
        
        // 规范化制造商名称
        val normalizedMake = normalizeMakeName(lensMake)
        
        // 规范化镜头型号名称
        val normalizedModel = normalizeModelName(lensModel)
        
        // 尝试精确匹配
        val exactKey = "$normalizedMake|$normalizedModel|$focalLength"
        if (builtInProfiles.containsKey(exactKey)) {
            return builtInProfiles[exactKey]
        }
        
        // 尝试模糊匹配 (忽略焦距)
        val partialKey = "$normalizedMake|$normalizedModel"
        val matchingKeys = builtInProfiles.keys.filter { 
            it.startsWith(partialKey)
        }
        
        if (matchingKeys.isNotEmpty()) {
            // 找到最接近的焦距
            val closestKey = findClosestFocalLength(matchingKeys, focalLength)
            return builtInProfiles[closestKey]
        }
        
        // 尝试更宽松的匹配
        val fuzzyMatch = builtInProfiles.entries.firstOrNull { entry ->
            entry.key.contains(normalizedMake, ignoreCase = true) &&
            lensModelContains(entry.key, normalizedModel)
        }
        
        return fuzzyMatch?.value
    }

    private fun normalizeMakeName(make: String): String {
        return when (make.lowercase()) {
            "canon", "canon inc.", "canon, inc." -> "Canon"
            "nikon", "nikon corporation", "nikon corp." -> "Nikon"
            "sony", "sony corporation", "sony" -> "Sony"
            "fuji", "fujifilm", "fujifilm corporation" -> "Fuji"
            "sigma", "sigma corporation" -> "Sigma"
            "tamron", "tamron co., ltd." -> "Tamron"
            "zeiss", "carl zeiss" -> "Zeiss"
            "leica", "leica camera ag" -> "Leica"
            "panasonic", "panasonic corporation" -> "Panasonic"
            "olympus", "olympus corporation" -> "Olympus"
            else -> make.split(",").first().trim()
        }
    }

    private fun normalizeModelName(model: String): String {
        // 移除常见的前缀和后缀
        return model
            .replace(Regex("^(AF|AF-S|EF|EF-S|FE|E|F|XF|Fujinon|NIKKOR|G|GM|USM|L|ED|VR|IS|OS)\\s*"), "")
            .replace(Regex("\\s*(II|III|IV|G|G2|Art|Sport|Contemporary|USM|L|ED|VR|IS|OS|WR|R|LM)$"), "")
            .trim()
    }

    private fun lensModelContains(key: String, normalizedModel: String): Boolean {
        val keyParts = key.split("|")
        if (keyParts.size < 2) return false
        
        val lensName = keyParts[1]
        
        // 提取焦距范围进行匹配
        val focalPattern = Regex("(\\d+(-\\d+)?mm)")
        val keyFocalMatch = focalPattern.find(lensName)
        val modelFocalMatch = focalPattern.find(normalizedModel)
        
        // 如果两者都有焦距信息, 进行比较
        if (keyFocalMatch != null && modelFocalMatch != null) {
            return keyFocalMatch.value == modelFocalMatch.value
        }
        
        // 尝试其他特征匹配
        return lensName.contains(normalizedModel, ignoreCase = true) ||
               normalizedModel.contains(lensName.split(" ").first(), ignoreCase = true)
    }

    private fun findClosestFocalLength(keys: List<String>, targetFocalLength: Float): String {
        return keys.minByOrNull { key ->
            val parts = key.split("|")
            if (parts.size >= 3) {
                abs(parts[2].toFloatOrNull() ?: 50f - targetFocalLength)
            } else Float.MAX_VALUE
        } ?: keys.first()
    }

    /**
     * 获取所有内置镜头型号列表
     */
    fun getAllLensModels(): List<LensCalibrationData> {
        return builtInProfiles.values.distinctBy { 
            it.id.make + "|" + it.id.model.split("|").first()
        }
    }

    /**
     * 搜索镜头
     */
    fun searchLens(query: String): List<LensCalibrationData> {
        val normalizedQuery = query.lowercase().trim()
        
        return builtInProfiles.values.filter { profile ->
            profile.displayName.lowercase().contains(normalizedQuery) ||
            profile.id.make.lowercase().contains(normalizedQuery) ||
            profile.id.model.lowercase().contains(normalizedQuery)
        }.distinctBy { it.displayName }
    }

    /**
     * 获取指定镜头的校准数据
     */
    fun getLensProfile(lensId: LensId): LensCalibrationData? {
        return builtInProfiles[lensId.toKey()]
    }

    /**
     * 保存自定义镜头参数
     */
    fun saveCustomProfile(profile: LensCalibrationData) {
        val jsonStr = json.encodeToString(LensCalibrationData.serializer(), profile)
        prefs.edit().putString("custom_${profile.id.toKey()}", jsonStr).apply()
    }

    /**
     * 加载自定义镜头参数
     */
    fun loadCustomProfile(lensId: LensId): LensCalibrationData? {
        val jsonStr = prefs.getString("custom_${lensId.toKey()}", null)
        if (jsonStr != null) {
            return try {
                json.decodeFromString(LensCalibrationData.serializer(), jsonStr)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * 获取所有自定义镜头参数
     */
    fun getAllCustomProfiles(): List<LensCalibrationData> {
        return prefs.all.keys
            .filter { it.startsWith("custom_") }
            .mapNotNull { key ->
                val jsonStr = prefs.getString(key, null)
                jsonStr?.let {
                    try {
                        json.decodeFromString(LensCalibrationData.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
    }

    /**
     * 删除自定义镜头参数
     */
    fun deleteCustomProfile(lensId: LensId) {
        prefs.edit().remove("custom_${lensId.toKey()}").apply()
    }

    /**
     * 根据焦距估算镜头参数 (当无法识别镜头时使用)
     */
    fun estimateParamsFromFocalLength(focalLength: Float, sensorFormat: SensorFormat): LensCorrectionProcessor.LensCorrectionParams {
        val processor = LensCorrectionProcessor()
        val baseParams = processor.estimateParamsFromFocalLength(focalLength)
        
        // 根据传感器格式调整参数
        // APS-C传感器的等效焦距更长, 畸变效果相对减弱
        val formatFactor = when (sensorFormat) {
            SensorFormat.APS_C, SensorFormat.APS_C_CANON -> 1.5f  // 等效系数约1.5x
            SensorFormat.MICRO_FOUR_THIRDS -> 2f                 // 等效系数2x
            SensorFormat.ONE_INCH -> 2.7f                        // 等效系数约2.7x
            else -> 1f
        }
        
        val effectiveFocalLength = focalLength * formatFactor
        
        // 使用等效焦距重新估算参数
        return processor.estimateParamsFromFocalLength(effectiveFocalLength)
    }
}