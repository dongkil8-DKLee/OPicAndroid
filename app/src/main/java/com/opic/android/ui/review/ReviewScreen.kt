package com.opic.android.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.theme.OPicColors

@Composable
fun ReviewScreen(
    onNavigateToStudy: (type: String?, set: String?, title: String?) -> Unit = { _, _, _ -> },
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingContent()
            state.results.isEmpty() -> EmptyContent()
            else -> ReviewContent(state, viewModel, onNavigateToStudy = onNavigateToStudy)
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
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("시험 결과가 없습니다.", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewContent(
    state: ReviewUiState,
    viewModel: ReviewViewModel,
    onNavigateToStudy: (type: String?, set: String?, title: String?) -> Unit
) {
    val currentResult = state.results[state.currentIndex]
    val isPlaying = state.playingTarget != null

    Column(modifier = Modifier.fillMaxSize()) {

        // --- 상단: Question X of N | ▶ Play | Spacer | Study 🔗 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Question ${state.currentIndex + 1} of ${state.totalQuestions}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Play All / Stop 버튼 (텍스트 바로 오른쪽)
            if (state.playAllActive) {
                IconButton(onClick = { viewModel.togglePlayAll() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop All", tint = OPicColors.RecordActive)
                }
            } else {
                IconButton(
                    onClick = { viewModel.togglePlayAll() },
                    enabled = !isPlaying,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter            = androidx.compose.ui.res.painterResource(com.opic.android.R.drawable.ic_group_play),
                        contentDescription = "Play All",
                        tint               = Color.Unspecified,
                        modifier           = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Study 바로가기 버튼
            TextButton(
                onClick = { onNavigateToStudy(currentResult.type, currentResult.set, currentResult.title) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Study 🔗",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OPicColors.Primary
                )
            }
        }

        // Play All 상태 텍스트
        if (state.playAllActive && state.playAllStatus.isNotBlank()) {
            Text(
                text = state.playAllStatus,
                fontSize = 12.sp,
                color = OPicColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

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

        // --- 중앙: 비율 고정, 내부 스크롤 ---
        val hasAnalysis = !state.sttText.isNullOrBlank() || state.analysisResult != null
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            // --- Answer Script (내부 스크롤) ---
            ScriptSection(
                modifier = Modifier.weight(if (hasAnalysis) 2f else 1f),
                label = "Answer Script",
                scriptText = currentResult.answerScript,
                isPlaying = state.playingTarget == PlayTarget.ANSWER,
                canPlay = !isPlaying,
                onPlay = { viewModel.playAnswerAudio() },
                onStop = { viewModel.stopAudio() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- User Audio (인라인, 고정 높이) ---
            UserAudioRow(
                isPlaying = state.playingTarget == PlayTarget.USER,
                hasAudio = state.hasUserAudio,
                canPlay = !isPlaying,
                onPlay = { viewModel.playUserAudio() },
                onStop = { viewModel.stopAudio() }
            )

            // --- STT 분석 (내부 스크롤, 조건부) ---
            if (hasAnalysis) {
                Spacer(modifier = Modifier.height(8.dp))
                AnalysisSection(modifier = Modifier.weight(1f), state = state)
            }
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
                Icon(
                    painter            = androidx.compose.ui.res.painterResource(com.opic.android.R.drawable.ic_rec_play),
                    contentDescription = null,
                    tint               = Color.Unspecified,
                    modifier           = Modifier.size(20.dp)
                )
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
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더 고정
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

        // 텍스트 내부 스크롤
        if (!scriptText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = scriptText,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
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
private fun AnalysisSection(state: ReviewUiState, modifier: Modifier = Modifier) {
    val currentResult = state.results.getOrNull(state.currentIndex) ?: return
    val sttText = state.sttText
    val analysisResult = state.analysisResult

    if (sttText.isNullOrBlank() && analysisResult == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더 고정
        Text("STT Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // 내용 내부 스크롤
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
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
}
