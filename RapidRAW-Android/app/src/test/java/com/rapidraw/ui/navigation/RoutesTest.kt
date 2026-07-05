package com.rapidraw.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class RoutesTest {

    @Test
    fun editorPath_encodesSpecialCharacters() {
        val path = "/sdcard/DCIM/photo 1.jpg"
        val route = Routes.editorPath(path)
        assertTrue(route.startsWith("editor/"))
        assertFalse(route.contains(" "))
    }

    @Test
    fun editorUri_encodesUri() {
        val uri = "content://media/external/images/media/12345"
        val route = Routes.editorUri(uri)
        assertTrue(route.startsWith("editor_uri/"))
        assertFalse(route.contains(":"))
    }

    @Test
    fun aiInpaintPath_matchesExpectedPattern() {
        val path = "/sdcard/IMG.dng"
        val route = Routes.aiInpaintPath(path)
        assertEquals("ai_inpaint/%2Fsdcard%2FIMG.dng", route)
    }

    @Test
    fun routeConstants_areStable() {
        assertEquals("onboarding", Routes.ONBOARDING)
        assertEquals("library", Routes.LIBRARY)
        assertEquals("editor/{imagePath}", Routes.EDITOR_PATH)
        assertEquals("editor_uri/{uri}", Routes.EDITOR_URI)
        assertEquals("ai_inpaint/{imagePath}", Routes.AI_INPAINT)
        assertEquals("presets_discovery", Routes.PRESETS_DISCOVERY)
        assertEquals("settings", Routes.SETTINGS)
        assertEquals("privacy_policy", Routes.PRIVACY_POLICY)
        assertEquals("user_agreement", Routes.USER_AGREEMENT)
        assertEquals("feedback", Routes.FEEDBACK)
        assertEquals("preset_import", Routes.PRESET_IMPORT)
        assertEquals("export_queue", Routes.EXPORT_QUEUE)
        assertEquals("lut_market", Routes.LUT_MARKET)
        assertEquals("recipe_share", Routes.RECIPE_SHARE)
    }

    // 2026 hotfix: 边界场景测试
    @Test
    fun editorPath_chinesePath_encodesCorrectly() {
        val path = "/sdcard/DCIM/照片.jpg"
        val route = Routes.editorPath(path)
        // 路径中所有 "/" 之后的字符应被 URL 编码
        assertTrue("Route should start with editor/: $route", route.startsWith("editor/"))
        // 解码后应能还原原始路径
        val encoded = route.removePrefix("editor/")
        val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
        assertEquals(path, decoded)
    }

    @Test
    fun editorPath_emptyPath_createsValidRoute() {
        val route = Routes.editorPath("")
        // 即使路径为空，也应该产生合法路由字符串
        assertEquals("editor/", route)
    }

    @Test
    fun editorUri_uriWithQueryString_encodesCorrectly() {
        val uri = "content://media/external/images/media/123?user=alice&token=abc"
        val route = Routes.editorUri(uri)
        // '?' '&' '=' 等特殊字符必须被编码
        assertFalse("Query string chars should be encoded: $route", route.contains("?"))
        assertFalse(route.contains("&"))
        assertFalse(route.contains("="))
        val encoded = route.removePrefix("editor_uri/")
        val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
        assertEquals(uri, decoded)
    }

    @Test
    fun editorPath_pathWithSlashes_preservesStructure() {
        val path = "/sdcard/DCIM/2024/01/IMG_001.dng"
        val route = Routes.editorPath(path)
        val encoded = route.removePrefix("editor/")
        val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
        assertEquals("Decoded path should equal original", path, decoded)
    }

    @Test
    fun deepLinkPrefix_isCorrect() {
        assertEquals("rapidraw://", Routes.DEEP_LINK_PREFIX)
    }

    @Test
    fun resultKeys_areDistinct() {
        val keys = setOf(
            Routes.ResultKeys.SELECTED_PRESET,
            Routes.ResultKeys.AI_INPAINT_RESULT,
            Routes.ResultKeys.IMPORTED_PRESET_URI,
        )
        assertEquals("All result keys should be distinct", 3, keys.size)
    }
}

