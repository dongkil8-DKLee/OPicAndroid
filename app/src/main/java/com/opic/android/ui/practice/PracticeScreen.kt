package com.opic.android.ui.practice

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.DiffText
import com.opic.android.ui.common.HomeButton
import com.opic.android.ui.theme.OPicColors

/**
 * 문장연습 화면 (이미지 기반 리디자인).
 * 레이아웃: SpeedFilterRow → TitleSelector → <<학습종료
 *           Answer섹션(전체스크립트+하이라이트) → 문장테이블+녹음버튼 → Result섹션
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PracticeContent(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    onBack: () -> Unit
) {
    val isBusy = state.isPlayingOriginal || state.isPlayingUser || state.isRecording || state.sttListening
    val currentSentenceText = state.sentences.getOrNull(state.currentIndex)?.segment?.text ?: ""
    val currentSentenceState = state.sentences.getOrNull(state.currentIndex)

    // 확대 섹션 상태: null=모두 표시, "answer"/"practice"/"result"=해당만 표시
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 최상단: 속도 + FilterList + Settings 아이콘 =====
        PracticeSpeedRow(state, viewModel)

        // ===== 타이틀 네비게이터 =====
        PracticeTitleRow(state, viewModel)

        // ===== << 학습 종료 =====
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("<< 학습 종료", fontSize = 13.sp, color = OPicColors.Primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ===== 스크롤 영역 =====
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== Answer 섹션 =====
            if (expandedSection == null || expandedSection == "answer") {
                AnswerSection(
                    answerScript = state.answerScript,
                    selectedSentenceText = currentSentenceText,
                    isPlayingTts = state.isPlayingOriginal,
                    canPlay = !isBusy,
                    isExpanded = expandedSection == "answer",
                    onPlay = { viewModel.playOriginal() },
                    onStop = { viewModel.stopOriginal() },
                    onExpandToggle = {
                        expandedSection = if (expandedSection == "answer") null else "answer"
                    }
                )
            }

            // ===== 녹음/문장연습 섹션 =====
            if (expandedSection == null || expandedSection == "practice") {
                PracticeSentenceSection(
                    state = state,
                    viewModel = viewModel,
                    isBusy = isBusy,
                    isExpanded = expandedSection == "practice",
                    onExpandToggle = {
                        expandedSection = if (expandedSection == "practice") null else "practice"
                    }
                )
            }

            // ===== Result 섹션 =====
            if (expandedSection == null || expandedSection == "result") {
                ResultSection(
                    sttText = currentSentenceState?.sttText,
                    analysisResult = currentSentenceState?.analysisResult,
                    expectedText = currentSentenceText,
                    isExpanded = expandedSection == "result",
                    onExpandToggle = {
                        expandedSection = if (expandedSection == "result") null else "result"
                    },
                    onAnalyze = { viewModel.analyzeCurrentSentence() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 하단: Home 버튼 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            HomeButton(onClick = onBack)
        }
    }
}

// ==================== 속도 행 ====================

@Composable
private fun PracticeSpeedRow(state: PracticeUiState, viewModel: PracticeViewModel) {
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
                onClick = { viewModel.setPlaybackSpeed(speed) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) OPicColors.Primary else Color.Gray
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // FilterList 아이콘 (향후 필터 기능 확장용 — 현재는 표시만)
        Icon(
            Icons.Filled.FilterList,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Settings 아이콘 (향후 설정 연동용)
        Icon(
            Icons.Filled.Settings,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(22.dp)
        )
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

        // 제목 표시
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

// ==================== Answer 섹션 ====================

@Composable
private fun AnswerSection(
    answerScript: String,
    selectedSentenceText: String,
    isPlayingTts: Boolean,
    canPlay: Boolean,
    isExpanded: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onExpandToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlayingTts) {
                    TextButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Stop", fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = onPlay, enabled = canPlay && selectedSentenceText.isNotBlank()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Play", fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isExpanded) "축소" else "확대",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 전체 스크립트 (선택된 문장 하이라이트)
        if (answerScript.isNotBlank()) {
            HighlightedAnswerText(
                fullText = answerScript,
                highlightedSentence = selectedSentenceText
            )
        } else {
            Text("스크립트 없음", color = OPicColors.DisabledBg, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

/** 전체 텍스트에서 선택된 문장을 Primary 색상으로 하이라이트 */
@Composable
private fun HighlightedAnswerText(fullText: String, highlightedSentence: String) {
    val annotated = remember(fullText, highlightedSentence) {
        if (highlightedSentence.isBlank()) {
            buildAnnotatedString { append(fullText) }
        } else {
            val idx = fullText.indexOf(highlightedSentence)
            if (idx < 0) {
                buildAnnotatedString { append(fullText) }
            } else {
                buildAnnotatedString {
                    append(fullText.substring(0, idx))
                    withStyle(SpanStyle(color = OPicColors.Primary, fontWeight = FontWeight.Bold)) {
                        append(fullText.substring(idx, idx + highlightedSentence.length))
                    }
                    append(fullText.substring(idx + highlightedSentence.length))
                }
            }
        }
    }

    Text(
        text = annotated,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

// ==================== 녹음/문장연습 섹션 ====================

@Composable
private fun PracticeSentenceSection(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isBusy: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val currentSentence = state.sentences.getOrNull(state.currentIndex)
    val hasUserRecording = currentSentence?.userRecordingPath != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: 컨트롤 버튼들
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

            // Rec+STT 통합
            if (!state.isCombinedRecording) {
                TextButton(
                    onClick = { viewModel.startRecordAndStt() },
                    enabled = !isBusy
                ) {
                    Text("Rec+STT", fontSize = 11.sp, color = Color(0xFF8E44AD))
                }
            } else {
                TextButton(onClick = { viewModel.stopRecordAndStt() }) {
                    Text("Stop All", fontSize = 11.sp, color = OPicColors.RecordActive)
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

        // 문장 테이블
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            .border(
                width = 0.5.dp,
                color = OPicColors.Border
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 선택 표시 박스
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

// ==================== Result 섹션 ====================

@Composable
private fun ResultSection(
    sttText: String?,
    analysisResult: com.opic.android.util.AnalysisResult?,
    expectedText: String,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onAnalyze: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Result", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2ECC71))

            IconButton(onClick = onExpandToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isExpanded) "축소" else "확대",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (analysisResult != null) {
            // 정확도 % 크게 표시
            Text(
                text = "정확도: ${analysisResult.accuracyPercent.toInt()}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    analysisResult.accuracyPercent >= 80 -> Color(0xFF2ECC71)
                    analysisResult.accuracyPercent >= 50 -> OPicColors.TimerOrange
                    else -> OPicColors.TimerRed
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // STT 텍스트
            if (!sttText.isNullOrBlank()) {
                Text("내 발화:", fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = sttText,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF3498DB), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Diff 표시
                if (expectedText.isNotBlank()) {
                    Text("단어 비교:", fontSize = 12.sp, color = Color.Gray)
                    DiffText(
                        expected = expectedText,
                        actual = sttText,
                        fontSize = 13
                    )
                }
            }
        } else if (!sttText.isNullOrBlank()) {
            // STT 결과는 있지만 분석 전
            Text("STT 결과:", fontSize = 12.sp, color = Color.Gray)
            Text(
                text = sttText,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3498DB), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("분석 버튼을 눌러 정확도를 확인하세요.", fontSize = 12.sp, color = Color.Gray)

            // Analyze 버튼
            TextButton(onClick = onAnalyze) {
                Text("분석하기", fontSize = 12.sp, color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "Rec+STT 버튼으로 녹음하고 발화 결과를 확인하세요.",
                color = OPicColors.DisabledBg,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
