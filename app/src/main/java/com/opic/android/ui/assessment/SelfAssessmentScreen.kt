package com.opic.android.ui.assessment

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opic.android.ui.common.HomeButton
import com.opic.android.ui.theme.OPicColors

/**
 * Python SelfAssessmentPage 1:1 이식.
 * 난이도 1~6 Radio 선택 → 미선택 시 경고 → 선택 시 BeginTestPage로 이동.
 */

private val DIFFICULTY_DESCRIPTIONS = listOf(
    "나는 10단어 이하의 단어로 말할 수 있습니다.",
    "나는 기본적인 물건, 색깔, 요일, 음식, 의류, 숫자 등을 말할 수 있습니다. 나는 항상 완벽한 문장을 구사하지는 못하고 간단한 질문도 하기 어렵습니다.",
    "나는 나 자신, 직장, 친숙한 사람과 장소, 일상에 대한 기본적인 정보를 간단한 문장으로 전달할 수 있습니다.",
    "나는 나 자신, 일상, 일/학교, 취미에 대해 간단한 대화를 할 수 있습니다. 나는 이런 친숙한 주제와 일상에 대해 일련의 간단한 문장들을 쉽게 만들 수 있습니다. 내가 필요한 것을 얻기 위한 질문도 할 수 있습니다.",
    "나는 친숙한 주제와 가정, 일/학교, 개인 및 사회적 관심사에 대해 대화할 수 있습니다. 나는 일어난 일과 일어나고 있는 일, 일어날 일에 대해 문장을 연결하여 말할 수 있습니다. 필요한 경우 설명을 할 수 있습니다. 일상 생활에서 예기치 못한 상황이 발생하더라도 임기응변으로 대처할 수 있습니다.",
    "나는 일/학교, 개인적인 관심사, 시사 문제에 대한 어떤 대화나 토론에도 자신 있게 참여할 수 있습니다. 대부분의 주제에 관해 높은 수준의 정확성과 폭넓은 어휘로 여전히 상세히 설명할 수 있습니다."
)

@Composable
fun SelfAssessmentScreen(
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onNext: (difficulty: Int) -> Unit
) {
    var selectedDifficulty by remember { mutableIntStateOf(-1) }
    var showWarning by remember { mutableStateOf(false) }

    // 경고 다이얼로그
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("난이도 선택") },
            text = { Text("먼저 본인의 영어 말하기 수준(1-6)을 선택해야 합니다.") },
            confirmButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("확인")
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 상단 제목 ---
            Text(
                text = "Self Assessment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "본 Self Assessment에 대한 응답을 기초로 개인별 문항이 출제됩니다. 설명을 잘 읽고 본인의 English 말하기 능력과 비슷한 수준을 선택하시기 바랍니다.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- 난이도 옵션 (스크롤) ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                DIFFICULTY_DESCRIPTIONS.forEachIndexed { index, description ->
                    val level = index + 1
                    val isSelected = selectedDifficulty == level

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) OPicColors.Primary else OPicColors.Border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .selectable(
                                selected = isSelected,
                                onClick = { selectedDifficulty = level },
                                role = Role.RadioButton
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 레벨 번호
                        Text(
                            text = "$level",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.size(30.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- 하단: Back / Home / Next ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("< Back", fontWeight = FontWeight.Bold)
                }

                HomeButton(onClick = onHome)

                Button(
                    onClick = {
                        if (selectedDifficulty == -1) {
                            showWarning = true
                        } else {
                            onNext(selectedDifficulty)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OPicColors.Primary,
                        contentColor = OPicColors.PrimaryText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Next >", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
