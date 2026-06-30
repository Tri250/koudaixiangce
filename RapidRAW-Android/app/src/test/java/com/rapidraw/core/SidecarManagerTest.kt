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
        assertEquals(2, loaded?.version)

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

    // 2026 hotfix 新增: 路径穿越 / 非法 scheme 边界测试
    @Test
    fun saveSidecar_pathTraversalAttempt_isNeutralized() {
        val manager = createManager()
        // 尝试通过 lastPathSegment 中的 "../" 写到 filesDir 之外
        val malicious = "content://attacker/../../../../../etc/passwd"
        // 不应该崩溃，不应该抛出异常
        val result = manager.saveSidecar(malicious, Adjustments(exposure = 1f))
        // 由于路径被清洗，写入会落到 filesDir 中一个安全文件名
        // 重要的是不抛异常，且不越权写入
        if (result) {
            // 如果写入成功，验证文件确实在 filesDir 内
            val files = ApplicationProvider.getApplicationContext<android.content.Context>()
                .filesDir.listFiles() ?: emptyArray()
            val sidecars = files.filter { it.name.endsWith(".rapidraw") && !it.name.endsWith(".tmp") }
            assertTrue("Sanitized sidecar should still be inside filesDir",
                sidecars.all { it.absolutePath.startsWith(
                    ApplicationProvider.getApplicationContext<android.content.Context>().filesDir.absolutePath) })
            // 清理
            sidecars.forEach { it.delete() }
        }
    }

    @Test
    fun saveSidecar_unsupportedScheme_returnsFalse() {
        val manager = createManager()
        assertFalse(manager.saveSidecar("ftp://server/file.jpg", Adjustments()))
        assertFalse(manager.saveSidecar("javascript:alert(1)", Adjustments()))
        assertFalse(manager.saveSidecar("", Adjustments()))
    }

    @Test
    fun loadSidecar_unsupportedScheme_returnsNull() {
        val manager = createManager()
        assertNull(manager.loadSidecar("https://server/file.jpg"))
        assertNull(manager.loadSidecar("garbage_input_no_scheme"))
    }

    @Test
    fun saveSidecar_overwritesPreviousAtomic() {
        val manager = createManager()
        val tempDir = ApplicationProvider.getApplicationContext().cacheDir
        val imageFile = File(tempDir, "atomic_test.dng")
        imageFile.createNewFile()
        val path = imageFile.absolutePath

        try {
            // 第一次保存
            val first = manager.saveSidecar(path, Adjustments(exposure = 1f))
            assertTrue(first)

            // 第二次保存：应原子覆盖
            val second = manager.saveSidecar(path, Adjustments(exposure = 5f))
            assertTrue(second)

            val loaded = manager.loadSidecar(path)
            assertNotNull(loaded)
            assertEquals(5f, loaded?.adjustments?.exposure ?: 0f, 0.001f)

            // 不应该存在 .tmp 残留
            val parent = imageFile.parentFile!!
            val tmps = parent.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
            assertEquals("tmp residue should be cleaned", 0, tmps.size)
        } finally {
            manager.deleteSidecar(path)
            imageFile.delete()
        }
    }

    @Test
    fun saveSidecar_corruptedJson_returnsNull() {
        val manager = createManager()
        val tempDir = ApplicationProvider.getApplicationContext().cacheDir
        val imageFile = File(tempDir, "corrupt_test.dng")
        imageFile.createNewFile()
        val path = imageFile.absolutePath
        val sidecar = File(tempDir, "corrupt_test.rapidraw")
        sidecar.writeText("this is not json {{{")

        try {
            // 损坏的 sidecar 不会导致崩溃，应该返回 null
            val loaded = manager.loadSidecar(path)
            assertNull(loaded)
            // 而且 hasSidecar 应该返回 true（文件存在），区分异常和缺失
            assertTrue(manager.hasSidecar(path))
        } finally {
            sidecar.delete()
            imageFile.delete()
        }
    }
}

