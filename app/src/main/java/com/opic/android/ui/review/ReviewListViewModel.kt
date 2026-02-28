package com.opic.android.ui.review

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.data.local.dao.SessionSummary
import com.opic.android.data.local.dao.TestDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReviewListUiState(
    val loading: Boolean = true,
    val sessions: List<SessionSummary> = emptyList(),
    val lockedSessions: Set<Int> = emptySet()
)

@HiltViewModel
class ReviewListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val testDao: TestDao
) : ViewModel() {

    companion object {
        private const val TAG = "ReviewListViewModel"
        private const val PREFS_NAME = "opic_session_locks"
        private const val KEY_LOCKED = "locked_sessions"
    }

    private val lockPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ReviewListUiState())
    val uiState: StateFlow<ReviewListUiState> = _uiState

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            try {
                val sessions = testDao.getAllSessionSummaries()
                val locked = loadLockedSessions()
                Log.d(TAG, "세션 목록 로드: ${sessions.size}개, 잠금: ${locked.size}개")
                _uiState.update {
                    it.copy(loading = false, sessions = sessions, lockedSessions = locked)
                }
            } catch (e: Exception) {
                Log.e(TAG, "세션 목록 로드 실패", e)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun toggleLock(sessionId: Int) {
        val current = _uiState.value.lockedSessions.toMutableSet()
        if (sessionId in current) {
            current.remove(sessionId)
        } else {
            current.add(sessionId)
        }
        saveLockedSessions(current)
        _uiState.update { it.copy(lockedSessions = current) }
    }

    fun deleteSession(sessionId: Int) {
        // 잠금된 세션은 삭제 불가
        if (sessionId in _uiState.value.lockedSessions) return

        viewModelScope.launch {
            try {
                // 1. 녹음 파일 삭제
                val audioPaths = testDao.getAudioPathsForSession(sessionId)
                audioPaths.forEach { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "녹음 파일 삭제 실패: $path", e)
                    }
                }

                // 2. DB에서 결과 삭제 → 세션 삭제
                testDao.deleteResultsBySession(sessionId)
                testDao.deleteSession(sessionId)

                // 3. UI 업데이트
                _uiState.update { state ->
                    state.copy(sessions = state.sessions.filter { it.sessionId != sessionId })
                }

                Log.d(TAG, "세션 삭제 완료: $sessionId (녹음 ${audioPaths.size}개)")
            } catch (e: Exception) {
                Log.e(TAG, "세션 삭제 실패: $sessionId", e)
            }
        }
    }

    private fun loadLockedSessions(): Set<Int> {
        val raw = lockPrefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun saveLockedSessions(locked: Set<Int>) {
        lockPrefs.edit().putStringSet(KEY_LOCKED, locked.map { it.toString() }.toSet()).apply()
    }
}
