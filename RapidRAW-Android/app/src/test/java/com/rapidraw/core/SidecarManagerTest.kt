package com.rapidraw.core

import androidx.test.core.app.ApplicationProvider
import com.rapidraw.data.model.Adjustments
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SidecarManagerTest {

    private fun createManager(): SidecarManager {
        return SidecarManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun saveAndLoadSidecar_fileScheme() {
        val manager = createManager()
        val tempDir = ApplicationProvider.getApplicationContext().cacheDir
        val imageFile = File(tempDir, "test.dng")
        imageFile.createNewFile()

        val imagePath = imageFile.absolutePath
        val adj = Adjustments(exposure = 1.2f, contrast = 25f)
        val saved = manager.saveSidecar(imagePath, adj, filmId = "kodak-portra-400")
        assertTrue(saved)
        assertTrue(manager.hasSidecar(imagePath))

        val loaded = manager.loadSidecar(imagePath)
        assertNotNull(loaded)
        assertEquals(1.2f, loaded?.adjustments?.exposure ?: 0f, 0.001f)
        assertEquals(25f, loaded?.adjustments?.contrast ?: 0f, 0.001f)
        assertEquals("kodak-portra-400", loaded?.filmId)
        assertEquals(1, loaded?.version)

        // 清理
        manager.deleteSidecar(imagePath)
        imageFile.delete()
    }

    @Test
    fun saveAndLoadSidecar_contentScheme() {
        val manager = createManager()
        val uri = "content://media/external/images/media/123"

        val adj = Adjustments(exposure = -0.5f, saturation = 15f)
        val saved = manager.saveSidecar(uri, adj)
        assertTrue(saved)
        assertTrue(manager.hasSidecar(uri))

        val loaded = manager.loadSidecar(uri)
        assertNotNull(loaded)
        assertEquals(-0.5f, loaded?.adjustments?.exposure ?: 0f, 0.001f)
        assertEquals(15f, loaded?.adjustments?.saturation ?: 0f, 0.001f)

        manager.deleteSidecar(uri)
        assertFalse(manager.hasSidecar(uri))
    }

    @Test
    fun loadSidecar_returnsNullWhenMissing() {
        val manager = createManager()
        val result = manager.loadSidecar("file:///nonexistent/image.dng")
        assertNull(result)
    }

    @Test
    fun hasSidecar_returnsFalseForUnsupportedScheme() {
        val manager = createManager()
        assertFalse(manager.hasSidecar("http://example.com/image.jpg"))
    }

    @Test
    fun deleteSidecar_returnsFalseWhenMissing() {
        val manager = createManager()
        val result = manager.deleteSidecar("file:///nonexistent/image.dng")
        assertFalse(result)
    }
}
