package com.opic.android.ui.common.filter

/**
 * Study/Practice 공유 필터 상태 — SSOT (StudyFilterController가 관리).
 * StudyUiState에서 필터 관련 필드만 분리.
 */
data class StudyFilterState(
    // ── 필터 옵션 (동적, 상호 연동) ──────────────────────────────
    val sets: List<String> = emptyList(),
    val types: List<String> = emptyList(),

    // ── 필터 결과 ─────────────────────────────────────────────────
    /** 현재 필터 조건 기준 정렬된 제목 목록 */
    val titles: List<String> = emptyList(),
    /** Practice 탐색용 (questionId, title) 쌍 목록 */
    val practiceQuestionList: List<Pair<Int, String>> = emptyList(),
    /** Practice 상단 필터 요약 문자열 */
    val practiceFilterSummary: String = "",

    // ── 선택값 ────────────────────────────────────────────────────
    val selectedSet: String = "전체",
    val selectedType: String = "전체",
    val selectedSort: String = "주제 순서",
    val selectedStudyFilter: String = "전체",

    // ── 등급 필터 (Report 화면에서 진입 시) ───────────────────────
    val gradeFilter: String? = null,

    // ── 초기화 완료 플래그 ────────────────────────────────────────
    /** allSummaries 로드 + 첫 updateTitleList 완료 후 true */
    val isReady: Boolean = false
)
