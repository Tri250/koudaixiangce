package com.rapidraw.ui.onboarding

import android.content.Context
import com.rapidraw.core.SafePreferences

/**
 * 引导状态的单一事实源。
 *
 * 解决 v1.5.5 之前 [com.rapidraw.ui.navigation.rememberStartDestination] 与
 * [OnboardingViewModel] 各读一份 SharedPreferences，可能在引导完成瞬间读到不同步的
 * 值导致冷启动时短暂闪现引导页的竞态。
 *
 * @since 2026.07
 */
object OnboardingState {

    /** SharedPreferences 名称 —— 与 v1.5.5 保持兼容。 */
    const val PREFS_NAME = "rapidraw_onboarding"

    /** 引导完成标志。 */
    const val KEY_COMPLETED = "onboarding_completed"

    /** 同步读取当前是否已完成引导（覆盖安装/升级场景下应在 Application 阶段调用）。 */
    fun isCompleted(context: Context): Boolean {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        return SafePreferences.getBoolean(prefs, KEY_COMPLETED, false)
    }

    /** 标记引导已完成。幂等。 */
    fun markCompleted(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.putBoolean(prefs, KEY_COMPLETED, true)
    }

    /** 清除引导状态 —— 主要用于测试。 */
    fun clear(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.clear(prefs)
    }
}
