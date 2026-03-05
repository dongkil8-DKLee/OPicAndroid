package com.opic.android.ui.practice

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.common.WaveformComparisonPanel
import com.opic.android.ui.common.filter.FilterPanel
import com.opic.android.ui.common.filter.StudyFilterState
import com.opic.android.ui.theme.OPicColors

/**
 * PracticeScreen (집중학습) - v3.
 * - Play 버튼: PracticeSentenceSection → WaveformComparisonPanel으로 이동
 * - endBufferMs 슬라이더 제거
 * - 문장별 독립 녹음 (🎤 아이콘으로 녹음 존재 표시)
 * - originalPlayProgress 파형 진행 바 연동
 * - 속도: ± 버튼 (0.5~1.5)
 * - 원본 파형: 핀치 줌 + 잠금 버튼
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
    onNavigateToQuestion: (Int) -> Unit = {},
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val state       by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterController.state.collectAsState()
    val context     = LocalContext.current

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
            else -> PracticeContent(state, filterState, viewModel, onBack, onNavigateToQuestion)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeContent(
    state: PracticeUiState,
    filterState: StudyFilterState,
    viewModel: PracticeViewModel,
    onBack: () -> Unit,
    onNavigateToQuestion: (Int) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 타이틀 네비게이터 =====
        PracticeTitleRow(state, filterState, viewModel, onNavigateToQuestion)

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 문장연습 섹션 (나머지 공간 전부 차지) =====
        PracticeSentenceSection(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = state,
            viewModel = viewModel
        )

        // ===== 탭 패널 (내용 높이에 맞게 자동 조정) =====
        val isBusyForComparison = state.isPlayingOriginal || state.isPlayingUser ||
                state.isRecording || state.isRecordingUserScript ||
                state.isPlayingUserAudio || state.sttListening ||
                state.userScriptSttListening

        val waveformDurationMs = (state.originalWaveformEndMs - state.originalWaveformStartMs).toFloat()
        val currentSeg = state.currentSegment
        val segStartMarker = if (currentSeg != null && waveformDurationMs > 0f) {
            val effectiveStartMs = currentSeg.startMs + (state.sentenceStartOffsets[state.currentIndex] ?: 0L)
            ((effectiveStartMs - state.originalWaveformStartMs) / waveformDurationMs).coerceIn(0f, 0.95f)
        } else 0f
        val segEndMarker = if (currentSeg != null && waveformDurationMs > 0f) {
            val effectiveEndMs = currentSeg.endMs + (state.sentenceEndOffsets[state.currentIndex] ?: 0L)
            ((effectiveEndMs - state.originalWaveformStartMs) / waveformDurationMs).coerceIn(0.05f, 1f)
        } else 1f

        Column(modifier = Modifier.fillMaxWidth()) {
            // 탭 행
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("발음 학습", "문장 학습").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) OPicColors.Primary else Color.Gray
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(0.6f)
                                    .height(2.dp)
                                    .background(OPicColors.Primary)
                            )
                        }
                    }
                }
            }

            // 탭 컨텐츠
            if (selectedTab == 0) {
                WaveformComparisonPanel(
                    modifier = Modifier.fillMaxWidth(),
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
                    segmentStartMarker = segStartMarker,
                    segmentEndMarker   = segEndMarker,
                    onSegmentStartMarkerChange = if (currentSeg != null && waveformDurationMs > 0f) { fraction ->
                        val absoluteMs = state.originalWaveformStartMs + (fraction * waveformDurationMs).toLong()
                        val offsetMs   = absoluteMs - currentSeg.startMs
                        viewModel.setSentenceStartOffset(state.currentIndex, offsetMs)
                    } else null,
                    onSegmentEndMarkerChange = if (currentSeg != null && waveformDurationMs > 0f) { fraction ->
                        val absoluteMs = state.originalWaveformStartMs + (fraction * waveformDurationMs).toLong()
                        val offsetMs   = absoluteMs - currentSeg.endMs
                        viewModel.setSentenceEndOffset(state.currentIndex, offsetMs)
                    } else null,
                    isRecordingUser = state.isRecordingUserScript,
                    isPlayingUser = state.isPlayingUserAudio,
                    hasUserAudio = state.hasUserAudio,
                    onStartRecording = { viewModel.toggleUserScriptRecording() },
                    onStopRecording = { viewModel.stopUserScriptRecording() },
                    onPlayUser = { viewModel.playUserScriptAudio() },
                    onStopUser = { viewModel.stopUserScriptAudio() },
                    isPlayingOriginal = state.isPlayingOriginal,
                    onPlayOriginal = { viewModel.playOriginal() },
                    onStopOriginal = { viewModel.stopOriginal() },
                    originalPlayProgress = state.originalPlayProgress,
                    onAutoSync = if (state.hasUserAudio) { { viewModel.autoSyncUserStart() } } else null,
                    isLoopPlaying = state.isLoopPlaying,
                    onToggleLoop = { viewModel.toggleLoopPlayback() },
                    isTimingModeEnabled = state.showTimingPanel,
                    onToggleTimingMode = { viewModel.toggleTimingPanel() },
                    expandBeforeMs = state.expandBeforeMs,
                    expandAfterMs = state.expandAfterMs,
                    onExpandBeforeChange = { viewModel.setExpandBefore(it) },
                    onExpandAfterChange = { viewModel.setExpandAfter(it) }
                )
            } else {
                UserScriptSection(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }

    // ===== AI STT 피드백 다이얼로그 =====
    if (state.showAiDialog) {
        val aiScrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = { viewModel.dismissAiDialog() },
            title = { Text("AI STT 피드백", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.heightIn(min = 80.dp, max = 360.dp)) {
                    when {
                        state.aiLoading -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Claude AI 피드백 생성 중...", fontSize = 13.sp, color = Color.Gray)
                        }
                        state.aiError != null -> Text(
                            text = state.aiError,
                            color = OPicColors.TimerRed,
                            fontSize = 13.sp
                        )
                        else -> Text(
                            text = state.aiSttFeedback,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.verticalScroll(aiScrollState)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAiDialog() }) { Text("닫기") }
            }
        )
    }
}

// ==================== 타이틀 네비게이터 (Study 스타일) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeTitleRow(
    state: PracticeUiState,
    filterState: StudyFilterState,
    viewModel: PracticeViewModel,
    onNavigateToQuestion: (Int) -> Unit = {}
) {
    val qList  = filterState.practiceQuestionList
    val qTotal = qList.size
    val qIndex = qList.indexOfFirst { it.first == state.questionId }

    var showSheet by remember { mutableStateOf(false) }

    Column {
        // ── 메인 Row: [<문제] [제목▾] [>문제] N/M [필터] ──────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.prevQuestionId()?.let { onNavigateToQuestion(it) } },
                enabled = qIndex > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 문제")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OPicColors.LightBg)
                    .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
                    .clickable { if (qList.isNotEmpty()) showSheet = true },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = if (state.questionTitle.isNotBlank()) state.questionTitle else "—",
                        fontSize   = 12.sp,
                        color      = OPicColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                    Text("▾", fontSize = 10.sp, color = OPicColors.TextOnLight.copy(alpha = 0.45f))
                }
            }

            IconButton(
                onClick = { viewModel.nextQuestionId()?.let { onNavigateToQuestion(it) } },
                enabled = qIndex >= 0 && qIndex < qTotal - 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "다음 문제")
            }

            Text(
                text     = if (qTotal > 0 && qIndex >= 0) "${qIndex + 1}/$qTotal" else "—",
                fontSize = 12.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(start = 2.dp)
            )

            IconButton(
                onClick  = { viewModel.togglePracticeFilterPanel() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "필터",
                    tint = if (state.showPracticeFilterPanel) OPicColors.Primary else Color.Gray
                )
            }
        }

        // ── FilterPanel (Study와 동일 UI, AnimatedVisibility) ─────────
        AnimatedVisibility(
            visible = state.showPracticeFilterPanel,
            enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit    = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                FilterPanel(
                    state                = filterState,
                    onSetChanged         = { viewModel.onSetChanged(it) },
                    onTypeChanged        = { viewModel.onTypeChanged(it) },
                    onSortChanged        = { viewModel.onSortChanged(it) },
                    onStudyFilterChanged = { viewModel.onStudyFilterChanged(it) },
                    showSort             = false
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    // ── 문제 선택 BottomSheet ─────────────────────────────────────────
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor   = OPicColors.Surface,
            tonalElevation   = 0.dp
        ) {
            Text(
                text       = "문제 선택",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = OPicColors.TextOnLight,
                modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = OPicColors.Border)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(qList) { (qId, qTitle) ->
                    val isSelected = qId == state.questionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSheet = false; if (!isSelected) onNavigateToQuestion(qId) }
                            .background(if (isSelected) OPicColors.Primary.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = qTitle,
                            fontSize   = 14.sp,
                            color      = if (isSelected) OPicColors.Primary else OPicColors.TextOnLight,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier   = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OPicColors.Primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(color = OPicColors.Border.copy(alpha = 0.5f))
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// ==================== 문장연습 섹션 ====================

@Composable
private fun PracticeSentenceSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 학습 통계 패널 (항상 표시)
        SessionStatsPanel(state = state)

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
                    startOffset = state.sentenceStartOffsets[index] ?: 0L,
                    endOffset   = state.sentenceEndOffsets[index]   ?: 0L,
                    hasRecording = state.sentenceHasRecording[index] == true,
                    onClick = { viewModel.goToSentence(index) }
                )
            }
        }
    }
}

// ==================== 인세션 학습 통계 패널 ====================

@Composable
private fun SessionStatsPanel(state: PracticeUiState) {
    val sentences = state.sentences
    val total = sentences.size
    if (total == 0) return

    // 완료된 문장 수 (COMPLETED 상태)
    val completedCount = sentences.count { it.status == SentenceStatus.COMPLETED }
    // 녹음이 있는 문장 수
    val recordedCount = state.sentenceHasRecording.values.count { it }
    // 정확도가 있는 문장들
    val withAccuracy = sentences.mapIndexedNotNull { idx, s ->
        s.analysisResult?.accuracyPercent?.let { idx to it }
    }
    val avgAccuracy = if (withAccuracy.isNotEmpty()) {
        withAccuracy.map { it.second }.average().toInt()
    } else null
    // 취약 문장 TOP 3 (정확도 낮은 순)
    val weakSentences = withAccuracy
        .sortedBy { it.second }
        .take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OPicColors.LightBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // ── 요약 수치 Row ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 완료율
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$completedCount/$total",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OPicColors.Primary
                )
                Text("완료", fontSize = 9.sp, color = Color.Gray)
            }
            // 녹음
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$recordedCount/$total",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OPicColors.TimerGreen
                )
                Text("녹음", fontSize = 9.sp, color = Color.Gray)
            }
            // 평균 정확도
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (avgAccuracy != null) "$avgAccuracy%" else "—",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        avgAccuracy == null -> Color.Gray
                        avgAccuracy >= 80 -> Color(0xFF2ECC71)
                        avgAccuracy >= 50 -> OPicColors.TimerOrange
                        else -> OPicColors.TimerRed
                    }
                )
                Text("평균정확도", fontSize = 9.sp, color = Color.Gray)
            }
        }

        // ── 취약 문장 TOP 3 ────────────────────────────────────────
        if (weakSentences.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("취약 문장", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            weakSentences.forEach { (idx, acc) ->
                val text = sentences.getOrNull(idx)?.segment?.text ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}.",
                        fontSize = 10.sp,
                        color = OPicColors.TimerRed,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = text,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$acc%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            acc >= 80 -> Color(0xFF2ECC71)
                            acc >= 50 -> OPicColors.TimerOrange
                            else -> OPicColors.TimerRed
                        }
                    )
                }
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
    // 파형 마커로 저장된 경계 조정값 (0이면 미표시, 비영이면 색상 강조)
    startOffset: Long = 0L,
    endOffset: Long = 0L,
    // 이 문장에 UserScript 녹음이 있는지
    hasRecording: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) OPicColors.LightBg else Color.Transparent
    val hasOffset = startOffset != 0L || endOffset != 0L

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

        // 녹음 존재 표시
        if (hasRecording) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.Mic,
                contentDescription = "녹음 있음",
                modifier = Modifier.size(12.dp),
                tint = OPicColors.TimerGreen
            )
        }

        // 경계 조정값 표시 (드래그 마커로 저장된 경우)
        if (hasOffset) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "✎",
                fontSize = 10.sp,
                color = OPicColors.Primary
            )
        }

        if (accuracyPercent != null) {
            Spacer(modifier = Modifier.width(4.dp))
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
    viewModel: PracticeViewModel
) {
    val isBusy = state.isRecordingUserScript || state.isPlayingUserAudio || state.userScriptSttListening || state.isComparisonPlaying
    val currentSentence = state.sentences.getOrNull(state.currentIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: STT + AI 피드백
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.userScriptSttListening) {
                TextButton(onClick = { viewModel.stopUserScriptStt() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = { viewModel.startUserScriptStt() },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (!isBusy) OPicColors.RecordActive else Color.Gray)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val hasStt = !state.userScriptSttText.isNullOrBlank()
            IconButton(
                onClick = { viewModel.generateSttFeedback() },
                enabled = hasStt && !state.aiLoading,
                modifier = Modifier.size(32.dp)
            ) {
                if (state.aiLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI 피드백",
                        tint = if (hasStt) OPicColors.LevelGauge else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
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

        // 내용 영역 (STT 결과 + 분석만)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 220.dp)
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
