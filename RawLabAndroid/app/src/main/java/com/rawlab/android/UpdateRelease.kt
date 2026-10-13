package com.rawlab.android

import java.util.Locale

data class UpdateRelease(val version: String, val notes: String, val url: String) {
    companion object {
        const val REPOSITORY = "https://github.com/dancancer/RawLab"
        const val AUTHOR = "https://www.xiaohongshu.com/user/profile/6474b1560000000010037c8e"
        const val ENDPOINT = "https://api.github.com/repos/dancancer/RawLab/releases/latest"

        fun select(tag: String, draft: Boolean, preview: Boolean, notes: String,
                   assets: List<String>, current: String): UpdateRelease? {
            if (draft || preview) return null
            val latest = numbers(tag)
            // 本机版本名异常（如 unknown）不应让检查整体失败，保守提示可更新让用户自行判断。
            val installed = runCatching { numbers(current) }.getOrNull()
            if (installed != null) {
                val difference = latest.zip(installed).firstOrNull { (a, b) -> a != b }
                if (difference == null || difference.first < difference.second) return null
            }
            // 资产命名大小写不规范时同样要能识别 Android 安装包。
            val hasApk = assets.any { name ->
                val lowered = name.lowercase(Locale.ROOT)
                lowered.startsWith("rawlab-android-") && lowered.endsWith(".apk")
            }
            if (!hasApk) return null
            return UpdateRelease(latest.joinToString("."), notes, "$REPOSITORY/releases/tag/$tag")
        }

        private fun numbers(text: String): List<Int> {
            require(Regex("v?[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(text)) { "Invalid release version" }
            val parts = text.removePrefix("v").split('.').map { it.toInt() }
            return if (parts.size == 2) parts + 0 else parts
        }

        fun shouldCheck(manual: Boolean, enabled: Boolean, last: Long, now: Long) =
            manual || (enabled && (last == 0L || now < last || now - last >= 86400))
    }
}
