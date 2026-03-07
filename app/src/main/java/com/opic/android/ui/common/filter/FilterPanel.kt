package com.opic.android.ui.common.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.ui.theme.OPicColors

/**
 * 공통 필터 패널 — Study/Practice 재사용. 1줄 레이아웃.
 *
 * showSort=true (Study 기본):
 *   Row: 주제 | 유형 | 정렬 | [모드] | 학습
 *
 * showSort=false (Practice):
 *   Row: 주제 | 유형 | 학습
 *
 * @param state          현재 필터 상태
 * @param onSetChanged   주제 변경 콜백
 * @param onTypeChanged  유형 변경 콜백
 * @param onSortChanged  정렬 변경 콜백
 * @param onStudyFilterChanged 학습 필터 변경 콜백
 * @param showSort       false이면 정렬 Picker 제거
 * @param pillHeight     각 Picker 버튼 높이 (기본 36dp)
 * @param showModeFilter true이면 모드 Picker 추가 (정렬 우측)
 * @param modeSelected   모드 현재 선택값
 * @param modeOptions    모드 선택 목록
 * @param onModeSelected 모드 변경 콜백
 */
@Composable
fun FilterPanel(
    state: StudyFilterState,
    onSetChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onSortChanged: (String) -> Unit,
    onStudyFilterChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSort: Boolean = true,
    pillHeight: Dp = 36.dp,
    showModeFilter: Boolean = false,
    modeSelected: String = "",
    modeOptions: List<String> = emptyList(),
    onModeSelected: (String) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomSheetPicker(
            label      = "주제",
            selected   = state.selectedSet,
            options    = listOf("전체") + state.sets,
            onSelected = onSetChanged,
            modifier   = Modifier.weight(1f),
            pillHeight = pillHeight
        )
        BottomSheetPicker(
            label      = "유형",
            selected   = state.selectedType,
            options    = listOf("전체") + state.types,
            onSelected = onTypeChanged,
            modifier   = Modifier.weight(1f),
            pillHeight = pillHeight
        )
        if (showSort) {
            BottomSheetPicker(
                label      = "정렬",
                selected   = state.selectedSort,
                options    = listOf("주제 순서", "오래된 순"),
                onSelected = onSortChanged,
                modifier   = Modifier.weight(1f),
                pillHeight = pillHeight
            )
        }
        if (showModeFilter && modeOptions.isNotEmpty()) {
            BottomSheetPicker(
                label      = "모드",
                selected   = modeSelected,
                options    = modeOptions,
                onSelected = onModeSelected,
                modifier   = Modifier.weight(1f),
                pillHeight = pillHeight
            )
        }
        BottomSheetPicker(
            label      = "학습",
            selected   = state.selectedStudyFilter,
            options    = listOf("전체", "📌", "저득점", "최근오답", "0", "1", "2", "3", "4", "5", "6", "7"),
            onSelected = onStudyFilterChanged,
            modifier   = Modifier.weight(1f),
            pillHeight = pillHeight
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// BottomSheetPicker — StudyScreen에서 이동
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPicker(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    pillHeight: Dp = 44.dp,
    cornerRadius: Dp = 10.dp
) {
    var showSheet by remember { mutableStateOf(false) }

    // Pill 버튼
    Box(
        modifier = modifier
            .height(pillHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(OPicColors.LightBg)
            .border(1.dp, OPicColors.Border, RoundedCornerShape(cornerRadius))
            .clickable { showSheet = true },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (label.isNotBlank()) {
                    Text(
                        text       = label,
                        fontSize   = 9.sp,
                        color      = OPicColors.TextOnLight.copy(alpha = 0.55f),
                        lineHeight = 11.sp
                    )
                }
                Text(
                    text       = selected,
                    fontSize   = 12.sp,
                    color      = OPicColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("▾", fontSize = 10.sp, color = OPicColors.TextOnLight.copy(alpha = 0.45f))
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor   = OPicColors.Surface,
            tonalElevation   = 0.dp
        ) {
            if (label.isNotBlank()) {
                Text(
                    text       = label,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = OPicColors.TextOnLight,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = OPicColors.Border)
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(options) { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                showSheet = false
                            }
                            .background(
                                if (isSelected) OPicColors.Primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text       = option,
                            fontSize   = 15.sp,
                            color      = if (isSelected) OPicColors.Primary else OPicColors.TextOnLight,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint     = OPicColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (option != options.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color    = OPicColors.Border.copy(alpha = 0.4f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
