package com.opic.android.ui.survey

import androidx.lifecycle.ViewModel
import com.opic.android.data.prefs.SurveyPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SurveyUiState(
    val currentPart: Int = 0, // 0-based (Part 1 = 0)
    // Part 1
    val part1Main: Int = -1,  // 0~3
    val part1Sub: Int = -1,
    // Part 2
    val part2Main: Int = -1,  // 0=예, 1=아니요
    val part2Sub: Int = -1,
    // Part 3
    val part3Selection: Int = -1, // 0~4
    // Part 4 checkboxes
    val part4Selections: Set<String> = DEFAULT_SELECTIONS.toSet()
)

/** Python DEFAULT_SELECTIONS 12개 항목 */
val DEFAULT_SELECTIONS = listOf(
    "영화보기", "공원가기", "걷기", "운동을 전혀 하지 않음",
    "술집/바에 가기", "TV보기", "쇼핑하기", "음악 감상하기",
    "신문읽기", "국내여행", "해외여행", "집에서 보내는 휴가"
)

// ========== Part 옵션 데이터 ==========

val PART1_MAIN_OPTIONS = listOf(
    "사업/회사", "재택근무/재택사업", "교사/교육자", "일 경험 없음"
)

val PART1_SUB_OPTIONS = mapOf(
    0 to listOf("예", "아니요"),                    // 사업/회사
    1 to listOf("예", "아니요"),                    // 재택근무
    2 to listOf("대학 이상", "초등/중/고등학교", "평생교육") // 교사
    // 3(일 경험 없음) → 하위 질문 없음
)

val PART1_SUB_QUESTIONS = mapOf(
    0 to "현재 귀하는 직업이 있습니까?",
    1 to "현재 귀하는 직업이 있습니까?",
    2 to "현재 귀하는 어디에서 학생을 가르치십니까?"
)

val PART2_SUB_YES = listOf(
    "학위 과정 수업", "전문 기술 향상을 위한 평생 학습", "어학수업"
)

val PART2_SUB_NO = listOf(
    "학위 과정 수업", "전문 기술 향상을 위한 평생 학습", "어학수업", "수강 후 5년 이상 지남"
)

val PART3_OPTIONS = listOf(
    "개인주택이나 아파트에 홀로 거주",
    "친구나 룸메이트와 함께 주택이나 아파트에 거주",
    "가족(배우자/자녀/기타 가족 일원)과 함께 주택이나 아파트에 거주",
    "학교 기숙사",
    "군대 막사"
)

// Part 4 그룹별 옵션
val PART4_LEISURE = listOf(
    "영화보기", "클럽/나이트클럽가기", "공연보기(연극,뮤지컬)", "콘서트 보기",
    "박물관가기", "공원가기", "미술관가기", "캠핑하기",
    "해변가기", "스포츠 관람", "주거개선", "술집/바에 가기",
    "카페/커피전문점가기", "게임하기(비디오, 카드, 보드 등)", "당구치기", "체스하기",
    "SNS에 글올리기", "친구들과 문자대화하기", "시험대비 과정 수강하기", "TV보기",
    "리얼리티쇼 시청하기", "뉴스를 보거나듣기", "요리 관련 프로그램 시청하기", "쇼핑하기",
    "차로 드라이브하기", "스파/마사지샵가기", "구직활동하기", "자원봉사하기"
)

val PART4_HOBBIES = listOf(
    "아이에게 책 읽어주기", "음악 감상하기", "악기 연주하기", "글쓰기(편지, 단문, 시 등)",
    "그림 그리기", "요리하기", "애완동물 기르기", "독서",
    "춤추기", "주식 투자하기", "신문읽기", "여행관련 잡지나 블로그읽기",
    "사진 촬영하기", "노래 부르기(혼자/합창)"
)

val PART4_SPORTS = listOf(
    "농구", "야구/소프트볼", "축구", "미식축구",
    "아이스하키", "럭비", "자전거", "조깅",
    "걷기", "수영", "하이킹/트레킹", "헬스(운동 기구)",
    "요가", "골프", "테니스", "운동을 전혀 하지 않음"
)

val PART4_TRAVEL = listOf(
    "국내출장", "해외출장", "집에서 보내는 휴가", "국내여행", "해외여행"
)

/**
 * SurveyPage 4파트 상태 관리.
 * Part 4 선택 항목을 SurveyPreferences에 저장.
 */
@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val surveyPrefs: SurveyPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState

    init {
        // 저장된 모든 파트 복원
        val savedTopics = surveyPrefs.selectedTopics
        _uiState.update {
            it.copy(
                part1Main      = surveyPrefs.part1Main,
                part1Sub       = surveyPrefs.part1Sub,
                part2Main      = surveyPrefs.part2Main,
                part2Sub       = surveyPrefs.part2Sub,
                part3Selection = surveyPrefs.part3Selection,
                part4Selections = if (savedTopics.isNotEmpty()) savedTopics else DEFAULT_SELECTIONS.toSet()
            )
        }
    }

    fun setPart1Main(index: Int) {
        surveyPrefs.part1Main = index; surveyPrefs.part1Sub = -1
        _uiState.update { it.copy(part1Main = index, part1Sub = -1) }
    }
    fun setPart1Sub(index: Int) {
        surveyPrefs.part1Sub = index
        _uiState.update { it.copy(part1Sub = index) }
    }
    fun setPart2Main(index: Int) {
        surveyPrefs.part2Main = index; surveyPrefs.part2Sub = -1
        _uiState.update { it.copy(part2Main = index, part2Sub = -1) }
    }
    fun setPart2Sub(index: Int) {
        surveyPrefs.part2Sub = index
        _uiState.update { it.copy(part2Sub = index) }
    }
    fun setPart3(index: Int) {
        surveyPrefs.part3Selection = index
        _uiState.update { it.copy(part3Selection = index) }
    }

    fun togglePart4(item: String) = _uiState.update { state ->
        val newSet = state.part4Selections.toMutableSet()
        if (newSet.contains(item)) newSet.remove(item) else newSet.add(item)
        surveyPrefs.selectedTopics = newSet
        state.copy(part4Selections = newSet)
    }

    /** @return true if can go next (Part 4 → SelfAssessment), false if still has parts */
    fun goNext(): Boolean {
        val current = _uiState.value.currentPart
        return if (current < 3) {
            _uiState.update { it.copy(currentPart = current + 1) }
            false
        } else {
            // Part 4 완료 → Preferences에 저장
            surveyPrefs.selectedTopics = _uiState.value.part4Selections
            true // → SelfAssessment로 이동
        }
    }

    /** @return true if should navigate back to StartPage */
    fun goBack(): Boolean {
        val current = _uiState.value.currentPart
        return if (current > 0) {
            _uiState.update { it.copy(currentPart = current - 1) }
            false
        } else {
            true // Part 1에서 Back → StartPage
        }
    }
}
