package com.rawlab.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

// 普通照片（JPEG/PNG/HEIC）解码：平台解码器 + EXIF 方向归一，
// 输出 tightly-packed RGBA8 字节，供 native 照片管线（INPUT_BUFFER）消费。
object StillImage {
    class Decoded(val pixels: ByteArray, val width: Int, val height: Int) {
        fun bitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        }
    }

    fun decode(file: File, longEdge: Int? = null): Decoded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Cannot decode ${file.name}")
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (longEdge != null && longEdge > 0) {
                // 采样取 2 的幂，保证长边不超过目标且尽量贴近。
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longEdge) sample *= 2
                inSampleSize = sample
            }
        }
        val decoded = BitmapFactory.decodeFile(file.path, options)
            ?: throw IOException("Cannot decode ${file.name}")
        val oriented = orient(decoded, file)
        try {
            val pixels = ByteArray(oriented.rowBytes * oriented.height)
            oriented.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
            return Decoded(pixels, oriented.width, oriented.height)
        } finally {
            if (oriented !== decoded) decoded.recycle()
            oriented.recycle()
        }
    }

    // 截图与相机照片常见 90/180/270 旋转；镜像类方向极罕见，按原始方向处理。
    private fun orient(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
