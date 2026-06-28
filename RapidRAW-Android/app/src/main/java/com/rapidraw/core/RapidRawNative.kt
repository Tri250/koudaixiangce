package com.rapidraw.core

import android.graphics.Bitmap
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * JNI bridge to the Rust `rapidraw_core` native library.
 *
 * This replaces the previous Kotlin-only [ImageProcessor] for heavy lifting.
 * All methods are `external` and delegate to `librapidraw_core.so`.
 */
object RapidRawNative {

    init {
        System.loadLibrary("rapidraw_core")
    }

    /** Load an image file and return an opaque native handle. */
    external fun loadImage(
        path: String,
        fastDemosaic: Boolean = false,
        highlightCompression: Float = 1.5f,
    ): Long

    /** Release a native image handle. */
    external fun freeImage(handle: Long)

    /** Return `[width, height]` for the given image handle. */
    external fun getImageDimensions(handle: Long): IntArray

    /** Initialize the native GPU context bound to an Android [Surface]. */
    external fun initGpuContext(nativeWindow: Surface): Boolean

    /**
     * Render a preview into a direct [IntBuffer].
     *
     * The buffer must be direct, allocated for at least
     * `previewWidth * previewHeight` 32-bit ARGB pixels, and in native byte
     * order. Use [createPreviewBuffer] to obtain a suitable buffer.
     */
    external fun processPreview(
        handle: Long,
        adjustmentsJson: String,
        outputBuffer: IntBuffer,
        previewWidth: Int,
        previewHeight: Int,
    ): Boolean

    /** Export a full-resolution image and return the encoded bytes. */
    external fun exportImage(
        handle: Long,
        adjustmentsJson: String,
        exportSettingsJson: String,
    ): ByteArray

    /** Read EXIF metadata as a JSON string. */
    external fun readExif(path: String): String

    /** Look up a lens correction profile as a JSON string. */
    external fun findLensCorrection(
        maker: String,
        model: String,
        focalLength: Float,
        aperture: Float,
    ): String

    /** Generate a thumbnail JPEG as a byte array. */
    external fun generateThumbnail(path: String, targetSize: Int): ByteArray

    /** Create a direct [IntBuffer] suitable for [processPreview]. */
    fun createPreviewBuffer(width: Int, height: Int): IntBuffer {
        require(width > 0 && height > 0)
        return ByteBuffer
            .allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
    }

    /**
     * Convenience helper: process a preview and copy the result into a
     * freshly allocated [Bitmap].
     */
    fun processPreviewToBitmap(
        handle: Long,
        adjustmentsJson: String,
        previewWidth: Int,
        previewHeight: Int,
    ): Bitmap? {
        val buffer = createPreviewBuffer(previewWidth, previewHeight)
        if (!processPreview(handle, adjustmentsJson, buffer, previewWidth, previewHeight)) {
            return null
        }
        buffer.rewind()
        return Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
            .apply { copyPixelsFromBuffer(buffer) }
    }
}
