package com.opic.android.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.data.local.dao.TestDao
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.domain.LevelCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartUiState(
    val level: Int = 1,
    val gaugePercent: Int = 0,
    val hasTestSession: Boolean = false,
    val isLoading: Boolean = true,
    val levelImageDir: String = ""
)

/**
 * Python _update_start_page_level() 대응.
 * StartScreen 진입 시 레벨/게이지 재계산 + Review 버튼 활성 여부.
 */
@HiltViewModel
class StartViewModel @Inject constructor(
    private val levelCalculator: LevelCalculator,
    private val testDao: TestDao,
    private val appPrefs: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val levelInfo = levelCalculator.calculate()
            val lastSession = testDao.getLastSession()
            _uiState.value = StartUiState(
                level = levelInfo.level,
                gaugePercent = levelInfo.gaugePercent,
                hasTestSession = lastSession != null,
                isLoading = false,
                levelImageDir = appPrefs.levelImageDir
            )
        }
    }
}
