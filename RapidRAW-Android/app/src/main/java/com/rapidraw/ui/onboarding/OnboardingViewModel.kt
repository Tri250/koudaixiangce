package com.rapidraw.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.SafePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the onboarding flow.
 *
 * 管理引导完成状态，使用 SafePreferences 持久化：
 * - 防 SharedPreferences XML 损坏导致启动崩溃
 * - 与项目其它持久化层保持一致
 *
 * 状态读写通过 [OnboardingState] 单例共享，使 [com.rapidraw.ui.navigation.rememberStartDestination]
 * 与本 ViewModel 读到同一份事实，避免冷启动时闪现引导页后又跳转的竞态。
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SafePreferences.get(application, OnboardingState.PREFS_NAME)

    private val _isCompleted = MutableStateFlow(
        SafePreferences.getBoolean(prefs, OnboardingState.KEY_COMPLETED, false)
    )
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isPermissionRequested = MutableStateFlow(false)
    val isPermissionRequested: StateFlow<Boolean> = _isPermissionRequested.asStateFlow()

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    fun markPermissionRequested() {
        _isPermissionRequested.value = true
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            SafePreferences.putBoolean(prefs, OnboardingState.KEY_COMPLETED, true)
            OnboardingState.markCompleted(getApplication())
            _isCompleted.value = true
        }
    }
}
