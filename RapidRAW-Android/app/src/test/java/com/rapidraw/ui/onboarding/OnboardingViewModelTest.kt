package com.rapidraw.ui.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rapidraw.core.SafePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OnboardingViewModel 单元测试。
 *
 * 覆盖：
 * 1. 首次启动 → isCompleted = false
 * 2. 调用 completeOnboarding → isCompleted = true 且 prefs 持久化
 * 3. 新建实例（模拟进程重建）后仍能读取到 isCompleted = true
 * 4. OnboardingState 与 ViewModel 共享同一事实源
 *
 * @since 2026.07
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        OnboardingState.clear(app)
    }

    @After
    fun tearDown() {
        OnboardingState.clear(app)
    }

    @Test
    fun `initial state - not completed on fresh install`() = runTest {
        val vm = OnboardingViewModel(app)
        assertFalse("Fresh install should be not completed", vm.isCompleted.value)
    }

    @Test
    fun `completeOnboarding - persists to SharedPreferences and updates state`() = runTest {
        val vm = OnboardingViewModel(app)
        assertFalse(vm.isCompleted.value)

        vm.completeOnboarding()

        assertTrue("ViewModel state should flip to completed", vm.isCompleted.value)
        // 通过 OnboardingState 单例再次验证持久化
        assertTrue("Persisted state via OnboardingState", OnboardingState.isCompleted(app))
    }

    @Test
    fun `new ViewModel after completeOnboarding - reads completed from prefs`() = runTest {
        val vm1 = OnboardingViewModel(app)
        vm1.completeOnboarding()

        // 模拟进程重建
        val vm2 = OnboardingViewModel(app)
        assertTrue("New instance should see persisted completion", vm2.isCompleted.value)
    }

    @Test
    fun `setCurrentPage - updates current page state`() = runTest {
        val vm = OnboardingViewModel(app)
        assertEquals(0, vm.currentPage.value)
        vm.setCurrentPage(2)
        assertEquals(2, vm.currentPage.value)
        vm.setCurrentPage(4)
        assertEquals(4, vm.currentPage.value)
    }

    @Test
    fun `markPermissionRequested - updates state flag`() = runTest {
        val vm = OnboardingViewModel(app)
        assertFalse(vm.isPermissionRequested.value)
        vm.markPermissionRequested()
        assertTrue(vm.isPermissionRequested.value)
    }

    @Test
    fun `corrupted onboarding prefs - ViewModel still works`() = runTest {
        // 写入损坏的 XML 模拟存储损坏
        val prefsDir = runCatching {
            app.getDir("shared_prefs", android.content.Context.MODE_PRIVATE)
        }.getOrNull() ?: java.io.File(app.filesDir.parentFile, "shared_prefs").also { it.mkdirs() }
        java.io.File(prefsDir, "${OnboardingState.PREFS_NAME}.xml").apply {
            writeText("### corrupted ###")
        }
        // SafePreferences 应能恢复 + ViewModel 应当读到默认 false
        val vm = OnboardingViewModel(app)
        assertFalse("Corrupted prefs should yield default false", vm.isCompleted.value)
    }

    @Test
    fun `OnboardingState and ViewModel are consistent - same source of truth`() = runTest {
        val vm = OnboardingViewModel(app)
        // 反向：先通过 OnboardingState 标记，再读 ViewModel
        OnboardingState.markCompleted(app)
        val vm2 = OnboardingViewModel(app)
        assertTrue(vm2.isCompleted.value)
        assertTrue(OnboardingState.isCompleted(app))
    }
}
