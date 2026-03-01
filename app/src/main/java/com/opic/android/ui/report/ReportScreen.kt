package com.opic.android.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.HomeButton
import com.opic.android.ui.theme.OPicColors

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
    onHome: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OPicColors.Primary)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 헤더
                    Text(
                        text = "학습 리포트",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OPicColors.TextOnLight
                    )

                    // 섹션 1: Overall Stats
                    OverallStatsSection(state)

                    // 섹션 2: Weekly Activity
                    WeeklyActivitySection(state)

                    // 섹션 3: Grade Distribution
                    GradeDistributionSection(state)

                    // 섹션 4: Topic Weakness
                    TopicWeaknessSection(state)

                    // 섹션 5: Recent Tests
                    RecentTestsSection(state)
                }

                // 하단 Home 버튼
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HomeButton(onClick = onHome)
                }
            }
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
private fun GradeDistributionSection(state: ReportUiState) {
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

// ==================== 섹션 4: Topic Weakness ====================

@Composable
private fun TopicWeaknessSection(state: ReportUiState) {
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

// ==================== 섹션 5: Recent Tests ====================

@Composable
private fun RecentTestsSection(state: ReportUiState) {
    SectionCard(title = "최근 테스트") {
        if (state.recentTests.isEmpty()) {
            Text(
                text = "테스트 기록이 없습니다",
                fontSize = 13.sp,
                color = OPicColors.DisabledBg
            )
        } else {
            state.recentTests.forEach { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = OPicColors.LightBg),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Session #${session.sessionId}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OPicColors.TextOnLight
                        )
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
                    }
                }
            }
        }
    }
}

// ==================== 공통 컴포넌트 ====================

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OPicColors.TextOnLight
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
