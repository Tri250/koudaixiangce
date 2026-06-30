package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.rapidraw.core.AiMaskGenerator
import com.rapidraw.core.AiInpainter
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AI物体去除器 - 一键去除路人和不需要的物体
 * 组合使用 AiSemanticSegmenter(识别人物) + AiInpainter(修复区域)
 */
object AiObjectRemover {
    
    data class RemovalResult(
        val resultBitmap: Bitmap,
        val detectedObjects: Int,
        val removedAreas: Int,
    )
    
    /**
     * 自动检测并去除图片中的人物/路人
     * @param source 原始位图
     * @param minAreaSize 最小面积阈值（像素），低于此值的区域不处理
     * @param preserveCenter 是否保留中心区域的人物（自拍保护）
     * @param progressCallback 进度回调 0.0~1.0
     */
    fun removePeople(
        source: Bitmap,
        minAreaSize: Int = 500,
        preserveCenter: Boolean = true,
        progressCallback: ((Float) -> Unit)? = null,
    ): RemovalResult {
        progressCallback?.invoke(0.1f)
        
        // Step 1: 生成人像语义蒙版
        val maskGenerator = AiMaskGenerator()
        val personMask = maskGenerator.generateMask(source, AiMaskGenerator.MaskType.SUBJECT)
        progressCallback?.invoke(0.4f)
        
        // Step 2: 如果启用中心保护，清除中心区域
        val refinedMask = if (preserveCenter) {
            refineMaskExcludeCenter(personMask, source.width, source.height)
        } else {
            personMask
        }
        progressCallback?.invoke(0.5f)
        
        // Step 3: 过滤太小的区域
        val filteredMask = filterSmallAreas(refinedMask, minAreaSize, source.width, source.height)
        progressCallback?.invoke(0.6f)
        
        // Step 4: 计算检测到的对象数量
        val detectedObjects = countConnectedRegions(personMask, source.width)
        val removedAreas = countConnectedRegions(filteredMask, source.width)
        
        // Step 5: 使用 AI Inpainter 修复
        val inpainter = AiInpainter()
        val result = inpainter.removeObject(source, filteredMask, iterations = 3)
        progressCallback?.invoke(1.0f)
        
        return RemovalResult(
            resultBitmap = result,
            detectedObjects = detectedObjects,
            removedAreas = removedAreas,
        )
    }
    
    /**
     * 去除指定区域的物体
     * @param source 原始位图
     * @param mask 蒙版位图（白色=要去除的区域）
     */
    fun removeObject(
        source: Bitmap,
        mask: Bitmap,
        progressCallback: ((Float) -> Unit)? = null,
    ): Bitmap {
        progressCallback?.invoke(0.2f)
        val inpainter = AiInpainter()
        val result = inpainter.removeObject(source, mask, iterations = 3)
        progressCallback?.invoke(1.0f)
        return result
    }
    
    /**
     * 清除蒙版中心1/4区域（自拍保护）
     */
    private fun refineMaskExcludeCenter(mask: Bitmap, imgW: Int, imgH: Int): Bitmap {
        val refined = mask.copy(Bitmap.Config.ARGB_8888, true)
        val cx = imgW / 2f
        val cy = imgH / 2f
        val radius = minOf(imgW, imgH) * 0.25f
        
        for (y in 0 until imgH) {
            for (x in 0 until imgW) {
                val dx = x - cx
                val dy = y - cy
                if (sqrt((dx * dx + dy * dy).toDouble()).toFloat() < radius) {
                    refined.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        return refined
    }
    
    /**
     * 过滤面积太小的区域，避免去除噪点
     */
    private fun filterSmallAreas(mask: Bitmap, minArea: Int, w: Int, h: Int): Bitmap {
        // 简化实现：使用形态学开运算去除小区域
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        
        // 先腐蚀再膨胀（开运算），去除小孤立点
        val kernelSize = 3
        val eroded = morphErode(pixels, w, h, kernelSize)
        val opened = morphDilate(eroded, w, h, kernelSize)
        
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(opened, 0, w, 0, 0, w, h)
        return result
    }
    
    private fun morphErode(pixels: IntArray, w: Int, h: Int, k: Int): IntArray {
        val result = IntArray(pixels.size) { 0xFF000000.toInt() }
        val half = k / 2
        for (y in half until h - half) {
            for (x in half until w - half) {
                var allWhite = true
                for (dy in -half..half) {
                    for (dx in -half..half) {
                        val p = pixels[(y + dy) * w + (x + dx)]
                        if ((p and 0x00FFFFFF) == 0) { allWhite = false; break }
                    }
                    if (!allWhite) break
                }
                result[y * w + x] = if (allWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return result
    }
    
    private fun morphDilate(pixels: IntArray, w: Int, h: Int, k: Int): IntArray {
        val result = IntArray(pixels.size) { 0xFF000000.toInt() }
        val half = k / 2
        for (y in half until h - half) {
            for (x in half until w - half) {
                var anyWhite = false
                for (dy in -half..half) {
                    for (dx in -half..half) {
                        val p = pixels[(y + dy) * w + (x + dx)]
                        if ((p and 0x00FFFFFF) != 0) { anyWhite = true; break }
                    }
                    if (anyWhite) break
                }
                result[y * w + x] = if (anyWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return result
    }
    
    /**
     * 简单的连通区域计数（用于统计检测到的对象数量）
     */
    private fun countConnectedRegions(mask: Bitmap, w: Int): Int {
        val pixels = IntArray(w * mask.height)
        mask.getPixels(pixels, 0, w, 0, 0, w, mask.height)
        val visited = BooleanArray(pixels.size)
        var count = 0
        
        for (i in pixels.indices) {
            if (!visited[i] && (pixels[i] and 0x00FFFFFF) != 0) {
                count++
                // BFS 标记
                val queue = java.util.ArrayDeque<Int>()
                queue.add(i)
                visited[i] = true
                while (queue.isNotEmpty()) {
                    val idx = queue.poll()
                    val x = idx % w
                    val y = idx / w
                    for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until mask.height) {
                            val nIdx = ny * w + nx
                            if (!visited[nIdx] && (pixels[nIdx] and 0x00FFFFFF) != 0) {
                                visited[nIdx] = true
                                queue.add(nIdx)
                            }
                        }
                    }
                }
            }
        }
        return count
    }
}
