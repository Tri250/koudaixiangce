package com.rapidraw.core

import android.graphics.Bitmap
import android.util.SparseArray
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * 颜色替换处理器（PixelFruit 特性）
 *
 * 基于 RGB 向量投影的精确色域范围选择与替换：
 * - 通过起止颜色定义源色域范围，投影匹配实现精确选色
 * - 动态容差：根据色域范围长度自适应调整匹配精度
 * - 目标颜色插值：根据像素在源范围内的位置比例，线性插值目标颜色
 * - 距离衰减混合：越靠近范围中心替换越强，越靠近边缘过渡越平滑
 * - 同色缓存优化：对相同像素值复用计算结果
 * - 完整操作链：支持撤销、预览、提交、历史记录
 */
class ColorReplacementProcessor {

    companion object {
        private const val TAG = "ColorReplacementProcessor"
        private val idGenerator = AtomicLong(0L)
    }

    data class ColorRange(
        val startColor: Int,    // ARGB source start color
        val endColor: Int       // ARGB source end color
    )

    data class Replacement(
        val id: Long,
        val sourceRange: ColorRange,
        val targetStartColor: Int,
        val targetEndColor: Int,
        val tolerance: Int = 80,        // 10-200, color matching precision
        val intensity: Float = 1.0f,    // 0..1, blend ratio
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ReplacementResult(
        val bitmap: Bitmap,
        val replacedPixelCount: Int,
        val replacement: Replacement
    )

    // ── Internal state ──────────────────────────────────────────────

    /** Baseline pixel data for undo: original pixels before any replacement */
    private var baselinePixels: IntArray? = null
    private var baselineWidth: Int = 0
    private var baselineHeight: Int = 0
    private var baselineConfig: Bitmap.Config? = null

    /** Applied replacement history */
    private val history = mutableListOf<Replacement>()

    /** Preview state: temporary replacement not yet committed */
    private var pendingPreview: PreviewState? = null

    private class PreviewState(
        val replacement: Replacement,
        val resultBitmap: Bitmap,
        val replacedPixelCount: Int
    )

    // ── Cache entry for same-color pixel optimization ───────────────

    private class CacheEntry(
        val weight: Float,      // replacement weight [0,1]
        val targetR: Int,       // target red   [0,255]
        val targetG: Int,       // target green [0,255]
        val targetB: Int        // target blue  [0,255]
    )

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Apply a single color replacement to the bitmap.
     * Stores baseline data on first call for undo support.
     * Commits any pending preview before applying.
     */
    fun applyReplacement(bitmap: Bitmap, replacement: Replacement): ReplacementResult {
        // Commit any pending preview first
        commitPreview()

        // Initialize baseline from the input bitmap (first application)
        initBaseline(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val replacedCount = applyReplacementToPixels(pixels, w, h, replacement)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)

        history.add(replacement)

        return ReplacementResult(result, replacedCount, replacement)
    }

    /**
     * Undo the last replacement. Requires baseline data to exist.
     * Returns the reconstructed bitmap with the last replacement removed,
     * or null if no history exists.
     */
    fun undoLast(): Bitmap? {
        if (history.isEmpty()) return null
        if (baselinePixels == null) return null

        // Discard any pending preview
        clearPreview()

        history.removeAt(history.lastIndex)

        // Rebuild from baseline by replaying all remaining replacements
        val pixels = baselinePixels!!.copyOf()
        val w = baselineWidth
        val h = baselineHeight

        for (rep in history) {
            applyReplacementToPixels(pixels, w, h, rep)
        }

        val result = Bitmap.createBitmap(w, h, baselineConfig ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Get replacement history.
     */
    fun getHistory(): List<Replacement> = history.toList()

    /**
     * Preview replacement (temporary, does not modify baseline).
     * Returns a new bitmap with the replacement applied on top of
     * the current state, without recording it in history.
     *
     * Initializes baseline from the input bitmap if no baseline exists yet,
     * ensuring undo works correctly after commitPreview.
     */
    fun previewReplacement(bitmap: Bitmap, replacement: Replacement): Bitmap {
        // Capture baseline from the input bitmap if not yet initialized.
        // This ensures undo works after committing the preview.
        initBaseline(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val replacedCount = applyReplacementToPixels(pixels, w, h, replacement)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)

        // Store preview state
        clearPreview()
        pendingPreview = PreviewState(replacement, result, replacedCount)

        return result
    }

    /**
     * Commit preview as permanent.
     * Returns the ReplacementResult if a preview was pending, null otherwise.
     * The replacement is recorded in history and can be undone via undoLast().
     */
    fun commitPreview(): ReplacementResult? {
        val preview = pendingPreview ?: return null

        val replacement = preview.replacement
        history.add(replacement)

        val result = ReplacementResult(
            preview.resultBitmap,
            preview.replacedPixelCount,
            replacement
        )

        pendingPreview = null
        return result
    }

    /**
     * Clear all replacements and reset to initial state.
     */
    fun reset() {
        clearPreview()
        history.clear()
        baselinePixels = null
        baselineWidth = 0
        baselineHeight = 0
        baselineConfig = null
    }

    // ── Core Algorithm ──────────────────────────────────────────────

    /**
     * Apply a replacement to the pixel array in-place.
     * Returns the count of pixels that were replaced (weight > threshold).
     *
     * Algorithm:
     * 1. Source color range analysis: Convert start/end to RGB vectors,
     *    compute direction vector and range length
     * 2. Per-pixel color matching: Project pixel onto source direction,
     *    compute distance from range, use dynamic tolerance
     * 3. Target color computation: Linear interpolation based on position ratio
     * 4. Blending: Mix target with original based on intensity + distance
     * 5. Cache optimization: Reuse computation for same-color pixels
     */
    private fun applyReplacementToPixels(
        pixels: IntArray,
        w: Int,
        h: Int,
        replacement: Replacement
    ): Int {
        val tolerance = replacement.tolerance.coerceIn(10, 200)
        val intensity = replacement.intensity.coerceIn(0f, 1f)

        if (intensity < 1e-4f) return 0

        // ── Step 1: Source color range analysis ──
        val srcStart = replacement.sourceRange.startColor
        val srcEnd = replacement.sourceRange.endColor

        val srcR0 = (srcStart shr 16) and 0xFF
        val srcG0 = (srcStart shr 8) and 0xFF
        val srcB0 = srcStart and 0xFF

        val srcR1 = (srcEnd shr 16) and 0xFF
        val srcG1 = (srcEnd shr 8) and 0xFF
        val srcB1 = srcEnd and 0xFF

        // Direction vector (end - start) in RGB space
        val dirR = srcR1.toFloat() - srcR0
        val dirG = srcG1.toFloat() - srcG0
        val dirB = srcB1.toFloat() - srcB0

        // Range length (Euclidean in RGB)
        val rangeLength = sqrt(dirR * dirR + dirG * dirG + dirB * dirB)

        // Squared range length for projection computation
        val rangeLengthSq = rangeLength * rangeLength

        // ── Step 1b: Target color range ──
        val tgtStart = replacement.targetStartColor
        val tgtEnd = replacement.targetEndColor

        val tgtR0 = (tgtStart shr 16) and 0xFF
        val tgtG0 = (tgtStart shr 8) and 0xFF
        val tgtB0 = tgtStart and 0xFF

        val tgtR1 = (tgtEnd shr 16) and 0xFF
        val tgtG1 = (tgtEnd shr 8) and 0xFF
        val tgtB1 = tgtEnd and 0xFF

        // ── Step 1c: Dynamic tolerance ──
        // max(tolerance*0.5, min(tolerance*1.5, rangeLength*0.2))
        val dynamicTolerance = maxOf(
            tolerance * 0.5f,
            minOf(tolerance * 1.5f, rangeLength * 0.2f)
        )
        val dynamicToleranceSq = dynamicTolerance * dynamicTolerance

        // Handle edge case: identical source colors (point range)
        // In this case, range length is 0, so we use Euclidean distance to the point
        val isPointRange = rangeLengthSq < 1f

        // ── Step 5: Cache for same-color pixels ──
        val cache = SparseArray<CacheEntry>()

        var replacedCount = 0

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) and 0xFF

            // Check cache: if we've computed this exact pixel color before, reuse
            val cached = cache.get(pixel)
            if (cached != null) {
                if (cached.weight > 0.01f) {
                    val origR = (pixel shr 16) and 0xFF
                    val origG = (pixel shr 8) and 0xFF
                    val origB = pixel and 0xFF

                    val outR = (origR + (cached.targetR - origR) * cached.weight).toInt().coerceIn(0, 255)
                    val outG = (origG + (cached.targetG - origG) * cached.weight).toInt().coerceIn(0, 255)
                    val outB = (origB + (cached.targetB - origB) * cached.weight).toInt().coerceIn(0, 255)

                    pixels[i] = (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
                    replacedCount++
                }
                continue
            }

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // ── Step 2: Per-pixel color matching ──
            val weight: Float
            val positionRatio: Float

            if (isPointRange) {
                // Point range: use Euclidean distance to the single source color
                val dr = r.toFloat() - srcR0
                val dg = g.toFloat() - srcG0
                val db = b.toFloat() - srcB0
                val distSq = dr * dr + dg * dg + db * db

                if (distSq > dynamicToleranceSq * tolerance * tolerance) {
                    // Outside tolerance
                    cache.put(pixel, CacheEntry(0f, 0, 0, 0))
                    continue
                }

                // Weight based on distance: 1.0 at center, 0.0 at tolerance boundary
                val maxDist = tolerance.toFloat()
                val dist = sqrt(distSq)
                val normalizedDist = dist / maxDist
                weight = if (normalizedDist >= 1f) 0f else (1f - normalizedDist) * (1f - normalizedDist)
                positionRatio = 0.5f  // center of point range
            } else {
                // Range matching: project pixel onto the direction vector
                val pr = r.toFloat() - srcR0
                val pg = g.toFloat() - srcG0
                val pb = b.toFloat() - srcB0

                // Projection scalar: t = dot(pixel-start, direction) / |direction|^2
                val projection = (pr * dirR + pg * dirG + pb * dirB) / rangeLengthSq

                // Position ratio clamped to [0,1] for target interpolation
                positionRatio = projection.coerceIn(0f, 1f)

                // Closest point on the line segment
                val clampedProj = projection.coerceIn(0f, 1f)
                val closestR = srcR0 + dirR * clampedProj
                val closestG = srcG0 + dirG * clampedProj
                val closestB = srcB0 + dirB * clampedProj

                // Distance from pixel to the closest point on the segment
                val dr = r.toFloat() - closestR
                val dg = g.toFloat() - closestG
                val db = b.toFloat() - closestB
                val distSq = dr * dr + dg * dg + db * db

                // Check against dynamic tolerance
                val effectiveToleranceSq = dynamicToleranceSq + tolerance * tolerance * 0.5f
                if (distSq > effectiveToleranceSq) {
                    cache.put(pixel, CacheEntry(0f, 0, 0, 0))
                    continue
                }

                // Compute weight: blend of tolerance distance and range center proximity
                val effectiveTolerance = sqrt(effectiveToleranceSq)
                val dist = sqrt(distSq)
                val normalizedDist = dist / effectiveTolerance

                // Distance-based falloff: quadratic from 1 at center to 0 at boundary
                val distanceWeight = if (normalizedDist >= 1f) {
                    0f
                } else {
                    (1f - normalizedDist) * (1f - normalizedDist)
                }

                weight = distanceWeight
            }

            if (weight < 0.01f) {
                cache.put(pixel, CacheEntry(0f, 0, 0, 0))
                continue
            }

            // ── Step 3: Target color computation ──
            // Linearly interpolate between target start and end based on position ratio
            val tgtR = (tgtR0 + (tgtR1 - tgtR0) * positionRatio).toInt().coerceIn(0, 255)
            val tgtG = (tgtG0 + (tgtG1 - tgtG0) * positionRatio).toInt().coerceIn(0, 255)
            val tgtB = (tgtB0 + (tgtB1 - tgtB0) * positionRatio).toInt().coerceIn(0, 255)

            // ── Step 4: Blending ──
            // Final weight combines distance-based weight with user intensity
            val finalWeight = weight * intensity

            // Cache the computed values for this pixel color
            cache.put(pixel, CacheEntry(finalWeight, tgtR, tgtG, tgtB))

            // Apply blending
            val outR = (r + (tgtR - r) * finalWeight).toInt().coerceIn(0, 255)
            val outG = (g + (tgtG - g) * finalWeight).toInt().coerceIn(0, 255)
            val outB = (b + (tgtB - b) * finalWeight).toInt().coerceIn(0, 255)

            pixels[i] = (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
            replacedCount++
        }

        return replacedCount
    }

    // ── Baseline management ─────────────────────────────────────────

    /**
     * Initialize baseline pixel data from the given bitmap.
     * Only sets baseline once; subsequent calls are no-ops.
     */
    private fun initBaseline(bitmap: Bitmap) {
        if (baselinePixels != null) return

        val w = bitmap.width
        val h = bitmap.height
        baselineWidth = w
        baselineHeight = h
        baselineConfig = bitmap.config ?: Bitmap.Config.ARGB_8888

        baselinePixels = IntArray(w * h)
        bitmap.getPixels(baselinePixels, 0, w, 0, 0, w, h)
    }

    // ── Preview management ──────────────────────────────────────────

    private fun clearPreview() {
        pendingPreview = null
    }

    // ── Utility ─────────────────────────────────────────────────────

    /** Generate a new unique replacement ID */
    fun generateId(): Long = idGenerator.incrementAndGet()

    /**
     * Convenience: create a Replacement with auto-generated ID.
     */
    fun createReplacement(
        sourceRange: ColorRange,
        targetStartColor: Int,
        targetEndColor: Int,
        tolerance: Int = 80,
        intensity: Float = 1.0f
    ): Replacement {
        return Replacement(
            id = generateId(),
            sourceRange = sourceRange,
            targetStartColor = targetStartColor,
            targetEndColor = targetEndColor,
            tolerance = tolerance.coerceIn(10, 200),
            intensity = intensity.coerceIn(0f, 1f)
        )
    }

    /**
     * Get the current baseline bitmap, or null if no baseline is set.
     * Returns a new Bitmap (does not expose internal state).
     */
    fun getBaselineBitmap(): Bitmap? {
        val pixels = baselinePixels ?: return null
        val w = baselineWidth
        val h = baselineHeight
        val config = baselineConfig ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(w, h, config)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Check if a preview is currently pending.
     */
    fun hasPendingPreview(): Boolean = pendingPreview != null

    // ── HSV Compatibility Bridge ─────────────────────────────────────

    /**
     * HSV 兼容便捷方法：基于色相参数创建 Replacement 并应用。
     *
     * 将 HSV 色相范围转换为 RGB ColorRange，从源色相范围中心生成起止颜色，
     * 并通过色相偏移计算目标颜色。
     *
     * @param bitmap 输入位图
     * @param sourceHue 源色相中心 0..360
     * @param hueWidth 色相范围半宽 0..180
     * @param hueShift 色相偏移 -180..180
     * @param tolerance 颜色匹配容差 10..200
     * @param intensity 混合强度 0..1
     * @return ReplacementResult
     */
    fun processFromHue(
        bitmap: Bitmap,
        sourceHue: Float,
        hueWidth: Float = 30f,
        hueShift: Float = 0f,
        tolerance: Int = 80,
        intensity: Float = 1.0f
    ): ReplacementResult {
        val hue = ((sourceHue % 360f) + 360f) % 360f
        val width = hueWidth.coerceIn(0f, 180f)

        // Source range: hue ± hueWidth, full saturation and value for pure color endpoints
        val srcStartHue = hue - width
        val srcEndHue = hue + width

        // Convert source hue range endpoints to ARGB (full saturation, mid value for better matching)
        val srcStartRgb = ColorMath.hsvToRgb(((srcStartHue % 360f + 360f) % 360f), 1.0f, 1.0f)
        val srcEndRgb = ColorMath.hsvToRgb(((srcEndHue % 360f + 360f) % 360f), 1.0f, 1.0f)

        val srcStartColor = (0xFF shl 24) or
            (srcStartRgb[0].times(255f).toInt().coerceIn(0, 255) shl 16) or
            (srcStartRgb[1].times(255f).toInt().coerceIn(0, 255) shl 8) or
            srcStartRgb[2].times(255f).toInt().coerceIn(0, 255)

        val srcEndColor = (0xFF shl 24) or
            (srcEndRgb[0].times(255f).toInt().coerceIn(0, 255) shl 16) or
            (srcEndRgb[1].times(255f).toInt().coerceIn(0, 255) shl 8) or
            srcEndRgb[2].times(255f).toInt().coerceIn(0, 255)

        // Target range: shifted hue, same span
        val tgtStartHue = srcStartHue + hueShift
        val tgtEndHue = srcEndHue + hueShift

        val tgtStartRgb = ColorMath.hsvToRgb(((tgtStartHue % 360f + 360f) % 360f), 1.0f, 1.0f)
        val tgtEndRgb = ColorMath.hsvToRgb(((tgtEndHue % 360f + 360f) % 360f), 1.0f, 1.0f)

        val tgtStartColor = (0xFF shl 24) or
            (tgtStartRgb[0].times(255f).toInt().coerceIn(0, 255) shl 16) or
            (tgtStartRgb[1].times(255f).toInt().coerceIn(0, 255) shl 8) or
            tgtStartRgb[2].times(255f).toInt().coerceIn(0, 255)

        val tgtEndColor = (0xFF shl 24) or
            (tgtEndRgb[0].times(255f).toInt().coerceIn(0, 255) shl 16) or
            (tgtEndRgb[1].times(255f).toInt().coerceIn(0, 255) shl 8) or
            tgtEndRgb[2].times(255f).toInt().coerceIn(0, 255)

        val sourceRange = ColorRange(srcStartColor, srcEndColor)
        val replacement = createReplacement(
            sourceRange = sourceRange,
            targetStartColor = tgtStartColor,
            targetEndColor = tgtEndColor,
            tolerance = tolerance,
            intensity = intensity
        )

        return applyReplacement(bitmap, replacement)
    }
}
