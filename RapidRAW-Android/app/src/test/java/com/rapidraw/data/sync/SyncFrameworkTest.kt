package com.rapidraw.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncFrameworkTest {

    @Test
    fun `SyncManager can be created with no provider`() {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        assertNotNull("SyncManager should be created successfully", manager)
    }

    @Test
    fun `syncProjects is initially empty`() {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        val projects = manager.syncProjects.value
        assertTrue("syncProjects should be empty initially", projects.isEmpty())
    }

    @Test
    fun `isSyncing is false initially`() {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        assertFalse("isSyncing should be false initially", manager.isSyncing.value)
    }

    @Test
    fun `syncAll with null provider returns immediately`() = runTest {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        // Should not throw and should complete immediately
        manager.syncAll()
        // syncProjects should remain empty since no provider is set
        assertTrue("syncProjects should remain empty", manager.syncProjects.value.isEmpty())
        assertFalse("isSyncing should be false after syncAll", manager.isSyncing.value)
    }

    @Test
    fun `syncAll with unauthenticated provider returns immediately`() = runTest {
        val mockProvider = object : SyncProvider {
            override val name = "test-provider"
            override val isAuthenticated = MutableStateFlow(false)
            override val syncState = MutableStateFlow(SyncState.IDLE)
            override suspend fun authenticate(): Boolean = false
            override suspend fun logout() {}
            override suspend fun uploadProject(project: SyncProject): Result<SyncProject> =
                Result.success(project)
            override suspend fun downloadProject(projectId: String): Result<SyncProject> =
                Result.failure(Exception("not found"))
            override suspend fun listRemoteProjects(): Result<List<SyncProject>> =
                Result.success(emptyList())
            override suspend fun deleteRemoteProject(projectId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun resolveConflict(local: SyncProject, remote: SyncProject): SyncProject =
                local
        }

        val manager = SyncManager(provider = mockProvider, sidecarBasePath = "/tmp/test")
        manager.syncAll()
        // Should return immediately because provider is not authenticated
        assertTrue("syncProjects should remain empty", manager.syncProjects.value.isEmpty())
    }

    @Test
    fun `syncProject with null provider returns failure`() = runTest {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        val project = SyncProject(
            id = "test-id",
            imagePath = "/tmp/test.jpg",
            sidecarPath = "/tmp/test.json",
            lastModifiedLocal = System.currentTimeMillis(),
            lastModifiedRemote = null,
            syncState = SyncState.IDLE,
            checksumLocal = "abc123",
            checksumRemote = null,
        )

        val result = manager.syncProject(project)
        assertTrue("syncProject should fail with no provider", result.isFailure)
    }

    @Test
    fun `restoreProject with null provider returns failure`() = runTest {
        val manager = SyncManager(provider = null, sidecarBasePath = "/tmp/test")
        val result = manager.restoreProject("test-id")
        assertTrue("restoreProject should fail with no provider", result.isFailure)
    }
}