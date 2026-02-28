package com.opic.android.ui.study

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.DiffText
import com.opic.android.ui.common.HomeButton
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.theme.OPicColors

/**
 * Python StudyScreen 1:1 이식 (2차 UI 개선).
 * 필터 체인 + Q/A/User 스크립트 + 재생 + 학습완료 + 즐겨찾기.
 * 개선: 필터 슬라이드 + 스크립트 확대/축소.
 */
@Composable
fun StudyScreen(
    onHome: () -> Unit,
    onPractice: (Int) -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
            StudyContent(state, viewModel, onHome, onPractice, onSettings)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StudyContent(
    state: StudyUiState,
    viewModel: StudyViewModel,
    onHome: () -> Unit,
    onPractice: (Int) -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val isBusy = state.playingTarget != null || state.isRecording || state.groupPlaying
    var showFilter by remember { mutableStateOf(false) }
    // null = 모두 표시, "question" / "answer" / "user" = 해당만 확장
    var expandedScript by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 최상단: 속도 + 필터 아이콘 + 설정 아이콘 =====
        SpeedFilterRow(
            state = state,
            viewModel = viewModel,
            showFilter = showFilter,
            onToggleFilter = { showFilter = !showFilter },
            onSettings = onSettings
        )

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
                GroupPlayRow(state, viewModel)
                Spacer(modifier = Modifier.height(4.dp))
                StudyControlRow(state, viewModel, context)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ===== 타이틀 선택 + Prev/Next (항상 표시) =====
        TitleSelector(state, viewModel)

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

        // ===== 스크립트 영역 (비율 고정, 내부 스크롤) =====
        if (expandedScript == null) {
            // 일반 모드: 비율로 크기 고정, 각 박스 내부 스크롤 (외부 스크롤 없음)
            Column(modifier = Modifier.weight(1f)) {
                ScriptSection(
                    modifier = Modifier.weight(2f),
                    label = "Question",
                    scriptText = state.currentQuestion?.questionText,
                    highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                    isEditing = state.editingQuestion,
                    draft = state.questionDraft,
                    isPlaying = state.playingTarget == StudyPlayTarget.QUESTION,
                    canPlay = !isBusy,
                    onPlay = { viewModel.playQuestionAudio() },
                    onStop = { viewModel.stopAudio() },
                    onToggleEdit = { viewModel.toggleEditQuestion() },
                    onDraftChange = { viewModel.updateQuestionDraft(it) },
                    onSave = { viewModel.saveQuestionScript() },
                    fontSize = state.fontSize,
                    isExpanded = false,
                    onExpandToggle = { expandedScript = "question" }
                )
                Spacer(modifier = Modifier.height(4.dp))
                ScriptSection(
                    modifier = Modifier.weight(2.5f),
                    label = "Answer",
                    scriptText = state.currentQuestion?.answerScript,
                    highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.ANSWER) state.highlightedWordIndex else -1,
                    isEditing = state.editingAnswer,
                    draft = state.answerDraft,
                    isPlaying = state.playingTarget == StudyPlayTarget.ANSWER,
                    canPlay = !isBusy,
                    onPlay = { viewModel.playAnswerAudio() },
                    onStop = { viewModel.stopAudio() },
                    onToggleEdit = { viewModel.toggleEditAnswer() },
                    onDraftChange = { viewModel.updateAnswerDraft(it) },
                    onSave = { viewModel.saveAnswerScript() },
                    fontSize = state.fontSize,
                    isExpanded = false,
                    onExpandToggle = { expandedScript = "answer" }
                )
                Spacer(modifier = Modifier.height(4.dp))
                UserScriptSection(
                    modifier = Modifier.weight(1f),
                    state = state,
                    viewModel = viewModel,
                    isBusy = isBusy,
                    isExpanded = false,
                    onExpandToggle = { expandedScript = "user" },
                    onPractice = {
                        val qId = state.currentQuestion?.questionId
                        if (qId != null) onPractice(qId)
                    }
                )
            }
        } else if (expandedScript == "question") {
            ScriptSection(
                modifier = Modifier.weight(1f),
                label = "Question",
                scriptText = state.currentQuestion?.questionText,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.QUESTION) state.highlightedWordIndex else -1,
                isEditing = state.editingQuestion,
                draft = state.questionDraft,
                isPlaying = state.playingTarget == StudyPlayTarget.QUESTION,
                canPlay = !isBusy,
                onPlay = { viewModel.playQuestionAudio() },
                onStop = { viewModel.stopAudio() },
                onToggleEdit = { viewModel.toggleEditQuestion() },
                onDraftChange = { viewModel.updateQuestionDraft(it) },
                onSave = { viewModel.saveQuestionScript() },
                fontSize = state.fontSize,
                isExpanded = true,
                onExpandToggle = { expandedScript = null }
            )
        } else if (expandedScript == "answer") {
            ScriptSection(
                modifier = Modifier.weight(1f),
                label = "Answer",
                scriptText = state.currentQuestion?.answerScript,
                highlightedWordIndex = if (state.playingTarget == StudyPlayTarget.ANSWER) state.highlightedWordIndex else -1,
                isEditing = state.editingAnswer,
                draft = state.answerDraft,
                isPlaying = state.playingTarget == StudyPlayTarget.ANSWER,
                canPlay = !isBusy,
                onPlay = { viewModel.playAnswerAudio() },
                onStop = { viewModel.stopAudio() },
                onToggleEdit = { viewModel.toggleEditAnswer() },
                onDraftChange = { viewModel.updateAnswerDraft(it) },
                onSave = { viewModel.saveAnswerScript() },
                fontSize = state.fontSize,
                isExpanded = true,
                onExpandToggle = { expandedScript = null }
            )
        } else if (expandedScript == "user") {
            UserScriptSection(
                modifier = Modifier.weight(1f),
                state = state,
                viewModel = viewModel,
                isBusy = isBusy,
                isExpanded = true,
                onExpandToggle = { expandedScript = null },
                onPractice = {
                    val qId = state.currentQuestion?.questionId
                    if (qId != null) onPractice(qId)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 하단: Home 버튼 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            HomeButton(onClick = onHome)
        }
    }
}

// ==================== 속도 + 필터 + 설정 아이콘 행 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedFilterRow(
    state: StudyUiState,
    viewModel: StudyViewModel,
    showFilter: Boolean,
    onToggleFilter: () -> Unit,
    onSettings: () -> Unit
) {
    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("속도:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

        speedOptions.forEach { speed ->
            val label = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
            val isSelected = state.playbackSpeed == speed
            TextButton(
                onClick = { viewModel.onPlaybackSpeedChanged(speed) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) OPicColors.Primary else Color.Gray
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 필터 아이콘 (토글 → 필터 패널)
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

        // 설정 아이콘 → Main Settings 화면 이동
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.Gray
            )
        }
    }
}

// ==================== 필터 섹션 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(state: StudyUiState, viewModel: StudyViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Set 드롭다운
        CompactDropdown(
            label = "주제",
            selected = state.selectedSet,
            options = listOf("전체") + state.sets,
            onSelected = { viewModel.onSetChanged(it) },
            modifier = Modifier.width(130.dp)
        )

        // Type 드롭다운
        CompactDropdown(
            label = "유형",
            selected = state.selectedType,
            options = listOf("전체") + state.types,
            onSelected = { viewModel.onTypeChanged(it) },
            modifier = Modifier.width(100.dp)
        )

        // Sort 드롭다운
        CompactDropdown(
            label = "정렬",
            selected = state.selectedSort,
            options = listOf("주제 순서", "오래된 순"),
            onSelected = { viewModel.onSortChanged(it) },
            modifier = Modifier.width(110.dp)
        )

        // Study Filter 드롭다운
        CompactDropdown(
            label = "학습",
            selected = state.selectedStudyFilter,
            options = listOf("전체", "\uD83D\uDCCC", "0", "1", "2", "3", "4", "5", "6", "7"),
            onSelected = { viewModel.onStudyFilterChanged(it) },
            modifier = Modifier.width(90.dp)
        )
    }
}

// ==================== 타이틀 선택 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleSelector(state: StudyUiState, viewModel: StudyViewModel) {
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
    }
}

// ==================== 학습 컨트롤 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyControlRow(
    state: StudyUiState,
    viewModel: StudyViewModel,
    context: android.content.Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Study Count (자동 카운트)
        Text(
            text = "학습: ${state.studyCount}/7",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier
                .border(1.dp, OPicColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // 즐겨찾기 버튼
        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint = if (state.isFavorite) OPicColors.TimerOrange else OPicColors.DisabledBg
            )
        }
    }
}

// ==================== 그룹재생 컨트롤 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPlayRow(state: StudyUiState, viewModel: StudyViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Group Play 버튼
        Button(
            onClick = { viewModel.toggleGroupPlay() },
            enabled = !state.isRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.groupPlaying) OPicColors.RecordActive else OPicColors.PlayButton,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(36.dp)
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

        // 모드 드롭다운
        CompactDropdown(
            label = "모드",
            selected = state.groupPlayMode,
            options = listOf("목록 재생", "질문 재생", "답변 재생"),
            onSelected = { viewModel.onGroupPlayModeChanged(it) },
            modifier = Modifier.width(105.dp)
        )
    }
}

// ==================== User Script 섹션 + 녹음 ====================

@Composable
private fun UserScriptSection(
    state: StudyUiState,
    viewModel: StudyViewModel,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    onPractice: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Record / Stop Record
                if (state.isRecording) {
                    TextButton(onClick = { viewModel.stopRecording() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                        Text(" Stop Rec", fontSize = 11.sp, color = OPicColors.RecordActive)
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.toggleRecording() },
                        enabled = !isBusy && state.currentQuestion != null
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Rec", fontSize = 11.sp)
                    }
                }

                // Play / Stop (user audio)
                if (state.playingTarget == StudyPlayTarget.USER) {
                    TextButton(onClick = { viewModel.stopAudio() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Stop", fontSize = 12.sp)
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.playUserAudio() },
                        enabled = state.hasUserAudio && !isBusy
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Play", fontSize = 12.sp)
                    }
                }

                // STT button
                if (state.sttListening) {
                    TextButton(onClick = { viewModel.stopStt() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                        Text(" Stop STT", fontSize = 11.sp, color = OPicColors.RecordActive)
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.startStt() },
                        enabled = !isBusy && !state.sttListening && state.currentQuestion != null
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF3498DB))
                        Text(" STT", fontSize = 11.sp)
                    }
                }

                TextButton(onClick = { viewModel.toggleEditUserScript() }) {
                    Text(if (state.editingUserScript) "Hide" else "Edit", fontSize = 12.sp)
                }

                if (state.editingUserScript) {
                    TextButton(onClick = { viewModel.saveUserScript() }) {
                        Text("Save", fontSize = 12.sp, color = OPicColors.Primary)
                    }
                }
            }

            // 문장연습 아이콘
            IconButton(
                onClick = onPractice,
                enabled = state.currentQuestion != null,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = "문장연습",
                    tint = if (state.currentQuestion != null) OPicColors.Primary else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 확대/축소 아이콘
            IconButton(
                onClick = onExpandToggle,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 마이크 레벨 바 (녹음 중에만 표시)
        if (state.isRecording) {
            LinearProgressIndicator(
                progress = { state.micLevel },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = OPicColors.TimerGreen,
                trackColor = OPicColors.Border,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 편집/STT 영역 (항상 내부 스크롤)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        ) {
        if (state.editingUserScript) {
            OutlinedTextField(
                value = state.userScriptDraft,
                onValueChange = { viewModel.updateUserScriptDraft(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = state.fontSize.sp),
                placeholder = {
                    Text(
                        "나만의 스크립트를 작성하세요.",
                        fontSize = 13.sp,
                        color = OPicColors.DisabledBg
                    )
                }
            )
        } else {
            Text(
                text = "Edit 버튼을 눌러 스크립트를 확인하세요.",
                color = OPicColors.DisabledBg,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // STT Result
        if (state.sttListening) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Listening...",
                fontSize = 13.sp,
                color = OPicColors.RecordActive,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (!state.sttText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("STT Result", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.sttText,
                fontSize = state.fontSize.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3498DB), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Analysis button + Diff toggle
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { viewModel.analyzeSpeech() },
                    enabled = !state.currentQuestion?.answerScript.isNullOrBlank()
                ) {
                    Text("분석", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9B59B6))
                }

                if (state.analysisResult == null) {
                    TextButton(onClick = { viewModel.toggleDiff() }) {
                        Text(
                            if (state.showDiff) "Hide Diff" else "Show Diff",
                            fontSize = 12.sp,
                            color = Color(0xFF3498DB)
                        )
                    }
                }
            }

            // Analysis result panel
            if (state.analysisResult != null) {
                SpeechAnalysisPanel(
                    result = state.analysisResult,
                    expectedText = state.currentQuestion?.answerScript ?: "",
                    actualText = state.sttText,
                    fontSize = state.fontSize
                )
            } else if (state.showDiff) {
                val answerScript = state.currentQuestion?.answerScript ?: ""
                if (answerScript.isNotBlank()) {
                    Text("Answer vs STT Diff", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    DiffText(
                        expected = answerScript,
                        actual = state.sttText,
                        fontSize = state.fontSize
                    )
                }
            }
        }
        } // 편집/STT 영역 Column
    }
}

// ==================== 하이라이트된 스크립트 텍스트 ====================

@Composable
private fun HighlightedScriptText(
    text: String,
    highlightedWordIndex: Int,
    fontSize: Int
) {
    val words = remember(text) { text.split("\\s+".toRegex()).filter { it.isNotBlank() } }
    val annotatedString = remember(text, highlightedWordIndex) {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                if (index == highlightedWordIndex) {
                    withStyle(
                        SpanStyle(
                            background = Color(0xFFFFEB3B),
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (index < words.size - 1) append(" ")
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = fontSize.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
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
    isPlaying: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    fontSize: Int,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label.isNotBlank()) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                TextButton(onClick = onToggleEdit) {
                    Text(if (isEditing) "Hide" else "Edit", fontSize = 12.sp)
                }

                if (isEditing) {
                    TextButton(onClick = onSave) {
                        Text("Save", fontSize = 12.sp, color = OPicColors.Primary)
                    }
                }

                // 확대/축소 아이콘
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isExpanded) "축소" else "확대",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
            // 텍스트 항상 내부 스크롤 (박스 크기는 외부 modifier로 결정)
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (isPlaying && highlightedWordIndex >= 0) {
                    HighlightedScriptText(
                        text = scriptText,
                        highlightedWordIndex = highlightedWordIndex,
                        fontSize = fontSize
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
                text = "Edit 버튼을 눌러 스크립트를 확인하세요.",
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
