package com.opic.android.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.navigation.LocalBottomNavState
import com.opic.android.ui.navigation.Screen

/**
 * Python SurveyPage 1:1 이식.
 * 4파트 설문 (Part 1~4) + Back/Next 네비게이션.
 * 하단 Back/Home/Next 버튼은 OPicBottomBar에서 표시.
 */
@Composable
fun SurveyScreen(
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onNext: () -> Unit,
    viewModel: SurveyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val bottomNavState = LocalBottomNavState.current

    // 하단바에 Back/Home/Next 액션 설정 (ownerRoute 기반으로 stale 액션 방지)
    DisposableEffect(Unit) {
        bottomNavState.setOwnerActions(
            ownerRoute = Screen.Survey.route,
            back = { if (viewModel.goBack()) onBack() },
            home = onHome,
            next = { if (viewModel.goNext()) onNext() },
            nextEnabled = true
        )
        onDispose { bottomNavState.clearOwnerActions(Screen.Survey.route) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 상단: Part 표시 ---
            Text(
                text = "Background Survey - Part ${state.currentPart + 1} of 4",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // --- 콘텐츠 (스크롤) ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                when (state.currentPart) {
                    0 -> Part1Content(state, viewModel)
                    1 -> Part2Content(state, viewModel)
                    2 -> Part3Content(state, viewModel)
                    3 -> Part4Content(state, viewModel)
                }
            }
        }
    }
}

// ==================== Part 1 ====================

@Composable
private fun Part1Content(state: SurveyUiState, vm: SurveyViewModel) {
    SectionTitle("현재 귀하는 어느 분야에 종사하고 계십니까?")
    RadioGroup(
        options = PART1_MAIN_OPTIONS,
        selected = state.part1Main,
        onSelect = { vm.setPart1Main(it) }
    )

    // 조건부 하위 질문
    val subQuestion = PART1_SUB_QUESTIONS[state.part1Main]
    val subOptions = PART1_SUB_OPTIONS[state.part1Main]
    if (subQuestion != null && subOptions != null) {
        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle(subQuestion)
        RadioGroup(
            options = subOptions,
            selected = state.part1Sub,
            onSelect = { vm.setPart1Sub(it) }
        )
    }
}

// ==================== Part 2 ====================

@Composable
private fun Part2Content(state: SurveyUiState, vm: SurveyViewModel) {
    SectionTitle("현재 당신은 학생입니까?")
    RadioGroup(
        options = listOf("예", "아니요"),
        selected = state.part2Main,
        onSelect = { vm.setPart2Main(it) }
    )

    if (state.part2Main != -1) {
        Spacer(modifier = Modifier.height(16.dp))
        val subTitle = if (state.part2Main == 0)
            "현재 어떤 강의를 듣고 있습니까?"
        else
            "최근 어떤 강의를 수강했습니까?"
        val subOptions = if (state.part2Main == 0) PART2_SUB_YES else PART2_SUB_NO

        SectionTitle(subTitle)
        RadioGroup(
            options = subOptions,
            selected = state.part2Sub,
            onSelect = { vm.setPart2Sub(it) }
        )
    }
}

// ==================== Part 3 ====================

@Composable
private fun Part3Content(state: SurveyUiState, vm: SurveyViewModel) {
    SectionTitle("현재 귀하는 어디에 살고 계십니까?")
    RadioGroup(
        options = PART3_OPTIONS,
        selected = state.part3Selection,
        onSelect = { vm.setPart3(it) }
    )
}

// ==================== Part 4 ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Part4Content(state: SurveyUiState, vm: SurveyViewModel) {
    CheckboxSection(
        title = "귀하는 여가 활동으로 주로 무엇을 하십니까? (두 개 이상 선택)",
        items = PART4_LEISURE,
        selected = state.part4Selections,
        onToggle = { vm.togglePart4(it) }
    )
    Spacer(modifier = Modifier.height(20.dp))
    CheckboxSection(
        title = "귀하의 취미나 관심사는 무엇입니까? (한 개 이상 선택)",
        items = PART4_HOBBIES,
        selected = state.part4Selections,
        onToggle = { vm.togglePart4(it) }
    )
    Spacer(modifier = Modifier.height(20.dp))
    CheckboxSection(
        title = "귀하는 어떤 종류의 운동을 즐기십니까? (한 개 이상 선택)",
        items = PART4_SPORTS,
        selected = state.part4Selections,
        onToggle = { vm.togglePart4(it) }
    )
    Spacer(modifier = Modifier.height(20.dp))
    CheckboxSection(
        title = "귀하는 어떤 휴가나 출장을 다녀온 경험이 있으십니까? (한 개 이상 선택)",
        items = PART4_TRAVEL,
        selected = state.part4Selections,
        onToggle = { vm.togglePart4(it) }
    )
}

// ==================== 공용 컴포넌트 ====================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun RadioGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == index, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckboxSection(
    title: String,
    items: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    SectionTitle(title)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        maxItemsInEachRow = 2
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .selectable(
                        selected = item in selected,
                        onClick = { onToggle(item) },
                        role = Role.Checkbox
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = item in selected, onCheckedChange = null)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
