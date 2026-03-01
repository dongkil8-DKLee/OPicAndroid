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
import com.opic.android.audio.SttManager
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.util.AnalysisResult
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
    val assetPath: com.opic.android.audio.AudioSource? = null
)

@HiltViewModel
class PracticeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val questionDao: QuestionDao,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val audioFileResolver: AudioFileResolver,
    private val ttsManager: TtsManager,
    private val sttManager: SttManager
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
    }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState

    private val recordingDir: File by lazy {
        File(context.getExternalFilesDir(null), "Recording").also { it.mkdirs() }
    }

    private var recordingJob: Job? = null

    init {
        val questionId = savedStateHandle.get<Int>("questionId") ?: 0
        if (questionId > 0) {
            loadQuestion(questionId)
        } else {
            _uiState.update { it.copy(loading = false, error = "Invalid question ID") }
        }
    }

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
                    // TTS fallback: 추정 시간 (단어 수 * 500ms)
                    val wordCount = answerScript.split("\\s+".toRegex()).size
                    (wordCount * 500L).coerceAtLeast(3000L)
                }

                val segments = SentenceSplitter.split(answerScript, totalDurationMs)
                val sentenceStates = segments.map { SentenceState(segment = it) }

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
                        hasOriginalAudio = assetPath != null,
                        assetPath = assetPath
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load question", e)
                _uiState.update { it.copy(loading = false, error = "Load failed: ${e.message}") }
            }
        }
    }

    /** 임시 MediaPlayer로 오디오 duration 측정 (asset 또는 외부 파일 모두 지원) */
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
            10000L // fallback 10s
        } finally {
            try { mp?.release() } catch (_: Exception) {}
        }
    }

    // ==================== 문장 이동 ====================

    fun goToSentence(index: Int) {
        val sentences = _uiState.value.sentences
        if (index < 0 || index >= sentences.size) return
        stopAll()
        _uiState.update {
            it.copy(
                currentIndex = index,
                currentSentenceText = sentences[index].segment.text,
                currentSegment = sentences[index].segment
            )
        }
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
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording) return
        val segment = state.currentSegment ?: return

        _uiState.update { it.copy(isPlayingOriginal = true) }

        // Mark sentence as IN_PROGRESS if NOT_STARTED
        markInProgressIfNeeded()

        when (val source = state.assetPath) {
            is com.opic.android.audio.AudioSource.AssetPath ->
                audioPlayer.playRangeFromAssets(source.path, segment.startMs, segment.endMs) {
                    _uiState.update { it.copy(isPlayingOriginal = false) }
                }
            is com.opic.android.audio.AudioSource.FilePath ->
                audioPlayer.playRangeFromFile(source.path, segment.startMs, segment.endMs) {
                    _uiState.update { it.copy(isPlayingOriginal = false) }
                }
            null -> viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(segment.text)
                if (ttsPath != null) {
                    audioPlayer.playFromFile(ttsPath) {
                        _uiState.update { it.copy(isPlayingOriginal = false) }
                    }
                } else {
                    _uiState.update { it.copy(isPlayingOriginal = false) }
                }
            }
        }
    }

    fun stopOriginal() {
        audioPlayer.stop()
        _uiState.update { it.copy(isPlayingOriginal = false) }
    }

    // ==================== 녹음 재생 ====================

    fun playUserRecording() {
        val state = _uiState.value
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording) return
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
        if (state.isPlayingOriginal || state.isPlayingUser) return
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
            // 녹음 완료
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

        // 1. 녹음 시작
        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            // 녹음 완료
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

        // 2. STT 동시 시작 (딜레이 후)
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

    // ==================== 속도 ====================

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
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
        audioPlayer.stop()
        if (_uiState.value.isRecording) audioRecorder.stop()
        if (_uiState.value.sttListening) sttManager.stopListening()
        _uiState.update {
            it.copy(
                isPlayingOriginal = false,
                isPlayingUser = false,
                isRecording = false,
                micLevel = 0f,
                sttListening = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stop()
        sttManager.stopListening()
        recordingJob?.cancel()
    }
}
