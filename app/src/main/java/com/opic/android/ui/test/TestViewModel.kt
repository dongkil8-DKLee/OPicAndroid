package com.opic.android.ui.test

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.AudioFileResolver
import com.opic.android.audio.AudioPlayer
import com.opic.android.audio.AudioRecorder
import com.opic.android.audio.SttManager
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.TestDao
import com.opic.android.data.local.entity.TestResultEntity
import com.opic.android.data.local.entity.TestSessionEntity
import com.opic.android.domain.QuestionGenerator
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

enum class TestPhase {
    LOADING,     // 문제 생성 중
    INITIAL,     // 문제 로드됨, Play만 활성
    PLAYING,     // 음성 재생 중
    BEEP_WAIT,   // Beep 후 대기
    RECORDING,   // 녹음 중
    RECORDED,    // 녹음 완료, Next 활성
    FINISHED     // 마지막 문제 완료 → Review 전환
}

data class TestUiState(
    val phase: TestPhase = TestPhase.LOADING,
    val currentIndex: Int = 0,
    val totalQuestions: Int = 0,
    val countdownSeconds: Int = 120,
    val micLevel: Float = 0f,
    val questions: List<QuestionGenerator.TestQuestion> = emptyList(),
    val answeredIndices: Set<Int> = emptySet(), // 녹음 완료된 문제 번호
    val canStop: Boolean = false // 녹음 5초 후 Stop 가능
)

/**
 * Python TestScreen 상태 머신 1:1 이식.
 *
 * INITIAL → Play → PLAYING → 재생완료 → Beep → RECORDING → Stop/120s → RECORDED → Next → 반복
 */
@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val questionGenerator: QuestionGenerator,
    private val testDao: TestDao,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val audioFileResolver: AudioFileResolver,
    private val ttsManager: TtsManager,
    private val sttManager: SttManager
) : ViewModel() {

    companion object {
        private const val TAG = "TestViewModel"
    }

    private val difficulty: Int = savedStateHandle["difficulty"] ?: 3

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState

    var sessionId: Int = -1
        private set
    private var recordingJob: Job? = null
    private var countdownJob: Job? = null

    private val recordingDir: File by lazy {
        File(context.getExternalFilesDir(null), "Recording").also { it.mkdirs() }
    }

    init {
        generateTest()
    }

    // ==================== 문제 생성 + 세션 저장 ====================

    private fun generateTest() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = TestPhase.LOADING) }
            val questions = questionGenerator.generate(difficulty)
            if (questions.isEmpty()) {
                Log.e(TAG, "문제 생성 실패")
                return@launch
            }

            // DB 세션 생성 (수동 ID 채번 — PK autoGenerate 미사용)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val nextSessionId = testDao.getMaxSessionId() + 1
            testDao.insertSession(
                TestSessionEntity(sessionId = nextSessionId, userId = 1, timestamp = timestamp)
            )
            sessionId = nextSessionId

            // DB 결과 행 생성 (수동 ID 채번)
            var nextResultId = testDao.getMaxResultId() + 1
            questions.forEachIndexed { index, q ->
                testDao.insertResult(TestResultEntity(
                    resultId = nextResultId++,
                    sessionId = sessionId,
                    questionId = q.questionId,
                    questionNumber = index + 1,
                    userAudioPath = null,
                    similarityScore = null,
                    sttResult = null
                ))
            }

            Log.d(TAG, "세션 $sessionId 생성, ${questions.size}문제")
            _uiState.update {
                it.copy(
                    phase = TestPhase.INITIAL,
                    questions = questions,
                    totalQuestions = questions.size,
                    currentIndex = 0
                )
            }
        }
    }

    // ==================== Play ====================

    fun onPlay() {
        val state = _uiState.value
        if (state.phase != TestPhase.INITIAL) return

        _uiState.update { it.copy(phase = TestPhase.PLAYING) }
        val q = state.questions[state.currentIndex]

        // 오디오 파일 검색 → TTS 폴백
        val assetPath = audioFileResolver.resolve(q.questionAudio)
        if (assetPath != null) {
            audioPlayer.playFromAssets(assetPath) { onPlaybackFinished() }
        } else if (!q.questionText.isNullOrBlank()) {
            // TTS 폴백
            viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(q.questionText)
                if (ttsPath != null) {
                    audioPlayer.playFromFile(ttsPath) { onPlaybackFinished() }
                } else {
                    onPlaybackFinished()
                }
            }
        } else {
            onPlaybackFinished()
        }
    }

    private fun onPlaybackFinished() {
        _uiState.update { it.copy(phase = TestPhase.BEEP_WAIT) }
        // Beep → 150ms 대기 → 자동 녹음 시작
        viewModelScope.launch {
            try {
                // beep.wav가 assets/Sound에 있으면 재생
                val beepPath = audioFileResolver.resolve("beep")
                if (beepPath != null) {
                    audioPlayer.playFromAssets(beepPath) {
                        viewModelScope.launch {
                            delay(150)
                            startRecording()
                        }
                    }
                } else {
                    delay(300)
                    startRecording()
                }
            } catch (_: Exception) {
                delay(300)
                startRecording()
            }
        }
    }

    // ==================== Recording ====================

    private fun startRecording() {
        val state = _uiState.value
        val q = state.questions[state.currentIndex]

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "TestRec_${q.questionId}_$timestamp.wav"
        val outputFile = File(recordingDir, filename)

        // user_audio_path 업데이트
        _uiState.value.questions[state.currentIndex].userAudioPath = outputFile.absolutePath

        _uiState.update {
            it.copy(phase = TestPhase.RECORDING, countdownSeconds = 120, micLevel = 0f, canStop = false)
        }

        // 5초 후 Stop 버튼 활성화
        viewModelScope.launch {
            delay(5000)
            if (_uiState.value.phase == TestPhase.RECORDING) {
                _uiState.update { it.copy(canStop = true) }
            }
        }

        // 카운트다운 타이머
        countdownJob = viewModelScope.launch {
            for (sec in 119 downTo 0) {
                delay(1000)
                if (_uiState.value.phase != TestPhase.RECORDING) break
                _uiState.update { it.copy(countdownSeconds = sec) }
            }
        }

        // 녹음 시작
        recordingJob = viewModelScope.launch {
            audioRecorder.record(outputFile) { rmsLevel ->
                _uiState.update { it.copy(micLevel = rmsLevel) }
            }
            // 녹음 종료 후 (stop 또는 120초)
            onRecordingFinished(outputFile)
        }

        // STT 동시 실행 (딜레이 후)
        viewModelScope.launch {
            delay(500)
            sttManager.startListening(
                onResult = { text ->
                    // DB에 STT 결과 바로 저장
                    viewModelScope.launch {
                        try {
                            testDao.updateSttResultByQuestion(sessionId, q.questionId, text)
                            Log.d(TAG, "STT 자동 저장: questionId=${q.questionId}")
                        } catch (e: Exception) {
                            Log.e(TAG, "STT 저장 실패", e)
                        }
                    }
                },
                onError = { error ->
                    // STT 실패해도 녹음은 계속
                    Log.w(TAG, "Test STT failed (recording continues): $error")
                }
            )
        }
    }

    fun onStopRecording() {
        if (_uiState.value.phase != TestPhase.RECORDING) return
        audioRecorder.stop()
        sttManager.stopListening()
    }

    private fun onRecordingFinished(outputFile: File) {
        countdownJob?.cancel()
        val state = _uiState.value
        val q = state.questions[state.currentIndex]

        // DB에 녹음 파일 경로 저장
        viewModelScope.launch {
            try {
                testDao.updateAudioPath(sessionId, q.questionId, outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "DB 녹음 경로 저장 실패", e)
            }
        }

        _uiState.update {
            it.copy(
                phase = TestPhase.RECORDED,
                micLevel = 0f,
                canStop = false,
                answeredIndices = it.answeredIndices + it.currentIndex
            )
        }
    }

    // ==================== Next ====================

    fun onNext() {
        val state = _uiState.value

        // 녹음 중이면 먼저 정지
        if (state.phase == TestPhase.RECORDING) {
            audioRecorder.stop()
            // onRecordingFinished가 호출된 후 자동 진행
            viewModelScope.launch {
                // 녹음 종료 대기
                while (_uiState.value.phase == TestPhase.RECORDING) delay(50)
                proceedToNext()
            }
            return
        }

        if (state.phase == TestPhase.RECORDED) {
            proceedToNext()
        }
    }

    private fun proceedToNext() {
        val state = _uiState.value
        if (state.currentIndex >= state.totalQuestions - 1) {
            _uiState.update { it.copy(phase = TestPhase.FINISHED) }
            return
        }

        val nextIndex = state.currentIndex + 1
        _uiState.update {
            it.copy(
                phase = TestPhase.INITIAL,
                currentIndex = nextIndex,
                countdownSeconds = 120,
                micLevel = 0f
            )
        }

        // 자동 재생 (900ms 후)
        viewModelScope.launch {
            delay(900)
            onPlay()
        }
    }

    // ==================== Home ====================

    fun onHome() {
        audioPlayer.stop()
        audioRecorder.stop()
        recordingJob?.cancel()
        countdownJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stop()
        recordingJob?.cancel()
        countdownJob?.cancel()
    }
}
