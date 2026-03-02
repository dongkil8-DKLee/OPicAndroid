package com.opic.android.ui.practice

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.common.WaveformComparisonPanel
import com.opic.android.ui.theme.OPicColors

/**
 * PracticeScreen (집중학습) - v2 리팩토링.
 * - AnimatedVisibility 제거 → 단순 if/else
 * - 문장 테이블 헤더: Play만 좌측 정렬 (Rec/STT 제거)
 * - 발화 연습 섹션: REC/PLAY/STT/확대만 (제목/Edit/UserText 제거)
 * - STT는 선택 문장 기준 평가, % 문장 테이블 반영
 * - Diff 항상 표시
 * - Back 버튼: BeginTestScreen 스타일
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 단어 추가 메시지 Toast
    LaunchedEffect(state.wordAddedMessage) {
        state.wordAddedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearWordAddedMessage()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("문장 분석 중...")
                    }
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            }
            else -> PracticeContent(state, viewModel, onBack)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeContent(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    onBack: () -> Unit
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    // 드래그 리사이즈: section1(문장연습) vs 나머지 비율
    var splitFraction by remember { mutableFloatStateOf(0.40f) }
    // 드래그 리사이즈: section2(발화연습) vs section3(음성비교) 비율 (나머지 공간 내)
    var innerSplitFraction by remember { mutableFloatStateOf(0.65f) }

    val showComparison = state.hasUserAudio && expandedSection == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 타이틀 네비게이터 =====
        PracticeTitleRow(state, viewModel)

        Spacer(modifier = Modifier.height(4.dp))

        if (expandedSection == null) {
            // ===== 드래그 리사이즈 모드 =====
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Column(modifier = Modifier.fillMaxSize()) {
                    // ===== 문장연습 섹션 =====
                    PracticeSentenceSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(splitFraction),
                        state = state,
                        viewModel = viewModel,
                        isExpanded = false,
                        onExpandToggle = { expandedSection = "practice" }
                    )

                    // ===== 드래그 핸들 1 =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    val maxFraction = if (showComparison) 0.75f else 0.85f
                                    splitFraction = (splitFraction + delta / totalHeightPx)
                                        .coerceIn(0.10f, maxFraction)
                                },
                                orientation = Orientation.Vertical
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                    }

                    if (showComparison) {
                        // ===== 발화 연습 섹션 (3분할) =====
                        UserScriptSection(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight((1f - splitFraction) * innerSplitFraction),
                            state = state,
                            viewModel = viewModel,
                            isExpanded = false,
                            onExpandToggle = { expandedSection = "userscript" }
                        )

                        // ===== 드래그 핸들 2 =====
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        innerSplitFraction = (innerSplitFraction + delta / (totalHeightPx * (1f - splitFraction)))
                                            .coerceIn(0.15f, 0.85f)
                                    },
                                    orientation = Orientation.Vertical
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            )
                        }

                        // ===== 음성 비교 패널 (3분할) =====
                        val isBusyForComparison = state.isPlayingOriginal || state.isPlayingUser ||
                                state.isRecording || state.isRecordingUserScript ||
                                state.isPlayingUserAudio || state.sttListening ||
                                state.userScriptSttListening
                        WaveformComparisonPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight((1f - splitFraction) * (1f - innerSplitFraction)),
                            originalWaveform = state.originalWaveform,
                            userWaveform = state.userWaveform,
                            isPlaying = state.isComparisonPlaying,
                            originalProgress = state.comparisonOriginalProgress,
                            userProgress = state.comparisonUserProgress,
                            balance = state.comparisonBalance,
                            enabled = !isBusyForComparison,
                            onTogglePlayback = { viewModel.toggleComparisonPlayback() },
                            onBalanceChange = { viewModel.setComparisonBalance(it) },
                            userStartFraction = state.userStartFraction,
                            onUserStartFractionChange = { viewModel.setUserStartFraction(it) },
                            userPlayProgress = state.userPlayProgress,
                            comparisonSpeed = state.comparisonSpeed,
                            onComparisonSpeedChange = { viewModel.setComparisonSpeed(it) },
                            isRecordingUser = state.isRecordingUserScript,
                            isPlayingUser = state.isPlayingUserAudio,
                            hasUserAudio = state.hasUserAudio,
                            onStartRecording = { viewModel.toggleUserScriptRecording() },
                            onStopRecording = { viewModel.stopUserScriptRecording() },
                            onPlayUser = { viewModel.playUserScriptAudio() },
                            onStopUser = { viewModel.stopUserScriptAudio() }
                        )
                    } else {
                        // ===== 발화 연습 섹션 (2분할) =====
                        UserScriptSection(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f - splitFraction),
                            state = state,
                            viewModel = viewModel,
                            isExpanded = false,
                            onExpandToggle = { expandedSection = "userscript" }
                        )
                    }
                }
            }
        } else {
            // ===== 전체화면 확대 모드 =====
            if (expandedSection == "practice") {
                PracticeSentenceSection(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = state,
                    viewModel = viewModel,
                    isExpanded = true,
                    onExpandToggle = { expandedSection = null }
                )
            } else {
                UserScriptSection(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = state,
                    viewModel = viewModel,
                    isExpanded = true,
                    onExpandToggle = { expandedSection = null }
                )
            }
        }
    }
}

// ==================== 타이틀 네비게이터 ====================

@Composable
private fun PracticeTitleRow(state: PracticeUiState, viewModel: PracticeViewModel) {
    val total = state.sentences.size
    val current = state.currentIndex

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { viewModel.prevSentence() },
            enabled = current > 0,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 문장")
        }

        Text(
            text = if (state.questionTitle.isNotBlank()) state.questionTitle else "—",
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { viewModel.nextSentence() },
            enabled = current < total - 1,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "다음 문장")
        }

        Text(
            text = "${current + 1}/$total",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ==================== 문장연습 섹션 ====================

@Composable
private fun PracticeSentenceSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val isBusy = state.isPlayingOriginal || state.isRecordingUserScript ||
            state.isPlayingUserAudio || state.userScriptSttListening || state.isComparisonPlaying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: Play만 좌측 정렬 + 확대/축소
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Stop (선택 문장 재생)
            if (state.isPlayingOriginal) {
                TextButton(onClick = { viewModel.stopOriginal() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Stop", fontSize = 11.sp)
                }
            } else {
                TextButton(
                    onClick = { viewModel.playOriginal() },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Play", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 문장 테이블 (내부 스크롤)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .border(1.dp, OPicColors.Border, RoundedCornerShape(4.dp))
        ) {
            state.sentences.forEachIndexed { index, sentenceState ->
                SentenceTableRow(
                    index = index,
                    text = sentenceState.segment.text,
                    isSelected = index == state.currentIndex,
                    accuracyPercent = sentenceState.analysisResult?.accuracyPercent?.toInt(),
                    onClick = { viewModel.goToSentence(index) }
                )
            }
        }
    }
}

@Composable
private fun SentenceTableRow(
    index: Int,
    text: String,
    isSelected: Boolean,
    accuracyPercent: Int?,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .border(width = 0.5.dp, color = OPicColors.Border)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelected) OPicColors.Primary else Color.Transparent)
                .border(
                    1.dp,
                    if (isSelected) OPicColors.Primary else OPicColors.Border,
                    RoundedCornerShape(3.dp)
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        if (accuracyPercent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${accuracyPercent}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    accuracyPercent >= 80 -> Color(0xFF2ECC71)
                    accuracyPercent >= 50 -> OPicColors.TimerOrange
                    else -> OPicColors.TimerRed
                }
            )
        }
    }
}

// ==================== 발화 연습 섹션 ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserScriptSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val isBusy = state.isRecordingUserScript || state.isPlayingUserAudio || state.userScriptSttListening || state.isComparisonPlaying
    val currentSentence = state.sentences.getOrNull(state.currentIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: STT + 확대/축소
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // STT
            if (state.userScriptSttListening) {
                TextButton(onClick = { viewModel.stopUserScriptStt() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop STT", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = { viewModel.startUserScriptStt() },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF3498DB))
                    Text(" STT", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 마이크 레벨 바
        if (state.isRecordingUserScript) {
            LinearProgressIndicator(
                progress = { state.userScriptMicLevel },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = OPicColors.TimerGreen,
                trackColor = OPicColors.Border,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (state.userScriptSttListening) {
            Text("Listening...", fontSize = 12.sp, color = OPicColors.RecordActive, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 내용 영역 (STT 결과 + 분석만, 편집 제거)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // STT 텍스트
            if (!state.userScriptSttText.isNullOrBlank()) {
                Text(
                    text = state.userScriptSttText,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF3498DB), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
            }

            // 분석 결과 (Diff 항상 표시, 선택 문장 기준)
            if (state.userScriptAnalysisResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SpeechAnalysisPanel(
                    result = state.userScriptAnalysisResult,
                    expectedText = currentSentence?.segment?.text ?: "",
                    actualText = state.userScriptSttText ?: ""
                )

                // 누락 단어 클릭 → 단어장 추가
                if (state.userScriptAnalysisResult.missingWords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("누락 단어 (탭하여 단어장 추가):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE74C3C))
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.userScriptAnalysisResult.missingWords.forEach { word ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE74C3C).copy(alpha = 0.12f),
                                modifier = Modifier.clickable {
                                    viewModel.addMissingWordToVocabulary(word)
                                }
                            ) {
                                Text(
                                    text = word,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE74C3C),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
