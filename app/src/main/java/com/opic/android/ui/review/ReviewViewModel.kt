package com.opic.android.ui.review

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.AudioFileResolver
import com.opic.android.audio.AudioPlayer
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.TestDao
import com.opic.android.data.local.dao.TestResultWithQuestion
import com.opic.android.util.AnalysisResult
import com.opic.android.util.SpeechAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 재생 대상 종류 */
enum class PlayTarget { QUESTION, ANSWER, USER }

data class ReviewUiState(
    val loading: Boolean = true,
    val results: List<TestResultWithQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val totalQuestions: Int = 0,

    // 오디오 재생
    val playingTarget: PlayTarget? = null,
    val hasUserAudio: Boolean = false,

    // STT + 분석 (저장된 결과만 표시)
    val sttText: String? = null,
    val showDiff: Boolean = false,
    val analysisResult: AnalysisResult? = null,

    // 분석 결과 맵 (인덱스별)
    val analysisResults: Map<Int, AnalysisResult> = emptyMap()
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testDao: TestDao,
    private val questionDao: QuestionDao,
    private val audioPlayer: AudioPlayer,
    private val audioFileResolver: AudioFileResolver,
    private val ttsManager: TtsManager
) : ViewModel() {

    companion object {
        private const val TAG = "ReviewViewModel"
    }

    private val sessionId: Int = savedStateHandle["sessionId"] ?: 0

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    init {
        loadSession()
    }

    // ==================== 데이터 로드 + 자동 분석 ====================

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val results = if (sessionId > 0) {
                    testDao.getSessionResults(sessionId)
                } else {
                    testDao.getLastSessionResults()
                }

                if (results.isEmpty()) {
                    Log.w(TAG, "세션 결과 없음 (sessionId=$sessionId)")
                    _uiState.update { it.copy(loading = false) }
                    return@launch
                }

                // 자동 분석: sttResult가 있는 항목은 바로 분석
                val analysisMap = mutableMapOf<Int, AnalysisResult>()
                results.forEachIndexed { index, result ->
                    if (!result.sttResult.isNullOrBlank() && !result.answerScript.isNullOrBlank()) {
                        analysisMap[index] = SpeechAnalyzer.analyze(result.answerScript, result.sttResult)
                    }
                }

                val firstAnalysis = analysisMap[0]

                Log.d(TAG, "리뷰 로드: sessionId=$sessionId, ${results.size}문제, ${analysisMap.size} 분석")
                _uiState.update {
                    it.copy(
                        loading = false,
                        results = results,
                        totalQuestions = results.size,
                        currentIndex = 0,
                        hasUserAudio = hasValidAudio(results[0].userAudioPath),
                        sttText = results[0].sttResult,
                        analysisResult = firstAnalysis,
                        analysisResults = analysisMap
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "리뷰 데이터 로드 실패", e)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ==================== 네비게이션 ====================

    fun goToQuestion(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.totalQuestions) return
        stopAudio()

        _uiState.update {
            it.copy(
                currentIndex = index,
                hasUserAudio = hasValidAudio(state.results[index].userAudioPath),
                sttText = state.results[index].sttResult,
                showDiff = false,
                analysisResult = state.analysisResults[index]
            )
        }
    }

    fun onPrev() {
        goToQuestion(_uiState.value.currentIndex - 1)
    }

    fun onNext() {
        goToQuestion(_uiState.value.currentIndex + 1)
    }

    // ==================== 오디오 재생 ====================

    fun playQuestionAudio() {
        val state = _uiState.value
        if (state.playingTarget != null) return
        val q = state.results.getOrNull(state.currentIndex) ?: return

        _uiState.update { it.copy(playingTarget = PlayTarget.QUESTION) }
        playAudioOrTts(q.questionAudio, q.questionText)
    }

    fun playAnswerAudio() {
        val state = _uiState.value
        if (state.playingTarget != null) return
        val q = state.results.getOrNull(state.currentIndex) ?: return

        _uiState.update { it.copy(playingTarget = PlayTarget.ANSWER) }
        playAudioOrTts(q.answerAudio, q.answerScript)
    }

    fun playUserAudio() {
        val state = _uiState.value
        if (state.playingTarget != null) return
        val q = state.results.getOrNull(state.currentIndex) ?: return

        val path = q.userAudioPath
        if (path == null || !File(path).exists()) {
            Log.w(TAG, "사용자 녹음 파일 없음")
            return
        }

        _uiState.update { it.copy(playingTarget = PlayTarget.USER) }
        audioPlayer.playFromFile(path) { onPlaybackFinished() }
    }

    fun stopAudio() {
        audioPlayer.stop()
        _uiState.update { it.copy(playingTarget = null) }
    }

    private fun playAudioOrTts(audioLink: String?, text: String?) {
        val assetPath = if (audioLink != null) audioFileResolver.resolve(audioLink) else null
        if (assetPath != null) {
            audioPlayer.playFromAssets(assetPath) { onPlaybackFinished() }
            return
        }

        if (!text.isNullOrBlank()) {
            viewModelScope.launch {
                val ttsPath = ttsManager.generateToFile(text)
                if (ttsPath != null) {
                    audioPlayer.playFromFile(ttsPath) { onPlaybackFinished() }
                } else {
                    onPlaybackFinished()
                }
            }
            return
        }

        onPlaybackFinished()
    }

    private fun onPlaybackFinished() {
        _uiState.update { it.copy(playingTarget = null) }
    }

    // ==================== Utility ====================

    fun toggleDiff() {
        _uiState.update { it.copy(showDiff = !it.showDiff) }
    }

    fun analyzeSpeech() {
        val state = _uiState.value
        val sttText = state.sttText ?: return
        val result = state.results.getOrNull(state.currentIndex) ?: return
        val answerScript = result.answerScript ?: return
        if (sttText.isBlank() || answerScript.isBlank()) return

        val analysis = SpeechAnalyzer.analyze(answerScript, sttText)
        val updatedMap = state.analysisResults.toMutableMap()
        updatedMap[state.currentIndex] = analysis
        _uiState.update { it.copy(analysisResult = analysis, analysisResults = updatedMap) }
    }

    private fun hasValidAudio(path: String?): Boolean {
        return path != null && File(path).exists()
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
