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
        assertEquals("library", Routes.LIBRARY)
        assertEquals("editor/{imagePath}", Routes.EDITOR_PATH)
        assertEquals("editor_uri/{uri}", Routes.EDITOR_URI)
        assertEquals("ai_inpaint/{imagePath}", Routes.AI_INPAINT)
        assertEquals("presets_discovery", Routes.PRESETS_DISCOVERY)
    }
}
