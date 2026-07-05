package com.alcedo.studio.core

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

class BitmapPool(
    private val maxSizeBytes: Long = Constants.BitmapPool.MAX_SIZE_BYTES.toLong(),
    private val maxBitmaps: Int = Constants.BitmapPool.MAX_BITMAPS
) {
    private val pool = ConcurrentHashMap<PoolKey, Bitmap>()
    private val semaphore = Semaphore(maxBitmaps)
    private val currentSize = AtomicLong(0L)

    @Volatile
    private var isClearing = false

    fun acquire(width: Int, height: Int, config: Config = Config.ARGB_8888): Bitmap {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid bitmap dimensions: $width x $height")
        }

        try {
            semaphore.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Bitmap.createBitmap(width, height, config)
        }

        if (isClearing) {
            semaphore.release()
            return Bitmap.createBitmap(width, height, config)
        }

        val key = PoolKey(width, height, config)
        val bitmap = pool.remove(key)

        if (bitmap != null && !bitmap.isRecycled && bitmap.width == width && bitmap.height == height) {
            currentSize.addAndGet(-getBitmapSize(bitmap))
            return bitmap
        }

        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }

        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (e: OutOfMemoryError) {
            L.e("BitmapPool", "OOM creating bitmap ${width}x$height, trimming memory", e)
            trimMemory(level = 100)
            try {
                Bitmap.createBitmap(width, height, config)
            } catch (e2: OutOfMemoryError) {
                L.e("BitmapPool", "OOM persists after trim, releasing semaphore", e2)
                semaphore.release()
                throw e2
            }
        }
    }

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) {
            semaphore.release()
            return
        }

        if (isClearing) {
            bitmap.recycle()
            semaphore.release()
            return
        }

        val size = getBitmapSize(bitmap)
        val current = currentSize.get()

        if (size + current <= maxSizeBytes && pool.size < maxBitmaps) {
            val key = PoolKey(bitmap.width, bitmap.height, bitmap.config)
            val existing = pool.putIfAbsent(key, bitmap)
            if (existing == null) {
                currentSize.addAndGet(size)
            } else {
                bitmap.recycle()
            }
        } else {
            bitmap.recycle()
        }
        semaphore.release()
    }

    fun trimMemory(level: Int = 100) {
        val targetSize = if (level >= 100) 0L else maxSizeBytes * (100 - level) / 100

        synchronized(this) {
            isClearing = true
            try {
                val iterator = pool.entries.iterator()
                while (iterator.hasNext() && currentSize.get() > targetSize) {
                    val entry = iterator.next()
                    try {
                        if (!entry.value.isRecycled) {
                            entry.value.recycle()
                        }
                    } catch (_: Exception) {
                    }
                    currentSize.addAndGet(-getBitmapSize(entry.value))
                    iterator.remove()
                }

                if (currentSize.get() < 0) {
                    currentSize.set(0)
                }
            } finally {
                isClearing = false
            }
        }
    }

    fun clear() {
        synchronized(this) {
            isClearing = true
            try {
                pool.values.forEach {
                    try {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    } catch (_: Exception) {
                    }
                }
                pool.clear()
                currentSize.set(0)
            } finally {
                isClearing = false
            }
        }
    }

    fun getPoolSize(): Int = pool.size
    fun getCurrentMemoryUsage(): Long = currentSize.get()

    private fun getBitmapSize(bitmap: Bitmap): Long {
        return try {
            if (bitmap.isRecycled) 0L else bitmap.allocationByteCount.toLong()
        } catch (e: Exception) {
            if (bitmap.isRecycled) 0L else (bitmap.width * bitmap.height * 4).toLong()
        }
    }

    private data class PoolKey(
        val width: Int,
        val height: Int,
        val config: Config?
    )
}

object BitmapManager {
    private val pool = BitmapPool(
        Constants.BitmapPool.MAX_SIZE_BYTES.toLong(),
        Constants.BitmapPool.MAX_BITMAPS
    )

    @JvmStatic
    fun getBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        return pool.acquire(width, height, config)
    }

    @JvmStatic
    fun returnBitmap(bitmap: Bitmap) {
        pool.release(bitmap)
    }

    @JvmStatic
    fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    @JvmStatic
    fun trimMemory(level: Int = 100) {
        pool.trimMemory(level)
    }

    @JvmStatic
    fun clearPool() {
        pool.clear()
    }

    fun getPoolStats(): Pair<Int, Long> {
        return pool.getPoolSize() to pool.getCurrentMemoryUsage()
    }

    @JvmStatic
    fun calculateSampleSize(
        options: android.graphics.BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
