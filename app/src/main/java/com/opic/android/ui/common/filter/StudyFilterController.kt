package com.opic.android.ui.common.filter

import android.util.Log
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.QuestionSummary
import com.opic.android.data.local.dao.StudyProgressDao
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.data.prefs.StudyPreferences
import com.opic.android.util.SpeechAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 필터 상태 SSOT — Study/Practice 공유.
 *
 * 책임:
 *  - allSummaries 캐시 소유 (QuestionDao)
 *  - analysisGrades 캐시 소유 (StudyProgressDao)
 *  - StudyPreferences 읽기/쓰기 (영속화)
 *  - AppPreferences.practiceQuestionList/FilterSummary 갱신
 *  - StateFlow<StudyFilterState> 공개 → Study/Practice ViewModel이 구독
 *
 * @Singleton → Application 생명주기 내 단 1개 인스턴스.
 * 자체 CoroutineScope(SupervisorJob) 사용 — ViewModel 생명주기와 무관.
 */
@Singleton
class StudyFilterController @Inject constructor(
    private val questionDao: QuestionDao,
    private val studyProgressDao: StudyProgressDao,
    private val prefs: StudyPreferences,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "StudyFilterController"
        private const val USER_ID = 1
    }

    // Application 생명주기와 동일한 scope (Singleton이므로 별도 생성)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(StudyFilterState())
    val state: StateFlow<StudyFilterState> = _state.asStateFlow()

    /** 전체 문제 요약 캐시 (필터 체인 반복 사용) */
    private var allSummaries: List<QuestionSummary> = emptyList()

    /** questionId → 분석 등급 캐시 (저득점/최근오답 필터용) */
    private var analysisGrades: Map<Int, String> = emptyMap()

    init {
        loadInitialData()
    }

    // ═══════════════════════════════════════════════════════════════
    // 초기화
    // ═══════════════════════════════════════════════════════════════

    private fun loadInitialData() {
        scope.launch {
            try {
                // ① DB 로드
                allSummaries = questionDao.getAllQuestionsWithProgress(USER_ID)
                refreshAnalysisGrades()

                // ② 옵션 목록 추출
                val sets  = allSummaries.mapNotNull { it.set  }.distinct().sorted()
                val types = allSummaries.mapNotNull { it.type }.distinct().sorted()

                // ③ prefs 복원 (유효성 검사)
                val savedSet    = prefs.set
                val savedType   = prefs.type
                val savedSort   = prefs.sort
                val savedFilter = prefs.studyFilter

                val effectiveSet  = if (savedSet  in sets  || savedSet  == "전체") savedSet  else "전체"
                val effectiveType = if (savedType in types || savedType == "전체") savedType else "전체"

                _state.update {
                    it.copy(
                        sets                = sets,
                        types               = types,
                        selectedSet         = effectiveSet,
                        selectedType        = effectiveType,
                        selectedSort        = savedSort,
                        selectedStudyFilter = savedFilter
                    )
                }

                // ④ 목록 계산 → isReady = true
                updateTitleList()
                _state.update { it.copy(isReady = true) }

            } catch (e: Exception) {
                Log.e(TAG, "초기 데이터 로드 실패", e)
                _state.update { it.copy(isReady = true) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API — 필터 변경 (Study/Practice 양쪽에서 호출 가능)
    // ═══════════════════════════════════════════════════════════════

    fun onSetChanged(set: String) {
        _state.update { it.copy(selectedSet = set) }
        prefs.set = set
        scope.launch {
            // set 변경 → 유효한 type 옵션 재계산
            val filteredTypes = if (set == "전체") {
                allSummaries.mapNotNull { it.type }.distinct().sorted()
            } else {
                allSummaries.filter { it.set == set }.mapNotNull { it.type }.distinct().sorted()
            }
            _state.update { it.copy(types = filteredTypes) }
            updateTitleList()
        }
    }

    fun onTypeChanged(type: String) {
        _state.update { it.copy(selectedType = type) }
        prefs.type = type
        scope.launch {
            // type 변경 → 유효한 set 옵션 재계산
            val filteredSets = if (type == "전체") {
                allSummaries.mapNotNull { it.set }.distinct().sorted()
            } else {
                allSummaries.filter { it.type == type }.mapNotNull { it.set }.distinct().sorted()
            }
            _state.update { it.copy(sets = filteredSets) }
            updateTitleList()
        }
    }

    fun onSortChanged(sort: String) {
        _state.update { it.copy(selectedSort = sort) }
        prefs.sort = sort
        scope.launch { updateTitleList() }
    }

    fun onStudyFilterChanged(filter: String) {
        _state.update { it.copy(selectedStudyFilter = filter) }
        prefs.studyFilter = filter
        scope.launch { updateTitleList() }
    }

    /**
     * Review → Study 바로가기: type + set 동시 적용.
     * studyFilter는 prefs 유지, UI만 "전체"로 임시 전환 (대상 문제 즉시 표시 목적).
     */
    fun initFilters(type: String, set: String?) {
        _state.update { it.copy(selectedType = type, selectedStudyFilter = "전체") }
        prefs.type = type
        scope.launch {
            val filteredSets = if (type == "전체") {
                allSummaries.mapNotNull { it.set }.distinct().sorted()
            } else {
                allSummaries.filter { it.type == type }.mapNotNull { it.set }.distinct().sorted()
            }
            if (!set.isNullOrBlank() && set in filteredSets) {
                _state.update { it.copy(sets = filteredSets, selectedSet = set) }
                prefs.set = set
            } else {
                _state.update { it.copy(sets = filteredSets, selectedSet = "전체") }
            }
            updateTitleList()
        }
    }

    /** Report 등급 분포 필터 — gradeFilter 설정 후 목록 재계산 */
    fun setGradeFilter(grade: String?) {
        _state.update { it.copy(gradeFilter = grade) }
        scope.launch { updateTitleList() }
    }

    // ═══════════════════════════════════════════════════════════════
    // 캐시 갱신 (ViewModel에서 DB 변경 후 호출)
    // ═══════════════════════════════════════════════════════════════

    /**
     * study_count / is_favorite 변경 후 단건 캐시 갱신.
     * suspend — 호출자(ViewModel)의 viewModelScope 내에서 실행.
     */
    suspend fun refreshSummaryCache(questionId: Int) {
        val updated = questionDao.getQuestionSummaryById(USER_ID, questionId)
        if (updated != null) {
            allSummaries = allSummaries.map { if (it.questionId == questionId) updated else it }
        }
        updateTitleList()
    }

    /**
     * 발화 분석 저장 후 등급 캐시 + 목록 재계산.
     * suspend — 호출자(ViewModel)의 viewModelScope 내에서 실행.
     */
    suspend fun refreshAnalysisGradesAndUpdate() {
        refreshAnalysisGrades()
        updateTitleList()
    }

    // ═══════════════════════════════════════════════════════════════
    // 내부 로직
    // ═══════════════════════════════════════════════════════════════

    /**
     * 현재 _state 필터 조건으로 allSummaries 필터 + 정렬 → _state 갱신.
     * AppPreferences도 동시 갱신 (Practice 호환성).
     * 주의: IO thread 또는 scope.launch 내에서만 호출.
     */
    private fun updateTitleList() {
        val s = _state.value
        var filtered = allSummaries.asSequence()

        if (s.selectedSet != "전체")  filtered = filtered.filter { it.set  == s.selectedSet  }
        if (s.selectedType != "전체") filtered = filtered.filter { it.type == s.selectedType }

        val gradeFilter = s.gradeFilter
        if (gradeFilter != null) {
            val ids = analysisGrades.filter { it.value == gradeFilter }.keys
            filtered = filtered.filter { it.questionId in ids }
        }

        when (s.selectedStudyFilter) {
            "전체" -> {}
            "📌"  -> filtered = filtered.filter { (it.isFavorite ?: 0) == 1 }
            "0"   -> filtered = filtered.filter { (it.studyCount  ?: 0) == 0 }
            "저득점" -> {
                val ids = analysisGrades.filter { it.value == "D" || it.value == "F" }.keys
                filtered = filtered.filter { it.questionId in ids }
            }
            "최근오답" -> {
                val ids = analysisGrades.filter { it.value == "D" || it.value == "F" }.keys
                val weekAgo = try { java.time.LocalDate.now().minusDays(7) } catch (_: Exception) { null }
                filtered = filtered.filter { summary ->
                    summary.questionId in ids && weekAgo != null && run {
                        val dateStr = summary.lastModified?.take(10) ?: return@run false
                        try { !java.time.LocalDate.parse(dateStr).isBefore(weekAgo) } catch (_: Exception) { false }
                    }
                }
            }
            else -> {
                val count = s.selectedStudyFilter.toIntOrNull()
                if (count != null) filtered = filtered.filter { (it.studyCount ?: 0) == count }
            }
        }

        val sorted = when (s.selectedSort) {
            "오래된 순" -> filtered.sortedBy { it.lastModified ?: "1970-01-01" }
            else        -> filtered.sortedBy { it.title }
        }

        val titles = sorted.map { it.title }.toList()
        val qList  = sorted.map { Pair(it.questionId, it.title) }.toList()

        val summary = buildString {
            if (s.selectedSet         != "전체")    append("주제: ${s.selectedSet}  ")
            if (s.selectedType        != "전체")    append("유형: ${s.selectedType}  ")
            if (s.selectedStudyFilter != "전체")    append("학습: ${s.selectedStudyFilter}  ")
            if (s.selectedSort        != "주제 순서") append("정렬: ${s.selectedSort}")
        }.trim()

        _state.update {
            it.copy(
                titles                = titles,
                practiceQuestionList  = qList,
                practiceFilterSummary = summary
            )
        }

        // AppPreferences 갱신 (Practice ViewModel 구독 전환 이전 호환성)
        appPreferences.practiceQuestionList  = qList
        appPreferences.practiceFilterSummary = summary
    }

    private suspend fun refreshAnalysisGrades() {
        val allProgress = studyProgressDao.getAllProgressForUserSync(USER_ID)
        analysisGrades = allProgress.mapNotNull { p ->
            val qId   = p.questionId ?: return@mapNotNull null
            val grade = p.analysisResult?.let { SpeechAnalyzer.fromJson(it)?.grade }
                ?: return@mapNotNull null
            qId to grade
        }.toMap()
    }
}
