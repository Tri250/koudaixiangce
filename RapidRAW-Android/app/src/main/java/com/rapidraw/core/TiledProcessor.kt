package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

/**
 * 大图分块处理器 — 将超大图像拆分为瓦片逐块处理，避免 OOM。
 * 支持 >20MP 图像的稳定处理，瓦片间 10% 重叠避免拼接痕迹。
 */
class TiledProcessor(
    private val tileSize: Int = 2048,
    private val overlap: Float = 0.1f,
) {

    private companion object {
        const val TAG = "TiledProcessor"
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 分块处理大图。
     * @param source 源图像
     * @param processor 对每个瓦片执行的处理函数（输入瓦片→输出瓦片）
     * @return 处理后的完整图像
     */
    suspend fun process(
        source: Bitmap,
        processor: suspend (Bitmap) -> Bitmap,
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height

        // 小图不需要分块
        if (w.toLong() * h.toLong() <= tileSize.toLong() * tileSize.toLong()) {
            return@withContext processor(source)
        }

        val result = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM creating result bitmap ${w}x${h}", e)
            return@withContext source
        }
        val canvas = Canvas(result)

        val overlapPx = (tileSize * overlap).toInt()
        val step = tileSize - overlapPx

        val cols = ceil(w.toDouble() / step).toInt()
        val rows = ceil(h.toDouble() / step).toInt()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = (col * step).coerceAtMost(w - 1)
                val y = (row * step).coerceAtMost(h - 1)
                val tw = min(tileSize, w - x)
                val th = min(tileSize, h - y)

                if (tw <= 0 || th <= 0) continue

                val tile = try {
                    Bitmap.createBitmap(source, x, y, tw, th)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM creating tile at ($x,$y) size ${tw}x${th}", e)
                    continue
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Cannot create tile at ($x,$y) size ${tw}x${th}", e)
                    continue
                }
                val processedTile = try {
                    processor(tile)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM processing tile at ($x,$y)", e)
                    if (tile !== source) tile.recycle()
                    continue
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing tile at ($x,$y)", e)
                    if (tile !== source) tile.recycle()
                    continue
                }

                // 绘制时排除重叠区域（仅绘制非重叠部分）
                val drawX = x
                val drawY = y
                val drawW = min(tw, step)
                val drawH = min(th, step)

                val src = Rect(0, 0, drawW, drawH)
                val dst = RectF(
                    drawX.toFloat(),
                    drawY.toFloat(),
                    (drawX + drawW).toFloat(),
                    (drawY + drawH).toFloat(),
                )
                canvas.drawBitmap(processedTile, src, dst, paint)

                if (tile !== source) tile.recycle()
                if (processedTile !== tile) processedTile.recycle()
            }
        }

        result
    }

    /**
     * 判断图像是否需要分块处理
     */
    fun needsTiling(width: Int, height: Int): Boolean {
        return width.toLong() * height.toLong() > tileSize.toLong() * tileSize.toLong()
    }
}
