package com.opic.android.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.util.AnalysisResult

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechAnalysisPanel(
    result: AnalysisResult,
    expectedText: String,
    actualText: String,
    fontSize: Int = 16
) {
    val animatedAccuracy by animateFloatAsState(
        targetValue = result.accuracyPercent / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "accuracy"
    )
    val color = gradeColor(result.grade)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Grade badge + progress bar + accuracy
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grade badge
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = color
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = result.grade,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { animatedAccuracy },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = color,
                    trackColor = Color(0xFFE0E0E0),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${result.accuracyPercent.toInt()}% 정확도",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Word count comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CountChip("예상", result.expectedWordCount, Color(0xFF607D8B))
            CountChip("실제", result.actualWordCount, Color(0xFF607D8B))
            CountChip("일치", result.matchedCount, GradeA)
            CountChip("누락", result.missingCount, GradeF)
            CountChip("추가", result.extraCount, GradeB)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Feedback
        Text(
            text = result.feedback,
            fontSize = 14.sp,
            color = Color(0xFF555555),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        )

        // Missing words
        if (result.missingWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("누락 단어:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GradeF)
            Spacer(modifier = Modifier.height(4.dp))
            WordChipFlow(words = result.missingWords, color = GradeF, maxShow = 20)
        }

        // Extra words
        if (result.extraWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("추가 단어:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GradeB)
            Spacer(modifier = Modifier.height(4.dp))
            WordChipFlow(words = result.extraWords, color = GradeB, maxShow = 20)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diff (항상 표시)
        if (expectedText.isNotBlank() && actualText.isNotBlank()) {
            DiffText(
                expected = expectedText,
                actual = actualText,
                fontSize = fontSize
            )
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChipFlow(words: List<String>, color: Color, maxShow: Int) {
    val displayed = if (words.size > maxShow) words.take(maxShow) else words
    val remaining = words.size - displayed.size

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        displayed.forEach { word ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = word,
                    fontSize = 12.sp,
                    color = color,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (remaining > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE0E0E0)
            ) {
                Text(
                    text = "+$remaining more",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
