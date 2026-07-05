package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin

/**
 * 水印渲染器
 * 来自 RapidRAW 导出功能，支持图像水印和文字水印，9 个锚点位置。
 *
 * 渲染流程：
 * 1. 计算水印的边界框（文字用 Paint.getTextBounds，图片按 scale 缩放）
 * 2. 根据锚点位置计算水印在输出图上的放置坐标
 * 3. 叠加偏移量（offsetX, offsetY）
 * 4. 以水印中心为原点执行旋转
 * 5. 通过 Alpha 通道应用透明度
 */
class WatermarkRenderer {

    companion object {
        private const val TAG = "WatermarkRenderer"
    }

    // ── 枚举与数据结构 ────────────────────────────────────────────

    enum class Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
    }

    data class TextWatermark(
        val text: String = "RapidRAW",
        val fontSize: Float = 24f,
        val color: Int = Color.WHITE,
        val typeface: Typeface = Typeface.DEFAULT,
        val shadowRadius: Float = 2f,
        val shadowColor: Int = Color.BLACK,
    )

    data class ImageWatermark(
        val bitmap: Bitmap,
        val scale: Float = 0.1f,
    )

    data class Params(
        val anchor: Anchor = Anchor.BOTTOM_RIGHT,
        val offsetX: Float = 16f,
        val offsetY: Float = 16f,
        val opacity: Float = 0.5f,
        val rotation: Float = 0f,
        val textWatermark: TextWatermark? = null,
        val imageWatermark: ImageWatermark? = null,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 将水印应用到输入 Bitmap 上
     *
     * 在输入图像上方渲染水印并返回新 Bitmap。
     * 不会修改原始 Bitmap。
     *
     * @param bitmap 输入图像
     * @param params 水印参数
     * @return 叠加了水印的新 Bitmap
     */
    fun apply(bitmap: Bitmap, params: Params): Bitmap {
        val watermarkLayer = renderWatermarkLayer(bitmap.width, bitmap.height, params)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 绘制原始图像
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // 叠加水印层
        canvas.drawBitmap(watermarkLayer, 0f, 0f, null)

        watermarkLayer.recycle()
        return result
    }

    /**
     * 仅渲染水印层（用于 GPU 合成或单独使用）
     *
     * 返回一个与 (width x height) 同大小的透明 Bitmap，
     * 仅包含水印内容。
     *
     * @param width 输出宽度
     * @param height 输出高度
     * @param params 水印参数
     * @return 水印层 Bitmap（透明背景）
     */
    fun renderWatermarkLayer(width: Int, height: Int, params: Params): Bitmap {
        val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 优先使用文字水印，若不存在则使用图片水印
        if (params.textWatermark != null) {
            renderTextWatermark(canvas, width, height, params)
        } else if (params.imageWatermark != null) {
            renderImageWatermark(canvas, width, height, params)
        }

        // 应用全局透明度
        applyOpacity(layer, params.opacity)

        return layer
    }

    // ── 文字水印渲染 ──────────────────────────────────────────────

    private fun renderTextWatermark(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        params: Params,
    ) {
        val tw = params.textWatermark ?: return

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = tw.fontSize
            color = tw.color
            typeface = tw.typeface
            // 阴影
            if (tw.shadowRadius > 0f) {
                setShadowLayer(tw.shadowRadius, 1f, 1f, tw.shadowColor)
            }
        }

        // 测量文字边界
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(tw.text, 0, tw.text.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()

        // 计算锚点位置
        val (anchorX, anchorY) = computeAnchorPosition(
            canvasWidth.toFloat(), canvasHeight.toFloat(),
            textWidth, textHeight,
            params.anchor,
        )

        // 加上偏移量
        val drawX = anchorX + params.offsetX
        val drawY = anchorY + params.offsetY

        // 水印中心点（用于旋转）
        val centerX = drawX + textWidth / 2f
        val centerY = drawY + textHeight / 2f

        // 应用旋转
        if (params.rotation != 0f) {
            canvas.save()
            canvas.rotate(params.rotation, centerX, centerY)
        }

        // drawText 的 y 坐标是基线，需要修正
        val baselineY = drawY + textHeight - textBounds.bottom.toFloat()
        canvas.drawText(tw.text, drawX, baselineY, paint)

        if (params.rotation != 0f) {
            canvas.restore()
        }
    }

    // ── 图片水印渲染 ──────────────────────────────────────────────

    private fun renderImageWatermark(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        params: Params,
    ) {
        val iw = params.imageWatermark ?: return
        val srcBitmap = iw.bitmap

        // 按 scale 缩放：相对于输出图像宽度
        val scaledWidth = (canvasWidth * iw.scale).coerceIn(1f, canvasWidth.toFloat())
        val aspectRatio = srcBitmap.height.toFloat() / srcBitmap.width.toFloat()
        val scaledHeight = (scaledWidth * aspectRatio).coerceIn(1f, canvasHeight.toFloat())

        // 缩放水印位图
        val scaledBitmap = Bitmap.createScaledBitmap(
            srcBitmap,
            scaledWidth.toInt().coerceAtLeast(1),
            scaledHeight.toInt().coerceAtLeast(1),
            true,
        )

        // 计算锚点位置
        val (anchorX, anchorY) = computeAnchorPosition(
            canvasWidth.toFloat(), canvasHeight.toFloat(),
            scaledWidth, scaledHeight,
            params.anchor,
        )

        // 加上偏移量
        val drawX = anchorX + params.offsetX
        val drawY = anchorY + params.offsetY

        // 水印中心点（用于旋转）
        val centerX = drawX + scaledWidth / 2f
        val centerY = drawY + scaledHeight / 2f

        // 应用旋转
        if (params.rotation != 0f) {
            canvas.save()
            canvas.rotate(params.rotation, centerX, centerY)
        }

        canvas.drawBitmap(scaledBitmap, drawX, drawY, null)

        if (params.rotation != 0f) {
            canvas.restore()
        }

        // 如果缩放后的位图不是原始位图，回收
        if (scaledBitmap !== srcBitmap) {
            scaledBitmap.recycle()
        }
    }

    // ── 锚点位置计算 ──────────────────────────────────────────────

    /**
     * 计算水印在画布上的左上角坐标
     *
     * @param canvasWidth  画布宽度
     * @param canvasHeight 画布高度
     * @param wmWidth      水印宽度
     * @param wmHeight     水印高度
     * @param anchor       锚点位置
     * @return (x, y) 水印左上角坐标
     */
    private fun computeAnchorPosition(
        canvasWidth: Float,
        canvasHeight: Float,
        wmWidth: Float,
        wmHeight: Float,
        anchor: Anchor,
    ): Pair<Float, Float> {
        // 各锚点对应的放置坐标
        // 锚点定义了水印边界框的哪个角/边对齐到画布的哪个区域
        return when (anchor) {
            // 顶部行
            Anchor.TOP_LEFT -> Pair(0f, 0f)
            Anchor.TOP_CENTER -> Pair((canvasWidth - wmWidth) / 2f, 0f)
            Anchor.TOP_RIGHT -> Pair(canvasWidth - wmWidth, 0f)

            // 中间行
            Anchor.MIDDLE_LEFT -> Pair(0f, (canvasHeight - wmHeight) / 2f)
            Anchor.MIDDLE_CENTER -> Pair((canvasWidth - wmWidth) / 2f, (canvasHeight - wmHeight) / 2f)
            Anchor.MIDDLE_RIGHT -> Pair(canvasWidth - wmWidth, (canvasHeight - wmHeight) / 2f)

            // 底部行
            Anchor.BOTTOM_LEFT -> Pair(0f, canvasHeight - wmHeight)
            Anchor.BOTTOM_CENTER -> Pair((canvasWidth - wmWidth) / 2f, canvasHeight - wmHeight)
            Anchor.BOTTOM_RIGHT -> Pair(canvasWidth - wmWidth, canvasHeight - wmHeight)
        }
    }

    // ── 透明度应用 ────────────────────────────────────────────────

    /**
     * 通过修改 Alpha 通道应用全局透明度
     *
     * 逐像素调整 Alpha 值：newAlpha = originalAlpha * opacity
     * 这样可以保留水印内部的抗锯齿信息。
     *
     * @param bitmap 要调整透明度的 Bitmap（原地修改）
     * @param opacity 透明度 [0,1]
     */
    private fun applyOpacity(bitmap: Bitmap, opacity: Float) {
        if (opacity >= 1f) return // 完全不透明，无需修改

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val alphaScale = (opacity.coerceIn(0f, 1f) * 255).toInt()

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = ((pixel ushr 24) and 0xFF)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // 按比例缩放 Alpha
            val newA = (a * alphaScale) / 255

            pixels[i] = (newA shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    /**
     * 从 ExportSettings 的 WatermarkAnchor 枚举转换为本地 Anchor 枚举
     * 用于与现有数据模型的互操作
     */
    fun fromExportAnchor(anchor: com.rapidraw.data.model.WatermarkAnchor): Anchor {
        return when (anchor) {
            com.rapidraw.data.model.WatermarkAnchor.TOP_LEFT -> Anchor.TOP_LEFT
            com.rapidraw.data.model.WatermarkAnchor.TOP_CENTER -> Anchor.TOP_CENTER
            com.rapidraw.data.model.WatermarkAnchor.TOP_RIGHT -> Anchor.TOP_RIGHT
            com.rapidraw.data.model.WatermarkAnchor.CENTER_LEFT -> Anchor.MIDDLE_LEFT
            com.rapidraw.data.model.WatermarkAnchor.CENTER -> Anchor.MIDDLE_CENTER
            com.rapidraw.data.model.WatermarkAnchor.CENTER_RIGHT -> Anchor.MIDDLE_RIGHT
            com.rapidraw.data.model.WatermarkAnchor.BOTTOM_LEFT -> Anchor.BOTTOM_LEFT
            com.rapidraw.data.model.WatermarkAnchor.BOTTOM_CENTER -> Anchor.BOTTOM_CENTER
            com.rapidraw.data.model.WatermarkAnchor.BOTTOM_RIGHT -> Anchor.BOTTOM_RIGHT
        }
    }
}
