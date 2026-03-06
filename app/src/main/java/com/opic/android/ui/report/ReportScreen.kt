package com.opic.android.ui.report

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.R
import com.opic.android.ui.theme.OPicColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Grade colors (SpeechAnalysisPanel.kt 기준)
private val GradeA = Color(0xFF2ECC71)
private val GradeB = Color(0xFF3498DB)
private val GradeC = Color(0xFFE67E22)
private val GradeD = Color(0xFFD35400)
private val GradeF = Color(0xFFE74C3C)

private fun gradeColor(grade: String): Color = when (grade) {
    "A" -> GradeA
    "B" -> GradeB
    "C" -> GradeC
    "D" -> GradeD
    else -> GradeF
}

@Composable
fun ReportScreen(
    onSessionClick: (Int) -> Unit = {},
    onVocabClick: () -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onGradeClick: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 통합 Q&A CSV 저장 launcher
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val content = viewModel.uiState.value.csvContent
            if (!content.isNullOrBlank()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Q&A CSV 저장 완료", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.clearCsvContent()
        }
    }

    // Q&A CSV 가져오기 launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importQaCsvFromUri(uri)
    }

    // DB 백업 저장 launcher
    val dbBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val bytes = viewModel.uiState.value.dbBackupBytes
            if (bytes != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    Toast.makeText(context, "DB 백업 저장 완료", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.clearDbBackupBytes()
        }
    }

    // DB 복원 파일 선택 launcher
    val dbRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreDatabaseFromUri(uri)
    }

    // Q&A CSV 준비 완료 → 파일 선택 다이얼로그
    LaunchedEffect(state.csvContent) {
        if (!state.csvContent.isNullOrBlank()) {
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            csvLauncher.launch("shadowtalk_qa_$date.csv")
        }
    }

    // DB 백업 준비 완료 → 파일 저장 다이얼로그
    LaunchedEffect(state.dbBackupBytes) {
        if (state.dbBackupBytes != null) {
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            dbBackupLauncher.launch("shadowtalk_backup_$date.db")
        }
    }

    // 가져오기/복원 결과 Toast
    LaunchedEffect(state.importResult) {
        if (!state.importResult.isNullOrBlank()) {
            Toast.makeText(context, state.importResult, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OPicColors.Primary)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    // 헤더: 레벨 아바타 + Level N + 게이지
                    LevelHeaderSection(state)

                    // 섹션 1: Overall Stats
                    OverallStatsSection(state)

                    // 단어장 요약 카드 (클릭 → VocabularyScreen)
                    VocabSummarySection(state, onVocabClick)

                    // 섹션 2: Weekly Activity
                    WeeklyActivitySection(state)

                    // 섹션 3: Grade Distribution (클릭 → StudyScreen)
                    GradeDistributionSection(state, onGradeClick)

                    // 섹션 4: Topic Weakness (클릭 → StudyScreen)
                    TopicWeaknessSection(state, onTopicClick)

                    // 섹션 5: Recent Tests (잠금/삭제 메뉴)
                    RecentTestsSection(state, viewModel, onSessionClick)

                    // 섹션 6: 데이터 관리
                DataManagementSection(
                    state        = state,
                    onExportQa   = { viewModel.prepareCsvExport() },
                    onImportQa   = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                    onBackupDb   = { viewModel.backupDatabase() },
                    onRestoreDb  = { dbRestoreLauncher.launch(arrayOf("*/*")) }
                )
            }

            // ⚙️ 설정 버튼 — 우상단 오버레이
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = OPicColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            } // Box 닫힘
        }
    }
}

// ==================== 레벨 헤더 (StartScreen에서 이동) ====================

@Composable
private fun LevelHeaderSection(state: ReportUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 레벨 아바타
        LevelAvatar(level = state.level, externalDir = state.levelImageDir)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Level ${state.level}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OPicColors.TextOnLight
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 게이지 바
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { state.gaugePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = OPicColors.LevelGauge,
                trackColor = OPicColors.Border,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${state.gaugePercent}%",
                fontSize = 14.sp,
                color = OPicColors.TextOnLight
            )
        }
    }
}

/** 외부 폴더 우선 이미지 로드, 없으면 drawable fallback */
@Composable
private fun LevelAvatar(level: Int, externalDir: String) {
    val externalBitmap = remember(level, externalDir) {
        if (externalDir.isNotBlank()) {
            val file = File(externalDir, "level_$level.png")
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (_: Exception) { null }
            } else null
        } else null
    }

    if (externalBitmap != null) {
        Image(
            bitmap = externalBitmap.asImageBitmap(),
            contentDescription = "Level $level Avatar",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(id = levelDrawable(level)),
            contentDescription = "Level $level Avatar",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** level 번호(1~10) → R.drawable.level_N 매핑 */
private fun levelDrawable(level: Int): Int {
    return when (level) {
        1 -> R.drawable.level_1
        2 -> R.drawable.level_2
        3 -> R.drawable.level_3
        4 -> R.drawable.level_4
        5 -> R.drawable.level_5
        6 -> R.drawable.level_6
        7 -> R.drawable.level_7
        8 -> R.drawable.level_8
        9 -> R.drawable.level_9
        10 -> R.drawable.level_10
        else -> R.drawable.level_1
    }
}

// ==================== 단어장 요약 (클릭 → VocabularyScreen) ====================

@Composable
private fun VocabSummarySection(state: ReportUiState, onVocabClick: () -> Unit) {
    SectionCard(
        title = "단어장 현황",
        modifier = Modifier.clickable { onVocabClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("총 단어", "${state.vocabTotal}")
            StatItem("암기완료", "${state.vocabMemorized}")
            StatItem("미암기", "${state.vocabTotal - state.vocabMemorized}")
            StatItem("즐겨찾기", "${state.vocabFavorites}")
        }

        if (state.vocabTotal > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("진행률:", fontSize = 12.sp, color = OPicColors.TextOnLight)
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { state.vocabMemorized.toFloat() / state.vocabTotal },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = GradeA,
                    trackColor = OPicColors.Border
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(state.vocabMemorized * 100f / state.vocabTotal).toInt()}%",
                    fontSize = 12.sp,
                    color = OPicColors.TextOnLight
                )
            }
        }

        if (state.vocabRecentWeekAdded > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "최근 7일 추가: ${state.vocabRecentWeekAdded}단어",
                fontSize = 12.sp,
                color = OPicColors.Primary
            )
        }
    }
}

// ==================== 섹션 1: Overall Stats ====================

@Composable
private fun OverallStatsSection(state: ReportUiState) {
    SectionCard(title = "전체 현황") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("전체", "${state.totalQuestions}")
            StatItem("학습", "${state.studiedQuestions}")
            StatItem("완료율", "${state.completionPercent.toInt()}%")
            StatItem("즐겨찾기", "${state.favoriteCount}")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OPicColors.Primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = OPicColors.TextOnLight
        )
    }
}

// ==================== 섹션 2: Weekly Activity ====================

@Composable
private fun WeeklyActivitySection(state: ReportUiState) {
    SectionCard(title = "주간 활동") {
        Text(
            text = "이번 주 학습: ${state.weeklyStudied}문제",
            fontSize = 14.sp,
            color = OPicColors.TextOnLight
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                (state.weeklyStudied.toFloat() / state.weeklyGoal).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = OPicColors.LevelGauge,
            trackColor = OPicColors.Border
        )
        Text(
            text = "${state.weeklyStudied} / ${state.weeklyGoal} 목표",
            fontSize = 11.sp,
            color = OPicColors.DisabledBg,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ==================== 섹션 3: Grade Distribution ====================

@Composable
private fun GradeDistributionSection(state: ReportUiState, onGradeClick: (String) -> Unit) {
    val grades = listOf("A", "B", "C", "D", "F")
    val maxCount = state.gradeDistribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    SectionCard(title = "등급 분포") {
        if (state.gradeDistribution.values.all { it == 0 }) {
            Text(
                text = "분석 데이터가 없습니다",
                fontSize = 13.sp,
                color = OPicColors.DisabledBg
            )
        } else {
            grades.forEach { grade ->
                val count = state.gradeDistribution[grade] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { if (count > 0) onGradeClick(grade) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = grade,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor(grade),
                        modifier = Modifier.width(24.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OPicColors.Border)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(gradeColor(grade))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$count",
                        fontSize = 13.sp,
                        color = OPicColors.TextOnLight,
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
        }
    }
}

// ==================== 섹션 4: Topic Weakness (클릭 → StudyScreen) ====================

@Composable
private fun TopicWeaknessSection(state: ReportUiState, onTopicClick: (String) -> Unit) {
    SectionCard(title = "주제별 약점") {
        if (state.topicWeakness.isEmpty()) {
            Text(
                text = "분석 데이터가 없습니다",
                fontSize = 13.sp,
                color = OPicColors.DisabledBg
            )
        } else {
            state.topicWeakness.forEach { topic ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onTopicClick(topic.type) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = topic.type,
                        fontSize = 13.sp,
                        color = OPicColors.TextOnLight,
                        modifier = Modifier.width(80.dp)
                    )
                    LinearProgressIndicator(
                        progress = { topic.averageAccuracy / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = when {
                            topic.averageAccuracy >= 75f -> GradeA
                            topic.averageAccuracy >= 50f -> GradeC
                            else -> GradeF
                        },
                        trackColor = OPicColors.Border
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${topic.averageAccuracy.toInt()}%",
                        fontSize = 13.sp,
                        color = OPicColors.TextOnLight,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}

// ==================== 섹션 5: Recent Tests (잠금/삭제 메뉴) ====================

@Composable
private fun RecentTestsSection(
    state: ReportUiState,
    viewModel: ReportViewModel,
    onSessionClick: (Int) -> Unit
) {
    SectionCard(title = "최근 테스트") {
        if (state.recentTests.isEmpty()) {
            Text(
                text = "테스트 기록이 없습니다",
                fontSize = 13.sp,
                color = OPicColors.DisabledBg
            )
        } else {
            state.recentTests.forEach { session ->
                val isLocked = session.sessionId in state.lockedSessions
                var showMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSessionClick(session.sessionId) },
                    colors = CardDefaults.cardColors(containerColor = OPicColors.Secondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLocked) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "잠금",
                                    modifier = Modifier.size(14.dp),
                                    tint = OPicColors.Primary
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "Session #${session.sessionId}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OPicColors.TextOnLight
                            )
                        }
                        Text(
                            text = "${session.questionCount}문제",
                            fontSize = 13.sp,
                            color = OPicColors.Primary
                        )
                        Text(
                            text = session.timestamp?.take(16) ?: "",
                            fontSize = 11.sp,
                            color = OPicColors.DisabledBg
                        )

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "메뉴",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Gray
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isLocked) "잠금 해제" else "잠금")
                                    },
                                    onClick = {
                                        viewModel.toggleLock(session.sessionId)
                                        showMenu = false
                                    }
                                )
                                if (!isLocked) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("삭제", color = Color.Red)
                                        },
                                        onClick = {
                                            viewModel.deleteSession(session.sessionId)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 섹션 6: 데이터 관리 ====================

@Composable
private fun DataManagementSection(
    state: ReportUiState,
    onExportQa: () -> Unit,
    onImportQa: () -> Unit,
    onBackupDb: () -> Unit,
    onRestoreDb: () -> Unit
) {
    SectionCard(title = "데이터 관리") {
        // 전체 백업
        Text(
            text = "전체 백업 (모든 데이터)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OPicColors.TextOnLight
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBackupDb,
                enabled = !state.isBackingUp,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OPicColors.Primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isBackingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("DB 백업", fontSize = 13.sp)
                }
            }
            OutlinedButton(
                onClick = onRestoreDb,
                enabled = !state.isRestoring,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OPicColors.Primary)
                } else {
                    Text("DB 복원", fontSize = 13.sp, color = OPicColors.Primary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Q&A 스크립트 편집
        Text(
            text = "스크립트 편집",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OPicColors.TextOnLight
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExportQa,
                enabled = !state.csvExporting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OPicColors.Primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.csvExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Q&A 내보내기", fontSize = 13.sp)
                }
            }
            OutlinedButton(
                onClick = onImportQa,
                enabled = !state.importingCsv,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.importingCsv) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OPicColors.Primary)
                } else {
                    Text("Q&A 가져오기", fontSize = 13.sp, color = OPicColors.Primary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "내보내기 컬럼: question_text, answer_script, user_script, ai_answer + 학습현황 포함\n가져오기: 같은 CSV를 수정 후 불러오면 DB 자동 업데이트",
            fontSize = 11.sp,
            color = OPicColors.DisabledBg,
            lineHeight = 16.sp
        )
    }
}

// ==================== 공통 컴포넌트 ====================

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OPicColors.Surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OPicColors.TextOnLight
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
