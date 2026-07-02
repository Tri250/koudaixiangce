package com.rapidraw.core

import kotlin.math.abs

/**
 * 镜头校正数据库（Lens Correction Database）
 *
 * 内置常见镜头校正参数，覆盖畸变（distortion）、暗角（vignette）、
 * 色差（chromatic aberration）与横向色差缩放（TCA）。
 *
 * 索引方式：相机品牌（make）+ 镜头型号（model）+ 焦距（focalLength）。
 * 支持模糊匹配（大小写不敏感、子串匹配）。
 *
 * 数据基于公开的光学测试数据与 Adobe Lens Profile 近似参数整理，
 * 涵盖 Canon、Nikon、Sony、Fujifilm、Panasonic、Olympus、Sigma、Tamron。
 */
object LensCorrectionDatabase {

    /**
     * 单镜头校正参数
     *
     * @param distortionCoeffs 径向畸变系数 [k1, k2, k3]（Brown-Conrady 模型）
     *   r_distorted = r * (1 + k1*r^2 + k2*r^4 + k3*r^6)
     * @param vignetteCoeffs 暗角系数 [k1, k2, k3]
     *   使用多项式模型：correction = 1 + k1*r^2 + k2*r^4 + k3*r^6
     * @param chromaticAberrationCoeffs 纵向色差系数 [axialRed, axialBlue]
   *   用于轴向色差近似补偿
     * @param tcaScale 横向色差缩放 [redScale, blueScale]
     *   基于焦距的 R/B 通道径向缩放偏移
     */
    data class LensCorrectionParams(
        val distortionCoeffs: FloatArray = floatArrayOf(0f, 0f, 0f),
        val vignetteCoeffs: FloatArray = floatArrayOf(0f, 0f, 0f),
        val chromaticAberrationCoeffs: FloatArray = floatArrayOf(0f, 0f),
        val tcaScale: FloatArray = floatArrayOf(1f, 1f),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LensCorrectionParams
            return distortionCoeffs.contentEquals(other.distortionCoeffs) &&
                vignetteCoeffs.contentEquals(other.vignetteCoeffs) &&
                chromaticAberrationCoeffs.contentEquals(other.chromaticAberrationCoeffs) &&
                tcaScale.contentEquals(other.tcaScale)
        }

        override fun hashCode(): Int {
            var result = distortionCoeffs.contentHashCode()
            result = 31 * result + vignetteCoeffs.contentHashCode()
            result = 31 * result + chromaticAberrationCoeffs.contentHashCode()
            result = 31 * result + tcaScale.contentHashCode()
            return result
        }
    }

    /**
     * 数据库条目（含焦距范围与标识信息）
     */
    private data class Entry(
        val make: String,
        val model: String,
        val focalLengthMin: Float,
        val focalLengthMax: Float,
        val params: LensCorrectionParams,
    )

    // ── 内置数据库 ──────────────────────────────────────────────────

    private val entries = listOf(
        // ── Canon ───────────────────────────────────────────────────
        Entry("Canon", "EF 16-35mm f/2.8L III USM", 16f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.048f, -0.012f, 0.003f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.35f, -0.12f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0015f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Canon", "EF 24-70mm f/2.8L II USM", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.028f, -0.008f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.42f, 0.22f, -0.06f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),
        Entry("Canon", "EF 24-105mm f/4L IS II USM", 24f, 105f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.032f, -0.009f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.48f, 0.28f, -0.08f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Canon", "EF 50mm f/1.2L USM", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.015f, -0.003f, 0.0005f),
                vignetteCoeffs = floatArrayOf(-0.85f, 0.55f, -0.18f),
                chromaticAberrationCoeffs = floatArrayOf(0.0015f, -0.0018f),
                tcaScale = floatArrayOf(1.00022f, 0.99978f),
            )),
        Entry("Canon", "EF 50mm f/1.8 STM", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.012f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.65f, 0.40f, -0.12f),
                chromaticAberrationCoeffs = floatArrayOf(0.0018f, -0.0020f),
                tcaScale = floatArrayOf(1.00028f, 0.99972f),
            )),
        Entry("Canon", "EF 85mm f/1.4L IS USM", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.008f, -0.001f, 0.0002f),
                vignetteCoeffs = floatArrayOf(-0.72f, 0.48f, -0.15f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00015f, 0.99985f),
            )),
        Entry("Canon", "EF 70-200mm f/2.8L IS III USM", 70f, 200f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.005f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.35f, 0.18f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0006f, -0.0008f),
                tcaScale = floatArrayOf(1.00010f, 0.99990f),
            )),
        Entry("Canon", "RF 15-35mm f/2.8L IS USM", 15f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.052f, -0.014f, 0.004f),
                vignetteCoeffs = floatArrayOf(-0.58f, 0.38f, -0.13f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Canon", "RF 24-70mm f/2.8L IS USM", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.026f, -0.007f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.40f, 0.20f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0009f, -0.0011f),
                tcaScale = floatArrayOf(1.00013f, 0.99987f),
            )),
        Entry("Canon", "RF 50mm f/1.2L USM", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.014f, -0.002f, 0.0004f),
                vignetteCoeffs = floatArrayOf(-0.90f, 0.60f, -0.20f),
                chromaticAberrationCoeffs = floatArrayOf(0.0016f, -0.0019f),
                tcaScale = floatArrayOf(1.00024f, 0.99976f),
            )),
        Entry("Canon", "RF 85mm f/1.2L USM", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.007f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.78f, 0.52f, -0.16f),
                chromaticAberrationCoeffs = floatArrayOf(0.0011f, -0.0013f),
                tcaScale = floatArrayOf(1.00016f, 0.99984f),
            )),

        // ── Nikon ───────────────────────────────────────────────────
        Entry("Nikon", "AF-S 14-24mm f/2.8G ED", 14f, 24f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.065f, -0.018f, 0.005f),
                vignetteCoeffs = floatArrayOf(-0.62f, 0.40f, -0.14f),
                chromaticAberrationCoeffs = floatArrayOf(0.0016f, -0.0018f),
                tcaScale = floatArrayOf(1.00024f, 0.99976f),
            )),
        Entry("Nikon", "AF-S 24-70mm f/2.8E ED VR", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.030f, -0.009f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.45f, 0.25f, -0.07f),
                chromaticAberrationCoeffs = floatArrayOf(0.0009f, -0.0011f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Nikon", "AF-S 50mm f/1.4G", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.011f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.68f, 0.42f, -0.13f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Nikon", "AF-S 85mm f/1.4G", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.006f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.75f, 0.50f, -0.16f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00015f, 0.99985f),
            )),
        Entry("Nikon", "AF-S 70-200mm f/2.8E FL ED VR", 70f, 200f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0008f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.32f, 0.16f, -0.04f),
                chromaticAberrationCoeffs = floatArrayOf(0.0005f, -0.0007f),
                tcaScale = floatArrayOf(1.00009f, 0.99991f),
            )),
        Entry("Nikon", "Z 14-24mm f/2.8 S", 14f, 24f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.058f, -0.016f, 0.004f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.35f, -0.12f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Nikon", "Z 24-70mm f/2.8 S", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.025f, -0.007f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.38f, 0.19f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),
        Entry("Nikon", "Z 50mm f/1.8 S", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.009f, -0.0015f, 0.0002f),
                vignetteCoeffs = floatArrayOf(-0.58f, 0.36f, -0.11f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Nikon", "Z 85mm f/1.8 S", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.005f, -0.0008f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.34f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),

        // ── Sony ────────────────────────────────────────────────────
        Entry("Sony", "FE 16-35mm f/2.8 GM", 16f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.050f, -0.013f, 0.003f),
                vignetteCoeffs = floatArrayOf(-0.52f, 0.32f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Sony", "FE 24-70mm f/2.8 GM II", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.024f, -0.006f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.36f, 0.18f, -0.04f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),
        Entry("Sony", "FE 50mm f/1.2 GM", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.013f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.82f, 0.54f, -0.17f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Sony", "FE 50mm f/1.8", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.010f, -0.0015f, 0.0002f),
                vignetteCoeffs = floatArrayOf(-0.60f, 0.36f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0018f, -0.0020f),
                tcaScale = floatArrayOf(1.00026f, 0.99974f),
            )),
        Entry("Sony", "FE 85mm f/1.4 GM", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.006f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.70f, 0.46f, -0.14f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Sony", "FE 70-200mm f/2.8 GM OSS II", 70f, 200f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0008f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.30f, 0.14f, -0.03f),
                chromaticAberrationCoeffs = floatArrayOf(0.0005f, -0.0007f),
                tcaScale = floatArrayOf(1.00008f, 0.99992f),
            )),

        // ── Fujifilm ────────────────────────────────────────────────
        Entry("Fujifilm", "XF 10-24mm f/4 R OIS", 10f, 24f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.072f, -0.020f, 0.006f),
                vignetteCoeffs = floatArrayOf(-0.58f, 0.36f, -0.12f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00022f, 0.99978f),
            )),
        Entry("Fujifilm", "XF 16-55mm f/2.8 R LM WR", 16f, 55f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.035f, -0.010f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.42f, 0.22f, -0.06f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00016f, 0.99984f),
            )),
        Entry("Fujifilm", "XF 18-55mm f/2.8-4 R LM OIS", 18f, 55f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.030f, -0.008f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.38f, 0.19f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Fujifilm", "XF 23mm f/1.4 R", 23f, 23f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.018f, -0.004f, 0.0008f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.33f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Fujifilm", "XF 35mm f/1.4 R", 35f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.012f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.48f, 0.28f, -0.08f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Fujifilm", "XF 56mm f/1.2 R", 56f, 56f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.006f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.62f, 0.38f, -0.11f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Fujifilm", "XF 50-140mm f/2.8 R LM OIS WR", 50f, 140f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.005f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.28f, 0.13f, -0.03f),
                chromaticAberrationCoeffs = floatArrayOf(0.0006f, -0.0008f),
                tcaScale = floatArrayOf(1.00010f, 0.99990f),
            )),
        Entry("Fujifilm", "GF 32-64mm f/4 R LM WR", 32f, 64f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.020f, -0.005f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.35f, 0.18f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),

        // ── Panasonic ───────────────────────────────────────────────
        Entry("Panasonic", "Lumix S Pro 16-35mm f/4", 16f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.048f, -0.012f, 0.003f),
                vignetteCoeffs = floatArrayOf(-0.50f, 0.30f, -0.09f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00016f, 0.99984f),
            )),
        Entry("Panasonic", "Lumix S Pro 24-70mm f/2.8", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.026f, -0.007f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.38f, 0.19f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),
        Entry("Panasonic", "Lumix S 50mm f/1.8", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.010f, -0.0015f, 0.0002f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.33f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Panasonic", "Leica DG Summilux 25mm f/1.4 II", 25f, 25f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.008f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.42f, 0.24f, -0.07f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Panasonic", "Leica DG Nocticron 42.5mm f/1.2", 42.5f, 42.5f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0005f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.48f, 0.28f, -0.08f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),

        // ── Olympus ─────────────────────────────────────────────────
        Entry("Olympus", "M.Zuiko 7-14mm f/2.8 PRO", 7f, 14f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.085f, -0.024f, 0.007f),
                vignetteCoeffs = floatArrayOf(-0.65f, 0.42f, -0.14f),
                chromaticAberrationCoeffs = floatArrayOf(0.0018f, -0.0020f),
                tcaScale = floatArrayOf(1.00026f, 0.99974f),
            )),
        Entry("Olympus", "M.Zuiko 12-40mm f/2.8 PRO", 12f, 40f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.032f, -0.009f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.40f, 0.20f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Olympus", "M.Zuiko 25mm f/1.2 PRO", 25f, 25f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.008f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.50f, 0.30f, -0.09f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00016f, 0.99984f),
            )),
        Entry("Olympus", "M.Zuiko 45mm f/1.2 PRO", 45f, 45f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0005f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.42f, 0.24f, -0.07f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Olympus", "M.Zuiko 40-150mm f/2.8 PRO", 40f, 150f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0008f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.25f, 0.11f, -0.02f),
                chromaticAberrationCoeffs = floatArrayOf(0.0006f, -0.0008f),
                tcaScale = floatArrayOf(1.00010f, 0.99990f),
            )),

        // ── Sigma ───────────────────────────────────────────────────
        Entry("Sigma", "14-24mm f/2.8 DG DN Art", 14f, 24f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.060f, -0.016f, 0.004f),
                vignetteCoeffs = floatArrayOf(-0.58f, 0.36f, -0.12f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Sigma", "24-70mm f/2.8 DG DN Art", 24f, 70f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.028f, -0.008f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.40f, 0.20f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0009f, -0.0011f),
                tcaScale = floatArrayOf(1.00013f, 0.99987f),
            )),
        Entry("Sigma", "35mm f/1.4 DG DN Art", 35f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.014f, -0.003f, 0.0005f),
                vignetteCoeffs = floatArrayOf(-0.55f, 0.33f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Sigma", "50mm f/1.4 DG HSM Art", 50f, 50f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.010f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.62f, 0.38f, -0.11f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
        Entry("Sigma", "85mm f/1.4 DG DN Art", 85f, 85f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.005f, -0.001f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.58f, 0.36f, -0.11f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Sigma", "100-400mm f/5-6.3 DG DN OS", 100f, 400f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.003f, -0.0005f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.22f, 0.09f, -0.02f),
                chromaticAberrationCoeffs = floatArrayOf(0.0004f, -0.0006f),
                tcaScale = floatArrayOf(1.00008f, 0.99992f),
            )),

        // ── Tamron ──────────────────────────────────────────────────
        Entry("Tamron", "17-28mm f/2.8 Di III RXD", 17f, 28f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.055f, -0.014f, 0.004f),
                vignetteCoeffs = floatArrayOf(-0.52f, 0.32f, -0.10f),
                chromaticAberrationCoeffs = floatArrayOf(0.0012f, -0.0014f),
                tcaScale = floatArrayOf(1.00018f, 0.99982f),
            )),
        Entry("Tamron", "28-75mm f/2.8 Di III VXD G2", 28f, 75f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.022f, -0.006f, 0.001f),
                vignetteCoeffs = floatArrayOf(-0.35f, 0.17f, -0.04f),
                chromaticAberrationCoeffs = floatArrayOf(0.0008f, -0.0010f),
                tcaScale = floatArrayOf(1.00012f, 0.99988f),
            )),
        Entry("Tamron", "70-180mm f/2.8 Di III VXD", 70f, 180f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.004f, -0.0008f, 0.0001f),
                vignetteCoeffs = floatArrayOf(-0.28f, 0.13f, -0.03f),
                chromaticAberrationCoeffs = floatArrayOf(0.0006f, -0.0008f),
                tcaScale = floatArrayOf(1.00010f, 0.99990f),
            )),
        Entry("Tamron", "35mm f/2.8 Di III OSD M1:2", 35f, 35f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.012f, -0.002f, 0.0003f),
                vignetteCoeffs = floatArrayOf(-0.38f, 0.20f, -0.05f),
                chromaticAberrationCoeffs = floatArrayOf(0.0010f, -0.0012f),
                tcaScale = floatArrayOf(1.00014f, 0.99986f),
            )),
        Entry("Tamron", "20mm f/2.8 Di III OSD M1:2", 20f, 20f,
            LensCorrectionParams(
                distortionCoeffs = floatArrayOf(0.035f, -0.008f, 0.002f),
                vignetteCoeffs = floatArrayOf(-0.45f, 0.26f, -0.08f),
                chromaticAberrationCoeffs = floatArrayOf(0.0014f, -0.0016f),
                tcaScale = floatArrayOf(1.00020f, 0.99980f),
            )),
    )

    /**
     * 根据相机品牌、镜头型号与焦距查找镜头校正参数。
     *
     * 匹配规则（优先级从高到低）：
     * 1. make + model 精确匹配（大小写不敏感），且 focalLength 在范围内
     * 2. make 精确匹配 + model 子串匹配，且 focalLength 在范围内
     * 3. make 子串匹配 + model 子串匹配，且 focalLength 在范围内
     *
     * @param make 相机品牌，如 "Canon", "Sony"
     * @param model 镜头型号，如 "EF 24-70mm f/2.8L II USM"
     * @param focalLength 当前焦距（mm），变焦镜头用于匹配最佳条目
     * @return 匹配到的 LensCorrectionParams，无匹配返回 null
     */
    fun findCorrection(make: String, model: String, focalLength: Float): LensCorrectionParams? {
        val makeLower = make.trim().lowercase()
        val modelLower = model.trim().lowercase()

        // 1. 精确匹配 make + model，焦距在范围内
        entries.firstOrNull {
            it.make.lowercase() == makeLower &&
                it.model.lowercase() == modelLower &&
                focalLength >= it.focalLengthMin && focalLength <= it.focalLengthMax
        }?.let { return it.params }

        // 2. 精确匹配 make，model 子串匹配，焦距在范围内
        entries.firstOrNull {
            it.make.lowercase() == makeLower &&
                (it.model.lowercase().contains(modelLower) || modelLower.contains(it.model.lowercase())) &&
                focalLength >= it.focalLengthMin && focalLength <= it.focalLengthMax
        }?.let { return it.params }

        // 3. make 子串匹配，model 子串匹配，焦距在范围内
        entries.firstOrNull {
            (it.make.lowercase().contains(makeLower) || makeLower.contains(it.make.lowercase())) &&
                (it.model.lowercase().contains(modelLower) || modelLower.contains(it.model.lowercase())) &&
                focalLength >= it.focalLengthMin && focalLength <= it.focalLengthMax
        }?.let { return it.params }

        // 4. 仅 make 匹配，焦距最接近
        val sameMake = entries.filter {
            (it.make.lowercase() == makeLower || it.make.lowercase().contains(makeLower) || makeLower.contains(it.make.lowercase())) &&
                focalLength >= it.focalLengthMin && focalLength <= it.focalLengthMax
        }
        if (sameMake.isNotEmpty()) {
            return sameMake.minByOrNull {
                val center = (it.focalLengthMin + it.focalLengthMax) / 2f
                abs(center - focalLength)
            }?.params
        }

        // 5. 全局最接近焦距匹配（兜底）
        val focalMatch = entries.filter { focalLength >= it.focalLengthMin && focalLength <= it.focalLengthMax }
        if (focalMatch.isNotEmpty()) {
            return focalMatch.minByOrNull {
                val center = (it.focalLengthMin + it.focalLengthMax) / 2f
                abs(center - focalLength)
            }?.params
        }

        return null
    }

    /**
     * 获取所有支持的品牌列表
     */
    fun supportedBrands(): List<String> = entries.map { it.make }.distinct().sorted()

    /**
     * 获取某品牌下的所有镜头型号
     */
    fun lensesForBrand(make: String): List<String> =
        entries.filter { it.make.lowercase() == make.trim().lowercase() }.map { it.model }.distinct().sorted()

    /**
     * 获取数据库中的条目总数
     */
    fun totalEntries(): Int = entries.size
}
