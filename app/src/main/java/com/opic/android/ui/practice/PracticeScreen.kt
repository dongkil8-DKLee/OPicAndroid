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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.opic.android.ui.common.DiffText
import com.opic.android.ui.common.SpeechAnalysisPanel
import com.opic.android.ui.theme.OPicColors

/**
 * PracticeScreen (집중학습) - 리팩토링 버전.
 * - PracticeSpeedRow, HomeButton, ResultSection 제거
 * - UserScript 섹션 추가 (Study에서 이동)
 * - < Back 좌측 하단으로 이동
 * - STT 완료 후 자동 분석
 * - 누락 단어 클릭 → 단어장 추가
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
    val isBusy = state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.sttListening

    // 확대 섹션 상태: null=모두 표시, "practice"/"userscript"=해당만 표시
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 타이틀 네비게이터 =====
        PracticeTitleRow(state, viewModel)

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 비율 고정 영역 =====
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== 녹음/문장연습 섹션 =====
            AnimatedVisibility(
                visible = expandedSection == null || expandedSection == "practice",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = if (expandedSection == null) Modifier.weight(2f) else Modifier.weight(1f)
            ) {
                PracticeSentenceSection(
                    modifier = Modifier,
                    state = state,
                    viewModel = viewModel,
                    isBusy = isBusy,
                    isExpanded = expandedSection == "practice",
                    onExpandToggle = {
                        expandedSection = if (expandedSection == "practice") null else "practice"
                    }
                )
            }

            // ===== UserScript 섹션 (Study에서 이동) =====
            AnimatedVisibility(
                visible = expandedSection == null || expandedSection == "userscript",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = if (expandedSection == null) Modifier.weight(1.5f) else Modifier.weight(1f)
            ) {
                UserScriptSection(
                    modifier = Modifier,
                    state = state,
                    viewModel = viewModel,
                    isExpanded = expandedSection == "userscript",
                    onExpandToggle = {
                        expandedSection = if (expandedSection == "userscript") null else "userscript"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== < Back 좌측 하단 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onBack) {
                Text("< Back", fontSize = 13.sp, color = OPicColors.Primary, fontWeight = FontWeight.Bold)
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

// ==================== 녹음/문장연습 섹션 ====================

@Composable
private fun PracticeSentenceSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isBusy: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val currentSentence = state.sentences.getOrNull(state.currentIndex)
    val hasUserRecording = currentSentence?.userRecordingPath != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: 컨트롤 버튼들 (Result 버튼 제거됨)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rec / Stop Rec
            if (state.isRecording) {
                TextButton(onClick = { viewModel.stopRecording() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop Rec", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = { viewModel.toggleRecording() },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Rec", fontSize = 11.sp)
                }
            }

            // Play user recording / Stop
            if (state.isPlayingUser) {
                TextButton(onClick = { viewModel.stopUserRecording() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Stop", fontSize = 11.sp)
                }
            } else {
                TextButton(
                    onClick = { viewModel.playUserRecording() },
                    enabled = hasUserRecording && !isBusy
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Play", fontSize = 11.sp)
                }
            }

            // STT / Stop STT
            if (state.sttListening) {
                TextButton(onClick = { viewModel.stopStt() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop STT", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = { viewModel.startStt() },
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

        if (state.sttListening) {
            Text("Listening...", fontSize = 12.sp, color = OPicColors.RecordActive, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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

// ==================== UserScript 섹션 (Study에서 이동) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserScriptSection(
    modifier: Modifier = Modifier,
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val isBusy = state.isRecordingUserScript || state.isPlayingUserAudio || state.userScriptSttListening

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: UserScript + Edit/Cancel/Save + 확대/축소
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("UserScript", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Spacer(modifier = Modifier.width(4.dp))

            // Edit/Cancel/Save 토글
            if (state.editingUserScript) {
                TextButton(onClick = { viewModel.cancelEditUserScript() }) {
                    Text("Cancel", fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = { viewModel.saveUserScript() }) {
                    Text("Save", fontSize = 12.sp, color = OPicColors.Primary)
                }
            } else {
                TextButton(onClick = { viewModel.toggleEditUserScript() }) {
                    Text("Edit", fontSize = 12.sp)
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

        // 녹음/재생/STT 컨트롤
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rec
            if (state.isRecordingUserScript) {
                TextButton(onClick = { viewModel.stopUserScriptRecording() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" Stop Rec", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = { viewModel.toggleUserScriptRecording() },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Rec", fontSize = 11.sp)
                }
            }

            // Play
            if (state.isPlayingUserAudio) {
                TextButton(onClick = { viewModel.stopUserScriptAudio() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Stop", fontSize = 11.sp)
                }
            } else {
                TextButton(
                    onClick = { viewModel.playUserScriptAudio() },
                    enabled = state.hasUserAudio && !isBusy
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Play", fontSize = 11.sp)
                }
            }

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

        // 내용 영역
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.editingUserScript) {
                OutlinedTextField(
                    value = state.userScriptDraft,
                    onValueChange = { viewModel.updateUserScriptDraft(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    placeholder = {
                        Text("나만의 스크립트를 작성하세요.", fontSize = 13.sp, color = OPicColors.DisabledBg)
                    }
                )
            }

            // STT 텍스트 바로 표시 (제목 없이)
            if (!state.userScriptSttText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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
                    expectedText = state.answerScript,
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
            } else if (!state.editingUserScript && state.userScriptSttText.isNullOrBlank()) {
                // 아무것도 없을 때 안내 없이 빈 공간
            }
        }
    }
}
