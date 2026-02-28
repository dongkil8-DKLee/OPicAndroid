package com.opic.android.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.HomeButton
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.theme.OPicColors

@Composable
fun ReviewScreen(
    onHome: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingContent()
            state.results.isEmpty() -> EmptyContent(onHome)
            else -> ReviewContent(state, viewModel, onHome)
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("결과 로딩 중...")
        }
    }
}

@Composable
private fun EmptyContent(onHome: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("시험 결과가 없습니다.", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onHome) { Text("Home") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewContent(
    state: ReviewUiState,
    viewModel: ReviewViewModel,
    onHome: () -> Unit
) {
    val currentResult = state.results[state.currentIndex]
    val isPlaying = state.playingTarget != null

    Column(modifier = Modifier.fillMaxSize()) {

        // --- 상단: Question X of N ---
        Text(
            text = "Question ${state.currentIndex + 1} of ${state.totalQuestions}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
        )

        // --- 상단 고정 카드: question_text ---
        QuestionTextCard(
            questionText = currentResult.questionText,
            isPlaying = state.playingTarget == PlayTarget.QUESTION,
            canPlay = !isPlaying,
            onPlay = { viewModel.playQuestionAudio() },
            onStop = { viewModel.stopAudio() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // --- 중앙: 전체폭 스크립트 (스크롤) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // --- Answer Script ---
            ScriptSection(
                label = "Answer Script",
                scriptText = currentResult.answerScript,
                isPlaying = state.playingTarget == PlayTarget.ANSWER,
                canPlay = !isPlaying,
                onPlay = { viewModel.playAnswerAudio() },
                onStop = { viewModel.stopAudio() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- User Audio (인라인) ---
            UserAudioRow(
                isPlaying = state.playingTarget == PlayTarget.USER,
                hasAudio = state.hasUserAudio,
                canPlay = !isPlaying,
                onPlay = { viewModel.playUserAudio() },
                onStop = { viewModel.stopAudio() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- STT 분석 ---
            AnalysisSection(state = state)
        }

        // --- 하단: Navigation ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 번호 그리드 (1줄, 축소된 크기)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                maxItemsInEachRow = 15
            ) {
                for (i in 0 until state.totalQuestions) {
                    val bgColor = when {
                        i == state.currentIndex -> OPicColors.GridCurrent
                        else -> OPicColors.GridDefault
                    }
                    val textColor = when {
                        i == state.currentIndex -> Color.White
                        else -> Color.Black
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .border(1.dp, OPicColors.Border, RoundedCornerShape(4.dp))
                            .clickable(enabled = !isPlaying) { viewModel.goToQuestion(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${i + 1}",
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Back / Home / Next 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.onPrev() },
                    enabled = state.currentIndex > 0 && !isPlaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("< Back", fontWeight = FontWeight.Bold)
                }

                HomeButton(onClick = onHome)

                Button(
                    onClick = { viewModel.onNext() },
                    enabled = state.currentIndex < state.totalQuestions - 1 && !isPlaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Next >", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== User Audio 인라인 ====================

@Composable
private fun UserAudioRow(
    isPlaying: Boolean,
    hasAudio: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("User Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        if (isPlaying) {
            TextButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Stop", fontSize = 12.sp)
            }
        } else {
            TextButton(onClick = onPlay, enabled = hasAudio && canPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Play", fontSize = 12.sp)
            }
        }
    }
}

// ==================== 스크립트 섹션 (항상 표시, Edit 제거) ====================

@Composable
private fun ScriptSection(
    label: String,
    scriptText: String?,
    isPlaying: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            if (isPlaying) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Stop", fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = onPlay, enabled = canPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Play", fontSize = 12.sp)
                }
            }
        }

        // 스크립트 항상 표시
        if (!scriptText.isNullOrBlank()) {
            Text(
                text = scriptText,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        } else {
            Text(
                text = "스크립트 없음",
                color = OPicColors.DisabledBg,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ==================== 질문 텍스트 상단 고정 카드 ====================

@Composable
private fun QuestionTextCard(
    questionText: String?,
    isPlaying: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Question", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (isPlaying) {
                    TextButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" Stop", fontSize = 11.sp)
                    }
                } else {
                    TextButton(onClick = onPlay, enabled = canPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" Play", fontSize = 11.sp)
                    }
                }
            }
            Text(
                text = questionText ?: "질문 없음",
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 분석 섹션 (저장된 STT 결과 + 자동 분석) ====================

@Composable
private fun AnalysisSection(state: ReviewUiState) {
    val currentResult = state.results.getOrNull(state.currentIndex) ?: return
    val sttText = state.sttText
    val analysisResult = state.analysisResult

    if (sttText.isNullOrBlank() && analysisResult == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text("STT Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // STT Result
        if (!sttText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("STT:", fontSize = 12.sp, color = Color.Gray)
            Text(
                text = sttText,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3498DB), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )
        }

        // Analysis result
        if (analysisResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            SpeechAnalysisPanel(
                result = analysisResult,
                expectedText = currentResult.answerScript ?: "",
                actualText = sttText ?: "",
                fontSize = 14
            )
        }
    }
}
