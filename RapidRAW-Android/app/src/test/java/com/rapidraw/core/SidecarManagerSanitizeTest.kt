package com.rapidraw.core

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SidecarManagerSanitizeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = SidecarManager(context)

    @Test
    fun `resolveSidecarFile rejects path traversal in content URI`() {
        val result = manager.javaClass.getDeclaredMethod("resolveSidecarFile", String::class.java)
            .apply { isAccessible = true }
            .invoke(manager, "content://authority/..%2F..%2Fsecret") as File?
        assertNull(result)
    }

    @Test
    fun `resolveSidecarFile sanitizes file scheme path`() {
        val dir = context.cacheDir
        val image = File(dir, "test.raw")
        val result = manager.javaClass.getDeclaredMethod("resolveSidecarFile", String::class.java)
            .apply { isAccessible = true }
            .invoke(manager, Uri.fromFile(image).toString()) as File?
        assertTrue(result!!.name.endsWith(".rapidraw"))
        assertFalse(result.name.contains("/"))
    }

    @Test
    fun `saveSidecar respects size limit`() {
        val huge = "x".repeat(3 * 1024 * 1024)
        val adjustments = com.rapidraw.data.model.Adjustments()
        val success = manager.saveSidecar(
            Uri.fromFile(File(context.cacheDir, "big.raw")).toString(),
            adjustments,
            filmId = huge,
        )
        assertFalse(success)
    }
}
