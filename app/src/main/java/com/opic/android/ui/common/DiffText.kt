package com.opic.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.util.DiffType
import com.opic.android.util.WordDiff

private val MatchColor = Color(0xFF2ECC71)       // green
private val MatchBgColor = Color(0xFFE8F5E9)     // light green background
private val MissingColor = Color(0xFFE74C3C)     // red
private val ExtraColor = Color(0xFF3498DB)        // blue

@Composable
fun DiffText(
    expected: String,
    actual: String,
    fontSize: Int = 16
) {
    val segments = remember(expected, actual) {
        WordDiff.computeWordDiff(expected, actual)
    }

    if (segments.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        val annotated = buildAnnotatedString {
            segments.forEachIndexed { index, seg ->
                if (index > 0) append(" ")
                when (seg.type) {
                    DiffType.MATCH -> {
                        withStyle(SpanStyle(color = MatchColor, background = MatchBgColor)) {
                            append(seg.text)
                        }
                    }
                    DiffType.MISSING -> {
                        withStyle(SpanStyle(color = MissingColor, textDecoration = TextDecoration.LineThrough)) {
                            append(seg.text)
                        }
                    }
                    DiffType.EXTRA -> {
                        withStyle(SpanStyle(color = ExtraColor)) {
                            append(seg.text)
                        }
                    }
                }
            }
        }

        Text(
            text = annotated,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5).sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = MatchColor, label = "Match")
            Spacer(modifier = Modifier.width(12.dp))
            LegendItem(color = MissingColor, label = "Missing")
            Spacer(modifier = Modifier.width(12.dp))
            LegendItem(color = ExtraColor, label = "Extra")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = RoundedCornerShape(2.dp),
            color = color
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}
