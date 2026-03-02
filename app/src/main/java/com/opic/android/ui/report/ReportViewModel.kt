package com.opic.android.ui.report

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.SessionSummary
import com.opic.android.data.local.dao.StudyProgressDao
import com.opic.android.data.local.dao.TestDao
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.domain.LevelCalculator
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ReportUiState(
    val loading: Boolean = true,
    val totalQuestions: Int = 0,
    val studiedQuestions: Int = 0,
    val completionPercent: Float = 0f,
    val favoriteCount: Int = 0,
    val weeklyStudied: Int = 0,
    val weeklyGoal: Int = 20,
    val gradeDistribution: Map<String, Int> = emptyMap(),
    val topicWeakness: List<TopicAccuracy> = emptyList(),
    val recentTests: List<SessionSummary> = emptyList(),

    // Level info (from StartScreen)
    val level: Int = 1,
    val gaugePercent: Int = 0,
    val levelImageDir: String = "",

    // Vocabulary summary
    val vocabTotal: Int = 0,
    val vocabMemorized: Int = 0,
    val vocabFavorites: Int = 0,
    val vocabRecentWeekAdded: Int = 0,

    // Session lock
    val lockedSessions: Set<Int> = emptySet()
)

data class TopicAccuracy(
    val type: String,
    val averageAccuracy: Float,
    val count: Int
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val questionDao: QuestionDao,
    private val studyProgressDao: StudyProgressDao,
    private val testDao: TestDao,
    private val levelCalculator: LevelCalculator,
    private val appPrefs: AppPreferences,
    private val vocabularyDao: VocabularyDao
) : ViewModel() {

    companion object {
        private const val TAG = "ReportViewModel"
        private const val USER_ID = 1
        private const val PREFS_NAME = "opic_session_locks"
        private const val KEY_LOCKED = "locked_sessions"
    }

    private val lockPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    init {
        loadReportData()
    }

    // ===== 잠금 관리 =====

    private fun loadLockedSessions(): Set<Int> {
        val raw = lockPrefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun saveLockedSessions(locked: Set<Int>) {
        lockPrefs.edit().putStringSet(KEY_LOCKED, locked.map { it.toString() }.toSet()).apply()
    }

    fun toggleLock(sessionId: Int) {
        val current = loadLockedSessions()
        val updated = if (sessionId in current) current - sessionId else current + sessionId
        saveLockedSessions(updated)
        _uiState.update { it.copy(lockedSessions = updated) }
    }

    fun deleteSession(sessionId: Int) {
        if (sessionId in _uiState.value.lockedSessions) return
        viewModelScope.launch {
            try {
                val audioPaths = testDao.getAudioPathsForSession(sessionId)
                audioPaths.forEach { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "녹음 파일 삭제 실패: $path", e)
                    }
                }
                testDao.deleteResultsBySession(sessionId)
                testDao.deleteSession(sessionId)
                _uiState.update { st ->
                    st.copy(recentTests = st.recentTests.filter { it.sessionId != sessionId })
                }
            } catch (e: Exception) {
                Log.e(TAG, "세션 삭제 실패: $sessionId", e)
            }
        }
    }

    // ===== 데이터 로드 =====

    private fun loadReportData() {
        viewModelScope.launch {
            try {
                val locked = loadLockedSessions()

                // Level info
                val levelInfo = levelCalculator.calculate()

                // 1. 전체/학습완료/즐겨찾기 수
                val allSummaries = questionDao.getAllQuestionsWithProgress(USER_ID)
                val total = allSummaries.size
                val studied = allSummaries.count { (it.studyCount ?: 0) > 0 }
                val favorites = allSummaries.count { (it.isFavorite ?: 0) == 1 }
                val completionPercent = if (total > 0) studied * 100f / total else 0f

                // 2. 등급 분포 + 주제별 약점
                val allProgress = studyProgressDao.getAllProgressForUserSync(USER_ID)
                val gradeMap = mutableMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0, "F" to 0)
                val topicAccuracyMap = mutableMapOf<String, MutableList<Float>>()

                // questionId → type 매핑
                val questionTypeMap = allSummaries.associate { it.questionId to (it.type ?: "기타") }

                for (p in allProgress) {
                    val analysisJson = p.analysisResult ?: continue
                    val result = SpeechAnalyzer.fromJson(analysisJson) ?: continue
                    gradeMap[result.grade] = (gradeMap[result.grade] ?: 0) + 1

                    val type = questionTypeMap[p.questionId] ?: "기타"
                    topicAccuracyMap.getOrPut(type) { mutableListOf() }.add(result.accuracyPercent)
                }

                val topicWeakness = topicAccuracyMap.map { (type, accuracies) ->
                    TopicAccuracy(
                        type = type,
                        averageAccuracy = accuracies.average().toFloat(),
                        count = accuracies.size
                    )
                }.sortedBy { it.averageAccuracy }

                // 3. 최근 테스트 세션
                val recentTests = testDao.getAllSessionSummaries().take(5)

                // 4. 주간 활동
                val today = LocalDate.now()
                val weekAgo = today.minusDays(7)
                val weeklyStudied = allProgress.count { p ->
                    val dateStr = p.lastModified?.take(10) ?: return@count false
                    try {
                        val date = LocalDate.parse(dateStr)
                        !date.isBefore(weekAgo)
                    } catch (_: Exception) {
                        false
                    }
                }

                // 5. Vocabulary summary
                val allWords = vocabularyDao.getAllWordsSync()
                val vocabTotal = allWords.size
                val vocabMemorized = allWords.count { it.isMemorized }
                val vocabFavorites = allWords.count { it.isFavorite }
                val vocabRecentWeekAdded = allWords.count { word ->
                    val dateStr = word.createdAt?.take(10) ?: return@count false
                    try {
                        val date = LocalDate.parse(dateStr)
                        !date.isBefore(weekAgo)
                    } catch (_: Exception) {
                        false
                    }
                }

                _uiState.update {
                    it.copy(
                        loading = false,
                        totalQuestions = total,
                        studiedQuestions = studied,
                        completionPercent = completionPercent,
                        favoriteCount = favorites,
                        weeklyStudied = weeklyStudied,
                        gradeDistribution = gradeMap,
                        topicWeakness = topicWeakness,
                        recentTests = recentTests,
                        level = levelInfo.level,
                        gaugePercent = levelInfo.gaugePercent,
                        levelImageDir = appPrefs.levelImageDir,
                        vocabTotal = vocabTotal,
                        vocabMemorized = vocabMemorized,
                        vocabFavorites = vocabFavorites,
                        vocabRecentWeekAdded = vocabRecentWeekAdded,
                        lockedSessions = locked
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }
}
