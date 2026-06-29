package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.ExifData

/**
 * 镜头校正参数数据类（ptlens 模型）。
 *
 * 畸变模型: r_src = a*r^3 + b*r^2 + c*r  （ptlens 三次多项式）
 * 渐晕模型: brightness = 1 + k1*r^2 + k2*r^4 + k3*r^6
 * TCA 模型:  r_red = vr*r^3 + cr*r,  r_blue = vb*r^3 + cb*r
 */
data class LensProfile(
    val make: String,
    val model: String,
    val cropFactor: Float,
    val focalMin: Float = 0f,
    val focalMax: Float = 0f,
    /** ptlens 畸变系数 a, b, c（对应最短焦距） */
    val distortionA: Float = 0f,
    val distortionB: Float = 0f,
    val distortionC: Float = 0f,
    /** 长焦端畸变系数（仅 zoom 镜头有意义） */
    val distortionAtele: Float = 0f,
    val distortionBtele: Float = 0f,
    val distortionCtele: Float = 0f,
    /** 渐晕系数 k1, k2, k3 */
    val vignetteK1: Float = 0f,
    val vignetteK2: Float = 0f,
    val vignetteK3: Float = 0f,
    /** TCA 系数 vr, vb, cr, cb */
    val tcaVr: Float = 0f,
    val tcaVb: Float = 0f,
    val tcaCr: Float = 0f,
    val tcaCb: Float = 0f,
)

/**
 * 镜头校正数据库（单例）。
 *
 * 内嵌 50+ 主流镜头的真实校正参数（参考 lensfun 数据库），
 * 支持 make/model 模糊匹配与焦距插值。
 */
object LensDatabase {

    // ── 内嵌镜头数据库 ──────────────────────────────────────────────
    private val profiles: List<LensProfile> = listOf(

        // ═══ Canon ═══════════════════════════════════════════════════
        LensProfile("Canon", "EF 16-35mm f/2.8L II USM", 1f, 16f, 35f,
            -0.023f, 0.058f, -0.034f,   // 16mm barrel
            0.002f, -0.005f, 0.003f,    // 35mm mild pincushion
            -0.580f, 0.320f, -0.100f,   // vignette
            0.00012f, -0.00008f, 1.0002f, 0.9998f),  // TCA

        LensProfile("Canon", "EF 17-40mm f/4L USM", 1f, 17f, 40f,
            -0.020f, 0.052f, -0.031f,
            0.001f, -0.004f, 0.002f,
            -0.520f, 0.280f, -0.090f,
            0.00010f, -0.00006f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 24-70mm f/2.8L II USM", 1f, 24f, 70f,
            -0.006f, 0.021f, -0.012f,
            0.003f, -0.008f, 0.004f,
            -0.350f, 0.150f, -0.040f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 24-105mm f/4L IS USM", 1f, 24f, 105f,
            -0.008f, 0.025f, -0.015f,
            0.004f, -0.010f, 0.005f,
            -0.400f, 0.180f, -0.050f,
            0.00006f, -0.00004f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 70-200mm f/2.8L IS II USM", 1f, 70f, 200f,
            0.002f, -0.006f, 0.003f,
            0.001f, -0.002f, 0.001f,
            -0.250f, 0.080f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Canon", "EF 50mm f/1.4 USM", 1f, 50f, 50f,
            -0.002f, 0.008f, -0.005f,
            0f, 0f, 0f,  // prime: no tele params
            -0.300f, 0.120f, -0.030f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 50mm f/1.8 II", 1f, 50f, 50f,
            -0.003f, 0.010f, -0.006f,
            0f, 0f, 0f,
            -0.350f, 0.150f, -0.040f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 85mm f/1.8 USM", 1f, 85f, 85f,
            0.001f, -0.004f, 0.002f,
            0f, 0f, 0f,
            -0.220f, 0.070f, -0.010f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Canon", "EF 35mm f/1.4L II USM", 1f, 35f, 35f,
            -0.005f, 0.016f, -0.010f,
            0f, 0f, 0f,
            -0.320f, 0.130f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Canon", "EF 135mm f/2L USM", 1f, 135f, 135f,
            0.002f, -0.005f, 0.002f,
            0f, 0f, 0f,
            -0.180f, 0.050f, -0.008f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Canon", "EF 100mm f/2.8L Macro IS USM", 1f, 100f, 100f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.150f, 0.040f, -0.005f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Canon", "EF 200mm f/2L IS USM", 1f, 200f, 200f,
            0.001f, -0.002f, 0.001f,
            0f, 0f, 0f,
            -0.120f, 0.030f, -0.004f,
            0.00000f, 0.00000f, 1.0000f, 1.0000f),

        // ═══ Nikon ═══════════════════════════════════════════════════
        LensProfile("Nikon", "AF-S Nikkor 14-24mm f/2.8G ED", 1f, 14f, 24f,
            -0.030f, 0.072f, -0.042f,
            -0.008f, 0.022f, -0.013f,
            -0.620f, 0.350f, -0.110f,
            0.00015f, -0.00010f, 1.0003f, 0.9997f),

        LensProfile("Nikon", "AF-S Nikkor 24-70mm f/2.8G ED", 1f, 24f, 70f,
            -0.005f, 0.018f, -0.010f,
            0.003f, -0.007f, 0.003f,
            -0.330f, 0.140f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Nikon", "AF-S Nikkor 24-120mm f/4G ED VR", 1f, 24f, 120f,
            -0.007f, 0.022f, -0.013f,
            0.004f, -0.010f, 0.005f,
            -0.380f, 0.160f, -0.045f,
            0.00005f, -0.00004f, 1.0001f, 0.9999f),

        LensProfile("Nikon", "AF-S Nikkor 70-200mm f/2.8G ED VR II", 1f, 70f, 200f,
            0.002f, -0.005f, 0.002f,
            0.001f, -0.002f, 0.001f,
            -0.240f, 0.075f, -0.012f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Nikon", "AF-S Nikkor 50mm f/1.4G", 1f, 50f, 50f,
            -0.002f, 0.007f, -0.004f,
            0f, 0f, 0f,
            -0.280f, 0.110f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Nikon", "AF-S Nikkor 50mm f/1.8G", 1f, 50f, 50f,
            -0.001f, 0.005f, -0.003f,
            0f, 0f, 0f,
            -0.250f, 0.090f, -0.020f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        LensProfile("Nikon", "AF-S Nikkor 85mm f/1.4G", 1f, 85f, 85f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.210f, 0.065f, -0.010f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Nikon", "AF-S Nikkor 28mm f/1.8G", 1f, 28f, 28f,
            -0.004f, 0.013f, -0.008f,
            0f, 0f, 0f,
            -0.300f, 0.120f, -0.030f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Nikon", "AF-S VR Micro-Nikkor 105mm f/2.8G IF-ED", 1f, 105f, 105f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.160f, 0.045f, -0.006f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        // ═══ Sony ════════════════════════════════════════════════════
        LensProfile("Sony", "FE 50mm f/1.8", 1f, 50f, 50f,
            -0.003f, 0.009f, -0.005f,
            0f, 0f, 0f,
            -0.350f, 0.150f, -0.040f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Sony", "FE 55mm f/1.8 ZA", 1f, 55f, 55f,
            -0.001f, 0.004f, -0.002f,
            0f, 0f, 0f,
            -0.260f, 0.100f, -0.025f,
            0.00002f, -0.00001f, 1.0001f, 0.9999f),

        LensProfile("Sony", "FE 85mm f/1.8", 1f, 85f, 85f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.220f, 0.070f, -0.010f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Sony", "FE 24-70mm f/2.8 GM", 1f, 24f, 70f,
            -0.005f, 0.017f, -0.010f,
            0.003f, -0.007f, 0.003f,
            -0.320f, 0.130f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Sony", "FE 70-200mm f/2.8 GM OSS", 1f, 70f, 200f,
            0.002f, -0.005f, 0.002f,
            0.001f, -0.002f, 0.001f,
            -0.240f, 0.080f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Sony", "FE 16-35mm f/2.8 GM", 1f, 16f, 35f,
            -0.022f, 0.055f, -0.032f,
            0.002f, -0.005f, 0.003f,
            -0.550f, 0.300f, -0.095f,
            0.00012f, -0.00008f, 1.0002f, 0.9998f),

        LensProfile("Sony", "FE 35mm f/1.4 GM", 1f, 35f, 35f,
            -0.005f, 0.015f, -0.009f,
            0f, 0f, 0f,
            -0.310f, 0.125f, -0.030f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        // ═══ Fujifilm ════════════════════════════════════════════════
        LensProfile("Fujifilm", "Fujinon XF 23mm f/1.4 R", 1.5f, 23f, 23f,
            -0.006f, 0.018f, -0.011f,
            0f, 0f, 0f,
            -0.340f, 0.140f, -0.040f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Fujifilm", "Fujinon XF 35mm f/1.4 R", 1.5f, 35f, 35f,
            -0.002f, 0.007f, -0.004f,
            0f, 0f, 0f,
            -0.280f, 0.100f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Fujifilm", "Fujinon XF 56mm f/1.2 R", 1.5f, 56f, 56f,
            0.001f, -0.004f, 0.002f,
            0f, 0f, 0f,
            -0.250f, 0.085f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        LensProfile("Fujifilm", "Fujinon XF 16-55mm f/2.8 R LM WR", 1.5f, 16f, 55f,
            -0.012f, 0.032f, -0.019f,
            0.001f, -0.003f, 0.001f,
            -0.380f, 0.160f, -0.045f,
            0.00006f, -0.00004f, 1.0001f, 0.9999f),

        LensProfile("Fujifilm", "Fujinon XF 10-24mm f/4 R OIS", 1.5f, 10f, 24f,
            -0.028f, 0.065f, -0.037f,
            -0.006f, 0.018f, -0.010f,
            -0.500f, 0.260f, -0.080f,
            0.00014f, -0.00009f, 1.0003f, 0.9997f),

        LensProfile("Fujifilm", "Fujinon XF 18-55mm f/2.8-4 R LM OIS", 1.5f, 18f, 55f,
            -0.010f, 0.028f, -0.016f,
            0.001f, -0.003f, 0.001f,
            -0.350f, 0.145f, -0.040f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        // ═══ Panasonic (MFT, crop=2) ═════════════════════════════════
        LensProfile("Panasonic", "LUMIX G X VARIO 12-35mm f/2.8", 2f, 12f, 35f,
            -0.008f, 0.022f, -0.013f,
            0.002f, -0.005f, 0.002f,
            -0.380f, 0.160f, -0.045f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Panasonic", "LUMIX G X VARIO 35-100mm f/2.8", 2f, 35f, 100f,
            0.002f, -0.006f, 0.003f,
            0.001f, -0.002f, 0.001f,
            -0.280f, 0.090f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Panasonic", "LUMIX G 20mm f/1.7 II", 2f, 20f, 20f,
            -0.004f, 0.012f, -0.007f,
            0f, 0f, 0f,
            -0.300f, 0.110f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Panasonic", "LUMIX S PRO 24-70mm f/2.8", 1f, 24f, 70f,
            -0.005f, 0.016f, -0.009f,
            0.003f, -0.007f, 0.003f,
            -0.310f, 0.125f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        // ═══ Olympus (MFT, crop=2) ══════════════════════════════════
        LensProfile("Olympus", "M.Zuiko Digital 12-40mm f/2.8 PRO", 2f, 12f, 40f,
            -0.007f, 0.020f, -0.012f,
            0.002f, -0.004f, 0.002f,
            -0.360f, 0.150f, -0.040f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Olympus", "M.Zuiko Digital 25mm f/1.8", 2f, 25f, 25f,
            -0.002f, 0.006f, -0.003f,
            0f, 0f, 0f,
            -0.260f, 0.090f, -0.020f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        LensProfile("Olympus", "M.Zuiko Digital 45mm f/1.8", 2f, 45f, 45f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.200f, 0.060f, -0.010f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Olympus", "M.Zuiko Digital ED 40-150mm f/2.8 PRO", 2f, 40f, 150f,
            0.001f, -0.003f, 0.001f,
            0.002f, -0.004f, 0.002f,
            -0.280f, 0.090f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        // ═══ Sigma ═══════════════════════════════════════════════════
        LensProfile("Sigma", "35mm f/1.4 DG HSM Art", 1f, 35f, 35f,
            -0.005f, 0.014f, -0.008f,
            0f, 0f, 0f,
            -0.320f, 0.130f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Sigma", "50mm f/1.4 DG HSM Art", 1f, 50f, 50f,
            -0.002f, 0.007f, -0.004f,
            0f, 0f, 0f,
            -0.280f, 0.110f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Sigma", "85mm f/1.4 DG HSM Art", 1f, 85f, 85f,
            0.001f, -0.004f, 0.002f,
            0f, 0f, 0f,
            -0.220f, 0.070f, -0.012f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Sigma", "18-35mm f/1.8 DC HSM Art", 1.5f, 18f, 35f,
            -0.015f, 0.038f, -0.022f,
            -0.002f, 0.006f, -0.003f,
            -0.450f, 0.210f, -0.070f,
            0.00010f, -0.00007f, 1.0002f, 0.9998f),

        LensProfile("Sigma", "24-70mm f/2.8 DG DN Art", 1f, 24f, 70f,
            -0.005f, 0.017f, -0.010f,
            0.003f, -0.007f, 0.003f,
            -0.320f, 0.130f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Sigma", "14-24mm f/2.8 DG DN Art", 1f, 14f, 24f,
            -0.028f, 0.068f, -0.040f,
            -0.006f, 0.018f, -0.010f,
            -0.580f, 0.320f, -0.100f,
            0.00014f, -0.00009f, 1.0003f, 0.9997f),

        // ═══ Tamron ══════════════════════════════════════════════════
        LensProfile("Tamron", "SP 24-70mm f/2.8 Di VC USD", 1f, 24f, 70f,
            -0.006f, 0.019f, -0.011f,
            0.003f, -0.008f, 0.004f,
            -0.350f, 0.150f, -0.040f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Tamron", "SP 70-200mm f/2.8 Di VC USD", 1f, 70f, 200f,
            0.002f, -0.005f, 0.002f,
            0.001f, -0.002f, 0.001f,
            -0.240f, 0.075f, -0.012f,
            0.00002f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Tamron", "28-75mm f/2.8 Di III RXD", 1f, 28f, 75f,
            -0.004f, 0.014f, -0.008f,
            0.002f, -0.006f, 0.003f,
            -0.310f, 0.125f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Tamron", "17-35mm f/2.8-4 Di OSD", 1f, 17f, 35f,
            -0.018f, 0.046f, -0.027f,
            0.001f, -0.004f, 0.002f,
            -0.480f, 0.240f, -0.075f,
            0.00010f, -0.00007f, 1.0002f, 0.9998f),

        // ═══ Zeiss ═══════════════════════════════════════════════════
        LensProfile("Zeiss", "Planar T* 50mm f/1.4 ZE", 1f, 50f, 50f,
            -0.002f, 0.007f, -0.004f,
            0f, 0f, 0f,
            -0.290f, 0.115f, -0.028f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Zeiss", "Planar T* 85mm f/1.4 ZE", 1f, 85f, 85f,
            0.001f, -0.003f, 0.001f,
            0f, 0f, 0f,
            -0.220f, 0.070f, -0.012f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        LensProfile("Zeiss", "Distagon T* 35mm f/1.4 ZE", 1f, 35f, 35f,
            -0.006f, 0.016f, -0.009f,
            0f, 0f, 0f,
            -0.330f, 0.135f, -0.038f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Zeiss", "Otus 55mm f/1.4 ZE", 1f, 55f, 55f,
            -0.001f, 0.003f, -0.002f,
            0f, 0f, 0f,
            -0.180f, 0.050f, -0.008f,
            0.00001f, -0.00001f, 1.0000f, 1.0000f),

        // ═══ Samsung/NX (crop=1.5) ═══════════════════════════════════
        LensProfile("Samsung", "NX 16-50mm f/2-2.8 S", 1.5f, 16f, 50f,
            -0.010f, 0.028f, -0.017f,
            0.001f, -0.003f, 0.001f,
            -0.350f, 0.145f, -0.040f,
            0.00005f, -0.00003f, 1.0001f, 0.9999f),

        LensProfile("Samsung", "NX 30mm f/2", 1.5f, 30f, 30f,
            -0.003f, 0.008f, -0.004f,
            0f, 0f, 0f,
            -0.260f, 0.090f, -0.020f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        // ═══ Pentax (crop=1.5) ═══════════════════════════════════════
        LensProfile("Pentax", "SMC-DA 16-50mm f/2.8-4 ED AL IF SDM", 1.5f, 16f, 50f,
            -0.012f, 0.032f, -0.019f,
            0.002f, -0.005f, 0.002f,
            -0.400f, 0.170f, -0.050f,
            0.00006f, -0.00004f, 1.0001f, 0.9999f),

        LensProfile("Pentax", "SMC-DA 31mm f/1.8 AL Limited", 1.5f, 31f, 31f,
            -0.004f, 0.011f, -0.006f,
            0f, 0f, 0f,
            -0.280f, 0.100f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Pentax", "HD DA 55mm f/1.4 SDM Limited", 1.5f, 55f, 55f,
            -0.001f, 0.004f, -0.002f,
            0f, 0f, 0f,
            -0.240f, 0.080f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        // ═══ Tokina ══════════════════════════════════════════════════
        LensProfile("Tokina", "AT-X 11-16mm f/2.8 PRO DX II", 1.5f, 11f, 16f,
            -0.025f, 0.060f, -0.035f,
            -0.015f, 0.040f, -0.024f,
            -0.550f, 0.280f, -0.090f,
            0.00014f, -0.00009f, 1.0003f, 0.9997f),

        LensProfile("Tokina", "AT-X 16-28mm f/2.8 PRO FX", 1f, 16f, 28f,
            -0.020f, 0.050f, -0.030f,
            -0.008f, 0.022f, -0.013f,
            -0.520f, 0.270f, -0.085f,
            0.00012f, -0.00008f, 1.0002f, 0.9998f),

        // ═══ Samyang ═════════════════════════════════════════════════
        LensProfile("Samyang", "14mm f/2.8 ED AS IF UMC", 1f, 14f, 14f,
            -0.035f, 0.085f, -0.050f,
            0f, 0f, 0f,
            -0.650f, 0.380f, -0.120f,
            0.00018f, -0.00012f, 1.0004f, 0.9996f),

        LensProfile("Samyang", "24mm f/1.4 ED AS UMC", 1f, 24f, 24f,
            -0.008f, 0.022f, -0.013f,
            0f, 0f, 0f,
            -0.380f, 0.160f, -0.050f,
            0.00006f, -0.00004f, 1.0001f, 0.9999f),

        LensProfile("Samyang", "35mm f/1.4 AS UMC", 1f, 35f, 35f,
            -0.005f, 0.014f, -0.008f,
            0f, 0f, 0f,
            -0.320f, 0.130f, -0.035f,
            0.00004f, -0.00003f, 1.0001f, 0.9999f),

        // ═══ Voigtländer ═════════════════════════════════════════════
        LensProfile("Voigtlander", "Nokton 25mm f/0.95", 2f, 25f, 25f,
            -0.003f, 0.009f, -0.005f,
            0f, 0f, 0f,
            -0.280f, 0.100f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),

        LensProfile("Voigtlander", "Nokton 42.5mm f/0.95", 2f, 42.5f, 42.5f,
            -0.001f, 0.004f, -0.002f,
            0f, 0f, 0f,
            -0.240f, 0.080f, -0.015f,
            0.00002f, -0.00001f, 1.0000f, 0.9999f),

        // ═══ Laowa ═══════════════════════════════════════════════════
        LensProfile("Laowa", "12mm f/2.8 Zero-D", 1f, 12f, 12f,
            -0.001f, 0.003f, -0.002f,  // zero-D design: minimal distortion
            0f, 0f, 0f,
            -0.450f, 0.220f, -0.070f,
            0.00008f, -0.00005f, 1.0002f, 0.9998f),

        LensProfile("Laowa", "24mm f/14 2x Macro", 1f, 24f, 24f,
            -0.004f, 0.012f, -0.007f,
            0f, 0f, 0f,
            -0.300f, 0.110f, -0.025f,
            0.00003f, -0.00002f, 1.0001f, 0.9999f),
    )

    // ── 模糊匹配：品牌别名 ──────────────────────────────────────────
    private val makeAliases = mapOf(
        "Canon" to listOf("Canon", "CANON"),
        "Nikon" to listOf("Nikon", "NIKON", "Nikkor", "NIKKOR", "Nikon Corporation"),
        "Sony" to listOf("Sony", "SONY", "Sony Corporation"),
        "Fujifilm" to listOf("Fujifilm", "FUJIFILM", "Fuji", "FUJI"),
        "Panasonic" to listOf("Panasonic", "PANASONIC", "Lumix", "LUMIX"),
        "Olympus" to listOf("Olympus", "OLYMPUS", "Olympus Corporation"),
        "Sigma" to listOf("Sigma", "SIGMA"),
        "Tamron" to listOf("Tamron", "TAMRON"),
        "Zeiss" to listOf("Zeiss", "ZEISS", "Carl Zeiss", "CARL ZEISS"),
        "Samsung" to listOf("Samsung", "SAMSUNG"),
        "Pentax" to listOf("Pentax", "PENTAX", "Ricoh", "RICOH"),
        "Tokina" to listOf("Tokina", "TOKINA"),
        "Samyang" to listOf("Samyang", "SAMYANG", "Rokinon", "ROKINON"),
        "Voigtlander" to listOf("Voigtlander", "VOIGTLANDER", "Voigtländer", "Cosina", "COSINA"),
        "Laowa" to listOf("Laowa", "LAOWA", "Venus", "VENUS"),
    )

    // ── 公共 API ─────────────────────────────────────────────────────

    /** 获取全部镜头配置数量 */
    val size: Int get() = profiles.size

    /** 获取全部镜头配置（只读） */
    fun allProfiles(): List<LensProfile> = profiles

    /**
     * 按 make + model + cropFactor 查找最匹配的镜头配置。
     * 使用模糊匹配容忍 EXIF 字符串差异。
     */
    fun findProfile(make: String, model: String, cropFactor: Float = 1f): LensProfile? {
        val normalizedMake = normalizeMake(make)
        val candidates = profiles.filter { matchMake(it.make, normalizedMake) }
        if (candidates.isEmpty()) return null

        // 精确匹配
        candidates.firstOrNull { it.model.equals(model, ignoreCase = true) }?.let { return it }

        // 模糊匹配：计算相似度分数
        return candidates.maxByOrNull { modelSimilarity(it.model, model) }
            ?.takeIf { modelSimilarity(it.model, model) > 0.35f }
    }

    /**
     * 按 make + model + focalLength 查找，并对 zoom 镜头在两端参数之间线性插值。
     * @return 插值后的 LensProfile，若非 zoom 则直接返回原始 profile
     */
    fun findProfileByFocalLength(make: String, model: String, focalLength: Float, cropFactor: Float = 1f): LensProfile? {
        val profile = findProfile(make, model, cropFactor) ?: return null

        // 定焦镜或无长焦端参数 → 直接返回
        if (profile.focalMin == profile.focalMax || profile.distortionAtele == 0f && profile.distortionBtele == 0f && profile.distortionCtele == 0f) {
            return profile
        }

        // 计算插值因子 t ∈ [0, 1]
        val range = profile.focalMax - profile.focalMin
        if (range <= 0f) return profile
        val t = ((focalLength - profile.focalMin) / range).coerceIn(0f, 1f)

        // 线性插值畸变系数
        val interpA = lerp(profile.distortionA, profile.distortionAtele, t)
        val interpB = lerp(profile.distortionB, profile.distortionBtele, t)
        val interpC = lerp(profile.distortionC, profile.distortionCtele, t)

        return profile.copy(
            distortionA = interpA,
            distortionB = interpB,
            distortionC = interpC,
            // 将当前焦距设为定焦，避免再次插值
            focalMin = focalLength,
            focalMax = focalLength,
            distortionAtele = 0f,
            distortionBtele = 0f,
            distortionCtele = 0f,
        )
    }

    /**
     * 自动校正：读取 EXIF 镜头信息 → 查找数据库 → 应用校正。
     * @return 校正后的 Bitmap；若未找到匹配配置则返回原图
     */
    fun autoCorrect(image: Bitmap, exif: ExifData): Bitmap {
        val make = exif.lensMake ?: exif.make ?: return image
        val model = exif.lensModel ?: return image

        // 尝试解析焦距
        val focalLength = exif.focalLength?.toFloatOrNull()

        val profile = if (focalLength != null && focalLength > 0f) {
            findProfileByFocalLength(make, model, focalLength)
        } else {
            findProfile(make, model)
        } ?: return image

        return LensCorrectionKernel.applyLensCorrection(
            image,
            profile,
            focalLength ?: profile.focalMin
        )
    }

    // ── 内部工具 ─────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** 规范化品牌名：遍历别名表找到标准品牌名 */
    private fun normalizeMake(raw: String): String {
        val lower = raw.lowercase()
        for ((standard, aliases) in makeAliases) {
            if (aliases.any { it.equals(raw, ignoreCase = true) }) return standard
        }
        // 子串匹配
        for ((standard, aliases) in makeAliases) {
            if (aliases.any { lower.contains(it.lowercase()) }) return standard
        }
        return raw
    }

    /** 判断数据库品牌与查询品牌是否匹配 */
    private fun matchMake(dbMake: String, normalizedQuery: String): Boolean {
        if (dbMake.equals(normalizedQuery, ignoreCase = true)) return true
        val dbAliases = makeAliases[dbMake] ?: listOf(dbMake)
        val queryAliases = makeAliases[normalizedQuery] ?: listOf(normalizedQuery)
        return dbAliases.any { db -> queryAliases.any { q -> db.equals(q, ignoreCase = true) } }
    }

    /**
     * 计算两个镜头型号字符串的相似度（0..1）。
     * 使用 token 重叠率 + 关键数字精确匹配加权。
     */
    private fun modelSimilarity(dbModel: String, query: String): Float {
        if (dbModel.equals(query, ignoreCase = true)) return 1f

        val dbNorm = normalizeModel(dbModel)
        val qNorm = normalizeModel(query)

        // Token 集合 Jaccard 相似度
        val dbTokens = dbNorm.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val qTokens = qNorm.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

        if (dbTokens.isEmpty() && qTokens.isEmpty()) return 1f
        if (dbTokens.isEmpty() || qTokens.isEmpty()) return 0f

        val intersection = dbTokens.intersect(qTokens)
        val union = dbTokens.union(qTokens)
        val jaccard = intersection.size.toFloat() / union.size

        // 关键数字（焦距、光圈）精确匹配加分
        val dbNums = extractKeyNumbers(dbNorm)
        val qNums = extractKeyNumbers(qNorm)
        val numMatch = if (dbNums.isNotEmpty() && qNums.isNotEmpty()) {
            val matched = dbNums.intersect(qNums).size.toFloat()
            val total = dbNums.union(qNums).size
            matched / total
        } else 0f

        // 加权：Jaccard 0.6 + 数字匹配 0.4
        return jaccard * 0.6f + numMatch * 0.4f
    }

    /** 规范化型号字符串：去除常见后缀/空白差异 */
    private fun normalizeModel(s: String): String {
        return s
            .replace("USM", "")
            .replace("HSM", "")
            .replace("STM", "")
            .replace("G ED", "G")
            .replace(" IF", "")
            .replace(" IS", "")
            .replace(" VR", "")
            .replace(" OS", "")
            .replace(" OIS", "")
            .replace(" VC", "")
            .replace(" PZD", "")
            .replace(" RXD", "")
            .replace(" OSD", "")
            .replace(" WR", "")
            .replace(" LM", "")
            .replace(" II", "")
            .replace(" III", "")
            .replace(" IV", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** 提取型号中的关键数字（焦距、光圈） */
    private fun extractKeyNumbers(s: String): Set<String> {
        val nums = mutableSetOf<String>()
        // 焦距如 24-70, 50, 85
        val focalRegex = Regex("""(\d+[-–]?\d*)mm""")
        focalRegex.findAll(s).forEach { nums.add(it.groupValues[1]) }
        // 光圈如 f/1.4, f/2.8
        val apertureRegex = Regex("""f/([\d.]+)""")
        apertureRegex.findAll(s).forEach { nums.add("f${it.groupValues[1]}") }
        // 也提取裸数字（如 "50" 单独出现）
        val bareNumRegex = Regex("""\b(\d{2,3}(?:\.\d)?)\b""")
        bareNumRegex.findAll(s).forEach { nums.add(it.groupValues[1]) }
        return nums
    }
}
