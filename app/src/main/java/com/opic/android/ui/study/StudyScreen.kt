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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.filter.BottomSheetPicker
import com.opic.android.ui.common.filter.FilterPanel
import com.opic.android.ui.common.filter.StudyFilterState
import com.opic.android.ui.theme.OPicColors

/**
 * StudyScreen — 필터 상태는 StudyFilterController.state 를 별도 수집.
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
    val state       by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterController.state.collectAsState()

    LaunchedEffect(initialTopicType, filterState.isReady) {
        if (filterState.isReady && !initialTopicType.isNullOrBlank()) {
            viewModel.initFilters(initialTopicType, initialTopicSet)
        }
    }

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
            StudyContent(state, filterState, viewModel, onPractice, onSettings)
        }
    }
}

@Composable
private fun StudyContent(
    state: StudyUiState,
    filterState: StudyFilterState,
    viewModel: StudyViewModel,
    onPractice: (Int) -> Unit,
    onSettings: () -> Unit
) {
    val context  = LocalContext.current
    val isBusy   = state.playingTarget != null || state.isRecording || state.groupPlaying
    var showFilter    by remember { mutableStateOf(false) }
    var expandedScript by remember { mutableStateOf<String?>(null) }
    var splitFraction  by remember { mutableFloatStateOf(0.44f) }
    val questionScrollState = rememberScrollState()
    val answerScrollState   = rememberScrollState()

    LaunchedEffect(showFilter) {
        if (showFilter) {
            questionScrollState.animateScrollTo(0)
            answerScrollState.animateScrollTo(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 타이틀 선택 + Prev/Next + 필터아이콘 =====
        TitleSelector(
            state        = state,
            filterState  = filterState,
            viewModel    = viewModel,
            showFilter   = showFilter,
            onToggleFilter = { showFilter = !showFilter }
        )

        // ===== 필터 패널 (타이틀 아래로 슬라이드 다운) =====
        AnimatedVisibility(
            visible = showFilter,
            enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit    = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                FilterPanel(
                    state               = filterState,
                    onSetChanged        = { viewModel.onSetChanged(it) },
                    onTypeChanged       = { viewModel.onTypeChanged(it) },
                    onSortChanged       = { viewModel.onSortChanged(it) },
                    onStudyFilterChanged = { viewModel.onStudyFilterChanged(it) },
                    extraRow2Content = {
                        Button(
                            onClick  = { viewModel.toggleGroupPlay() },
                            enabled  = !state.isRecording,
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (state.groupPlaying) OPicColors.RecordActive else OPicColors.PlayButton,
                                contentColor   = Color.White
                            ),
                            shape    = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp).weight(1f)
                        ) {
                            Icon(
                                if (state.groupPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                if (state.groupPlaying) "Stop" else "Group Play",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        BottomSheetPicker(
                            label      = "모드",
                            selected   = state.groupPlayMode,
                            options    = listOf("목록 재생", "질문 재생", "답변 재생"),
                            onSelected = { viewModel.onGroupPlayModeChanged(it) },
                            modifier   = Modifier.weight(1f)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 그룹재생 상태 =====
        if (state.groupPlaying && state.groupPlayStatus.isNotBlank()) {
            Text(
                text       = state.groupPlayStatus,
                fontSize   = 12.sp,
                color      = OPicColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(vertical = 2.dp)
            )
        }

        // ===== 스크립트 영역 =====
        if (expandedScript == null) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Column(modifier = Modifier.fillMaxSize()) {
                    ScriptSection(
                        modifier             = Modifier.fillMaxWidth().weight(splitFraction),
                        label                = "Question",
                        scriptText           = state.currentQuestion?.questionText,
                        highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                        isEditing            = state.editingQuestion,
                        draft                = state.questionDraft,
                        onToggleEdit         = { viewModel.toggleEditQuestion() },
                        onCancelEdit         = { viewModel.cancelEditQuestion() },
                        onDraftChange        = { viewModel.updateQuestionDraft(it) },
                        onSave               = { viewModel.saveQuestionScript() },
                        fontSize             = state.fontSize,
                        isPlaying            = state.playingTarget == StudyPlayTarget.QUESTION,
                        canPlay              = !isBusy && state.currentQuestion != null,
                        onPlay               = { viewModel.playQuestionAudio() },
                        onStop               = { viewModel.stopAudio() },
                        isExpanded           = false,
                        onExpandToggle       = { expandedScript = "question" },
                        scrollState          = questionScrollState
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(12.dp).draggable(
                            state       = rememberDraggableState { delta ->
                                splitFraction = (splitFraction + delta / totalHeightPx).coerceIn(0.15f, 0.85f)
                            },
                            orientation = Orientation.Vertical
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(width = 40.dp, height = 4.dp).background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                    AnswerTabSection(
                        modifier       = Modifier.fillMaxWidth().weight(1f - splitFraction),
                        state          = state,
                        viewModel      = viewModel,
                        isBusy         = isBusy,
                        isExpanded     = false,
                        onExpandToggle = { expandedScript = "answer" },
                        scrollState    = answerScrollState
                    )
                }
            }
        } else if (expandedScript == "question") {
            ScriptSection(
                modifier             = Modifier.weight(1f),
                label                = "Question",
                scriptText           = state.currentQuestion?.questionText,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                isEditing            = state.editingQuestion,
                draft                = state.questionDraft,
                onToggleEdit         = { viewModel.toggleEditQuestion() },
                onCancelEdit         = { viewModel.cancelEditQuestion() },
                onDraftChange        = { viewModel.updateQuestionDraft(it) },
                onSave               = { viewModel.saveQuestionScript() },
                fontSize             = state.fontSize,
                isPlaying            = state.playingTarget == StudyPlayTarget.QUESTION,
                canPlay              = !isBusy && state.currentQuestion != null,
                onPlay               = { viewModel.playQuestionAudio() },
                onStop               = { viewModel.stopAudio() },
                isExpanded           = true,
                onExpandToggle       = { expandedScript = null },
                scrollState          = questionScrollState
            )
        } else {
            AnswerTabSection(
                modifier       = Modifier.weight(1f),
                state          = state,
                viewModel      = viewModel,
                isBusy         = isBusy,
                isExpanded     = true,
                onExpandToggle = { expandedScript = null },
                scrollState    = answerScrollState
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        SpeedAndControlRow(state = state, viewModel = viewModel, onSettings = onSettings)
        Spacer(modifier = Modifier.height(4.dp))
        IconButtonRow(
            state     = state,
            viewModel = viewModel,
            isBusy    = isBusy,
            onPractice = {
                val qId = state.currentQuestion?.questionId
                if (qId != null) onPractice(qId)
            }
        )

        if (state.isRecording) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress    = { state.micLevel },
                modifier    = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color       = OPicColors.TimerGreen,
                trackColor  = OPicColors.Border,
            )
        }
    }
}

// ==================== 아이콘 행 ====================

@Composable
private fun IconButtonRow(
    state: StudyUiState,
    viewModel: StudyViewModel,
    isBusy: Boolean,
    onPractice: () -> Unit
) {
    val hasQuestion = state.currentQuestion != null
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (state.isRecording) {
            IconButton(onClick = { viewModel.stopRecording() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "녹음 중지", tint = OPicColors.RecordActive, modifier = Modifier.size(28.dp))
            }
        } else {
            IconButton(
                onClick  = { viewModel.toggleRecording() },
                enabled  = !isBusy && state.currentQuestion != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "녹음",
                    tint     = if (!isBusy && state.currentQuestion != null) OPicColors.RecordActive else Color.Gray,
                    modifier = Modifier.size(28.dp))
            }
        }

        // 녹음 재생 버튼 — 원형 outline 스타일
        // ★ 미세 튜닝 포인트:
        // ★ CIRCLE_SIZE  = 40.dp  : 원 전체 크기
        // ★ BORDER_WIDTH = 1.5.dp : 테두리 두께
        // ★ ICON_SIZE    = 20.dp  : 삼각형/정지 아이콘 크기
        // ★ IDLE_COLOR   = OPicColors.PlayButton   : 대기 상태 색상
        // ★ ACTIVE_COLOR = OPicColors.RecordActive : 재생 중 색상
        // ★ DISABLED_COLOR = Color.Gray            : 비활성화 색상
        val isUserPlaying  = state.playingTarget == StudyPlayTarget.USER
        val userPlayEnabled = state.hasUserAudio && !isBusy
        val userCircleColor = when {
            isUserPlaying   -> OPicColors.RecordActive  // ★ ACTIVE_COLOR
            userPlayEnabled -> OPicColors.PlayButton    // ★ IDLE_COLOR
            else            -> Color.Gray               // ★ DISABLED_COLOR
        }
        Box(
            modifier = Modifier
                .size(24.dp)                                          // ★ CIRCLE_SIZE
                .border(1.5.dp, userCircleColor, CircleShape)         // ★ BORDER_WIDTH
                .clickable(enabled = isUserPlaying || userPlayEnabled) {
                    if (isUserPlaying) viewModel.stopAudio() else viewModel.playUserAudio()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUserPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isUserPlaying) "재생 중지" else "녹음 재생",
                tint     = userCircleColor,
                modifier = Modifier.size(20.dp)                       // ★ ICON_SIZE
            )
        }

        IconButton(onClick = onPractice, enabled = hasQuestion, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "집중학습",
                tint     = if (hasQuestion) OPicColors.Primary else Color.Gray,
                modifier = Modifier.size(28.dp))
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
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("속도:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(2.dp))
        TextButton(
            onClick         = { viewModel.onPlaybackSpeedChanged((state.playbackSpeed - 0.1f).coerceAtLeast(0.5f)) },
            contentPadding  = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier        = Modifier.height(28.dp)
        ) { Text("−", fontSize = 14.sp, color = OPicColors.TimerRed) }
        Text(
            text     = String.format("%.1f", state.playbackSpeed) + "x",
            fontSize = 11.sp,
            color    = OPicColors.Primary,
            modifier = Modifier.width(36.dp)
        )
        TextButton(
            onClick         = { viewModel.onPlaybackSpeedChanged((state.playbackSpeed + 0.1f).coerceAtMost(1.5f)) },
            contentPadding  = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier        = Modifier.height(28.dp)
        ) { Text("+", fontSize = 14.sp, color = OPicColors.TimerGreen) }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text     = "${state.studyCount}/7",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.border(1.dp, OPicColors.Border, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
        )
        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint     = if (state.isFavorite) OPicColors.TimerOrange else OPicColors.DisabledBg,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = OPicColors.Primary, modifier = Modifier.size(18.dp))
        }
    }
}

// ==================== 타이틀 선택 + 필터 아이콘 ====================

@Composable
private fun TitleSelector(
    state: StudyUiState,
    filterState: StudyFilterState,
    viewModel: StudyViewModel,
    showFilter: Boolean,
    onToggleFilter: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.onPrevTitle() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev")
        }

        BottomSheetPicker(
            label        = "",
            selected     = state.selectedTitle,
            options      = filterState.titles,
            onSelected   = { viewModel.onTitleSelected(it) },
            modifier     = Modifier.weight(1f),
            pillHeight   = 40.dp,
            cornerRadius = 8.dp
        )

        IconButton(onClick = { viewModel.onNextTitle() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
        }

        Text(
            text     = "${filterState.titles.indexOf(state.selectedTitle) + 1}/${filterState.titles.size}",
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        IconButton(onClick = onToggleFilter, modifier = Modifier.size(32.dp)) {
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
    val sentences = remember(text) {
        text.split("(?<=[.?!])\\s+".toRegex()).filter { it.isNotBlank() }
    }
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
    val sentencePositions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    LaunchedEffect(activeSentenceIndex) {
        if (activeSentenceIndex < 0) return@LaunchedEffect
        val pos = sentencePositions[activeSentenceIndex] ?: return@LaunchedEffect
        val (y, h) = pos
        val target = (y + h / 2 - 200).coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        sentences.forEachIndexed { sentenceIdx, sentence ->
            Text(
                text     = sentence,
                fontSize = fontSize.sp,
                modifier = Modifier.fillMaxWidth()
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

// ==================== Answer 탭 섹션 (기본 답변 / AI 답변) ====================

@Composable
private fun AnswerTabSection(
    modifier: Modifier = Modifier,
    state: StudyUiState,
    viewModel: StudyViewModel,
    isBusy: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    scrollState: ScrollState
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = state.answerTabIndex) {
            Tab(
                selected = state.answerTabIndex == 0,
                onClick  = { viewModel.onAnswerTabSelected(0) },
                text     = { Text("기본 답변", fontSize = 12.sp) }
            )
            Tab(
                selected = state.answerTabIndex == 1,
                onClick  = { viewModel.onAnswerTabSelected(1) },
                text     = { Text("AI 답변", fontSize = 12.sp) }
            )
        }
        when (state.answerTabIndex) {
            0 -> ScriptSection(
                modifier             = Modifier.fillMaxWidth().weight(1f),
                label                = "Answer",
                scriptText           = state.currentQuestion?.answerScript,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.ANSWER) state.highlightedWordIndex else -1,
                isEditing            = state.editingAnswer,
                draft                = state.answerDraft,
                onToggleEdit         = { viewModel.toggleEditAnswer() },
                onCancelEdit         = { viewModel.cancelEditAnswer() },
                onDraftChange        = { viewModel.updateAnswerDraft(it) },
                onSave               = { viewModel.saveAnswerScript() },
                fontSize             = state.fontSize,
                isPlaying            = state.playingTarget == StudyPlayTarget.ANSWER,
                canPlay              = !isBusy && state.currentQuestion != null,
                onPlay               = { viewModel.playAnswerAudio() },
                onStop               = { viewModel.stopAudio() },
                isExpanded           = isExpanded,
                onExpandToggle       = onExpandToggle,
                scrollState          = scrollState
            )
            else -> AiAnswerSection(
                modifier  = Modifier.fillMaxWidth().weight(1f),
                state     = state,
                viewModel = viewModel
            )
        }
    }
}

// ==================== AI 답변 섹션 ====================

@Composable
private fun AiAnswerSection(
    modifier: Modifier = Modifier,
    state: StudyUiState,
    viewModel: StudyViewModel
) {
    val aiAnswer      = state.currentQuestion?.aiAnswer
    val aiScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더 행
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AI 모범 답안", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            if (state.aiLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.editingAiAnswer) {
                TextButton(onClick = { viewModel.cancelEditAiAnswer() }) {
                    Text("Cancel", fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = { viewModel.saveAiAnswerScript() }) {
                    Text("Save", fontSize = 12.sp, color = OPicColors.Primary)
                }
            } else if (!aiAnswer.isNullOrBlank()) {
                TextButton(onClick = { viewModel.toggleEditAiAnswer() }) {
                    Text("Edit", fontSize = 12.sp)
                }
            }
        }

        // 생성 버튼
        Button(
            onClick  = { viewModel.generateModelAnswer() },
            enabled  = !state.aiLoading && state.currentQuestion != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = OPicColors.Primary,
                contentColor   = Color.White
            ),
            shape    = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            Text(
                text     = if (aiAnswer.isNullOrBlank()) "AI 답변 생성" else "재생성",
                fontSize = 12.sp
            )
        }

        // 에러
        if (state.aiError != null) {
            Text(
                text     = state.aiError,
                color    = OPicColors.TimerRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 컨텐츠
        if (state.editingAiAnswer) {
            OutlinedTextField(
                value         = state.aiAnswerDraft,
                onValueChange = { viewModel.updateAiAnswerDraft(it) },
                modifier      = Modifier.fillMaxWidth().weight(1f),
                textStyle     = MaterialTheme.typography.bodyMedium.copy(fontSize = state.fontSize.sp)
            )
        } else if (!aiAnswer.isNullOrBlank()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(aiScrollState)) {
                Text(
                    text     = aiAnswer,
                    fontSize = state.fontSize.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        } else if (!state.aiLoading) {
            Text(
                text     = "아직 AI 답변이 없습니다.\n버튼을 눌러 생성하세요.",
                color    = OPicColors.DisabledBg,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

// ==================== 공용 스크립트 섹션 ====================

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
    onExpandToggle: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = modifier.fillMaxWidth().border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp)).padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 제목 + 아이콘 — 클릭 시 Play/Stop
            Row(
                modifier = Modifier.clickable(enabled = canPlay || isPlaying) {
                    if (isPlaying) onStop() else onPlay()
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label.isNotBlank()) Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = when {
                        isPlaying -> OPicColors.RecordActive
                        canPlay   -> OPicColors.PlayButton
                        else      -> Color.Gray
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (isEditing) {
                TextButton(onClick = onCancelEdit) { Text("Cancel", fontSize = 12.sp, color = Color.Gray) }
                TextButton(onClick = onSave) { Text("Save", fontSize = 12.sp, color = OPicColors.Primary) }
            } else {
                TextButton(onClick = onToggleEdit) { Text("Edit", fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint     = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value       = draft,
                onValueChange = onDraftChange,
                modifier    = Modifier.fillMaxWidth().weight(1f),
                textStyle   = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize.sp)
            )
        } else if (!scriptText.isNullOrBlank()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                if (highlightedWordIndex >= 0) {
                    SentenceAutoScrollText(
                        text                 = scriptText,
                        highlightedWordIndex = highlightedWordIndex,
                        fontSize             = fontSize,
                        scrollState          = scrollState
                    )
                } else {
                    Text(
                        text     = scriptText,
                        fontSize = fontSize.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            Text(text = "스크립트 없음", color = OPicColors.DisabledBg, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
