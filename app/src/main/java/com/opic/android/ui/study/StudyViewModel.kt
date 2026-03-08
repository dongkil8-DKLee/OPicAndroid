package com.opic.android.ui.study

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.ai.ClaudeApiService
import com.opic.android.audio.AudioFileResolver
import com.opic.android.audio.AudioPlayer
import com.opic.android.audio.AudioRecorder
import com.opic.android.audio.SttManager
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.QuestionWithProgress
import com.opic.android.data.local.dao.StudyProgressDao
import com.opic.android.data.local.entity.StudyProgressEntity
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.data.prefs.StudyPreferences
import com.opic.android.ui.common.filter.StudyFilterController
import com.opic.android.util.AnalysisResult
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class StudyPlayTarget { QUESTION, ANSWER, USER }

/**
 * Study 전용 UI 상태 — 필터 상태는 StudyFilterController.state 로 분리.
 */
data class StudyUiState(
    val loading: Boolean = true,

    // 현재 선택 타이틀
    val selectedTitle: String = "",

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
    val groupPlayMode: String = "목록 재생",
    val repeatCount: Int = 1,
    val groupPlayStatus: String = "",
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
    val highlightedWordIndex: Int = -1,
    val focusMode: Boolean = false,

    // AI 모범 답안
    val aiLoading: Boolean = false,
    val aiModelAnswer: String = "",
    val aiError: String? = null,

    // AI 답변 탭
    val answerTabIndex: Int = 0,
    val editingAiAnswer: Boolean = false,
    val aiAnswerDraft: String = ""
)

/** 그룹 재생 플레이리스트 항목 */
data class PlaylistItem(
    val title: String,
    val audioLink: String?,
    val text: String?,
    val mode: String
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
    private val prefs: StudyPreferences,
    private val appPrefs: AppPreferences,
    private val claudeApiService: ClaudeApiService,
    val filterController: StudyFilterController       // public — StudyScreen에서 state 직접 구독
) : ViewModel() {

    companion object {
        private const val TAG = "StudyViewModel"
        private const val USER_ID = 1
    }

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState

    private val recordingDir: File by lazy {
        File(context.getExternalFilesDir(null), "Recording").also { it.mkdirs() }
    }

    private fun beepStart() {
        viewModelScope.launch {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
            try { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150); kotlinx.coroutines.delay(180) }
            finally { tg.release() }
        }
    }

    private fun beepStop() {
        viewModelScope.launch {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
            try { tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 150); kotlinx.coroutines.delay(180) }
            finally { tg.release() }
        }
    }

    private var recordingJob: Job? = null
    private var groupPlayJob: Job? = null
    private var positionPollingJob: Job? = null
    private var playlist: List<PlaylistItem> = emptyList()
    private var currentPlaylistIndex = 0

    init {
        // ① 비-필터 prefs 복원 (즉시)
        restoreNonFilterPrefs()

        // ② Controller 준비 대기 → 저장된 타이틀 복원
        viewModelScope.launch {
            try {
                filterController.state.first { it.isReady }
                restoreSavedTitle()
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "초기화 실패", e)
                _uiState.update { it.copy(loading = false) }
            }
        }

        // ③ 필터 타이틀 목록 변경 감지 → selectFirstTitle (loading 완료 후에만)
        viewModelScope.launch {
            filterController.state
                .map { it.titles }
                .distinctUntilChanged()
                .collect { titles ->
                    if (_uiState.value.loading) return@collect
                    val current = _uiState.value.selectedTitle
                    if (current !in titles) {
                        if (titles.isNotEmpty()) onTitleSelected(titles.first())
                        else _uiState.update {
                            it.copy(selectedTitle = "", currentQuestion = null, studyCount = 0, isFavorite = false)
                        }
                    }
                }
        }
    }

    private fun restoreNonFilterPrefs() {
        val savedPlaybackSpeed = prefs.playbackSpeed
        if (savedPlaybackSpeed != 1.0f) audioPlayer.setSpeed(savedPlaybackSpeed)
        _uiState.update {
            it.copy(
                fontSize        = prefs.fontSize,
                repeatCount     = prefs.repeat,
                groupPlayMode   = prefs.groupPlayMode,
                playbackSpeed   = savedPlaybackSpeed,
                focusMode       = prefs.focusMode
            )
        }
    }

    private fun restoreSavedTitle() {
        val titles     = filterController.state.value.titles
        val savedTitle = prefs.title
        if (savedTitle in titles) {
            onTitleSelected(savedTitle)
        } else if (titles.isNotEmpty()) {
            onTitleSelected(titles.first())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 필터 API — Controller에 위임 (Study/Practice 공통)
    // ═══════════════════════════════════════════════════════════════

    fun onSetChanged(set: String)              = filterController.onSetChanged(set)
    fun onTypeChanged(type: String)            = filterController.onTypeChanged(type)
    fun onSortChanged(sort: String)            = filterController.onSortChanged(sort)
    fun onStudyFilterChanged(filter: String)   = filterController.onStudyFilterChanged(filter)
    fun initFilters(type: String, set: String?) = filterController.initFilters(type, set)
    fun setGradeFilter(grade: String?)         = filterController.setGradeFilter(grade)

    // ═══════════════════════════════════════════════════════════════
    // 타이틀 선택
    // ═══════════════════════════════════════════════════════════════

    fun onTitleSelected(title: String) {
        if (title.isBlank()) return
        stopAudio()
        _uiState.update {
            it.copy(
                selectedTitle        = title,
                editingQuestion      = false,
                editingAnswer        = false,
                editingUserScript    = false,
                questionDraft        = "",
                answerDraft          = "",
                userScriptDraft      = "",
                answerTabIndex       = 0,
                editingAiAnswer      = false,
                aiAnswerDraft        = "",
                aiLoading            = false,
                aiError              = null
            )
        }
        prefs.title = title
        loadQuestionData(title)
    }

    fun onPrevTitle() {
        val titles = filterController.state.value.titles
        val idx    = titles.indexOf(_uiState.value.selectedTitle)
        if (idx > 0) onTitleSelected(titles[idx - 1])
    }

    fun onNextTitle() {
        val titles = filterController.state.value.titles
        val idx    = titles.indexOf(_uiState.value.selectedTitle)
        if (idx >= 0 && idx < titles.size - 1) onTitleSelected(titles[idx + 1])
    }

    fun onFontSizeChanged(size: Int) {
        _uiState.update { it.copy(fontSize = size) }
        prefs.fontSize = size
    }

    // ═══════════════════════════════════════════════════════════════
    // 문제 데이터 로드
    // ═══════════════════════════════════════════════════════════════

    private fun loadQuestionData(title: String) {
        viewModelScope.launch {
            try {
                val qp = questionDao.getQuestionWithProgress(title, USER_ID)
                if (qp == null) {
                    Log.w(TAG, "문제 데이터 없음: $title")
                    return@launch
                }
                val userAudio    = findUserRecording(qp.questionId)
                val savedAnalysis = qp.analysisResult?.let { SpeechAnalyzer.fromJson(it) }
                _uiState.update {
                    it.copy(
                        currentQuestion = qp,
                        studyCount      = qp.studyCount ?: 0,
                        isFavorite      = (qp.isFavorite ?: 0) == 1,
                        hasUserAudio    = userAudio != null,
                        userAudioPath   = userAudio,
                        sttText         = qp.sttText,
                        showDiff        = false,
                        analysisResult  = savedAnalysis
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "문제 데이터 로드 실패: $title", e)
            }
        }
    }

    private fun findUserRecording(questionId: Int): String? {
        val file = File(recordingDir, "Study_${questionId}.wav")
        return if (file.exists()) file.absolutePath else null
    }

    // ═══════════════════════════════════════════════════════════════
    // 오디오 재생
    // ═══════════════════════════════════════════════════════════════

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
        if (_uiState.value.groupPlaying) { stopGroupPlay(); return }
        stopPositionPolling()
        audioPlayer.stop()
        _uiState.update { it.copy(playingTarget = null) }
    }

    private fun playAudioOrTts(audioLink: String?, text: String?, onFinish: (() -> Unit)? = null) {
        val finishCallback = onFinish ?: { onPlaybackFinished() }
        val audioSource = if (audioLink != null) audioFileResolver.resolve(audioLink) else null
        when (audioSource) {
            is com.opic.android.audio.AudioSource.AssetPath -> {
                audioPlayer.playFromAssets(audioSource.path) { finishCallback() }; return
            }
            is com.opic.android.audio.AudioSource.FilePath -> {
                audioPlayer.playFromFile(audioSource.path) { finishCallback() }; return
            }
            null -> {}
        }
        if (!text.isNullOrBlank()) {
            viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(text)
                if (ttsPath != null) audioPlayer.playFromFile(ttsPath) { finishCallback() }
                else finishCallback()
            }
            return
        }
        finishCallback()
    }

    private fun onPlaybackFinished() {
        val wasAnswer = _uiState.value.playingTarget == StudyPlayTarget.ANSWER
        stopPositionPolling()
        _uiState.update { it.copy(playingTarget = null) }
        if (wasAnswer) tryAutoStudyComplete()
    }

    private fun tryAutoStudyComplete() {
        val q            = _uiState.value.currentQuestion ?: return
        val currentCount = _uiState.value.studyCount
        if (currentCount >= 7) return
        val today        = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (q.lastModified?.startsWith(today) == true) return

        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.incrementStudyCount(USER_ID, q.questionId)
                val newCount = currentCount + 1
                val now      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                _uiState.update {
                    it.copy(
                        studyCount      = newCount,
                        currentQuestion = it.currentQuestion?.copy(studyCount = newCount, lastModified = now)
                    )
                }
                filterController.refreshSummaryCache(q.questionId)
            } catch (e: Exception) {
                Log.e(TAG, "자동 학습완료 실패", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 스크립트 편집
    // ═══════════════════════════════════════════════════════════════

    fun toggleEditQuestion() {
        val state = _uiState.value
        if (state.editingQuestion) _uiState.update { it.copy(editingQuestion = false, questionDraft = "") }
        else _uiState.update { it.copy(editingQuestion = true, questionDraft = state.currentQuestion?.questionText ?: "") }
    }

    fun toggleEditAnswer() {
        val state = _uiState.value
        if (state.editingAnswer) _uiState.update { it.copy(editingAnswer = false, answerDraft = "") }
        else _uiState.update { it.copy(editingAnswer = true, answerDraft = state.currentQuestion?.answerScript ?: "") }
    }

    fun toggleEditUserScript() {
        val state = _uiState.value
        if (state.editingUserScript) _uiState.update { it.copy(editingUserScript = false, userScriptDraft = "") }
        else _uiState.update { it.copy(editingUserScript = true, userScriptDraft = state.currentQuestion?.userScript ?: "") }
    }

    fun cancelEditQuestion()    { _uiState.update { it.copy(editingQuestion    = false, questionDraft    = "") } }
    fun cancelEditAnswer()      { _uiState.update { it.copy(editingAnswer      = false, answerDraft      = "") } }
    fun cancelEditUserScript()  { _uiState.update { it.copy(editingUserScript  = false, userScriptDraft  = "") } }

    fun updateQuestionDraft(text: String)   { _uiState.update { it.copy(questionDraft   = text) } }
    fun updateAnswerDraft(text: String)     { _uiState.update { it.copy(answerDraft     = text) } }
    fun updateUserScriptDraft(text: String) { _uiState.update { it.copy(userScriptDraft = text) } }

    fun saveQuestionScript() {
        val q     = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.questionDraft
        viewModelScope.launch {
            questionDao.updateQuestionText(q.questionId, draft)
            _uiState.update {
                it.copy(currentQuestion = it.currentQuestion?.copy(questionText = draft), editingQuestion = false, questionDraft = "")
            }
            filterController.refreshSummaryCache(q.questionId)
        }
    }

    fun saveAnswerScript() {
        val q     = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.answerDraft
        viewModelScope.launch {
            questionDao.updateAnswerScript(q.questionId, draft)
            _uiState.update {
                it.copy(currentQuestion = it.currentQuestion?.copy(answerScript = draft), editingAnswer = false, answerDraft = "")
            }
        }
    }

    fun saveUserScript() {
        val q     = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.userScriptDraft
        viewModelScope.launch {
            questionDao.updateUserScript(q.questionId, draft)
            _uiState.update {
                it.copy(currentQuestion = it.currentQuestion?.copy(userScript = draft), editingUserScript = false, userScriptDraft = "")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 학습 완료
    // ═══════════════════════════════════════════════════════════════

    fun onStudyComplete(): String {
        val q            = _uiState.value.currentQuestion ?: return "문제가 선택되지 않았습니다."
        val currentCount = _uiState.value.studyCount
        if (currentCount >= 7) return "최대 7회에 도달했습니다."
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (q.lastModified?.startsWith(today) == true) return "오늘 이미 학습완료 했습니다."

        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.incrementStudyCount(USER_ID, q.questionId)
                val newCount = currentCount + 1
                val now      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                _uiState.update {
                    it.copy(
                        studyCount      = newCount,
                        currentQuestion = it.currentQuestion?.copy(studyCount = newCount, lastModified = now)
                    )
                }
                filterController.refreshSummaryCache(q.questionId)
            } catch (e: Exception) {
                Log.e(TAG, "학습완료 실패", e)
            }
        }
        return "학습완료! (${currentCount + 1}/7)"
    }

    // ═══════════════════════════════════════════════════════════════
    // 즐겨찾기
    // ═══════════════════════════════════════════════════════════════

    fun toggleFavorite() {
        val q = _uiState.value.currentQuestion ?: return
        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.toggleFavorite(USER_ID, q.questionId)
                val newFav = !_uiState.value.isFavorite
                _uiState.update {
                    it.copy(isFavorite = newFav, currentQuestion = it.currentQuestion?.copy(isFavorite = if (newFav) 1 else 0))
                }
                filterController.refreshSummaryCache(q.questionId)
            } catch (e: Exception) {
                Log.e(TAG, "즐겨찾기 토글 실패", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 녹음
    // ═══════════════════════════════════════════════════════════════

    fun toggleRecording() {
        if (_uiState.value.isRecording) { stopRecording(); return }
        val q = _uiState.value.currentQuestion ?: return
        if (_uiState.value.playingTarget != null || _uiState.value.groupPlaying) return

        val outputFile = File(recordingDir, "Study_${q.questionId}.wav")
        _uiState.update { it.copy(isRecording = true, micLevel = 0f) }
        beepStart()

        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            beepStop()
            _uiState.update {
                it.copy(
                    isRecording  = false,
                    micLevel     = 0f,
                    hasUserAudio = outputFile.exists() && outputFile.length() > 44,
                    userAudioPath = outputFile.absolutePath
                )
            }
        }
    }

    fun stopRecording() { audioRecorder.stop() }

    // ═══════════════════════════════════════════════════════════════
    // 그룹 재생
    // ═══════════════════════════════════════════════════════════════

    fun onGroupPlayModeChanged(mode: String) {
        _uiState.update { it.copy(groupPlayMode = mode) }
        prefs.groupPlayMode = mode
    }

    fun onRepeatChanged(count: Int) {
        _uiState.update { it.copy(repeatCount = count) }
        prefs.repeat = count
    }

    fun toggleGroupPlay() {
        if (_uiState.value.groupPlaying) { stopGroupPlay(); return }
        if (_uiState.value.isRecording) return

        val state  = _uiState.value
        val titles = filterController.state.value.titles   // Controller에서 최신 목록
        if (titles.isEmpty()) return

        viewModelScope.launch {
            val items  = mutableListOf<PlaylistItem>()
            val repeat = state.repeatCount

            for (title in titles) {
                val q = questionDao.getQuestionWithProgress(title, USER_ID) ?: continue
                when (state.groupPlayMode) {
                    "목록 재생" -> {
                        repeat(repeat) { items.add(PlaylistItem(title, q.questionAudio, q.questionText, "question")) }
                        repeat(repeat) { items.add(PlaylistItem(title, q.answerAudio,   q.answerScript,  "answer"))   }
                    }
                    "질문 재생" -> repeat(repeat) { items.add(PlaylistItem(title, q.questionAudio, q.questionText, "question")) }
                    "답변 재생" -> repeat(repeat) { items.add(PlaylistItem(title, q.answerAudio,   q.answerScript,  "answer"))   }
                }
            }
            if (items.isEmpty()) return@launch

            playlist             = items
            currentPlaylistIndex = 0
            _uiState.update {
                it.copy(groupPlaying = true, groupPlayIndex = 0, groupPlayTotal = items.size, groupPlayStatus = "Group Play: 1/${items.size}")
            }
            playNextInPlaylist()
        }
    }

    fun stopGroupPlay() {
        audioPlayer.stop()
        groupPlayJob?.cancel()
        playlist             = emptyList()
        currentPlaylistIndex = 0
        _uiState.update {
            it.copy(groupPlaying = false, playingTarget = null, groupPlayStatus = "", groupPlayIndex = 0, groupPlayTotal = 0)
        }
    }

    fun skipToNextInPlaylist() {
        if (!_uiState.value.groupPlaying) return
        audioPlayer.stop()
        currentPlaylistIndex++
        playNextInPlaylist()
    }

    private fun playNextInPlaylist() {
        if (currentPlaylistIndex >= playlist.size) { stopGroupPlay(); return }
        val item     = playlist[currentPlaylistIndex]
        val progress = "${currentPlaylistIndex + 1}/${playlist.size}"
        _uiState.update {
            it.copy(
                groupPlayIndex  = currentPlaylistIndex,
                groupPlayStatus = "Group Play: $progress - ${item.title}",
                playingTarget   = if (item.mode == "question") StudyPlayTarget.QUESTION else StudyPlayTarget.ANSWER
            )
        }
        if (item.title != _uiState.value.selectedTitle) {
            _uiState.update { it.copy(selectedTitle = item.title) }
            loadQuestionData(item.title)
        }
        playAudioOrTts(item.audioLink, item.text, onFinish = {
            groupPlayJob = viewModelScope.launch {
                delay(2000)
                currentPlaylistIndex++
                playNextInPlaylist()
            }
        })
    }

    // ═══════════════════════════════════════════════════════════════
    // STT
    // ═══════════════════════════════════════════════════════════════

    fun startStt() {
        val q = _uiState.value.currentQuestion ?: return
        if (_uiState.value.sttListening) return
        _uiState.update { it.copy(sttListening = true) }
        sttManager.startListening(
            onResult = { text ->
                _uiState.update { it.copy(sttText = text, sttListening = false) }
                viewModelScope.launch {
                    try {
                        ensureProgressExists(q.questionId)
                        studyProgressDao.updateSttText(USER_ID, q.questionId, text)
                    } catch (e: Exception) { Log.e(TAG, "STT 저장 실패", e) }
                }
            },
            onError = { _uiState.update { it.copy(sttListening = false) } }
        )
    }

    fun stopStt() { sttManager.stopListening() }
    fun toggleDiff() { _uiState.update { it.copy(showDiff = !it.showDiff) } }

    // ═══════════════════════════════════════════════════════════════
    // 발화 분석
    // ═══════════════════════════════════════════════════════════════

    fun analyzeSpeech() {
        val state        = _uiState.value
        val sttText      = state.sttText ?: return
        val answerScript = state.currentQuestion?.answerScript ?: return
        if (sttText.isBlank() || answerScript.isBlank()) return

        val result = SpeechAnalyzer.analyze(answerScript, sttText)
        _uiState.update { it.copy(analysisResult = result) }

        val q = state.currentQuestion ?: return
        viewModelScope.launch {
            try {
                ensureProgressExists(q.questionId)
                studyProgressDao.updateAnalysisResult(USER_ID, q.questionId, SpeechAnalyzer.toJson(result))
                filterController.refreshAnalysisGradesAndUpdate()
            } catch (e: Exception) { Log.e(TAG, "분석 저장 실패", e) }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 속도 / 집중모드
    // ═══════════════════════════════════════════════════════════════

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
                    val wordIndex = ((position.toLong() * words.size) / duration).toInt().coerceIn(0, words.size - 1)
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

    // ═══════════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════════

    private suspend fun ensureProgressExists(questionId: Int) {
        if (studyProgressDao.getProgress(USER_ID, questionId) == null) {
            studyProgressDao.insert(
                StudyProgressEntity(progressId = 0, userId = USER_ID, questionId = questionId,
                    studyCount = 0, lastModified = null, isFavorite = 0, sttText = null, analysisResult = null)
            )
        }
    }

    // ==================== AI 모범 답안 ====================

    fun generateModelAnswer() {
        val q = _uiState.value.currentQuestion ?: return
        val questionText = q.questionText?.takeIf { it.isNotBlank() } ?: q.title
        val targetGrade = appPrefs.targetGrade

        _uiState.update { it.copy(aiLoading = true, aiError = null, aiModelAnswer = "") }
        viewModelScope.launch {
            val result = claudeApiService.generateModelAnswer(questionText, targetGrade)
            result.onSuccess { answer ->
                questionDao.updateAiAnswer(q.questionId, answer)
                _uiState.update {
                    it.copy(
                        aiLoading = false,
                        aiModelAnswer = answer,
                        currentQuestion = it.currentQuestion?.copy(aiAnswer = answer)
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(aiLoading = false, aiError = e.message) }
            }
        }
    }

    fun onAnswerTabSelected(index: Int) = _uiState.update { it.copy(answerTabIndex = index) }

    fun toggleEditAiAnswer() = _uiState.update {
        it.copy(editingAiAnswer = true, aiAnswerDraft = it.currentQuestion?.aiAnswer ?: it.aiModelAnswer)
    }

    fun cancelEditAiAnswer() = _uiState.update { it.copy(editingAiAnswer = false, aiAnswerDraft = "") }

    fun updateAiAnswerDraft(text: String) = _uiState.update { it.copy(aiAnswerDraft = text) }

    fun saveAiAnswerScript() {
        val q = _uiState.value.currentQuestion ?: return
        val draft = _uiState.value.aiAnswerDraft
        viewModelScope.launch {
            questionDao.updateAiAnswer(q.questionId, draft)
            _uiState.update {
                it.copy(
                    editingAiAnswer = false,
                    aiAnswerDraft = "",
                    currentQuestion = it.currentQuestion?.copy(aiAnswer = draft)
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stop()
        sttManager.stopListening()
        recordingJob?.cancel()
        groupPlayJob?.cancel()
        positionPollingJob?.cancel()
    }
}
