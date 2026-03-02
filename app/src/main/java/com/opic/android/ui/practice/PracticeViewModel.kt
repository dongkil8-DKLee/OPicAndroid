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
    val userAudioPath: String? = null,
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
    val comparisonBalance: Float = 0.5f
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

    private var recordingJob: Job? = null
    private var userScriptRecordingJob: Job? = null

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
                    val wordCount = answerScript.split("\\s+".toRegex()).size
                    (wordCount * 500L).coerceAtLeast(3000L)
                }

                val segments = SentenceSplitter.split(answerScript, totalDurationMs)
                val sentenceStates = segments.map { SentenceState(segment = it) }

                // UserScript 녹음 파일 검색
                val userAudio = findUserRecording(questionId)

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
                        assetPath = assetPath,
                        hasUserAudio = userAudio != null,
                        userAudioPath = userAudio
                    )
                }
                if (userAudio != null) loadWaveforms()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load question", e)
                _uiState.update { it.copy(loading = false, error = "Load failed: ${e.message}") }
            }
        }
    }

    private fun findUserRecording(questionId: Int): String? {
        if (!recordingDir.exists()) return null
        val pattern = "UserRec_${questionId}_"
        return recordingDir.listFiles()
            ?.filter { it.name.startsWith(pattern) && it.name.endsWith(".wav") }
            ?.maxByOrNull { it.name }
            ?.absolutePath
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
                userWaveform = FloatArray(0)
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
        if (state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.isComparisonPlaying) return
        val segment = state.currentSegment ?: return

        _uiState.update { it.copy(isPlayingOriginal = true) }
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

                // STT 완료 시 자동 분석
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
        if (qId <= 0) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "UserRec_${qId}_$timestamp.wav"
        val outputFile = File(recordingDir, filename)

        _uiState.update { it.copy(isRecordingUserScript = true, userScriptMicLevel = 0f) }

        userScriptRecordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(userScriptMicLevel = rmsLevel) }
            }
            _uiState.update {
                it.copy(
                    isRecordingUserScript = false,
                    userScriptMicLevel = 0f,
                    hasUserAudio = outputFile.exists() && outputFile.length() > 44,
                    userAudioPath = outputFile.absolutePath
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
        val path = state.userAudioPath ?: return
        if (state.isPlayingUserAudio || state.isComparisonPlaying) return

        _uiState.update { it.copy(isPlayingUserAudio = true) }
        // PLAY 버튼은 항상 처음(0)부터 재생, userStartFraction은 동시재생에만 적용
        audioPlayer.playFromFile(path) {
            _uiState.update { it.copy(isPlayingUserAudio = false) }
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

                // 자동 분석
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
        dualPlaybackManager.stop()
        if (_uiState.value.isRecording) audioRecorder.stop()
        if (_uiState.value.sttListening) sttManager.stopListening()
        _uiState.update {
            it.copy(
                isPlayingOriginal = false,
                isPlayingUser = false,
                isRecording = false,
                micLevel = 0f,
                sttListening = false,
                isComparisonPlaying = false,
                comparisonOriginalProgress = 0f,
                comparisonUserProgress = 0f
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

            // 원본 파형 로드 (WAV 직접 파싱 → MP3 등은 MediaCodec 디코딩)
            val origWaveform = when (val source = state.assetPath) {
                is AudioSource.AssetPath -> {
                    WavSampleReader.readFromAssets(
                        context, source.path, 300,
                        segment?.startMs, segment?.endMs
                    )
                }
                is AudioSource.FilePath -> {
                    WavSampleReader.readFromFile(
                        source.path, 300,
                        segment?.startMs, segment?.endMs
                    )
                }
                null -> FloatArray(0)
            }

            // 사용자 녹음: 선행 묵음 감지 → 트리밍하여 파형 로드
            val userPath = state.userAudioPath
            val silenceTrimMs = if (userPath != null) {
                WavSampleReader.detectLeadingSilenceMs(userPath)
            } else 0L

            val userWaveform = if (userPath != null) {
                WavSampleReader.readFromFile(
                    userPath, 300,
                    startMs = if (silenceTrimMs > 0) silenceTrimMs else null
                )
            } else FloatArray(0)

            // 사용자 오디오 전체 길이 측정
            val userDurationMs: Long = if (userPath != null) {
                var mp: android.media.MediaPlayer? = null
                try {
                    mp = android.media.MediaPlayer().apply {
                        setDataSource(userPath)
                        prepare()
                    }
                    mp.duration.toLong()
                } catch (e: Exception) { 0L }
                finally { try { mp?.release() } catch (_: Exception) {} }
            } else 0L

            // 초기 시작 위치: 자동 감지된 묵음 끝 지점
            val initialStartFraction = if (userDurationMs > 0 && silenceTrimMs > 0) {
                (silenceTrimMs.toFloat() / userDurationMs).coerceIn(0f, 0.5f)
            } else 0f

            _uiState.update {
                it.copy(
                    originalWaveform = origWaveform,
                    userWaveform = userWaveform,
                    userAudioSilenceTrimMs = silenceTrimMs,
                    userAudioDurationMs = userDurationMs,
                    userStartFraction = initialStartFraction
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

        val userPath = state.userAudioPath ?: return
        val segment = state.currentSegment ?: return

        _uiState.update { it.copy(isComparisonPlaying = true, comparisonOriginalProgress = 0f, comparisonUserProgress = 0f) }

        val userStartMs = if (state.userAudioDurationMs > 0) {
            (state.userStartFraction * state.userAudioDurationMs).toLong()
        } else {
            state.userAudioSilenceTrimMs
        }

        dualPlaybackManager.playSimultaneous(
            originalSource = state.assetPath,
            originalStartMs = segment.startMs,
            originalEndMs = segment.endMs,
            userFilePath = userPath,
            userStartMs = userStartMs,
            initialBalance = state.comparisonBalance,
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

    fun setUserStartFraction(fraction: Float) {
        _uiState.update { it.copy(userStartFraction = fraction.coerceIn(0f, 1f)) }
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
