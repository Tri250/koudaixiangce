package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * 遮罩合成器 — 支持遮罩的加/减/交/差运算。
 * 对标 CyberTimon/RapidRAW v1.5.4 的遮罩相交功能。
 */
class MaskCompositor {

    enum class CombineMode {
        ADD,        // 并集 — A ∪ B
        SUBTRACT,   // 差集 — A - B
        INTERSECT,  // 交集 — A ∩ B
        DIFFERENCE, // 对称差 — A △ B
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * 合成两个遮罩位图
     * @param maskA 第一个遮罩（白色=选中区域）
     * @param maskB 第二个遮罩
     * @param mode 合成模式
     * @return 合成后的遮罩位图
     */
    fun combine(maskA: Bitmap, maskB: Bitmap, mode: CombineMode): Bitmap {
        val w = maskA.width
        val h = maskA.height

        // 确保 maskB 尺寸匹配
        val resizedB = if (maskB.width != w || maskB.height != h) {
            Bitmap.createScaledBitmap(maskB, w, h, true)
        } else {
            maskB
        }

        val pixelsA = IntArray(w * h)
        val pixelsB = IntArray(w * h)
        maskA.getPixels(pixelsA, 0, w, 0, 0, w, h)
        resizedB.getPixels(pixelsB, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)

        for (i in pixelsA.indices) {
            val a = (pixelsA[i] ushr 24) and 0xFF  // Alpha 通道作为遮罩值
            val b = (pixelsB[i] ushr 24) and 0xFF

            val resultAlpha = when (mode) {
                CombineMode.ADD -> maxOf(a, b)
                CombineMode.SUBTRACT -> maxOf(0, a - b)
                CombineMode.INTERSECT -> minOf(a, b)
                CombineMode.DIFFERENCE -> kotlin.math.abs(a - b)
            }

            result[i] = (resultAlpha shl 24) or 0x00FFFFFF.toInt()
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * 使用 Canvas 合成（GPU加速路径）
     */
    fun combineCanvas(maskA: Bitmap, maskB: Bitmap, mode: CombineMode): Bitmap {
        val w = maskA.width
        val h = maskA.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 先绘制 maskA
        canvas.drawBitmap(maskA, 0f, 0f, paint)

        // 使用 Xfermode 合成 maskB
        val xfermode = when (mode) {
            CombineMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            CombineMode.SUBTRACT -> PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
            CombineMode.INTERSECT -> PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            CombineMode.DIFFERENCE -> PorterDuffXfermode(PorterDuff.Mode.XOR)
        }

        paint.xfermode = xfermode
        canvas.drawBitmap(maskB, 0f, 0f, paint)
        paint.xfermode = null

        return result
    }
}
