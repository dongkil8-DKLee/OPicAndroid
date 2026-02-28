package com.opic.android.ui.study

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.AudioFileResolver
import com.opic.android.audio.AudioPlayer
import com.opic.android.audio.AudioRecorder
import com.opic.android.audio.SttManager
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.QuestionSummary
import com.opic.android.data.local.dao.QuestionWithProgress
import com.opic.android.data.local.dao.StudyProgressDao
import com.opic.android.data.local.entity.StudyProgressEntity
import com.opic.android.data.prefs.StudyPreferences
import com.opic.android.util.AnalysisResult
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class StudyPlayTarget { QUESTION, ANSWER, USER }

data class StudyUiState(
    val loading: Boolean = true,

    // 필터 옵션
    val sets: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val titles: List<String> = emptyList(),

    // 필터 선택
    val selectedSet: String = "전체",
    val selectedType: String = "전체",
    val selectedTitle: String = "",
    val selectedSort: String = "주제 순서",
    val selectedStudyFilter: String = "전체",

    // 현재 문제
    val currentQuestion: QuestionWithProgress? = null,
    val hasUserAudio: Boolean = false,
    val userAudioPath: String? = null,

    // 스크립트 편집
    val editingQuestion: Boolean = false,
    val editingAnswer: Boolean = false,
    val editingUserScript: Boolean = false,
    val questionDraft: String = "",
    val answerDraft: String = "",
    val userScriptDraft: String = "",

    // 오디오 재생
    val playingTarget: StudyPlayTarget? = null,

    // 녹음
    val isRecording: Boolean = false,
    val micLevel: Float = 0f,

    // 그룹 재생
    val groupPlaying: Boolean = false,
    val groupPlayMode: String = "목록 재생",  // 목록 재생, 질문 재생, 답변 재생
    val repeatCount: Int = 1,
    val groupPlayStatus: String = "",  // "재생: 3/15 (반복 2/3)"
    val groupPlayIndex: Int = 0,
    val groupPlayTotal: Int = 0,

    // 학습 진도
    val studyCount: Int = 0,
    val isFavorite: Boolean = false,

    // STT
    val sttText: String? = null,
    val sttListening: Boolean = false,
    val showDiff: Boolean = false,

    // 발화 분석
    val analysisResult: AnalysisResult? = null,

    // 설정
    val fontSize: Int = 18,

    // 속도/자막/집중모드
    val playbackSpeed: Float = 1.0f,
    val highlightedWordIndex: Int = -1,   // -1 = 하이라이트 없음
    val focusMode: Boolean = false
)

/**
 * Python StudyScreen 상태관리 1:1 이식 (Phase 7 기본).
 * 필터 체인 + 문제 로드 + 오디오 재생 + 스크립트 편집 + 학습완료/즐겨찾기.
 */
/** 그룹 재생 플레이리스트 항목 */
data class PlaylistItem(
    val title: String,
    val audioLink: String?,  // assets 오디오 link name
    val text: String?,       // TTS 폴백용 텍스트
    val mode: String         // "question" or "answer"
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val questionDao: QuestionDao,
    private val studyProgressDao: StudyProgressDao,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val audioFileResolver: AudioFileResolver,
    private val ttsManager: TtsManager,
    private val sttManager: SttManager,
    private val prefs: StudyPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "StudyViewModel"
        private const val USER_ID = 1
    }

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState

    /** 전체 문제 요약 캐시 (필터 체인에서 반복 사용) */
    private var allSummaries: List<QuestionSummary> = emptyList()

    private val recordingDir: File by lazy {
        File(context.getExternalFilesDir(null), "Recording").also { it.mkdirs() }
    }

    private var recordingJob: Job? = null
    private var groupPlayJob: Job? = null
    private var positionPollingJob: Job? = null
    private var playlist: List<PlaylistItem> = emptyList()
    private var currentPlaylistIndex = 0

    init {
        loadInitialData()
    }

    // ==================== 초기 데이터 로드 ====================

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                allSummaries = questionDao.getAllQuestionsWithProgress(USER_ID)
                val sets = allSummaries.mapNotNull { it.set }.distinct().sorted()
                val types = allSummaries.mapNotNull { it.type }.distinct().sorted()

                // SharedPreferences 에서 복원
                val savedSet = prefs.set
                val savedType = prefs.type
                val savedSort = prefs.sort
                val savedStudyFilter = prefs.studyFilter
                val savedFontSize = prefs.fontSize
                val savedRepeat = prefs.repeat
                val savedGroupPlayMode = prefs.groupPlayMode
                val savedPlaybackSpeed = prefs.playbackSpeed
                val savedFocusMode = prefs.focusMode

                val effectiveSet = if (savedSet in sets || savedSet == "전체") savedSet else "전체"
                val effectiveType = if (savedType in types || savedType == "전체") savedType else "전체"

                // AudioPlayer에 저장된 속도 적용
                if (savedPlaybackSpeed != 1.0f) {
                    audioPlayer.setSpeed(savedPlaybackSpeed)
                }

                _uiState.update {
                    it.copy(
                        loading = false,
                        sets = sets,
                        types = types,
                        selectedSet = effectiveSet,
                        selectedType = effectiveType,
                        selectedSort = savedSort,
                        selectedStudyFilter = savedStudyFilter,
                        fontSize = savedFontSize,
                        repeatCount = savedRepeat,
                        groupPlayMode = savedGroupPlayMode,
                        playbackSpeed = savedPlaybackSpeed,
                        focusMode = savedFocusMode
                    )
                }

                // 타이틀 목록 갱신 → 저장된 타이틀 복원
                updateTitleList()

                val savedTitle = prefs.title
                val currentTitles = _uiState.value.titles
                if (savedTitle in currentTitles) {
                    onTitleSelected(savedTitle)
                } else if (currentTitles.isNotEmpty()) {
                    onTitleSelected(currentTitles.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "초기 데이터 로드 실패", e)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ==================== 필터 체인 ====================

    fun onSetChanged(set: String) {
        _uiState.update { it.copy(selectedSet = set) }
        prefs.set = set

        // set 변경 → type 옵션 갱신
        viewModelScope.launch {
            val filtered = if (set == "전체") {
                allSummaries.mapNotNull { it.type }.distinct().sorted()
            } else {
                allSummaries.filter { it.set == set }.mapNotNull { it.type }.distinct().sorted()
            }
            _uiState.update { it.copy(types = filtered) }
            updateTitleList()
            selectFirstTitle()
        }
    }

    fun onTypeChanged(type: String) {
        _uiState.update { it.copy(selectedType = type) }
        prefs.type = type

        // type 변경 → set 옵션 갱신
        viewModelScope.launch {
            val filtered = if (type == "전체") {
                allSummaries.mapNotNull { it.set }.distinct().sorted()
            } else {
                allSummaries.filter { it.type == type }.mapNotNull { it.set }.distinct().sorted()
            }
            _uiState.update { it.copy(sets = filtered) }
            updateTitleList()
            selectFirstTitle()
        }
    }

    fun onSortChanged(sort: String) {
        _uiState.update { it.copy(selectedSort = sort) }
        prefs.sort = sort
        viewModelScope.launch {
            updateTitleList()
            selectFirstTitle()
        }
    }

    fun onStudyFilterChanged(filter: String) {
        _uiState.update { it.copy(selectedStudyFilter = filter) }
        prefs.studyFilter = filter
        viewModelScope.launch {
            updateTitleList()
            selectFirstTitle()
        }
    }

    fun onTitleSelected(title: String) {
        if (title.isBlank()) return
        stopAudio()
        _uiState.update {
            it.copy(
                selectedTitle = title,
                editingQuestion = false,
                editingAnswer = false,
                editingUserScript = false,
                questionDraft = "",
                answerDraft = "",
                userScriptDraft = ""
            )
        }
        prefs.title = title
        loadQuestionData(title)
    }

    fun onFontSizeChanged(size: Int) {
        _uiState.update { it.copy(fontSize = size) }
        prefs.fontSize = size
    }

    /** 필터 조건에 따라 타이틀 목록 갱신 */
    private fun updateTitleList() {
        val state = _uiState.value
        var filtered = allSummaries.asSequence()

        // set 필터
        if (state.selectedSet != "전체") {
            filtered = filtered.filter { it.set == state.selectedSet }
        }
        // type 필터
        if (state.selectedType != "전체") {
            filtered = filtered.filter { it.type == state.selectedType }
        }
        // study 필터
        when (state.selectedStudyFilter) {
            "전체" -> { /* no filter */ }
            "📌" -> filtered = filtered.filter { (it.isFavorite ?: 0) == 1 }
            "0" -> filtered = filtered.filter { (it.studyCount ?: 0) == 0 }
            else -> {
                val count = state.selectedStudyFilter.toIntOrNull()
                if (count != null) {
                    filtered = filtered.filter { (it.studyCount ?: 0) == count }
                }
            }
        }

        // 정렬
        val sorted = when (state.selectedSort) {
            "오래된 순" -> filtered.sortedBy { it.lastModified ?: "1970-01-01" }
            else -> filtered.sortedBy { it.title } // 주제 순서
        }

        val titles = sorted.map { it.title }.toList()
        _uiState.update { it.copy(titles = titles) }
    }

    private fun selectFirstTitle() {
        val titles = _uiState.value.titles
        if (titles.isNotEmpty()) {
            onTitleSelected(titles.first())
        } else {
            _uiState.update {
                it.copy(selectedTitle = "", currentQuestion = null, studyCount = 0, isFavorite = false)
            }
        }
    }

    // ==================== 문제 데이터 로드 ====================

    private fun loadQuestionData(title: String) {
        viewModelScope.launch {
            try {
                val qp = questionDao.getQuestionWithProgress(title, USER_ID)
                if (qp == null) {
                    Log.w(TAG, "문제 데이터 없음: $title")
                    return@launch
                }

                val userAudio = findUserRecording(qp.questionId)
                val savedAnalysis = qp.analysisResult?.let { SpeechAnalyzer.fromJson(it) }
                _uiState.update {
                    it.copy(
                        currentQuestion = qp,
                        studyCount = qp.studyCount ?: 0,
                        isFavorite = (qp.isFavorite ?: 0) == 1,
                        hasUserAudio = userAudio != null,
                        userAudioPath = userAudio,
                        sttText = qp.sttText,
                        showDiff = false,
                        analysisResult = savedAnalysis
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "문제 데이터 로드 실패: $title", e)
            }
        }
    }

    /** Python _find_user_recording() 대응: Recording/ 디렉터리에서 해당 문제의 녹음 파일 검색 */
    private fun findUserRecording(questionId: Int): String? {
        if (!recordingDir.exists()) return null
        val pattern = "UserRec_${questionId}_"
        return recordingDir.listFiles()
            ?.filter { it.name.startsWith(pattern) && it.name.endsWith(".wav") }
            ?.maxByOrNull { it.name }
            ?.absolutePath
    }

    // ==================== Prev / Next (타이틀 목록 내) ====================

    fun onPrevTitle() {
        val state = _uiState.value
        val idx = state.titles.indexOf(state.selectedTitle)
        if (idx > 0) onTitleSelected(state.titles[idx - 1])
    }

    fun onNextTitle() {
        val state = _uiState.value
        val idx = state.titles.indexOf(state.selectedTitle)
        if (idx >= 0 && idx < state.titles.size - 1) onTitleSelected(state.titles[idx + 1])
    }

    // ==================== 오디오 재생 ====================

    fun playQuestionAudio() {
        val state = _uiState.value
        if (state.playingTarget != null || state.isRecording || state.groupPlaying) return
        val q = state.currentQuestion ?: return

        _uiState.update { it.copy(playingTarget = StudyPlayTarget.QUESTION) }
        playAudioOrTts(q.questionAudio, q.questionText)
        startPositionPolling(q.questionText)
    }

    fun playAnswerAudio() {
        val state = _uiState.value
        if (state.playingTarget != null || state.isRecording || state.groupPlaying) return
        val q = state.currentQuestion ?: return

        _uiState.update { it.copy(playingTarget = StudyPlayTarget.ANSWER) }
        playAudioOrTts(q.answerAudio, q.answerScript)
        startPositionPolling(q.answerScript)
    }

    fun playUserAudio() {
        val state = _uiState.value
        if (state.playingTarget != null || state.isRecording || state.groupPlaying) return
        val path = state.userAudioPath ?: return

        _uiState.update { it.copy(playingTarget = StudyPlayTarget.USER) }
        audioPlayer.playFromFile(path) { onPlaybackFinished() }
    }

    fun stopAudio() {
        if (_uiState.value.groupPlaying) {
            stopGroupPlay()
            return
        }
        stopPositionPolling()
        audioPlayer.stop()
        _uiState.update { it.copy(playingTarget = null) }
    }

    private fun playAudioOrTts(audioLink: String?, text: String?, onFinish: (() -> Unit)? = null) {
        val finishCallback = onFinish ?: { onPlaybackFinished() }
        val assetPath = if (audioLink != null) audioFileResolver.resolve(audioLink) else null
        if (assetPath != null) {
            audioPlayer.playFromAssets(assetPath) { finishCallback() }
            return
        }

        if (!text.isNullOrBlank()) {
            viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(text)
                if (ttsPath != null) {
                    audioPlayer.playFromFile(ttsPath) { finishCallback() }
                } else {
                    finishCallback()
                }
            }
            return
        }

        finishCallback()
    }

    private fun onPlaybackFinished() {
        val wasPlayingAnswer = _uiState.value.playingTarget == StudyPlayTarget.ANSWER
        stopPositionPolling()
        _uiState.update { it.copy(playingTarget = null) }

        if (wasPlayingAnswer) {
            tryAutoStudyComplete()
        }
    }

    /** 답변 재생 완료 시 자동 학습완료 (하루 1회, 최대 7) */
    private fun tryAutoStudyComplete() {
        val q = _uiState.value.currentQuestion ?: return
        val currentCount = _uiState.value.studyCount
        if (currentCount >= 7) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastModified = q.lastModified
        if (lastModified != null && lastModified.startsWith(today)) return

        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.incrementStudyCount(USER_ID, q.questionId)

                val newCount = currentCount + 1
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                _uiState.update {
                    it.copy(
                        studyCount = newCount,
                        currentQuestion = it.currentQuestion?.copy(
                            studyCount = newCount,
                            lastModified = now
                        )
                    )
                }
                refreshSummaryCache(q.questionId)
                Log.d(TAG, "자동 학습완료: questionId=${q.questionId}, count=$newCount")
            } catch (e: Exception) {
                Log.e(TAG, "자동 학습완료 실패", e)
            }
        }
    }

    // ==================== 스크립트 편집 ====================

    fun toggleEditQuestion() {
        val state = _uiState.value
        if (state.editingQuestion) {
            _uiState.update { it.copy(editingQuestion = false, questionDraft = "") }
        } else {
            _uiState.update {
                it.copy(editingQuestion = true, questionDraft = state.currentQuestion?.questionText ?: "")
            }
        }
    }

    fun toggleEditAnswer() {
        val state = _uiState.value
        if (state.editingAnswer) {
            _uiState.update { it.copy(editingAnswer = false, answerDraft = "") }
        } else {
            _uiState.update {
                it.copy(editingAnswer = true, answerDraft = state.currentQuestion?.answerScript ?: "")
            }
        }
    }

    fun toggleEditUserScript() {
        val state = _uiState.value
        if (state.editingUserScript) {
            _uiState.update { it.copy(editingUserScript = false, userScriptDraft = "") }
        } else {
            _uiState.update {
                it.copy(editingUserScript = true, userScriptDraft = state.currentQuestion?.userScript ?: "")
            }
        }
    }

    fun updateQuestionDraft(text: String) { _uiState.update { it.copy(questionDraft = text) } }
    fun updateAnswerDraft(text: String) { _uiState.update { it.copy(answerDraft = text) } }
    fun updateUserScriptDraft(text: String) { _uiState.update { it.copy(userScriptDraft = text) } }

    fun saveQuestionScript() {
        val q = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.questionDraft
        viewModelScope.launch {
            questionDao.updateQuestionText(q.questionId, draft)
            _uiState.update {
                it.copy(
                    currentQuestion = it.currentQuestion?.copy(questionText = draft),
                    editingQuestion = false,
                    questionDraft = ""
                )
            }
            refreshSummaryCache(q.questionId)
        }
    }

    fun saveAnswerScript() {
        val q = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.answerDraft
        viewModelScope.launch {
            questionDao.updateAnswerScript(q.questionId, draft)
            _uiState.update {
                it.copy(
                    currentQuestion = it.currentQuestion?.copy(answerScript = draft),
                    editingAnswer = false,
                    answerDraft = ""
                )
            }
        }
    }

    fun saveUserScript() {
        val q = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.userScriptDraft
        viewModelScope.launch {
            questionDao.updateUserScript(q.questionId, draft)
            _uiState.update {
                it.copy(
                    currentQuestion = it.currentQuestion?.copy(userScript = draft),
                    editingUserScript = false,
                    userScriptDraft = ""
                )
            }
        }
    }

    // ==================== 학습 완료 ====================

    /**
     * Python _on_study_complete_clicked() 1:1 이식.
     * 하루 1회, 최대 7회 제한.
     * @return 결과 메시지 (UI에서 Toast 등으로 표시)
     */
    fun onStudyComplete(): String {
        val q = _uiState.value.currentQuestion ?: return "문제가 선택되지 않았습니다."
        val currentCount = _uiState.value.studyCount

        if (currentCount >= 7) return "최대 7회에 도달했습니다."

        // 오늘 이미 완료했는지 확인
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastModified = q.lastModified
        if (lastModified != null && lastModified.startsWith(today)) {
            return "오늘 이미 학습완료 했습니다."
        }

        viewModelScope.launch {
            try {
                // progress 레코드가 없으면 생성
                ensureProgressExists(q.questionId)
                studyProgressDao.incrementStudyCount(USER_ID, q.questionId)

                val newCount = currentCount + 1
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                _uiState.update {
                    it.copy(
                        studyCount = newCount,
                        currentQuestion = it.currentQuestion?.copy(
                            studyCount = newCount,
                            lastModified = now
                        )
                    )
                }

                // 캐시 갱신
                refreshSummaryCache(q.questionId)
                Log.d(TAG, "학습완료: questionId=${q.questionId}, count=$newCount")
            } catch (e: Exception) {
                Log.e(TAG, "학습완료 실패", e)
            }
        }

        return "학습완료! (${currentCount + 1}/7)"
    }

    // ==================== 즐겨찾기 ====================

    fun toggleFavorite() {
        val q = _uiState.value.currentQuestion ?: return
        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.toggleFavorite(USER_ID, q.questionId)

                val newFav = !_uiState.value.isFavorite
                _uiState.update {
                    it.copy(
                        isFavorite = newFav,
                        currentQuestion = it.currentQuestion?.copy(
                            isFavorite = if (newFav) 1 else 0
                        )
                    )
                }
                refreshSummaryCache(q.questionId)
                Log.d(TAG, "즐겨찾기 토글: questionId=${q.questionId}, isFavorite=$newFav")
            } catch (e: Exception) {
                Log.e(TAG, "즐겨찾기 토글 실패", e)
            }
        }
    }

    // ==================== 녹음 (Phase 8) ====================

    /**
     * Python toggle_recording() 대응.
     * 녹음 시작/정지 토글. 파일명: UserRec_{questionId}_{timestamp}.wav
     */
    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
            return
        }

        val q = _uiState.value.currentQuestion ?: return
        if (_uiState.value.playingTarget != null || _uiState.value.groupPlaying) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "UserRec_${q.questionId}_$timestamp.wav"
        val outputFile = File(recordingDir, filename)

        _uiState.update { it.copy(isRecording = true, micLevel = 0f) }

        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            // 녹음 완료 (stop 또는 120초)
            _uiState.update {
                it.copy(
                    isRecording = false,
                    micLevel = 0f,
                    hasUserAudio = outputFile.exists() && outputFile.length() > 44,
                    userAudioPath = outputFile.absolutePath
                )
            }
            Log.d(TAG, "녹음 완료: ${outputFile.name} (${outputFile.length()} bytes)")
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
        // recordingJob의 코루틴이 종료되면서 상태 업데이트됨
    }

    // ==================== 그룹 재생 (Phase 8) ====================

    fun onGroupPlayModeChanged(mode: String) {
        _uiState.update { it.copy(groupPlayMode = mode) }
        prefs.groupPlayMode = mode
    }

    fun onRepeatChanged(count: Int) {
        _uiState.update { it.copy(repeatCount = count) }
        prefs.repeat = count
    }

    /**
     * Python toggle_group_play() 대응.
     * 현재 필터된 타이틀 목록으로 플레이리스트 빌드 → 순차 재생.
     */
    fun toggleGroupPlay() {
        if (_uiState.value.groupPlaying) {
            stopGroupPlay()
            return
        }

        if (_uiState.value.isRecording) return

        val state = _uiState.value
        val titles = state.titles
        if (titles.isEmpty()) return

        // 플레이리스트 빌드
        viewModelScope.launch {
            val items = mutableListOf<PlaylistItem>()
            val repeat = state.repeatCount

            for (title in titles) {
                val q = questionDao.getQuestionWithProgress(title, USER_ID) ?: continue

                when (state.groupPlayMode) {
                    "목록 재생" -> {
                        // Q * repeat + A * repeat
                        repeat(repeat) {
                            items.add(PlaylistItem(title, q.questionAudio, q.questionText, "question"))
                        }
                        repeat(repeat) {
                            items.add(PlaylistItem(title, q.answerAudio, q.answerScript, "answer"))
                        }
                    }
                    "질문 재생" -> {
                        repeat(repeat) {
                            items.add(PlaylistItem(title, q.questionAudio, q.questionText, "question"))
                        }
                    }
                    "답변 재생" -> {
                        repeat(repeat) {
                            items.add(PlaylistItem(title, q.answerAudio, q.answerScript, "answer"))
                        }
                    }
                }
            }

            if (items.isEmpty()) return@launch

            playlist = items
            currentPlaylistIndex = 0

            _uiState.update {
                it.copy(
                    groupPlaying = true,
                    groupPlayIndex = 0,
                    groupPlayTotal = items.size,
                    groupPlayStatus = "Group Play: 1/${items.size}"
                )
            }

            playNextInPlaylist()
        }
    }

    fun stopGroupPlay() {
        audioPlayer.stop()
        groupPlayJob?.cancel()
        playlist = emptyList()
        currentPlaylistIndex = 0
        _uiState.update {
            it.copy(
                groupPlaying = false,
                playingTarget = null,
                groupPlayStatus = "",
                groupPlayIndex = 0,
                groupPlayTotal = 0
            )
        }
    }

    /** 그룹재생 다음 항목 재생 (또는 skip) */
    fun skipToNextInPlaylist() {
        if (!_uiState.value.groupPlaying) return
        audioPlayer.stop()
        currentPlaylistIndex++
        playNextInPlaylist()
    }

    private fun playNextInPlaylist() {
        if (currentPlaylistIndex >= playlist.size) {
            // 플레이리스트 종료
            stopGroupPlay()
            return
        }

        val item = playlist[currentPlaylistIndex]
        val progress = "${currentPlaylistIndex + 1}/${playlist.size}"
        _uiState.update {
            it.copy(
                groupPlayIndex = currentPlaylistIndex,
                groupPlayStatus = "Group Play: $progress - ${item.title}",
                playingTarget = if (item.mode == "question") StudyPlayTarget.QUESTION else StudyPlayTarget.ANSWER
            )
        }

        // 해당 타이틀로 이동
        if (item.title != _uiState.value.selectedTitle) {
            _uiState.update { it.copy(selectedTitle = item.title) }
            loadQuestionData(item.title)
        }

        playAudioOrTts(item.audioLink, item.text, onFinish = {
            // 2초 대기 → 다음 항목
            groupPlayJob = viewModelScope.launch {
                delay(2000)
                currentPlaylistIndex++
                playNextInPlaylist()
            }
        })
    }

    // ==================== STT (Phase 9) ====================

    fun startStt() {
        val q = _uiState.value.currentQuestion ?: return
        if (_uiState.value.sttListening) return

        _uiState.update { it.copy(sttListening = true) }

        sttManager.startListening(
            onResult = { text ->
                _uiState.update { it.copy(sttText = text, sttListening = false) }
                // Save to DB
                viewModelScope.launch {
                    try {
                        ensureProgressExists(q.questionId)
                        studyProgressDao.updateSttText(USER_ID, q.questionId, text)
                        Log.d(TAG, "STT result saved: questionId=${q.questionId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "STT result save failed", e)
                    }
                }
            },
            onError = { error ->
                _uiState.update { it.copy(sttListening = false) }
                Log.w(TAG, "STT error: $error")
            }
        )
    }

    fun stopStt() {
        sttManager.stopListening()
    }

    fun toggleDiff() {
        _uiState.update { it.copy(showDiff = !it.showDiff) }
    }

    // ==================== 발화 분석 (Phase 13) ====================

    fun analyzeSpeech() {
        val state = _uiState.value
        val sttText = state.sttText ?: return
        val answerScript = state.currentQuestion?.answerScript ?: return
        if (sttText.isBlank() || answerScript.isBlank()) return

        val result = SpeechAnalyzer.analyze(answerScript, sttText)
        _uiState.update { it.copy(analysisResult = result) }

        // Save to DB
        val q = state.currentQuestion ?: return
        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                val json = SpeechAnalyzer.toJson(result)
                studyProgressDao.updateAnalysisResult(USER_ID, q.questionId, json)
                Log.d(TAG, "Analysis result saved: questionId=${q.questionId}")
            } catch (e: Exception) {
                Log.e(TAG, "Analysis result save failed", e)
            }
        }
    }

    // ==================== 속도/자막/집중모드 (Phase 11) ====================

    fun onPlaybackSpeedChanged(speed: Float) {
        audioPlayer.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
        prefs.playbackSpeed = speed
    }

    fun toggleFocusMode() {
        val newMode = !_uiState.value.focusMode
        _uiState.update { it.copy(focusMode = newMode) }
        prefs.focusMode = newMode
    }

    private fun startPositionPolling(scriptText: String?) {
        stopPositionPolling()
        if (scriptText.isNullOrBlank()) return

        val words = scriptText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return

        positionPollingJob = viewModelScope.launch {
            delay(100)
            while (audioPlayer.isPlaying) {
                val duration = audioPlayer.duration
                val position = audioPlayer.currentPosition
                if (duration > 0) {
                    val wordIndex = ((position.toLong() * words.size) / duration)
                        .toInt().coerceIn(0, words.size - 1)
                    _uiState.update { it.copy(highlightedWordIndex = wordIndex) }
                }
                delay(100)
            }
            _uiState.update { it.copy(highlightedWordIndex = -1) }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
        _uiState.update { it.copy(highlightedWordIndex = -1) }
    }

    // ==================== Utility ====================

    /** User_Study_Progress 레코드가 없으면 INSERT */
    private suspend fun ensureProgressExists(questionId: Int) {
        val existing = studyProgressDao.getProgress(USER_ID, questionId)
        if (existing == null) {
            studyProgressDao.insert(
                StudyProgressEntity(
                    progressId = 0,
                    userId = USER_ID,
                    questionId = questionId,
                    studyCount = 0,
                    lastModified = null,
                    isFavorite = 0,
                    sttText = null,
                    analysisResult = null
                )
            )
        }
    }

    /** 필터 체인 캐시 갱신 (study_count/is_favorite 변경 시) */
    private suspend fun refreshSummaryCache(questionId: Int) {
        allSummaries = questionDao.getAllQuestionsWithProgress(USER_ID)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stop()
        recordingJob?.cancel()
        groupPlayJob?.cancel()
        positionPollingJob?.cancel()
    }
}
