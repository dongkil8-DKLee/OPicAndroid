package com.opic.android.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.ui.theme.OPicColors

/**
 * Pill 스타일 탭 행 (애니메이션 적용).
 * - 선택 배경: spring 기반 슬라이딩 (탄성 있는 자연스러운 이동)
 * - 텍스트 색상: tween 페이드 전환
 */
@Composable
fun PillTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 선택 인덱스를 실수로 애니메이션 (spring — 살짝 탄성)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "pill_index"
    )

    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = Color(0xFFE0E0E0),
        modifier = modifier.height(34.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
        ) {
            val pillWidth   = maxWidth / tabs.size
            val pillWidthPx = with(LocalDensity.current) { pillWidth.toPx() }

            // 슬라이딩 배경 pill
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = pillWidthPx * animatedIndex }
                    .background(OPicColors.Primary, RoundedCornerShape(17.dp))
            )

            // 탭 레이블 (텍스트 색상 페이드)
            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue    = if (isSelected) Color.White else Color.Gray,
                        animationSpec  = tween(durationMillis = 200),
                        label          = "tab_color_$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = label,
                            fontSize   = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = textColor
                        )
                    }
                }
            }
        }
    }
}
