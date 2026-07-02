package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * 拼图制作器。
 *
 * 功能：
 * - 支持多种布局模板（水平双图、垂直双图、1大+2小、2x2网格、1精选+4小、2x3网格、胶片条）
 * - 拖放排列（通过 slot 交换实现）
 * - Cover 模式图片适配（缩放填满槽位，居中裁剪多余部分）
 * - 可调间距、圆角、背景色、全局旋转
 */
class CollageMaker {

    enum class Layout(val slots: Int) {
        TWO_HORIZONTAL(2),
        TWO_VERTICAL(2),
        THREE_ONE_BIG(3),
        FOUR_GRID(4),
        FIVE_FEATURED(5),
        SIX_GRID(6),
        STRIP(8),
    }

    data class Slot(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val imageIndex: Int = -1,
    )

    data class Params(
        val layout: Layout = Layout.FOUR_GRID,
        val canvasWidth: Int = 2048,
        val canvasHeight: Int = 2048,
        val spacing: Int = 8,
        val cornerRadius: Int = 0,
        val backgroundColor: Int = 0xFF000000.toInt(),
        val imageScale: Float = 1.0f,
        val rotation: Float = 0f,
    )

    data class CollageResult(
        val bitmap: Bitmap,
        val layout: Layout,
        val slotAssignments: List<Int>,
    )

    /**
     * 根据布局和画布尺寸计算各槽位的归一化坐标。
     *
     * 间距以像素为单位，在归一化坐标系中扣除：将 spacing 换算为归一化偏移量，
     * 然后从各槽位边缘向内收缩。
     */
    fun computeSlots(params: Params): List<Slot> {
        val w = params.canvasWidth.toFloat()
        val h = params.canvasHeight.toFloat()
        val sp = params.spacing.toFloat()

        return when (params.layout) {
            Layout.TWO_HORIZONTAL -> {
                val halfW = 0.5f
                val spNormX = sp / w
                listOf(
                    Slot(0f, 0f, halfW - spNormX / 2f, 1f),
                    Slot(halfW + spNormX / 2f, 0f, halfW - spNormX / 2f, 1f),
                )
            }

            Layout.TWO_VERTICAL -> {
                val halfH = 0.5f
                val spNormY = sp / h
                listOf(
                    Slot(0f, 0f, 1f, halfH - spNormY / 2f),
                    Slot(0f, halfH + spNormY / 2f, 1f, halfH - spNormY / 2f),
                )
            }

            Layout.THREE_ONE_BIG -> {
                val bigW = 0.67f
                val smallW = 0.33f
                val spNormX = sp / w
                val spNormY = sp / h
                listOf(
                    Slot(0f, 0f, bigW - spNormX / 2f, 1f),
                    Slot(bigW + spNormX / 2f, 0f, smallW - spNormX / 2f, 0.5f - spNormY / 2f),
                    Slot(bigW + spNormX / 2f, 0.5f + spNormY / 2f, smallW - spNormX / 2f, 0.5f - spNormY / 2f),
                )
            }

            Layout.FOUR_GRID -> {
                val spNormX = sp / w
                val spNormY = sp / h
                val halfW = 0.5f
                val halfH = 0.5f
                val cellW = halfW - spNormX / 2f
                val cellH = halfH - spNormY / 2f
                listOf(
                    Slot(0f, 0f, cellW, cellH),
                    Slot(halfW + spNormX / 2f, 0f, cellW, cellH),
                    Slot(0f, halfH + spNormY / 2f, cellW, cellH),
                    Slot(halfW + spNormX / 2f, halfH + spNormY / 2f, cellW, cellH),
                )
            }

            Layout.FIVE_FEATURED -> {
                val featW = 0.6f
                val featH = 0.6f
                val rightW = 1f - featW       // 0.4
                val bottomH = 1f - featH      // 0.4
                val spNormX = sp / w
                val spNormY = sp / h
                // 右侧列分为上下两格
                val rightHalfH = (featH - spNormY) / 2f
                // 底部行分为左右两格
                val bottomHalfW = (1f - spNormX) / 2f
                listOf(
                    // 精选大图：左上
                    Slot(0f, 0f, featW - spNormX / 2f, featH - spNormY / 2f),
                    // 右列上
                    Slot(featW + spNormX / 2f, 0f, rightW - spNormX / 2f, rightHalfH),
                    // 右列下
                    Slot(featW + spNormX / 2f, rightHalfH + spNormY, rightW - spNormX / 2f, rightHalfH),
                    // 底行左
                    Slot(0f, featH + spNormY / 2f, bottomHalfW, bottomH - spNormY / 2f),
                    // 底行右
                    Slot(bottomHalfW + spNormX, featH + spNormY / 2f, bottomHalfW, bottomH - spNormY / 2f),
                )
            }

            Layout.SIX_GRID -> {
                val cols = 3
                val rows = 2
                val spNormX = sp / w
                val spNormY = sp / h
                val cellW = (1f - (cols - 1) * spNormX) / cols
                val cellH = (1f - (rows - 1) * spNormY) / rows
                val slots = mutableListOf<Slot>()
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val x = col * (cellW + spNormX)
                        val y = row * (cellH + spNormY)
                        slots.add(Slot(x, y, cellW, cellH))
                    }
                }
                slots
            }

            Layout.STRIP -> {
                val count = 8
                val spNormX = sp / w
                val cellW = (1f - (count - 1) * spNormX) / count
                val slots = mutableListOf<Slot>()
                for (i in 0 until count) {
                    val x = i * (cellW + spNormX)
                    slots.add(Slot(x, 0f, cellW, 1f))
                }
                slots
            }
        }
    }

    /**
     * 自动分配图片到槽位并生成拼图。
     *
     * 分配规则：按图片列表顺序依次填入槽位，超出槽位数量的图片忽略，
     * 不足时部分槽位留空。
     */
    fun createCollage(images: List<Bitmap>, params: Params): CollageResult {
        val slots = computeSlots(params)
        val assignments = mutableListOf<Int>()
        for (i in slots.indices) {
            if (i < images.size) {
                assignments.add(i)
            } else {
                assignments.add(-1)
            }
        }
        return renderCollage(images, assignments, slots, params)
    }

    /**
     * 使用显式槽位分配生成拼图。
     *
     * @param assignments assignments[slotIndex] = imageIndex，-1 表示空槽位
     */
    fun createCollageWithAssignment(
        images: List<Bitmap>,
        assignments: List<Int>,
        params: Params = Params(),
    ): CollageResult {
        val slots = computeSlots(params)
        val effectiveAssignments = assignments.toMutableList()
        // 如果 assignments 不足，补 -1
        while (effectiveAssignments.size < slots.size) {
            effectiveAssignments.add(-1)
        }
        return renderCollage(images, effectiveAssignments, slots, params)
    }

    /**
     * 交换两个槽位的分配。
     */
    fun swapSlots(assignments: MutableList<Int>, slotA: Int, slotB: Int): List<Int> {
        if (slotA < 0 || slotA >= assignments.size) return assignments.toList()
        if (slotB < 0 || slotB >= assignments.size) return assignments.toList()
        val temp = assignments[slotA]
        assignments[slotA] = assignments[slotB]
        assignments[slotB] = temp
        return assignments.toList()
    }

    // ── 内部渲染 ──────────────────────────────────────────────────────────

    private fun renderCollage(
        images: List<Bitmap>,
        assignments: List<Int>,
        slots: List<Slot>,
        params: Params,
    ): CollageResult {
        val config = if (images.isNotEmpty()) {
            val firstAssigned = assignments.firstOrNull { it >= 0 && it < images.size }
            if (firstAssigned != null) images[firstAssigned].config ?: Bitmap.Config.ARGB_8888 else Bitmap.Config.ARGB_8888
        } else {
            Bitmap.Config.ARGB_8888
        }

        val canvas = Bitmap.createBitmap(params.canvasWidth, params.canvasHeight, config)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = params.backgroundColor }
        val canvasObj = Canvas(canvas)
        canvasObj.drawRect(
            0f, 0f, params.canvasWidth.toFloat(), params.canvasHeight.toFloat(), paint
        )

        // 全局旋转
        if (params.rotation != 0f) {
            canvasObj.save()
            canvasObj.rotate(
                params.rotation,
                params.canvasWidth / 2f,
                params.canvasHeight / 2f,
            )
        }

        val hasRoundedCorners = params.cornerRadius > 0

        for ((slotIndex, slot) in slots.withIndex()) {
            if (slotIndex >= assignments.size) break
            val imageIndex = assignments[slotIndex]
            if (imageIndex < 0 || imageIndex >= images.size) continue

            val image = images[imageIndex]
            val slotRect = normalizedToPixel(slot, params.canvasWidth, params.canvasHeight)

            if (hasRoundedCorners) {
                drawRoundedSlot(canvasObj, image, slotRect, params.cornerRadius.toFloat(), params.imageScale)
            } else {
                drawSlot(canvasObj, image, slotRect, params.imageScale)
            }
        }

        if (params.rotation != 0f) {
            canvasObj.restore()
        }

        return CollageResult(
            bitmap = canvas,
            layout = params.layout,
            slotAssignments = assignments,
        )
    }

    /**
     * 将归一化槽位坐标转换为像素坐标。
     */
    private fun normalizedToPixel(slot: Slot, canvasW: Int, canvasH: Int): RectF {
        return RectF(
            slot.x * canvasW,
            slot.y * canvasH,
            (slot.x + slot.width) * canvasW,
            (slot.y + slot.height) * canvasH,
        )
    }

    /**
     * Cover 模式绘制：缩放图片填满槽位，居中裁剪多余部分。
     */
    private fun drawSlot(canvas: Canvas, image: Bitmap, slotRect: RectF, imageScale: Float) {
        val slotW = slotRect.width()
        val slotH = slotRect.height()
        if (slotW <= 0f || slotH <= 0f) return

        val imgW = image.width.toFloat()
        val imgH = image.height.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        val scale = imageScale * maxOf(slotW / imgW, slotH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale

        val dx = slotRect.left + (slotW - scaledW) / 2f
        val dy = slotRect.top + (slotH - scaledH) / 2f

        val srcLeft = 0f
        val srcTop = 0f
        val srcRight = imgW
        val srcBottom = imgH

        val srcRect = android.graphics.Rect(srcLeft.toInt(), srcTop.toInt(), srcRight.toInt(), srcBottom.toInt())
        canvas.drawBitmap(
            image,
            srcRect,
            RectF(dx, dy, dx + scaledW, dy + scaledH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    /**
     * 圆角槽位绘制：先裁剪为圆角矩形，再在 Cover 模式下绘制图片。
     */
    private fun drawRoundedSlot(
        canvas: Canvas,
        image: Bitmap,
        slotRect: RectF,
        radius: Float,
        imageScale: Float,
    ) {
        val saveCount = canvas.saveLayer(slotRect, null)

        // 绘制圆角蒙版
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        path.addRoundRect(slotRect, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, maskPaint)

        // 使用 SRC_IN 裁剪图片到蒙版区域
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        val slotW = slotRect.width()
        val slotH = slotRect.height()
        val imgW = image.width.toFloat()
        val imgH = image.height.toFloat()
        if (imgW <= 0f || imgH <= 0f || slotW <= 0f || slotH <= 0f) {
            canvas.restoreToCount(saveCount)
            return
        }

        val scale = imageScale * maxOf(slotW / imgW, slotH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale

        val dx = slotRect.left + (slotW - scaledW) / 2f
        val dy = slotRect.top + (slotH - scaledH) / 2f

        canvas.drawBitmap(
            image,
            android.graphics.Rect(0, 0, imgW.toInt(), imgH.toInt()),
            RectF(dx, dy, dx + scaledW, dy + scaledH),
            clipPaint,
        )

        canvas.restoreToCount(saveCount)
    }
}
