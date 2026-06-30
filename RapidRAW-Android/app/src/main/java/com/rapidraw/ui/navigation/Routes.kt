package com.rapidraw.ui.navigation

import java.net.URLEncoder

/**
 * 集中管理所有 Compose 路由常量与路由构造工具。
 *
 * - 路由常量：用于 NavHost composable 注册和 navigate。
 * - 路由构造工具：负责对参数进行 URL 编码，避免路径中的特殊字符破坏路由匹配。
 * - ResultKeys：跨页面返回结果（SavedStateHandle）使用的字符串键。
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val EDITOR_PATH = "editor/{imagePath}"
    const val EDITOR_URI = "editor_uri/{uri}"
    const val AI_INPAINT = "ai_inpaint/{imagePath}"
    const val PRESETS_DISCOVERY = "presets_discovery"
    const val SETTINGS = "settings"
    const val PRIVACY_POLICY = "privacy_policy"
    const val USER_AGREEMENT = "user_agreement"
    const val FEEDBACK = "feedback"
    const val PRESET_IMPORT = "preset_import"
    const val EXPORT_QUEUE = "export_queue"
    const val LUT_MARKET = "lut_market"
    const val RECIPE_SHARE = "recipe_share"

    const val DEEP_LINK_PREFIX = "rapidraw://"

    fun editorPath(path: String): String = "editor/${urlEncode(path)}"
    fun editorUri(uri: String): String = "editor_uri/${urlEncode(uri)}"
    fun aiInpaintPath(path: String): String = "ai_inpaint/${urlEncode(path)}"

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    /**
     * 跨页面结果传递使用的字符串键，必须与 SavedStateHandle 中的 key 一致。
     */
    object ResultKeys {
        const val SELECTED_PRESET = "selected_preset"
        const val AI_INPAINT_RESULT = "ai_inpaint_result"
        const val IMPORTED_PRESET_URI = "imported_preset_uri"
    }
}
