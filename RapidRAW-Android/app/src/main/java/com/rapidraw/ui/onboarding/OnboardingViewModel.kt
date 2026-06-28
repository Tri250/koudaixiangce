package com.rapidraw.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the onboarding flow.
 *
 * Manages onboarding state and tracks completion using SharedPreferences.
 * DataStore is not used because the project currently has no DataStore dependency,
 * and SharedPreferences is sufficient for a simple boolean flag.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("rapidraw_onboarding", 0)

    private val _isCompleted = MutableStateFlow(prefs.getBoolean(KEY_COMPLETED, false))
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
            prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
            _isCompleted.value = true
        }
    }

    companion object {
        private const val KEY_COMPLETED = "onboarding_completed"
    }
}
