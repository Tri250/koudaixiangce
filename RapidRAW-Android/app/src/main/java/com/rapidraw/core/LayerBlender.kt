package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.AdjustmentLayer
import com.rapidraw.data.model.BlendMode

/**
 * 图层混合处理器 - 实现BlendMode的像素级混合算法
 */
object LayerBlender {

    /**
     * 将图层混合到基础位图上
     * @param base 基础位图（会被修改）
     * @param layer 要混合的图层
     * @param layerBitmap 图层处理后的位图
     * @return 混合后的位图
     */
    fun blend(base: Bitmap, layer: AdjustmentLayer, layerBitmap: Bitmap): Bitmap {
        if (!layer.enabled || layer.opacity < 0.01f) return base

        val w = minOf(base.width, layerBitmap.width)
        val h = minOf(base.height, layerBitmap.height)
        val result = base.copy(Bitmap.Config.ARGB_8888, true)

        val basePixels = IntArray(w * h)
        val layerPixels = IntArray(w * h)
        result.getPixels(basePixels, 0, w, 0, 0, w, h)
        layerBitmap.getPixels(layerPixels, 0, w, 0, 0, w, h)

        val opacity = layer.opacity

        for (i in basePixels.indices) {
            val bp = basePixels[i]
            val lp = layerPixels[i]

            var br = ((bp shr 16) and 0xFF) / 255f
            var bg = ((bp shr 8) and 0xFF) / 255f
            var bb = (bp and 0xFF) / 255f

            val lr = ((lp shr 16) and 0xFF) / 255f
            val lg = ((lp shr 8) and 0xFF) / 255f
            val lb = (lp and 0xFF) / 255f

            // Apply blend mode
            val (or, og, ob) = applyBlendMode(br, bg, bb, lr, lg, lb, layer.blendMode)

            // Apply opacity
            br = br + (or - br) * opacity
            bg = bg + (og - bg) * opacity
            bb = bb + (ob - bb) * opacity

            // Apply mask if present (mask stored as alpha channel)
            val maskAlpha = ((lp ushr 24) and 0xFF) / 255f
            if (maskAlpha < 1f) {
                br = br * maskAlpha + ((bp shr 16) and 0xFF) / 255f * (1f - maskAlpha)
                bg = bg * maskAlpha + ((bp shr 8) and 0xFF) / 255f * (1f - maskAlpha)
                bb = bb * maskAlpha + (bp and 0xFF) / 255f * (1f - maskAlpha)
            }

            val ri = (br.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val gi = (bg.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val bi = (bb.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            basePixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        result.setPixels(basePixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyBlendMode(
        br: Float, bg: Float, bb: Float,
        lr: Float, lg: Float, lb: Float,
        mode: BlendMode
    ): Triple<Float, Float, Float> {
        return when (mode) {
            BlendMode.NORMAL -> Triple(lr, lg, lb)
            BlendMode.MULTIPLY -> Triple(br * lr, bg * lg, bb * lb)
            BlendMode.SCREEN -> {
                Triple(
                    1f - (1f - br) * (1f - lr),
                    1f - (1f - bg) * (1f - lg),
                    1f - (1f - bb) * (1f - lb)
                )
            }
            BlendMode.OVERLAY -> Triple(
                if (br < 0.5f) 2f * br * lr else 1f - 2f * (1f - br) * (1f - lr),
                if (bg < 0.5f) 2f * bg * lg else 1f - 2f * (1f - bg) * (1f - lg),
                if (bb < 0.5f) 2f * bb * lb else 1f - 2f * (1f - bb) * (1f - lb),
            )
            BlendMode.SOFT_LIGHT -> {
                fun softLight(base: Float, layer: Float): Float {
                    return if (layer < 0.5f) {
                        base - (1f - 2f * layer) * base * (1f - base)
                    } else {
                        val t = 1f - 2f * layer
                        base + t * (4f * base * (1f - base) + 0.375f) * (base - 0.5f * (1f - t))
                    }
                }
                Triple(softLight(br, lr), softLight(bg, lg), softLight(bb, lb))
            }
            BlendMode.HARD_LIGHT -> Triple(
                if (lr < 0.5f) 2f * br * lr else 1f - 2f * (1f - br) * (1f - lr),
                if (lg < 0.5f) 2f * bg * lg else 1f - 2f * (1f - bg) * (1f - lg),
                if (lb < 0.5f) 2f * bb * lb else 1f - 2f * (1f - bb) * (1f - lb),
            )
            BlendMode.COLOR -> {
                // Use layer hue+sat with base luminance
                val baseLum = 0.2126f * br + 0.7152f * bg + 0.0722f * bb
                val layerLum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb
                val scale = if (layerLum > 0.001f) baseLum / layerLum else 1f
                Triple(
                    (lr * scale).coerceIn(0f, 1f),
                    (lg * scale).coerceIn(0f, 1f),
                    (lb * scale).coerceIn(0f, 1f)
                )
            }
            BlendMode.LUMINOSITY -> {
                // Use base hue+sat with layer luminance
                val baseLum = 0.2126f * br + 0.7152f * bg + 0.0722f * bb
                val layerLum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb
                val scale = if (baseLum > 0.001f) layerLum / baseLum else 1f
                Triple(
                    (br * scale).coerceIn(0f, 1f),
                    (bg * scale).coerceIn(0f, 1f),
                    (bb * scale).coerceIn(0f, 1f)
                )
            }
        }
    }
}
