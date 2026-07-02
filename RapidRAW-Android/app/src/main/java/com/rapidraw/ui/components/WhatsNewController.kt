package com.rapidraw.ui.components

import android.content.Context
import com.rapidraw.core.SafePreferences

/**
 * "What's New" 对话框的版本检测控制器。
 *
 * 在升版本（versionCode 增大）后第一次启动时返回 true，让调用方展示
 * [WhatsNewDialog]。记录最后展示的 versionCode，同版本不重复展示。
 *
 * 与 v1.10.3 实现的差异（2026.07）：
 * 1. 提取为可单测对象（不再内嵌于 Composable 内部）；
 * 2. 持久化使用 SafePreferences，损坏时不闪退；
 * 3. 修复 "重装降级"场景：versionCode 降低（理论上不应发生，但 debug 模式可能）
 *    也视为未展示过。
 *
 * @since 2026.07
 */
object WhatsNewController {

    private const val TAG = "WhatsNewController"

    /** SharedPreferences 名称。 */
    const val PREFS_NAME = "rapidraw_whats_new"

    /** 最后一次成功展示 What's New 的 versionCode。 */
    const val KEY_LAST_SHOWN_VERSION_CODE = "last_shown_version_code"

    /**
     * 判断当前是否应展示 [WhatsNewDialog]。
     *
     * @param currentVersionCode 当前应用 versionCode（来自 PackageInfo）
     * @return true 表示该版本是首次安装或升级到了新版本
     */
    fun shouldShow(context: Context, currentVersionCode: Long): Boolean {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        val lastShown = SafePreferences.getLong(prefs, KEY_LAST_SHOWN_VERSION_CODE, -1L)
        return currentVersionCode > lastShown
    }

    /**
     * 标记当前版本已展示过 What's New。
     * 应在用户关闭 [WhatsNewDialog] 后调用。
     */
    fun markShown(context: Context, currentVersionCode: Long) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.putLong(prefs, KEY_LAST_SHOWN_VERSION_CODE, currentVersionCode)
    }

    /** 清除状态 —— 主要用于测试。 */
    fun clear(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.clear(prefs)
    }
}
