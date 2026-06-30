package com.rapidraw.cloud

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class CloudSyncManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockPrefsEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockPrefs.edit()).thenReturn(mockPrefsEditor)
        `when`(mockPrefsEditor.putString(anyString(), anyString())).thenReturn(mockPrefsEditor)
        `when`(mockPrefsEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockPrefsEditor)
        `when`(mockPrefsEditor.putLong(anyString(), anyLong())).thenReturn(mockPrefsEditor)
        `when`(mockPrefsEditor.remove(anyString())).thenReturn(mockPrefsEditor)
        `when`(mockPrefsEditor.apply()).then {}

        `when`(mockContext.getSharedPreferences(eq("cloud_sync"), eq(Context.MODE_PRIVATE)))
            .thenReturn(mockPrefs)

        tempDir = File(System.getProperty("java.io.tmpdir"), "cloud_sync_test_${System.nanoTime()}")
        tempDir.mkdirs()
        `when`(mockContext.filesDir).thenReturn(tempDir)
    }

    @Test
    fun `CloudSyncManager can be created`() {
        val manager = CloudSyncManager(mockContext)
        assertNotNull("CloudSyncManager should be created successfully", manager)
    }

    @Test
    fun `syncStatus returns initial NOT_CONFIGURED value`() {
        val manager = CloudSyncManager(mockContext)
        assertEquals(
            "syncStatus should be NOT_CONFIGURED initially",
            CloudSyncManager.SyncStatus.NOT_CONFIGURED,
            manager.syncStatus.value
        )
    }

    @Test
    fun `userId is null initially`() {
        `when`(mockPrefs.getString(eq("user_id"), isNull())).thenReturn(null)
        `when`(mockPrefs.getString(eq("auth_token"), isNull())).thenReturn(null)
        `when`(mockPrefs.getBoolean(eq("sync_enabled"), eq(false))).thenReturn(false)

        val manager = CloudSyncManager(mockContext)
        assertNull("userId should be null initially", manager.userId)
    }

    @Test
    fun `isLoggedIn returns false initially`() {
        `when`(mockPrefs.getString(eq("auth_token"), isNull())).thenReturn(null)
        `when`(mockPrefs.getBoolean(eq("sync_enabled"), eq(false))).thenReturn(false)

        val manager = CloudSyncManager(mockContext)
        assertFalse("isLoggedIn should be false initially", manager.isLoggedIn)
    }

    @Test
    fun `downloadPresets handles empty list`() = runTest {
        // Token is needed for login check
        `when`(mockPrefs.getString(eq("auth_token"), isNull()))
            .thenReturn("local_550e8400-e29b-41d4-a716-446655440000")
        `when`(mockPrefs.getString(eq("user_id"), isNull()))
            .thenReturn("550e8400-e29b-41d4-a716-446655440000")
        `when`(mockPrefs.getBoolean(eq("sync_enabled"), eq(false))).thenReturn(true)

        val manager = CloudSyncManager(mockContext)
        val result = manager.downloadPresets()

        assertTrue("downloadPresets should succeed", result.isSuccess)
        val presets = result.getOrNull()
        assertNotNull("Presets list should not be null", presets)
        assertTrue("Presets list should be empty initially", presets!!.isEmpty())
    }

    @Test
    fun `pendingQueue is empty initially`() {
        `when`(mockPrefs.getString(eq("auth_token"), isNull())).thenReturn(null)
        `when`(mockPrefs.getBoolean(eq("sync_enabled"), eq(false))).thenReturn(false)

        val manager = CloudSyncManager(mockContext)
        assertTrue("pendingQueue should be empty initially", manager.pendingQueue.value.isEmpty())
    }

    @Test
    fun `getSyncStats returns initial values`() {
        `when`(mockPrefs.getString(eq("auth_token"), isNull())).thenReturn(null)
        `when`(mockPrefs.getBoolean(eq("sync_enabled"), eq(false))).thenReturn(false)

        val manager = CloudSyncManager(mockContext)
        val stats = manager.getSyncStats()

        assertEquals("pendingUploads should be 0 initially", 0, stats["pendingUploads"])
        assertEquals("pendingDeletes should be 0 initially", 0, stats["pendingDeletes"])
        assertEquals("totalPending should be 0 initially", 0, stats["totalPending"])
    }
}