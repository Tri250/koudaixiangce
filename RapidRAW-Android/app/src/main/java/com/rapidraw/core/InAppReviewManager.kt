package com.rapidraw.core

import android.app.Activity
import android.util.Log

/**
 * Google Play 应用内评价管理器。
 *
 * 使用 Play In-App Review API 在合适的时机（如完成一次导出后）
 * 引导用户评价，提升应用评分。
 *
 * 使用示例:
 * ```
 * InAppReviewManager.requestReview(activity) { launched ->
 *     if (launched) Log.d(TAG, "Review flow launched")
 * }
 * ```
 *
 * 触发时机建议：
 * - 完成第 3 次导出后
 * - 使用 AI 功能 5 次后
 * - 连续使用 7 天后
 *
 * @since v1.10.3（正式版功能完整性）
 */
object InAppReviewManager {

    private const val TAG = "InAppReview"
    private const val MIN_EXPORT_COUNT = 3
    private const val MIN_AI_USAGE_COUNT = 5
    private const val REVIEW_COOLDOWN_DAYS = 90

    private var lastReviewRequestMs: Long = 0L

    /**
     * 请求应用内评价流程。
     *
     * 注意：Google Play 限制评价弹窗的显示频率（每月最多一次），
     * 且不保证每次调用都会显示。此方法处理了所有边缘情况。
     *
     * @param activity 当前 Activity
     * @param onResult 结果回调（launched: 是否成功启动了评价流程）
     */
    fun requestReview(activity: Activity, onResult: ((Boolean) -> Unit)? = null) {
        if (isInCooldown()) {
            Log.d(TAG, "Review request skipped — in cooldown period")
            onResult?.invoke(false)
            return
        }

        try {
            val factoryClass = Class.forName("com.google.android.play.core.review.ReviewManagerFactory")
            val createMethod = factoryClass.getMethod("create", android.content.Context::class.java)
            val manager = createMethod.invoke(null, activity)
            val requestReviewFlowMethod = manager.javaClass.getMethod("requestReviewFlow")
            val request = requestReviewFlowMethod.invoke(manager)
            val addOnCompleteListenerMethod = request.javaClass.getMethod("addOnCompleteListener", com.google.android.gms.tasks.OnCompleteListener::class.java)
            addOnCompleteListenerMethod.invoke(request, com.google.android.gms.tasks.OnCompleteListener<Any> { task ->
                try {
                    val isSuccessfulMethod = task.javaClass.getMethod("isSuccessful")
                    val isSuccessful = isSuccessfulMethod.invoke(task) as Boolean
                    if (isSuccessful) {
                        val getResultMethod = task.javaClass.getMethod("getResult")
                        val reviewInfo = getResultMethod.invoke(task)
                        val launchReviewFlowMethod = manager.javaClass.getMethod("launchReviewFlow", android.app.Activity::class.java, reviewInfo.javaClass)
                        val launchRequest = launchReviewFlowMethod.invoke(manager, activity, reviewInfo)
                        launchRequest.javaClass.getMethod("addOnCompleteListener", com.google.android.gms.tasks.OnCompleteListener::class.java)
                            .invoke(launchRequest, com.google.android.gms.tasks.OnCompleteListener<Any> { launchTask ->
                                try {
                                    val launched = launchTask.javaClass.getMethod("isSuccessful").invoke(launchTask) as Boolean
                                    if (launched) {
                                        lastReviewRequestMs = System.currentTimeMillis()
                                    }
                                    Log.d(TAG, "Review flow launched: $launched")
                                    onResult?.invoke(launched)
                                } catch (_: Exception) {
                                    onResult?.invoke(false)
                                }
                            })
                    } else {
                        Log.w(TAG, "Failed to request review flow")
                        onResult?.invoke(false)
                    }
                } catch (_: Exception) {
                    onResult?.invoke(false)
                }
            })
        } catch (_: Exception) {
            Log.d(TAG, "Play In-App Review API not available")
            onResult?.invoke(false)
        }
    }

    /**
     * 根据用户使用数据判断是否应该触发评价请求。
     *
     * @param exportCount 导出次数
     * @param aiUsageCount AI 功能使用次数
     * @return true 表示应该触发评价
     */
    fun shouldRequestReview(exportCount: Int, aiUsageCount: Int): Boolean {
        if (isInCooldown()) return false
        return exportCount >= MIN_EXPORT_COUNT || aiUsageCount >= MIN_AI_USAGE_COUNT
    }

    private fun isInCooldown(): Boolean {
        val cooldownMs = REVIEW_COOLDOWN_DAYS * 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastReviewRequestMs) < cooldownMs
    }
}