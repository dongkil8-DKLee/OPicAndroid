package com.opic.android.ui.study

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.theme.OPicColors

/**
 * StudyScreen (리팩토링: 자동스크롤, SpeedRow 통합, FilterSection 2행).
 */
@Composable
fun StudyScreen(
    initialTopicType: String? = null,
    initialTopicSet: String? = null,
    initialGrade: String? = null,
    onPractice: (Int) -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // initialTopicType+Set → loading 완료 후 적용 (레이스 컨디션 방지)
    // loading 중에 적용하면 loadInitialData()가 완료되며 selectedType을 덮어씀
    LaunchedEffect(initialTopicType, state.loading) {
        if (!state.loading && !initialTopicType.isNullOrBlank()) {
            viewModel.initFilters(initialTopicType, initialTopicSet)
        }
    }

    // initialGrade → 등급 필터 자동 적용
    LaunchedEffect(initialGrade) {
        viewModel.setGradeFilter(initialGrade)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("학습 데이터 로딩 중...")
                }
            }
        } else {
            StudyContent(state, viewModel, onPractice, onSettings)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyContent(
    state: StudyUiState,
    viewModel: StudyViewModel,
    onPractice: (Int) -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val isBusy = state.playingTarget != null || state.isRecording || state.groupPlaying
    var showFilter by remember { mutableStateOf(false) }
    // 전체화면 토글: null=기본, "question"=Q 전체화면, "answer"=A 전체화면
    var expandedScript by remember { mutableStateOf<String?>(null) }
    // 두 스크립트 창 비율 (0.0~1.0, Question 비율)
    var splitFraction by remember { mutableFloatStateOf(0.44f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 필터 패널 (슬라이드 다운) =====
        AnimatedVisibility(
            visible = showFilter,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                FilterSection(state, viewModel)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ===== 타이틀 선택 + Prev/Next + 필터아이콘 (항상 표시) =====
        TitleSelector(state, viewModel, showFilter, onToggleFilter = { showFilter = !showFilter })

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 그룹재생 상태 =====
        if (state.groupPlaying && state.groupPlayStatus.isNotBlank()) {
            Text(
                text = state.groupPlayStatus,
                fontSize = 12.sp,
                color = OPicColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // ===== 스크립트 영역 (전체화면 토글 + 드래그 크기 조절 지원) =====
        if (expandedScript == null) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Column(modifier = Modifier.fillMaxSize()) {
                    ScriptSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(splitFraction),
                        label = "Question",
                        scriptText = state.currentQuestion?.questionText,
                        highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                        isEditing = state.editingQuestion,
                        draft = state.questionDraft,
                        onToggleEdit = { viewModel.toggleEditQuestion() },
                        onCancelEdit = { viewModel.cancelEditQuestion() },
                        onDraftChange = { viewModel.updateQuestionDraft(it) },
                        onSave = { viewModel.saveQuestionScript() },
                        fontSize = state.fontSize,
                        isPlaying = state.playingTarget == StudyPlayTarget.QUESTION,
                        canPlay = !isBusy && state.currentQuestion != null,
                        onPlay = { viewModel.playQuestionAudio() },
                        onStop = { viewModel.stopAudio() },
                        isExpanded = false,
                        onExpandToggle = { expandedScript = "question" }
                    )
                    // 드래그 핸들
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    splitFraction = (splitFraction + delta / totalHeightPx).coerceIn(0.15f, 0.85f)
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
                    ScriptSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - splitFraction),
                        label = "Answer",
                        scriptText = state.currentQuestion?.answerScript,
                        highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.ANSWER) state.highlightedWordIndex else -1,
                        isEditing = state.editingAnswer,
                        draft = state.answerDraft,
                        onToggleEdit = { viewModel.toggleEditAnswer() },
                        onCancelEdit = { viewModel.cancelEditAnswer() },
                        onDraftChange = { viewModel.updateAnswerDraft(it) },
                        onSave = { viewModel.saveAnswerScript() },
                        fontSize = state.fontSize,
                        isPlaying = state.playingTarget == StudyPlayTarget.ANSWER,
                        canPlay = !isBusy && state.currentQuestion != null,
                        onPlay = { viewModel.playAnswerAudio() },
                        onStop = { viewModel.stopAudio() },
                        isExpanded = false,
                        onExpandToggle = { expandedScript = "answer" }
                    )
                }
            }
        } else if (expandedScript == "question") {
            ScriptSection(
                modifier = Modifier.weight(1f),
                label = "Question",
                scriptText = state.currentQuestion?.questionText,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                isEditing = state.editingQuestion,
                draft = state.questionDraft,
                onToggleEdit = { viewModel.toggleEditQuestion() },
                onCancelEdit = { viewModel.cancelEditQuestion() },
                onDraftChange = { viewModel.updateQuestionDraft(it) },
                onSave = { viewModel.saveQuestionScript() },
                fontSize = state.fontSize,
                isPlaying = state.playingTarget == StudyPlayTarget.QUESTION,
                canPlay = !isBusy && state.currentQuestion != null,
                onPlay = { viewModel.playQuestionAudio() },
                onStop = { viewModel.stopAudio() },
                isExpanded = true,
                onExpandToggle = { expandedScript = null }
            )
        } else {
            ScriptSection(
                modifier = Modifier.weight(1f),
                label = "Answer",
                scriptText = state.currentQuestion?.answerScript,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.ANSWER) state.highlightedWordIndex else -1,
                isEditing = state.editingAnswer,
                draft = state.answerDraft,
                onToggleEdit = { viewModel.toggleEditAnswer() },
                onCancelEdit = { viewModel.cancelEditAnswer() },
                onDraftChange = { viewModel.updateAnswerDraft(it) },
                onSave = { viewModel.saveAnswerScript() },
                fontSize = state.fontSize,
                isPlaying = state.playingTarget == StudyPlayTarget.ANSWER,
                canPlay = !isBusy && state.currentQuestion != null,
                onPlay = { viewModel.playAnswerAudio() },
                onStop = { viewModel.stopAudio() },
                isExpanded = true,
                onExpandToggle = { expandedScript = null }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 속도 + 학습/별표/설정 통합 행 =====
        SpeedAndControlRow(state = state, viewModel = viewModel, onSettings = onSettings)

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 아이콘 행 (🎤 녹음, ▶ 재생, 💬 연습) =====
        IconButtonRow(
            state = state,
            viewModel = viewModel,
            isBusy = isBusy,
            onPractice = {
                val qId = state.currentQuestion?.questionId
                if (qId != null) onPractice(qId)
            }
        )

        // 마이크 레벨 바 (녹음 중에만 표시)
        if (state.isRecording) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.micLevel },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = OPicColors.TimerGreen,
                trackColor = OPicColors.Border,
            )
        }
    }
}

// ==================== 아이콘 행 (🎤 녹음, ▶ 재생, 💬 연습) ====================

@Composable
private fun IconButtonRow(
    state: StudyUiState,
    viewModel: StudyViewModel,
    isBusy: Boolean,
    onPractice: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🎤 녹음
        if (state.isRecording) {
            IconButton(onClick = { viewModel.stopRecording() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "녹음 중지", tint = OPicColors.RecordActive, modifier = Modifier.size(28.dp))
            }
        } else {
            IconButton(
                onClick = { viewModel.toggleRecording() },
                enabled = !isBusy && state.currentQuestion != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "녹음",
                    tint = if (!isBusy && state.currentQuestion != null) OPicColors.RecordActive else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ▶ 사용자 녹음 재생
        if (state.playingTarget == StudyPlayTarget.USER) {
            IconButton(onClick = { viewModel.stopAudio() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "재생 중지", modifier = Modifier.size(28.dp))
            }
        } else {
            IconButton(
                onClick = { viewModel.playUserAudio() },
                enabled = state.hasUserAudio && !isBusy,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "녹음 재생",
                    tint = if (state.hasUserAudio && !isBusy) OPicColors.PlayButton else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 💬 집중학습 (Practice)
        IconButton(
            onClick = onPractice,
            enabled = state.currentQuestion != null,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = "집중학습",
                tint = if (state.currentQuestion != null) OPicColors.Primary else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ==================== 속도 + 학습/별표/설정 통합 행 ====================

@Composable
private fun SpeedAndControlRow(
    state: StudyUiState,
    viewModel: StudyViewModel,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 좌측: 속도 ± 컨트롤 (0.5~1.5, 0.1 단위)
        Text("속도:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(2.dp))
        TextButton(
            onClick = { viewModel.onPlaybackSpeedChanged((state.playbackSpeed - 0.1f).coerceAtLeast(0.5f)) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("−", fontSize = 14.sp, color = OPicColors.TimerRed)
        }
        Text(
            text = String.format("%.1f", state.playbackSpeed) + "x",
            fontSize = 11.sp,
            color = OPicColors.Primary,
            modifier = Modifier.width(36.dp)
        )
        TextButton(
            onClick = { viewModel.onPlaybackSpeedChanged((state.playbackSpeed + 0.1f).coerceAtMost(1.5f)) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("+", fontSize = 14.sp, color = OPicColors.TimerGreen)
        }

        // 중간 여백 → 오른쪽 항목 항상 보이도록
        Spacer(modifier = Modifier.weight(1f))

        // 우측: 학습 카운트 + 별표 + 설정
        Text(
            text = "${state.studyCount}/7",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .border(1.dp, OPicColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint = if (state.isFavorite) OPicColors.TimerOrange else OPicColors.DisabledBg,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = OPicColors.Primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==================== 필터 섹션 (2행 구조) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(state: StudyUiState, viewModel: StudyViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: 주제 | 유형 | 정렬
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactDropdown(
                label = "주제",
                selected = state.selectedSet,
                options = listOf("전체") + state.sets,
                onSelected = { viewModel.onSetChanged(it) },
                modifier = Modifier.weight(1.3f)
            )

            CompactDropdown(
                label = "유형",
                selected = state.selectedType,
                options = listOf("전체") + state.types,
                onSelected = { viewModel.onTypeChanged(it) },
                modifier = Modifier.weight(1f)
            )

            CompactDropdown(
                label = "정렬",
                selected = state.selectedSort,
                options = listOf("주제 순서", "오래된 순"),
                onSelected = { viewModel.onSortChanged(it) },
                modifier = Modifier.weight(1.1f)
            )
        }

        // Row 2: Group Play 버튼 | 모드 | 학습
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.toggleGroupPlay() },
                enabled = !state.isRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.groupPlaying) OPicColors.RecordActive else OPicColors.PlayButton,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(
                    if (state.groupPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    if (state.groupPlaying) "Stop" else "Group Play",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            CompactDropdown(
                label = "모드",
                selected = state.groupPlayMode,
                options = listOf("목록 재생", "질문 재생", "답변 재생"),
                onSelected = { viewModel.onGroupPlayModeChanged(it) },
                modifier = Modifier.weight(1f)
            )

            CompactDropdown(
                label = "학습",
                selected = state.selectedStudyFilter,
                options = listOf("전체", "\uD83D\uDCCC", "저득점", "최근오답", "0", "1", "2", "3", "4", "5", "6", "7"),
                onSelected = { viewModel.onStudyFilterChanged(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== 타이틀 선택 + 필터 아이콘 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleSelector(
    state: StudyUiState,
    viewModel: StudyViewModel,
    showFilter: Boolean,
    onToggleFilter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { viewModel.onPrevTitle() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev")
        }

        CompactDropdown(
            label = "목록",
            selected = state.selectedTitle,
            options = state.titles,
            onSelected = { viewModel.onTitleSelected(it) },
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { viewModel.onNextTitle() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
        }

        Text(
            text = "${state.titles.indexOf(state.selectedTitle) + 1}/${state.titles.size}",
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        // 필터 아이콘
        IconButton(
            onClick = onToggleFilter,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = "Filter",
                tint = if (showFilter) OPicColors.Primary else Color.Gray
            )
        }
    }
}

// ==================== 문장 단위 자동 스크롤 텍스트 ====================

@Composable
private fun SentenceAutoScrollText(
    text: String,
    highlightedWordIndex: Int,
    fontSize: Int,
    scrollState: ScrollState
) {
    // 문장 분리
    val sentences = remember(text) {
        text.split("(?<=[.?!])\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // 각 문장의 누적 단어 수 → 문장 인덱스 매핑
    val sentenceWordCounts = remember(sentences) {
        sentences.map { it.split("\\s+".toRegex()).filter { w -> w.isNotBlank() }.size }
    }

    val activeSentenceIndex = remember(highlightedWordIndex, sentenceWordCounts) {
        if (highlightedWordIndex < 0) -1
        else {
            var cumulative = 0
            for (i in sentenceWordCounts.indices) {
                cumulative += sentenceWordCounts[i]
                if (highlightedWordIndex < cumulative) return@remember i
            }
            sentenceWordCounts.lastIndex
        }
    }

    // 각 문장의 Y좌표/높이 측정
    val sentencePositions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() } // idx → (y, height)

    // 활성 문장 변경 시 스크롤
    LaunchedEffect(activeSentenceIndex) {
        if (activeSentenceIndex < 0) return@LaunchedEffect
        val pos = sentencePositions[activeSentenceIndex] ?: return@LaunchedEffect
        val (y, h) = pos
        // 센터로 스크롤: 문장 중심이 뷰포트 중심에 오도록
        val target = (y + h / 2 - 200).coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        sentences.forEachIndexed { sentenceIdx, sentence ->
            Text(
                text = sentence,
                fontSize = fontSize.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        sentencePositions[sentenceIdx] = Pair(
                            coordinates.positionInParent().y.toInt(),
                            coordinates.size.height
                        )
                    }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

// ==================== 공용 스크립트 섹션 (Play + Edit + Fullscreen 포함) ====================

@Composable
private fun ScriptSection(
    modifier: Modifier = Modifier,
    label: String,
    scriptText: String? = null,
    highlightedWordIndex: Int = -1,
    isEditing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    fontSize: Int,
    isPlaying: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: 라벨 + ▶ Play + Edit + Fullscreen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label.isNotBlank()) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Play / Stop 버튼
            if (isPlaying) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(onClick = onPlay, enabled = canPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Play", fontSize = 11.sp)
                }
            }

            // Edit / Cancel / Save
            if (isEditing) {
                TextButton(onClick = onCancelEdit) {
                    Text("Cancel", fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = onSave) {
                    Text("Save", fontSize = 12.sp, color = OPicColors.Primary)
                }
            } else {
                TextButton(onClick = onToggleEdit) {
                    Text("Edit", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Fullscreen 아이콘
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize.sp)
            )
        } else if (!scriptText.isNullOrBlank()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                if (highlightedWordIndex >= 0) {
                    SentenceAutoScrollText(
                        text = scriptText,
                        highlightedWordIndex = highlightedWordIndex,
                        fontSize = fontSize,
                        scrollState = scrollState
                    )
                } else {
                    Text(
                        text = scriptText,
                        fontSize = fontSize.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            Text(
                text = "스크립트 없음",
                color = OPicColors.DisabledBg,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

// ==================== 공용 컴팩트 드롭다운 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 10.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .height(52.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
