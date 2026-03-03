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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
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
    // 드래그 리사이즈: section2(음성비교) vs section3(발화연습) 비율 (나머지 공간 내)
    // 초기값 0.85f → UserScript 최소 크기로 시작
    var innerSplitFraction by remember { mutableFloatStateOf(0.85f) }

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
                                    splitFraction = (splitFraction + delta / totalHeightPx)
                                        .coerceIn(0.10f, 0.75f)
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

                    // ===== 음성 비교 패널 (항상 표시) =====
                    val isBusyForComparison = state.isPlayingOriginal || state.isPlayingUser ||
                            state.isRecording || state.isRecordingUserScript ||
                            state.isPlayingUserAudio || state.sttListening ||
                            state.userScriptSttListening

                    // ── 원본 파형 마커 위치 계산 (ms → fraction) ──────────────
                    // originalWaveformStartMs/EndMs: loadWaveforms() 에서 저장된 파형 범위
                    val waveformDurationMs = (state.originalWaveformEndMs - state.originalWaveformStartMs).toFloat()
                    val currentSeg = state.currentSegment
                    // 시작 마커: segment.startMs + startOffset → 파형 내 fraction
                    val segStartMarker = if (currentSeg != null && waveformDurationMs > 0f) {
                        val effectiveStartMs = currentSeg.startMs + (state.sentenceStartOffsets[state.currentIndex] ?: 0L)
                        ((effectiveStartMs - state.originalWaveformStartMs) / waveformDurationMs).coerceIn(0f, 0.95f)
                    } else 0f
                    // 끝 마커: segment.endMs + endOffset → 파형 내 fraction
                    val segEndMarker = if (currentSeg != null && waveformDurationMs > 0f) {
                        val effectiveEndMs = currentSeg.endMs + (state.sentenceEndOffsets[state.currentIndex] ?: 0L)
                        ((effectiveEndMs - state.originalWaveformStartMs) / waveformDurationMs).coerceIn(0.05f, 1f)
                    } else 1f

                    WaveformComparisonPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight((1f - splitFraction) * innerSplitFraction),
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
                        // ── 원본 파형 경계 마커 (주황=시작, 빨강=끝) ──────────────
                        // ★ 마커 드래그 → fraction → ms 변환 후 ViewModel에 저장
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
                        // 원본 단독 재생
                        isPlayingOriginal = state.isPlayingOriginal,
                        onPlayOriginal = { viewModel.playOriginal() },
                        onStopOriginal = { viewModel.stopOriginal() },
                        originalPlayProgress = state.originalPlayProgress,
                        onAutoSync = if (state.hasUserAudio) { { viewModel.autoSyncUserStart() } } else null
                    )

                    // ===== 드래그 핸들 2 =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    innerSplitFraction = (innerSplitFraction + delta / (totalHeightPx * (1f - splitFraction)))
                                        .coerceIn(0.15f, 0.92f)
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

                    // ===== 발화 연습 섹션 (하단, 초기 최소 크기) =====
                    UserScriptSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight((1f - splitFraction) * (1f - innerSplitFraction)),
                        state = state,
                        viewModel = viewModel,
                        isExpanded = false,
                        onExpandToggle = { expandedSection = "userscript" }
                    )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: 통계 + ±타이밍 + 확대/축소 (Play 버튼은 WaveformComparisonPanel으로 이동)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 학습 통계 토글
            TextButton(
                onClick = { viewModel.toggleStatsPanel() },
                modifier = Modifier.padding(horizontal = 0.dp)
            ) {
                Text(
                    "문장 학습",
                    fontSize = 11.sp,
                    color = if (state.showStatsPanel) OPicColors.Primary else Color.Gray,
                    fontWeight = if (state.showStatsPanel) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 타이밍 보정 토글
            TextButton(
                onClick = { viewModel.toggleTimingPanel() },
                modifier = Modifier.padding(horizontal = 0.dp)
            ) {
                Text(
                    " ± 타이밍",
                    fontSize = 11.sp,
                    color = if (state.showTimingPanel) OPicColors.Primary else Color.Gray,
                    fontWeight = if (state.showTimingPanel) FontWeight.Bold else FontWeight.Normal
                )
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

        // 타이밍 보정 패널 (토글)
        if (state.showTimingPanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // ── 파형 표시 확장 범위: 앞/뒤 ±500ms 단위 조절 ──────────
                // ★ 1회 클릭 단위(500L)와 최대값(3000L)은 ViewModel.setExpandBefore/After에서 변경
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("파형 확장", fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    // 앞
                    Text("앞", fontSize = 10.sp, color = Color.Gray)
                    TextButton(onClick = { viewModel.setExpandBefore(state.expandBeforeMs - 500L) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("−", fontSize = 13.sp, color = OPicColors.TimerRed)
                    }
                    Text("${state.expandBeforeMs}ms", fontSize = 10.sp, color = OPicColors.Primary,
                        modifier = Modifier.width(44.dp))
                    TextButton(onClick = { viewModel.setExpandBefore(state.expandBeforeMs + 500L) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("+", fontSize = 13.sp, color = OPicColors.TimerGreen)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 뒤
                    Text("뒤", fontSize = 10.sp, color = Color.Gray)
                    TextButton(onClick = { viewModel.setExpandAfter(state.expandAfterMs - 500L) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("−", fontSize = 13.sp, color = OPicColors.TimerRed)
                    }
                    Text("${state.expandAfterMs}ms", fontSize = 10.sp, color = OPicColors.Primary,
                        modifier = Modifier.width(44.dp))
                    TextButton(onClick = { viewModel.setExpandAfter(state.expandAfterMs + 500L) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("+", fontSize = 13.sp, color = OPicColors.TimerGreen)
                    }
                }
            }
        }

        // 학습 통계 패널 (토글)
        if (state.showStatsPanel) {
            SessionStatsPanel(state = state)
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
            .background(Color(0xFFF0F7FF), RoundedCornerShape(6.dp))
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

        // 내용 영역 (STT 결과 + 분석만)
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
