package com.opic.android.ui.practice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.AudioFileResolver
import com.opic.android.audio.AudioPlayer
import com.opic.android.audio.AudioRecorder
import com.opic.android.audio.AudioSource
import com.opic.android.audio.DualPlaybackManager
import com.opic.android.audio.SttManager
import com.opic.android.audio.TtsManager
import com.opic.android.util.WavSampleReader
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.local.entity.VocabularyEntity
import com.opic.android.util.AnalysisResult
import com.opic.android.util.DictionaryApi
import com.opic.android.util.SentenceSegment
import com.opic.android.util.SentenceSplitter
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class SentenceStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class SentenceState(
    val segment: SentenceSegment,
    val status: SentenceStatus = SentenceStatus.NOT_STARTED,
    val userRecordingPath: String? = null,
    val sttText: String? = null,
    val analysisResult: AnalysisResult? = null
)

data class PracticeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val questionTitle: String = "",
    val questionId: Int = 0,
    val answerScript: String = "",
    val sentences: List<SentenceState> = emptyList(),
    val currentIndex: Int = 0,
    val currentSentenceText: String = "",
    val currentSegment: SentenceSegment? = null,
    val isPlayingOriginal: Boolean = false,
    val isPlayingUser: Boolean = false,
    val isRecording: Boolean = false,
    val micLevel: Float = 0f,
    val sttListening: Boolean = false,
    val isCombinedRecording: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val hasOriginalAudio: Boolean = false,
    val assetPath: com.opic.android.audio.AudioSource? = null,

    // 발화 연습 관련 상태
    val hasUserAudio: Boolean = false,
    // 문장별 UserScript 녹음 존재 여부 (key = sentenceIndex)
    val sentenceHasRecording: Map<Int, Boolean> = emptyMap(),
    val isRecordingUserScript: Boolean = false,
    val userScriptMicLevel: Float = 0f,
    val isPlayingUserAudio: Boolean = false,
    val userScriptSttListening: Boolean = false,
    val userScriptSttText: String? = null,
    val userScriptAnalysisResult: AnalysisResult? = null,

    // 단어 추가 피드백
    val wordAddedMessage: String? = null,

    // 파형 비교 + 동시 재생
    val originalWaveform: FloatArray = FloatArray(0),
    val userWaveform: FloatArray = FloatArray(0),
    val userAudioSilenceTrimMs: Long = 0L,
    val userAudioDurationMs: Long = 0L,
    val userStartFraction: Float = 0f,
    val isComparisonPlaying: Boolean = false,
    val comparisonOriginalProgress: Float = 0f,
    val comparisonUserProgress: Float = 0f,
    val comparisonBalance: Float = 0.5f,
    val comparisonSpeed: Float = 1.0f,
    // PLAY 단독 재생 진행률
    val userPlayProgress: Float = 0f,
    // 원본 단독 재생 진행률 (파형 윈도우 기준)
    val originalPlayProgress: Float = 0f,
    // 구간 반복 재생
    val isLoopPlaying: Boolean = false,

    // ─── 문장 경계 보정 ───────────────────────────────────────────────
    // 파형 확장 표시 범위: 구간 앞/뒤로 얼마나 더 보여줄지 (ms)
    // ★ 기본값 변경: expandBeforeMs / expandAfterMs 수정
    val expandBeforeMs: Long = 1000L,   // 구간 시작 앞쪽 확장 (기본 1초)
    val expandAfterMs:  Long = 1000L,   // 구간 끝   뒤쪽 확장 (기본 1초)
    // 문장별 시작/끝 조정 (드래그 저장값, ms 오프셋)
    val sentenceStartOffsets: Map<Int, Long> = emptyMap(),
    val sentenceEndOffsets:   Map<Int, Long> = emptyMap(),
    // 현재 로드된 파형의 실제 ms 범위 (마커↔ms 변환에 사용)
    val originalWaveformStartMs: Long = 0L,
    val originalWaveformEndMs:   Long = 0L,
    val showTimingPanel: Boolean = false,
    val showStatsPanel: Boolean = false
)

@HiltViewModel
class PracticeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val questionDao: QuestionDao,
    private val vocabularyDao: VocabularyDao,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val audioFileResolver: AudioFileResolver,
    private val ttsManager: TtsManager,
    private val sttManager: SttManager,
    private val dualPlaybackManager: DualPlaybackManager
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
    }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState

    private val recordingDir: File by lazy {
        File(context.getExternalFilesDir(null), "Recording").also { it.mkdirs() }
    }

    private val prefs by lazy {
        context.getSharedPreferences("practice_settings", android.content.Context.MODE_PRIVATE)
    }

    private var recordingJob: Job? = null
    private var userScriptRecordingJob: Job? = null
    private var isLoopActive = false

    init {
        val questionId = savedStateHandle.get<Int>("questionId") ?: 0
        // 저장된 전역 설정 복원
        // ★ 기본값: expand_before/after_ms → 여기서도 기본값 일치시킬 것
        val savedExpandBefore = prefs.getLong("expand_before_ms", 1000L)
        val savedExpandAfter  = prefs.getLong("expand_after_ms",  1000L)
        _uiState.update {
            it.copy(
                expandBeforeMs = savedExpandBefore,
                expandAfterMs  = savedExpandAfter
            )
        }
        if (questionId > 0) {
            loadQuestion(questionId)
        } else {
            _uiState.update { it.copy(loading = false, error = "Invalid question ID") }
        }
    }

    /** 문장별 UserScript 녹음 파일 경로 (고정 파일명, 재녹음 = 덮어쓰기) */
    private fun userRecordingPath(sentenceIndex: Int): String =
        File(recordingDir, "UserRec_${_uiState.value.questionId}_S${sentenceIndex}.wav").absolutePath

    private fun loadQuestion(questionId: Int) {
        viewModelScope.launch {
            try {
                val question = questionDao.getQuestionById(questionId)
                if (question == null) {
                    _uiState.update { it.copy(loading = false, error = "Question not found") }
                    return@launch
                }

                val answerScript = question.answerScript
                if (answerScript.isNullOrBlank()) {
                    _uiState.update { it.copy(loading = false, error = "No answer script available") }
                    return@launch
                }

                val assetPath = audioFileResolver.resolve(question.answerAudio)
                val totalDurationMs = if (assetPath != null) {
                    measureAudioDuration(assetPath)
                } else {
                    val wordCount = answerScript.split("\\s+".toRegex()).size
                    (wordCount * 500L).coerceAtLeast(3000L)
                }

                val segments = SentenceSplitter.split(answerScript, totalDurationMs)
                val sentenceStates = segments.map { SentenceState(segment = it) }

                // 문장별 UserScript 녹음 파일 존재 여부 스캔
                val hasRecording = sentenceStates.indices.associate { i ->
                    i to File(recordingDir, "UserRec_${questionId}_S${i}.wav").exists()
                }

                // 저장된 문장별 경계 오프셋 복원 (0인 경우 맵에서 제외하여 깔끔하게 유지)
                val savedStartOffsets = sentenceStates.indices
                    .associate { i -> i to prefs.getLong("start_off_${questionId}_$i", 0L) }
                    .filter { it.value != 0L }
                val savedEndOffsets = sentenceStates.indices
                    .associate { i -> i to prefs.getLong("end_off_${questionId}_$i", 0L) }
                    .filter { it.value != 0L }

                _uiState.update {
                    it.copy(
                        loading = false,
                        questionTitle = question.title,
                        questionId = questionId,
                        answerScript = answerScript,
                        sentences = sentenceStates,
                        currentIndex = 0,
                        currentSentenceText = sentenceStates.firstOrNull()?.segment?.text ?: "",
                        currentSegment = sentenceStates.firstOrNull()?.segment,
                        sentenceStartOffsets = savedStartOffsets,
                        sentenceEndOffsets   = savedEndOffsets,
                        hasOriginalAudio = assetPath != null,
                        assetPath = assetPath,
                        sentenceHasRecording = hasRecording,
                        hasUserAudio = hasRecording[0] == true
                    )
                }
                loadWaveforms() // 녹음 없어도 원본 파형 항상 로드
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load question", e)
                _uiState.update { it.copy(loading = false, error = "Load failed: ${e.message}") }
            }
        }
    }

    private suspend fun measureAudioDuration(source: com.opic.android.audio.AudioSource): Long = withContext(Dispatchers.IO) {
        var mp: MediaPlayer? = null
        try {
            mp = when (source) {
                is com.opic.android.audio.AudioSource.AssetPath -> {
                    val afd = context.assets.openFd(source.path)
                    MediaPlayer().apply {
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        prepare()
                    }
                }
                is com.opic.android.audio.AudioSource.FilePath -> {
                    MediaPlayer().apply {
                        setDataSource(source.path)
                        prepare()
                    }
                }
            }
            mp.duration.toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to measure duration: $source", e)
            10000L
        } finally {
            try { mp?.release() } catch (_: Exception) {}
        }
    }

    // ==================== 문장 이동 ====================

    fun goToSentence(index: Int) {
        val sentences = _uiState.value.sentences
        if (index < 0 || index >= sentences.size) return
        stopComparisonPlayback()
        stopAll()
        stopUserScriptAll()
        _uiState.update {
            it.copy(
                currentIndex = index,
                currentSentenceText = sentences[index].segment.text,
                currentSegment = sentences[index].segment,
                userScriptSttText = null,
                userScriptAnalysisResult = null,
                originalWaveform = FloatArray(0),
                userWaveform = FloatArray(0),
                originalWaveformStartMs = 0L,
                originalWaveformEndMs   = 0L,
                hasUserAudio = it.sentenceHasRecording[index] == true
            )
        }
        loadWaveforms()
    }

    fun nextSentence() {
        goToSentence(_uiState.value.currentIndex + 1)
    }

    fun prevSentence() {
        goToSentence(_uiState.value.currentIndex - 1)
    }

    // ==================== 원본 오디오 재생 ====================

    fun playOriginal() {
        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.isComparisonPlaying || state.isLoopPlaying) return
        val segment      = state.currentSegment ?: return
        val effectiveStart = effectiveSegmentStartMs(segment)
        val effectiveEnd   = effectiveSegmentEndMs(segment)

        _uiState.update { it.copy(isPlayingOriginal = true, originalPlayProgress = 0f) }
        markInProgressIfNeeded()

        // 비교 속도와 동일하게 원본도 재생
        audioPlayer.setSpeed(state.comparisonSpeed)

        val onComplete = {
            _uiState.update { it.copy(isPlayingOriginal = false, originalPlayProgress = 0f) }
        }

        when (val source = state.assetPath) {
            is com.opic.android.audio.AudioSource.AssetPath ->
                audioPlayer.playRangeFromAssets(source.path, effectiveStart, effectiveEnd, onComplete)
            is com.opic.android.audio.AudioSource.FilePath ->
                audioPlayer.playRangeFromFile(source.path, effectiveStart, effectiveEnd, onComplete)
            null -> viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(segment.text)
                if (ttsPath != null) {
                    audioPlayer.playFromFile(ttsPath, onComplete)
                } else {
                    _uiState.update { it.copy(isPlayingOriginal = false, originalPlayProgress = 0f) }
                }
            }
        }

        // 진행 바 폴링 (50ms 간격) — 파형 윈도우 기준으로 매핑
        viewModelScope.launch {
            while (_uiState.value.isPlayingOriginal) {
                val posMs = audioPlayer.currentPosition.toLong()
                val wStart = _uiState.value.originalWaveformStartMs
                val wEnd   = _uiState.value.originalWaveformEndMs
                val wDur   = (wEnd - wStart).toFloat()
                val progress = if (wDur > 0f) {
                    ((posMs - wStart).toFloat() / wDur).coerceIn(0f, 1f)
                } else 0f
                _uiState.update { it.copy(originalPlayProgress = progress) }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun stopOriginal() {
        audioPlayer.stop()
        _uiState.update { it.copy(isPlayingOriginal = false, originalPlayProgress = 0f) }
    }

    // ==================== 구간 반복 재생 ====================

    fun toggleLoopPlayback() {
        if (_uiState.value.isLoopPlaying) {
            stopLoopPlayback()
        } else {
            startLoopPlayback()
        }
    }

    fun stopLoopPlayback() {
        isLoopActive = false
        audioPlayer.stop()
        _uiState.update { it.copy(isLoopPlaying = false, originalPlayProgress = 0f) }
    }

    private fun startLoopPlayback() {
        val state = _uiState.value
        if (state.isLoopPlaying || state.isPlayingOriginal || state.isComparisonPlaying ||
            state.isRecordingUserScript || state.isPlayingUserAudio) return
        val segment = state.currentSegment ?: return
        if (state.assetPath == null) return   // TTS 소스는 루프 미지원

        isLoopActive = true
        _uiState.update { it.copy(isLoopPlaying = true, originalPlayProgress = 0f) }
        audioPlayer.setSpeed(state.comparisonSpeed)

        // 진행 바 폴링 (playOriginal과 동일한 방식)
        viewModelScope.launch {
            while (_uiState.value.isLoopPlaying) {
                val posMs = audioPlayer.currentPosition.toLong()
                val wStart = _uiState.value.originalWaveformStartMs
                val wEnd   = _uiState.value.originalWaveformEndMs
                val wDur   = (wEnd - wStart).toFloat()
                val progress = if (wDur > 0f) {
                    ((posMs - wStart).toFloat() / wDur).coerceIn(0f, 1f)
                } else 0f
                _uiState.update { it.copy(originalPlayProgress = progress) }
                kotlinx.coroutines.delay(50)
            }
        }

        playOneLoop()
    }

    /** 한 회 재생 완료 시 isLoopActive이면 다시 재생 (구간 경계는 매번 최신값 사용) */
    private fun playOneLoop() {
        if (!isLoopActive) {
            _uiState.update { it.copy(isLoopPlaying = false, originalPlayProgress = 0f) }
            return
        }
        val state = _uiState.value
        val segment = state.currentSegment ?: run {
            isLoopActive = false
            _uiState.update { it.copy(isLoopPlaying = false, originalPlayProgress = 0f) }
            return
        }
        val startMs = effectiveSegmentStartMs(segment)
        val endMs   = effectiveSegmentEndMs(segment)

        when (val source = state.assetPath) {
            is com.opic.android.audio.AudioSource.AssetPath ->
                audioPlayer.playRangeFromAssets(source.path, startMs, endMs) { playOneLoop() }
            is com.opic.android.audio.AudioSource.FilePath ->
                audioPlayer.playRangeFromFile(source.path, startMs, endMs) { playOneLoop() }
            null -> {
                isLoopActive = false
                _uiState.update { it.copy(isLoopPlaying = false, originalPlayProgress = 0f) }
            }
        }
    }

    // ==================== 녹음 재생 ====================

    fun playUserRecording() {
        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.isComparisonPlaying) return
        val idx = state.currentIndex
        val path = state.sentences.getOrNull(idx)?.userRecordingPath ?: return

        _uiState.update { it.copy(isPlayingUser = true) }
        audioPlayer.playFromFile(path) {
            _uiState.update { it.copy(isPlayingUser = false) }
        }
    }

    fun stopUserRecording() {
        audioPlayer.stop()
        _uiState.update { it.copy(isPlayingUser = false) }
    }

    // ==================== 녹음 ====================

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
            return
        }
        startRecording()
    }

    private fun startRecording() {
        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isComparisonPlaying) return
        val idx = state.currentIndex

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "Practice_${state.questionId}_S${idx}_$timestamp.wav"
        val outputFile = File(recordingDir, filename)

        _uiState.update { it.copy(isRecording = true, micLevel = 0f) }
        markInProgressIfNeeded()

        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            val valid = outputFile.exists() && outputFile.length() > 44
            _uiState.update { s ->
                val updated = s.sentences.toMutableList()
                if (idx < updated.size && valid) {
                    updated[idx] = updated[idx].copy(userRecordingPath = outputFile.absolutePath)
                }
                s.copy(
                    isRecording = false,
                    micLevel = 0f,
                    sentences = updated
                )
            }
            Log.d(TAG, "Recording done: ${outputFile.name} (${outputFile.length()} bytes)")
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
    }

    // ==================== STT ====================

    fun startStt() {
        val state = _uiState.value
        if (state.sttListening || state.isRecording) return

        _uiState.update { it.copy(sttListening = true) }
        markInProgressIfNeeded()

        sttManager.startListening(
            onResult = { text ->
                val idx = _uiState.value.currentIndex
                _uiState.update { s ->
                    val updated = s.sentences.toMutableList()
                    if (idx < updated.size) {
                        updated[idx] = updated[idx].copy(sttText = text)
                    }
                    s.copy(sttListening = false, sentences = updated)
                }
                Log.d(TAG, "STT result for sentence $idx: $text")
                autoAnalyzeAfterStt()
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

    /** STT 완료 시 자동 분석 */
    private fun autoAnalyzeAfterStt() {
        analyzeCurrentSentence()
    }

    // ==================== Rec+STT 통합 ====================

    fun startRecordAndStt() {
        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.sttListening) return
        val idx = state.currentIndex

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "Practice_${state.questionId}_S${idx}_$timestamp.wav"
        val outputFile = File(recordingDir, filename)

        _uiState.update { it.copy(isRecording = true, isCombinedRecording = true, micLevel = 0f) }
        markInProgressIfNeeded()

        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            val valid = outputFile.exists() && outputFile.length() > 44
            _uiState.update { s ->
                val updated = s.sentences.toMutableList()
                if (idx < updated.size && valid) {
                    updated[idx] = updated[idx].copy(userRecordingPath = outputFile.absolutePath)
                }
                s.copy(
                    isRecording = false,
                    isCombinedRecording = false,
                    micLevel = 0f,
                    sentences = updated
                )
            }
            Log.d(TAG, "Combined recording done: ${outputFile.name}")
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            sttManager.startListening(
                onResult = { text ->
                    val currentIdx = _uiState.value.currentIndex
                    _uiState.update { s ->
                        val updated = s.sentences.toMutableList()
                        if (currentIdx < updated.size) {
                            updated[currentIdx] = updated[currentIdx].copy(sttText = text)
                        }
                        s.copy(sttListening = false, sentences = updated)
                    }
                    Log.d(TAG, "Combined STT result for sentence $currentIdx: $text")
                    autoAnalyzeAfterStt()
                },
                onError = { error ->
                    _uiState.update { it.copy(sttListening = false) }
                    Log.w(TAG, "Combined STT failed (recording continues): $error")
                }
            )
        }
    }

    fun stopRecordAndStt() {
        audioRecorder.stop()
        sttManager.stopListening()
    }

    // ==================== 분석 ====================

    fun analyzeCurrentSentence() {
        val state = _uiState.value
        val idx = state.currentIndex
        val sentence = state.sentences.getOrNull(idx) ?: return
        val sttText = sentence.sttText ?: return
        val expectedText = sentence.segment.text
        if (sttText.isBlank() || expectedText.isBlank()) return

        val result = SpeechAnalyzer.analyze(expectedText, sttText)

        _uiState.update { s ->
            val updated = s.sentences.toMutableList()
            if (idx < updated.size) {
                updated[idx] = updated[idx].copy(
                    analysisResult = result,
                    status = SentenceStatus.COMPLETED
                )
            }
            s.copy(sentences = updated)
        }
        Log.d(TAG, "Analysis for sentence $idx: ${result.grade} (${result.accuracyPercent}%)")
    }

    // ==================== UserScript 기능 (Study에서 이동) ====================

    fun toggleUserScriptRecording() {
        if (_uiState.value.isRecordingUserScript) {
            stopUserScriptRecording()
            return
        }
        startUserScriptRecording()
    }

    private fun startUserScriptRecording() {
        val state = _uiState.value
        if (state.isComparisonPlaying) return
        val qId = state.questionId
        val idx = state.currentIndex
        if (qId <= 0) return

        // 문장별 고정 파일명: 재녹음 시 덮어쓰기
        val filename = "UserRec_${qId}_S${idx}.wav"
        val outputFile = File(recordingDir, filename)

        _uiState.update { it.copy(isRecordingUserScript = true, userScriptMicLevel = 0f) }

        userScriptRecordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(userScriptMicLevel = rmsLevel) }
            }
            val exists = outputFile.exists() && outputFile.length() > 44
            _uiState.update {
                it.copy(
                    isRecordingUserScript = false,
                    userScriptMicLevel = 0f,
                    hasUserAudio = exists,
                    sentenceHasRecording = it.sentenceHasRecording + (idx to exists)
                )
            }
            Log.d(TAG, "UserScript recording done: ${outputFile.name}")
            loadWaveforms()
        }
    }

    fun stopUserScriptRecording() {
        audioRecorder.stop()
    }

    fun playUserScriptAudio() {
        val state = _uiState.value
        val path = userRecordingPath(state.currentIndex).takeIf { File(it).exists() } ?: return
        if (state.isPlayingUserAudio || state.isComparisonPlaying || state.isLoopPlaying) return

        // userStartFraction 위치부터 재생 (동시재생과 동일한 시작점)
        val startMs = (state.userStartFraction * state.userAudioDurationMs).toLong().coerceAtLeast(0L)
        val initialProgress = if (state.userAudioDurationMs > 0) {
            (startMs.toFloat() / state.userAudioDurationMs).coerceIn(0f, 1f)
        } else 0f

        _uiState.update { it.copy(isPlayingUserAudio = true, userPlayProgress = initialProgress) }

        val onComplete = { _uiState.update { it.copy(isPlayingUserAudio = false, userPlayProgress = 0f) } }

        if (startMs > 0) {
            // endMs를 충분히 크게 설정 → 자연 완료(onCompletionListener)에 의존
            audioPlayer.playRangeFromFile(path, startMs, startMs + 3_600_000L, onComplete)
        } else {
            audioPlayer.playFromFile(path, onComplete)
        }

        // 진행 바 폴링 (절대 파일 위치 기준, 사용자 파형 전체와 동기화)
        viewModelScope.launch {
            while (_uiState.value.isPlayingUserAudio) {
                val pos = audioPlayer.currentPosition.toLong()
                val dur = if (state.userAudioDurationMs > 0) state.userAudioDurationMs
                          else audioPlayer.duration.toLong()
                if (dur > 0) {
                    _uiState.update { it.copy(userPlayProgress = (pos.toFloat() / dur).coerceIn(0f, 1f)) }
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun stopUserScriptAudio() {
        audioPlayer.stop()
        _uiState.update { it.copy(isPlayingUserAudio = false) }
    }

    fun startUserScriptStt() {
        val state = _uiState.value
        if (state.userScriptSttListening) return

        _uiState.update { it.copy(userScriptSttListening = true) }

        sttManager.startListening(
            onResult = { text ->
                _uiState.update { it.copy(userScriptSttText = text, userScriptSttListening = false) }
                Log.d(TAG, "UserScript STT result: $text")
                analyzeUserScript()
            },
            onError = { error ->
                _uiState.update { it.copy(userScriptSttListening = false) }
                Log.w(TAG, "UserScript STT error: $error")
            }
        )
    }

    fun stopUserScriptStt() {
        sttManager.stopListening()
    }

    /** 선택된 문장 기준으로 STT 결과 분석 → 문장 테이블 % 반영 */
    fun analyzeUserScript() {
        val state = _uiState.value
        val sttText = state.userScriptSttText ?: return
        val idx = state.currentIndex
        val sentence = state.sentences.getOrNull(idx) ?: return
        val expectedText = sentence.segment.text
        if (sttText.isBlank() || expectedText.isBlank()) return

        val result = SpeechAnalyzer.analyze(expectedText, sttText)
        _uiState.update { s ->
            val updated = s.sentences.toMutableList()
            if (idx < updated.size) {
                updated[idx] = updated[idx].copy(
                    analysisResult = result,
                    status = SentenceStatus.COMPLETED
                )
            }
            s.copy(
                userScriptAnalysisResult = result,
                sentences = updated
            )
        }
        Log.d(TAG, "UserScript analysis for sentence $idx: ${result.grade} (${result.accuracyPercent}%)")
    }

    // ==================== 누락 단어 → 단어장 추가 ====================

    fun addMissingWordToVocabulary(word: String) {
        viewModelScope.launch {
            try {
                val existing = vocabularyDao.getWordByText(word.lowercase())
                if (existing != null) {
                    _uiState.update { it.copy(wordAddedMessage = "'$word' 이미 단어장에 있습니다.") }
                    return@launch
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val pronunciation = DictionaryApi.fetchPronunciation(word)
                val entity = VocabularyEntity(
                    word = word.lowercase(),
                    pronunciation = pronunciation,
                    sourceQuestionId = _uiState.value.questionId,
                    createdAt = now
                )
                vocabularyDao.insertWord(entity)
                _uiState.update { it.copy(wordAddedMessage = "'$word' 단어장에 추가됨!") }
                Log.d(TAG, "Word added to vocabulary: $word")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add word: $word", e)
                _uiState.update { it.copy(wordAddedMessage = "단어 추가 실패") }
            }
        }
    }

    fun clearWordAddedMessage() {
        _uiState.update { it.copy(wordAddedMessage = null) }
    }

    // ==================== 속도 ====================

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    // ==================== 문장 경계 보정 ====================

    /** 실제 재생 시작 ms: segment.startMs + 문장별 시작 오프셋 */
    private fun effectiveSegmentStartMs(segment: com.opic.android.util.SentenceSegment): Long {
        val adjustment = _uiState.value.sentenceStartOffsets[segment.index] ?: 0L
        return (segment.startMs + adjustment).coerceAtLeast(0L)
    }

    /** 실제 재생 끝 ms: segment.endMs + 문장별 끝 오프셋 (endBufferMs 제거됨) */
    private fun effectiveSegmentEndMs(segment: com.opic.android.util.SentenceSegment): Long {
        val state = _uiState.value
        val adjustment      = state.sentenceEndOffsets[segment.index] ?: 0L
        val totalDurationMs = state.sentences.lastOrNull()?.segment?.endMs ?: segment.endMs
        return (segment.endMs + adjustment).coerceIn(segment.startMs + 100L, totalDurationMs)
    }

    // ── 파형 확장 범위 설정 ────────────────────────────────────────────
    // ★ UI 버튼 1회 클릭 = 500ms 변경 / 최대값 = 3000ms (PracticeScreen 쪽 참고)
    fun setExpandBefore(ms: Long) {
        val clamped = ms.coerceIn(0L, 3000L)
        prefs.edit().putLong("expand_before_ms", clamped).apply()
        _uiState.update { it.copy(expandBeforeMs = clamped) }
        loadWaveforms()   // 범위가 바뀌면 파형 재로드
    }

    fun setExpandAfter(ms: Long) {
        val clamped = ms.coerceIn(0L, 3000L)
        prefs.edit().putLong("expand_after_ms", clamped).apply()
        _uiState.update { it.copy(expandAfterMs = clamped) }
        loadWaveforms()
    }

    // ── 문장별 경계 드래그 저장 ──────────────────────────────────────
    // SharedPreferences 키: "start_off_{questionId}_{sentenceIndex}"
    fun setSentenceStartOffset(index: Int, offsetMs: Long) {
        val clamped    = offsetMs.coerceIn(-3000L, 3000L)
        val questionId = _uiState.value.questionId
        _uiState.update { it.copy(sentenceStartOffsets = it.sentenceStartOffsets + (index to clamped)) }
        prefs.edit().putLong("start_off_${questionId}_$index", clamped).apply()
    }

    fun setSentenceEndOffset(index: Int, offsetMs: Long) {
        val clamped    = offsetMs.coerceIn(-3000L, 3000L)
        val questionId = _uiState.value.questionId
        _uiState.update { it.copy(sentenceEndOffsets = it.sentenceEndOffsets + (index to clamped)) }
        prefs.edit().putLong("end_off_${questionId}_$index", clamped).apply()
    }

    fun toggleTimingPanel() {
        _uiState.update { it.copy(showTimingPanel = !it.showTimingPanel, showStatsPanel = false) }
    }

    fun toggleStatsPanel() {
        _uiState.update { it.copy(showStatsPanel = !it.showStatsPanel, showTimingPanel = false) }
    }

    // ==================== Utility ====================

    private fun markInProgressIfNeeded() {
        val idx = _uiState.value.currentIndex
        _uiState.update { s ->
            val updated = s.sentences.toMutableList()
            if (idx < updated.size && updated[idx].status == SentenceStatus.NOT_STARTED) {
                updated[idx] = updated[idx].copy(status = SentenceStatus.IN_PROGRESS)
            }
            s.copy(sentences = updated)
        }
    }

    private fun stopAll() {
        isLoopActive = false
        audioPlayer.stop()
        dualPlaybackManager.stop()
        if (_uiState.value.isRecording) audioRecorder.stop()
        if (_uiState.value.sttListening) sttManager.stopListening()
        _uiState.update {
            it.copy(
                isPlayingOriginal = false,
                isPlayingUser = false,
                isLoopPlaying = false,
                isRecording = false,
                micLevel = 0f,
                sttListening = false,
                isComparisonPlaying = false,
                comparisonOriginalProgress = 0f,
                comparisonUserProgress = 0f,
                originalPlayProgress = 0f
            )
        }
    }

    private fun stopUserScriptAll() {
        if (_uiState.value.isRecordingUserScript) audioRecorder.stop()
        if (_uiState.value.isPlayingUserAudio) audioPlayer.stop()
        if (_uiState.value.userScriptSttListening) sttManager.stopListening()
        _uiState.update {
            it.copy(
                isRecordingUserScript = false,
                userScriptMicLevel = 0f,
                isPlayingUserAudio = false,
                userScriptSttListening = false,
                isComparisonPlaying = false,
                comparisonOriginalProgress = 0f,
                comparisonUserProgress = 0f
            )
        }
    }

    // ==================== 파형 비교 + 동시 재생 ====================

    private fun loadWaveforms() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val segment = state.currentSegment

            // ── 원본 파형: 구간 앞뒤를 expandBefore/After 만큼 확장하여 로드 ──
            // ★ 표시 범위 조정: expandBeforeMs / expandAfterMs 값이 범위를 결정
            val waveformStartMs: Long
            val waveformEndMs: Long
            if (segment != null) {
                // 앞으로 확장 (0 미만이면 0으로 클램핑)
                waveformStartMs = (segment.startMs - state.expandBeforeMs).coerceAtLeast(0L)
                // 뒤로 확장
                waveformEndMs   = segment.endMs + state.expandAfterMs
            } else {
                waveformStartMs = 0L
                waveformEndMs   = 0L
            }

            val origWaveform = when (val source = state.assetPath) {
                is AudioSource.AssetPath -> WavSampleReader.readFromAssets(
                    context, source.path, 300,
                    if (segment != null) waveformStartMs else null,
                    if (segment != null) waveformEndMs   else null
                )
                is AudioSource.FilePath -> WavSampleReader.readFromFile(
                    source.path, 300,
                    if (segment != null) waveformStartMs else null,
                    if (segment != null) waveformEndMs   else null
                )
                null -> FloatArray(0)
            }

            // 사용자 녹음: 문장별 고정 파일명으로 로드
            val userPath = File(recordingDir, "UserRec_${state.questionId}_S${state.currentIndex}.wav")
                .takeIf { it.exists() }?.absolutePath
            val silenceTrimMs = if (userPath != null) WavSampleReader.detectLeadingSilenceMs(userPath) else 0L
            // 파형을 0부터 전체 로드 — fraction 좌표계를 totalDuration 기준으로 일치시킴.
            // 선행 묵음 구간은 파형에 평탄하게 표시되고, startMarker가 실제 음성 시작을 가리킴.
            // (이전: silenceTrimMs부터 로드 → 마커·진행바·드래그 좌표가 어긋나는 버그)
            val userWaveform = if (userPath != null) {
                WavSampleReader.readFromFile(userPath, 300)
            } else FloatArray(0)

            // 사용자 오디오 전체 길이 측정
            val userDurationMs: Long = if (userPath != null) {
                var mp: android.media.MediaPlayer? = null
                try {
                    mp = android.media.MediaPlayer().apply { setDataSource(userPath); prepare() }
                    mp.duration.toLong()
                } catch (e: Exception) { 0L }
                finally { try { mp?.release() } catch (_: Exception) {} }
            } else 0L

            val initialStartFraction = if (userDurationMs > 0 && silenceTrimMs > 0) {
                (silenceTrimMs.toFloat() / userDurationMs).coerceIn(0f, 0.5f)
            } else 0f

            _uiState.update {
                it.copy(
                    originalWaveform       = origWaveform,
                    userWaveform           = userWaveform,
                    userAudioSilenceTrimMs = silenceTrimMs,
                    userAudioDurationMs    = userDurationMs,
                    userStartFraction      = initialStartFraction,
                    hasUserAudio           = userPath != null,
                    // 마커↔ms 변환에 필요한 실제 파형 범위 저장
                    originalWaveformStartMs = waveformStartMs,
                    originalWaveformEndMs   = waveformEndMs
                )
            }
        }
    }

    fun toggleComparisonPlayback() {
        if (_uiState.value.isComparisonPlaying) {
            stopComparisonPlayback()
            return
        }

        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording ||
            state.isRecordingUserScript || state.isPlayingUserAudio) return

        val userPath = userRecordingPath(state.currentIndex).takeIf { File(it).exists() } ?: return
        val segment = state.currentSegment ?: return

        _uiState.update { it.copy(isComparisonPlaying = true, comparisonOriginalProgress = 0f, comparisonUserProgress = 0f) }

        val userStartMs = if (state.userAudioDurationMs > 0) {
            (state.userStartFraction * state.userAudioDurationMs).toLong()
        } else {
            state.userAudioSilenceTrimMs
        }

        dualPlaybackManager.playSimultaneous(
            originalSource = state.assetPath,
            originalStartMs = effectiveSegmentStartMs(segment),
            originalEndMs   = effectiveSegmentEndMs(segment),
            userFilePath = userPath,
            userStartMs = userStartMs,
            initialBalance = state.comparisonBalance,
            speed = state.comparisonSpeed,
            onPositionUpdate = { origProgress, userProgress ->
                _uiState.update {
                    it.copy(
                        comparisonOriginalProgress = origProgress,
                        comparisonUserProgress = userProgress
                    )
                }
            },
            onComplete = {
                _uiState.update {
                    it.copy(
                        isComparisonPlaying = false,
                        comparisonOriginalProgress = 0f,
                        comparisonUserProgress = 0f
                    )
                }
            }
        )
    }

    fun stopComparisonPlayback() {
        dualPlaybackManager.stop()
        _uiState.update {
            it.copy(
                isComparisonPlaying = false,
                comparisonOriginalProgress = 0f,
                comparisonUserProgress = 0f
            )
        }
    }

    fun setComparisonBalance(balance: Float) {
        _uiState.update { it.copy(comparisonBalance = balance) }
        dualPlaybackManager.setBalance(balance)
    }

    fun setComparisonSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 1.5f)
        _uiState.update { it.copy(comparisonSpeed = clamped) }
        if (_uiState.value.isComparisonPlaying) {
            dualPlaybackManager.setSpeed(clamped)
        }
    }

    fun setUserStartFraction(fraction: Float) {
        _uiState.update { it.copy(userStartFraction = fraction.coerceIn(0f, 1f)) }
    }

    // ==================== Auto Sync ====================

    /**
     * 사용자 녹음에서 음성 시작점을 자동 감지하여 userStartFraction을 설정.
     *
     * PCM 프레임 직접 스캔 방식 (기존 300포인트 다운샘플 파형 방식 대체):
     * - detectSpeechOnsetMs() 호출 → ms 단위 정밀 감지
     * - 선행 묵음(silenceTrimMs) 이후부터 스캔
     * - 지속 에너지(sustainWindowMs) 조건으로 노이즈 스파이크 제거
     *
     * ★ 감도 조절: thresholdPercent, sustainWindowMs, leadMs 파라미터
     */
    fun autoSyncUserStart() {
        val state     = _uiState.value
        val totalMs   = state.userAudioDurationMs
        val silenceMs = state.userAudioSilenceTrimMs
        if (totalMs <= 0L) return

        val userPath = userRecordingPath(state.currentIndex)
        if (!File(userPath).exists()) return

        viewModelScope.launch(Dispatchers.IO) {
            // PCM 직접 스캔 — ms 단위 정밀도
            val onsetMs = WavSampleReader.detectSpeechOnsetMs(
                filePath       = userPath,
                startOffsetMs  = silenceMs,
                thresholdPercent = 5f,   // ★ 감도 조절 포인트 (낮을수록 민감)
                sustainWindowMs  = 20L,  // ★ 지속 조건 (ms, 클수록 노이즈에 강함)
                leadMs           = 50L   // ★ 앞 여유 (ms, 클수록 시작이 더 당겨짐)
            )
            val fraction = (onsetMs.toFloat() / totalMs).coerceIn(0f, 0.9f)
            _uiState.update { it.copy(userStartFraction = fraction) }
            Log.d(TAG, "AutoSync PCM: onsetMs=$onsetMs, fraction=$fraction")
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        dualPlaybackManager.stop()
        audioRecorder.stop()
        sttManager.stopListening()
        recordingJob?.cancel()
        userScriptRecordingJob?.cancel()
    }
}
