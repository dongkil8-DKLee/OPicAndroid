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
import com.opic.android.data.local.db.OPicDatabase
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.domain.LevelCalculator
import com.opic.android.util.CsvUtil
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.net.Uri
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
    val lockedSessions: Set<Int> = emptySet(),

    // 통합 Q&A CSV 내보내기/가져오기
    val csvContent: String? = null,
    val csvExporting: Boolean = false,
    val importingCsv: Boolean = false,
    val importResult: String? = null,

    // DB 백업/복원
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val dbBackupBytes: ByteArray? = null
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
    private val vocabularyDao: VocabularyDao,
    private val database: OPicDatabase
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

    // ===== 통합 Q&A CSV 내보내기/가져오기 =====

    fun prepareCsvExport() {
        if (_uiState.value.csvExporting) return
        _uiState.update { it.copy(csvExporting = true) }
        viewModelScope.launch {
            try {
                val rows = questionDao.getAllQuestionsWithProgressFull(USER_ID)
                val sb = StringBuilder()
                sb.append("question_id,title,set,type,question_text,answer_script,user_script,ai_answer,study_count,is_favorite,stt_grade\n")
                for (r in rows) {
                    val analysis = r.analysisResult?.let { SpeechAnalyzer.fromJson(it) }
                    sb.append("${r.questionId},")
                    sb.append("\"${CsvUtil.escape(r.title)}\",")
                    sb.append("\"${CsvUtil.escape(r.set ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.type ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.questionText ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.answerScript ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.userScript ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.aiAnswer ?: "")}\",")
                    sb.append("${r.studyCount ?: 0},")
                    sb.append("${(r.isFavorite ?: 0) == 1},")
                    sb.append("${analysis?.grade ?: ""}")
                    sb.append("\n")
                }
                _uiState.update { it.copy(csvContent = sb.toString(), csvExporting = false) }
            } catch (e: Exception) {
                Log.e(TAG, "CSV 생성 실패", e)
                _uiState.update { it.copy(csvExporting = false) }
            }
        }
    }

    fun clearCsvContent() = _uiState.update { it.copy(csvContent = null) }

    fun importQaCsvFromUri(uri: Uri) {
        if (_uiState.value.importingCsv) return
        _uiState.update { it.copy(importingCsv = true, importResult = null) }
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: run {
                    _uiState.update { it.copy(importingCsv = false, importResult = "파일 읽기 실패") }
                    return@launch
                }

                val rows = CsvUtil.parse(content)
                if (rows.size < 2) {
                    _uiState.update { it.copy(importingCsv = false, importResult = "유효한 데이터 없음") }
                    return@launch
                }

                val headers = rows[0]
                val idIdx = headers.indexOf("question_id")
                val qtIdx = headers.indexOf("question_text")
                val asIdx = headers.indexOf("answer_script")
                val usIdx = headers.indexOf("user_script")
                val aiIdx = headers.indexOf("ai_answer")

                if (idIdx < 0) {
                    _uiState.update { it.copy(importingCsv = false, importResult = "question_id 컬럼이 없습니다") }
                    return@launch
                }

                var count = 0
                for (row in rows.drop(1)) {
                    val id = row.getOrNull(idIdx)?.toIntOrNull() ?: continue
                    if (qtIdx >= 0) row.getOrNull(qtIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateQuestionText(id, v) }
                    if (asIdx >= 0) row.getOrNull(asIdx)?.let { v -> questionDao.updateAnswerScript(id, v) }
                    if (usIdx >= 0) row.getOrNull(usIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateUserScript(id, v) }
                    if (aiIdx >= 0) row.getOrNull(aiIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateAiAnswer(id, v) }
                    count++
                }
                _uiState.update { it.copy(importingCsv = false, importResult = "${count}개 문제 업데이트 완료") }
            } catch (e: Exception) {
                Log.e(TAG, "Q&A 가져오기 실패", e)
                _uiState.update { it.copy(importingCsv = false, importResult = "가져오기 실패: ${e.message}") }
            }
        }
    }

    fun clearImportResult() = _uiState.update { it.copy(importResult = null) }

    // ===== DB 백업/복원 =====

    fun backupDatabase() {
        if (_uiState.value.isBackingUp) return
        _uiState.update { it.copy(isBackingUp = true) }
        viewModelScope.launch {
            try {
                val src = context.getDatabasePath("opic.db")
                val bytes = src.readBytes()
                _uiState.update { it.copy(isBackingUp = false, dbBackupBytes = bytes) }
            } catch (e: Exception) {
                Log.e(TAG, "DB 백업 실패", e)
                _uiState.update { it.copy(isBackingUp = false, importResult = "백업 실패: ${e.message}") }
            }
        }
    }

    fun clearDbBackupBytes() = _uiState.update { it.copy(dbBackupBytes = null) }

    fun restoreDatabaseFromUri(uri: Uri) {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, importResult = null) }
        viewModelScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: run {
                        _uiState.update { it.copy(isRestoring = false, importResult = "파일 읽기 실패") }
                        return@launch
                    }
                database.close()
                context.getDatabasePath("opic.db").writeBytes(bytes)
                _uiState.update { it.copy(isRestoring = false, importResult = "복원 완료 — 앱을 재시작해주세요") }
            } catch (e: Exception) {
                Log.e(TAG, "DB 복원 실패", e)
                _uiState.update { it.copy(isRestoring = false, importResult = "복원 실패: ${e.message}") }
            }
        }
    }
}
