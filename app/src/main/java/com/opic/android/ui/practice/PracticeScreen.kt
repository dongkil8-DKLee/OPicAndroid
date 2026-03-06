package com.opic.android.ui.practice

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * PracticeScreen (집중학습) - v4.
 * - 3섹션: PracticeSentenceSection / 드래그핸들 / 탭(발음학습|문장학습)
 * - 공유 하단 버튼 행: ▶ ↺ 🎤 ⭕ [↔/✨ 카드뒤집기]
 * - 발음 탭: WaveformComparisonPanel (버튼 없음), 🎤=녹음, ↔=동시재생
 * - 문장 탭: UserScriptSection (STT 결과), 🎤=STT, ✨=AI 피드백
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
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

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    filterState: StudyFilterState,
    viewModel: PracticeViewModel,
    onBack: () -> Unit,
    onNavigateToQuestion: (Int) -> Unit = {}
) {
    var selectedTab   by remember { mutableStateOf(0) }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ★ 화면 비율 조정 포인트 ★
    //   splitFraction = 상단(문장 섹션) 높이 비율
    //   1 - splitFraction = 하단(탭 패널) 높이 비율
    //
    //   예) 0.45f → 문장 45%, 탭 55%
    //       0.35f → 문장 35%, 탭 65%  (탭 공간 더 넓게)
    //       0.55f → 문장 55%, 탭 45%  (문장 목록 더 넓게)
    //
    //   드래그 허용 범위: coerceIn(0.20f, 0.75f)
    //     → 아래 draggableState 람다 안의 coerceIn 값을 바꾸면 됨
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    var splitFraction by remember { mutableStateOf(0.45f) }   // ← 초기 비율 여기서 변경

    // WaveformComparisonPanel에 전달할 값 사전 계산
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 타이틀 네비게이터 =====
        PracticeTitleRow(state, filterState, viewModel, onNavigateToQuestion)

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 드래그 리사이즈 영역 (splitFraction: sentence / tab panel) =====
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val density = LocalDensity.current
            val totalHeightPx = with(density) { maxHeight.toPx() }
            val draggableState = rememberDraggableState { delta ->
                // ← 드래그 허용 범위: coerceIn(최소비율, 최대비율) 변경으로 조정
                splitFraction = (splitFraction + delta / totalHeightPx).coerceIn(0.20f, 0.75f)
            }

            Column(modifier = Modifier.fillMaxSize()) {

                // ── 문장연습 섹션 ──────────────────────────────────────
                PracticeSentenceSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(splitFraction),
                    state = state,
                    viewModel = viewModel
                )

                // ── 드래그 핸들 ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OPicColors.Border)
                    )
                }

                // ── 탭 + 컨텐츠 ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - splitFraction)
                ) {
                    // 탭 행
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (selectedTab == 0) {
                            WaveformComparisonPanel(
                                modifier = Modifier.fillMaxWidth(),
                                showButtonRow = false,
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
                                segmentEndMarker = segEndMarker,
                                onSegmentStartMarkerChange = if (currentSeg != null && waveformDurationMs > 0f) { fraction ->
                                    val absoluteMs = state.originalWaveformStartMs + (fraction * waveformDurationMs).toLong()
                                    val offsetMs = absoluteMs - currentSeg.startMs
                                    viewModel.setSentenceStartOffset(state.currentIndex, offsetMs)
                                } else null,
                                onSegmentEndMarkerChange = if (currentSeg != null && waveformDurationMs > 0f) { fraction ->
                                    val absoluteMs = state.originalWaveformStartMs + (fraction * waveformDurationMs).toLong()
                                    val offsetMs = absoluteMs - currentSeg.endMs
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
                                modifier = Modifier.fillMaxWidth(),
                                state = state,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 공유 하단 버튼 행 =====
        PracticeSharedButtonRow(
            selectedTab = selectedTab,
            state = state,
            viewModel = viewModel
        )
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

// ==================== 공유 하단 버튼 행 ====================

/**
 * 발음/문장 탭 공통 하단 버튼 행.
 * - 발음 탭 (selectedTab=0): ▶원본 ↺루프 🎤녹음 ⭕녹음재생 ↔동시재생
 * - 문장 탭 (selectedTab=1): ▶원본 ↺루프 🎤STT  ⭕녹음재생 ✨AI (카드뒤집기)
 */
@Composable
private fun PracticeSharedButtonRow(
    selectedTab: Int,
    state: PracticeUiState,
    viewModel: PracticeViewModel
) {
    // 카드 뒤집기 애니메이션 (↔ ↔ ✨)
    val rotation by animateFloatAsState(
        targetValue = if (selectedTab == 0) 0f else 180f,
        animationSpec = tween(durationMillis = 300),
        label = "tabFlip"
    )
    val isFlipped = rotation >= 90f

    val isBusy = state.isComparisonPlaying || state.isPlayingOriginal || state.isLoopPlaying ||
            state.isRecordingUserScript || state.isPlayingUserAudio || state.userScriptSttListening

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ★ 버튼 간격 조정 포인트 ★
    //   모든 버튼 슬롯을 동일한 Box(size(SLOT))로 감싸서 위치 고정
    //
    //   전체 간격 조절: horizontalArrangement = Arrangement.SpaceEvenly
    //     → Arrangement.spacedBy(N.dp) 로 바꾸면 고정 간격 설정 가능
    //   버튼 슬롯 크기: val BUTTON_SLOT = 48.dp ← 이 값 변경
    //   아이콘 크기:    val ICON_SIZE   = 28.dp ← 이 값 변경
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    val BUTTON_SLOT = 48.dp   // ← 슬롯 크기 (모든 버튼에 동일 적용)
    val ICON_SIZE   = 28.dp   // ← 아이콘 크기

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OPicColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,  // ← 간격 방식 변경 포인트
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── ▶/⏹ 원본재생 ──────────────────────────────────────────────
        Box(modifier = Modifier.size(BUTTON_SLOT), contentAlignment = Alignment.Center) {
            if (state.isPlayingOriginal) {
                IconButton(onClick = { viewModel.stopOriginal() }, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.Stop, contentDescription = "원본 정지",
                        modifier = Modifier.size(ICON_SIZE))
                }
            } else {
                IconButton(
                    onClick = { viewModel.playOriginal() },
                    enabled = !state.isComparisonPlaying && !state.isLoopPlaying && !state.isRecordingUserScript,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "원본 재생",
                        modifier = Modifier.size(ICON_SIZE))
                }
            }
        }

        // ── ↺/⏹ 구간반복 ──────────────────────────────────────────────
        Box(modifier = Modifier.size(BUTTON_SLOT), contentAlignment = Alignment.Center) {
            if (state.isLoopPlaying) {
                IconButton(onClick = { viewModel.toggleLoopPlayback() }, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.Stop, contentDescription = "구간 정지",
                        tint = OPicColors.TimerRed, modifier = Modifier.size(ICON_SIZE))
                }
            } else {
                IconButton(
                    onClick = { viewModel.toggleLoopPlayback() },
                    enabled = !state.isComparisonPlaying && !state.isPlayingOriginal &&
                            !state.isPlayingUserAudio && !state.isRecordingUserScript,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Filled.Repeat, contentDescription = "구간 반복",
                        modifier = Modifier.size(ICON_SIZE))
                }
            }
        }

        // ── 🎤/⏹ 녹음(tab0) or STT(tab1) ─────────────────────────────
        Box(modifier = Modifier.size(BUTTON_SLOT), contentAlignment = Alignment.Center) {
            if (selectedTab == 0) {
                val recEnabled = !state.isPlayingUserAudio && !state.isComparisonPlaying &&
                        !state.isPlayingOriginal && !state.isLoopPlaying
                IconButton(
                    onClick = {
                        if (state.isRecordingUserScript) viewModel.stopUserScriptRecording()
                        else viewModel.toggleUserScriptRecording()
                    },
                    enabled = state.isRecordingUserScript || recEnabled,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (state.isRecordingUserScript) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (state.isRecordingUserScript) "녹음 중지" else "녹음",
                        tint = if (state.isRecordingUserScript || recEnabled) OPicColors.RecordActive else Color.Gray,
                        modifier = Modifier.size(ICON_SIZE)
                    )
                }
            } else {
                val sttEnabled = !state.isRecordingUserScript && !state.isComparisonPlaying &&
                        !state.isPlayingOriginal && !state.isLoopPlaying
                if (state.userScriptSttListening) {
                    IconButton(onClick = { viewModel.stopUserScriptStt() }, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Stop, contentDescription = "STT 정지",
                            tint = OPicColors.RecordActive, modifier = Modifier.size(ICON_SIZE))
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.startUserScriptStt() },
                        enabled = sttEnabled,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "STT 시작",
                            tint = if (sttEnabled) OPicColors.RecordActive else Color.Gray,
                            modifier = Modifier.size(ICON_SIZE))
                    }
                }
            }
        }

        // ── ⭕/⏹ 녹음재생 (원형 outline 스타일) ──────────────────────
        Box(modifier = Modifier.size(BUTTON_SLOT), contentAlignment = Alignment.Center) {
            val isUserPlayEnabled = state.hasUserAudio && !state.isRecordingUserScript &&
                    !state.isComparisonPlaying && !state.isPlayingOriginal && !state.isLoopPlaying
            val circleColor = when {
                state.isPlayingUserAudio -> OPicColors.RecordActive
                isUserPlayEnabled        -> OPicColors.PlayButton
                else                     -> Color.Gray
            }
            Box(
                modifier = Modifier
                    .size(ICON_SIZE + 4.dp)   // ← 원 크기: ICON_SIZE보다 약간 크게
                    .border(2.dp, circleColor, CircleShape)
                    .clickable(enabled = state.isPlayingUserAudio || isUserPlayEnabled) {
                        if (state.isPlayingUserAudio) viewModel.stopUserScriptAudio()
                        else viewModel.playUserScriptAudio()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlayingUserAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlayingUserAudio) "녹음 정지" else "녹음 재생",
                    tint = circleColor,
                    modifier = Modifier.size(ICON_SIZE)
                )
            }
        }

        // ── ↔/✨ 카드뒤집기 (tab0=동시재생, tab1=AI 피드백) ─────────────
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .size(BUTTON_SLOT)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density.density
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isFlipped) {
                // 앞면: ↔ 동시재생
                if (state.isComparisonPlaying) {
                    IconButton(
                        onClick = { viewModel.toggleComparisonPlayback() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "동시 정지",
                            tint = OPicColors.RecordActive, modifier = Modifier.size(28.dp))
                    }
                } else {
                    val compareEnabled = state.userWaveform.isNotEmpty() && !state.isPlayingOriginal &&
                            !state.isLoopPlaying && !state.isRecordingUserScript && !state.isPlayingUserAudio
                    IconButton(
                        onClick = { viewModel.toggleComparisonPlayback() },
                        enabled = compareEnabled,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "동시 재생",
                            tint = if (compareEnabled) OPicColors.TextOnLight else Color.Gray,
                            modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                // 뒷면: ✨ AI 피드백 (counter-rotate to stay readable)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
                    contentAlignment = Alignment.Center
                ) {
                    val hasStt = !state.userScriptSttText.isNullOrBlank()
                    if (state.aiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { viewModel.generateSttFeedback() },
                            enabled = hasStt,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = "AI 피드백",
                                tint = if (hasStt) OPicColors.LevelGauge else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
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

    val completedCount = sentences.count { it.status == SentenceStatus.COMPLETED }
    val recordedCount = state.sentenceHasRecording.values.count { it }
    val withAccuracy = sentences.mapIndexedNotNull { idx, s ->
        s.analysisResult?.accuracyPercent?.let { idx to it }
    }
    val avgAccuracy = if (withAccuracy.isNotEmpty()) {
        withAccuracy.map { it.second }.average().toInt()
    } else null
    val weakSentences = withAccuracy.sortedBy { it.second }.take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OPicColors.LightBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        // 학습 통계 1줄 요약
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("완료", fontSize = 11.sp, color = Color.Gray)
            Text(
                text = "$completedCount/$total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OPicColors.Primary
            )
            Text("·", fontSize = 12.sp, color = OPicColors.Border)
            Text("녹음", fontSize = 11.sp, color = Color.Gray)
            Text(
                text = "$recordedCount/$total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OPicColors.TimerGreen
            )
            Text("·", fontSize = 12.sp, color = OPicColors.Border)
            Text("평균정확도", fontSize = 11.sp, color = Color.Gray)
            Text(
                text = if (avgAccuracy != null) "$avgAccuracy%" else "—",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    avgAccuracy == null -> Color.Gray
                    avgAccuracy >= 80   -> Color(0xFF2ECC71)
                    avgAccuracy >= 50   -> OPicColors.TimerOrange
                    else                -> OPicColors.TimerRed
                }
            )
        }

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
                            else      -> OPicColors.TimerRed
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
    startOffset: Long = 0L,
    endOffset: Long = 0L,
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

        if (hasRecording) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.Mic,
                contentDescription = "녹음 있음",
                modifier = Modifier.size(12.dp),
                tint = OPicColors.TimerGreen
            )
        }

        if (hasOffset) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "✎", fontSize = 10.sp, color = OPicColors.Primary)
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
                    else                  -> OPicColors.TimerRed
                }
            )
        }
    }
}

// ==================== 발화 연습 섹션 (문장 탭 컨텐츠) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserScriptSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel
) {
    val currentSentence = state.sentences.getOrNull(state.currentIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // STT 리스닝 표시
        if (state.userScriptSttListening) {
            Text(
                "Listening...",
                fontSize = 12.sp,
                color = OPicColors.RecordActive,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 내용 영역 (STT 결과 + 분석)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 240.dp)
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

            // 분석 결과
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
                    Text(
                        "누락 단어 (탭하여 단어장 추가):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE74C3C)
                    )
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
