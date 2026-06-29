package com.rapidraw.core

import android.graphics.Bitmap

/**
 * Cross-image copy/paste for adjustments and masks.
 *
 * Singleton object that stores adjustments and mask data in memory (session-only, transient).
 */
object EditorClipboard {

    @Volatile
    private var adjustments: Adjustments? = null

    @Volatile
    private var mask: Bitmap? = null

    private val lock = Any()

    /**
     * Copies the given adjustments to the clipboard.
     */
    fun copyAdjustments(adjustments: Adjustments) {
        synchronized(lock) {
            this.adjustments = adjustments.deepCopy()
        }
    }

    /**
     * Copies the given mask bitmap to the clipboard.
     * The mask is stored as a mutable copy to avoid reference issues.
     */
    fun copyMask(mask: Bitmap) {
        synchronized(lock) {
            this.mask?.recycle()
            this.mask = mask.copy(mask.config ?: Bitmap.Config.ALPHA_8, true)
        }
    }

    /**
     * Returns a deep copy of the currently stored adjustments, or null if none.
     */
    fun pasteAdjustments(): Adjustments? {
        synchronized(lock) {
            return adjustments?.deepCopy()
        }
    }

    /**
     * Returns a copy of the currently stored mask, or null if none.
     */
    fun pasteMask(): Bitmap? {
        synchronized(lock) {
            return mask?.copy(mask!!.config ?: Bitmap.Config.ALPHA_8, true)
        }
    }

    /**
     * Returns true if there are adjustments on the clipboard.
     */
    fun hasAdjustments(): Boolean {
        synchronized(lock) {
            return adjustments != null
        }
    }

    /**
     * Returns true if there is a mask on the clipboard.
     */
    fun hasMask(): Boolean {
        synchronized(lock) {
            return mask != null && !mask!!.isRecycled
        }
    }

    /**
     * Clears all clipboard content.
     */
    fun clear() {
        synchronized(lock) {
            adjustments = null
            mask?.let {
                if (!it.isRecycled) it.recycle()
            }
            mask = null
        }
    }
}

/**
 * Represents a set of image adjustments that can be copied between images.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val noiseReduction: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val colorProfile: String = "",
    val customParams: Map<String, Float> = emptyMap()
) {
    fun deepCopy(): Adjustments {
        return this.copy(customParams = customParams.toMap())
    }
}