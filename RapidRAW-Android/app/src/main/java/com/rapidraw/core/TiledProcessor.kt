package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Large image tiled processing to avoid OutOfMemoryError.
 *
 * Splits the source image into tiles, processes each independently,
 * and reassembles them into a single output bitmap.
 */
object TiledProcessor {

    /**
     * Processes a bitmap in tiles to limit memory usage.
     *
     * @param source     Source bitmap to process
     * @param tileSize   Maximum tile dimension (width and height), default 1024
     * @param processor  Lambda that processes each tile bitmap
     * @param onProgress Optional progress callback receiving 0.0 to 1.0
     * @return Processed output bitmap
     */
    suspend fun processTiled(
        source: Bitmap,
        tileSize: Int = 1024,
        onProgress: ((Float) -> Unit)? = null,
        processor: (Bitmap) -> Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        // If the image is smaller than tile size, process directly
        if (width <= tileSize && height <= tileSize) {
            onProgress?.invoke(0.5f)
            val result = processor(source)
            onProgress?.invoke(1.0f)
            return@withContext result
        }

        // Calculate number of tiles
        val cols = (width + tileSize - 1) / tileSize
        val rows = (height + tileSize - 1) / tileSize
        val totalTiles = cols * rows

        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var completed = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                // Calculate tile bounds (handle edge tiles correctly)
                val left = col * tileSize
                val top = row * tileSize
                val right = minOf(left + tileSize, width)
                val bottom = minOf(top + tileSize, height)

                // Extract tile
                val tileRect = Rect(left, top, right, bottom)
                val tileWidth = right - left
                val tileHeight = bottom - top

                val tile = Bitmap.createBitmap(
                    source,
                    left, top, tileWidth, tileHeight
                )

                // Process the tile
                val processedTile = try {
                    processor(tile)
                } catch (e: Exception) {
                    // If processing fails, use original tile
                    tile.copy(tile.config, true)
                } finally {
                    // Only recycle if tile was not returned by processor
                    if (tile != processedTile) {
                        tile.recycle()
                    }
                }

                // Draw processed tile onto output canvas
                if (processedTile.width == tileWidth && processedTile.height == tileHeight) {
                    canvas.drawBitmap(processedTile, left.toFloat(), top.toFloat(), paint)
                } else {
                    // Handle case where processor changes tile dimensions
                    val dstRect = Rect(left, top, right, bottom)
                    canvas.drawBitmap(processedTile, null, dstRect, paint)
                }

                processedTile.recycle()

                completed++
                onProgress?.invoke(completed.toFloat() / totalTiles)
            }
        }

        onProgress?.invoke(1.0f)
        output
    }
}