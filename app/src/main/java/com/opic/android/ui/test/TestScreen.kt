package com.opic.android.ui.test

import androidx.activity.compose.BackHandler
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.R
import java.io.File
import com.opic.android.ui.navigation.LocalBottomNavState
import com.opic.android.ui.navigation.Screen
import com.opic.android.ui.theme.OPicColors

/**
 * OPIc 시험 화면 (2차 UI 개선).
 * 2열 레이아웃: 좌(EVA+타이머+버튼), 우(번호그리드 3열).
 * 하단 Back/Next 버튼은 OPicBottomBar에서 표시.
 */
@Composable
fun TestScreen(
    onHome: () -> Unit,
    onFinish: (Int) -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showHomeDialog by remember { mutableStateOf(false) }
    val bottomNavState = LocalBottomNavState.current

    // 시험 완료 → Review(sessionId)
    LaunchedEffect(state.phase) {
        if (state.phase == TestPhase.FINISHED) onFinish(viewModel.sessionId)
    }

    // 하단바에 Back/Next 액션 설정 (ownerRoute 기반으로 stale 액션 방지)
    // 초기 진입 순간 Next 오작동 방지: nextEnabled = false로 잠금 후,
    // 아래 LaunchedEffect에서 조건 충족 시에만 true로 업데이트
    DisposableEffect(Unit) {
        bottomNavState.setOwnerActions(
            ownerRoute = Screen.Test.route,
            back = { showHomeDialog = true },
            home = null,
            next = { viewModel.onNext() },
            nextEnabled = false
        )
        onDispose { bottomNavState.clearOwnerActions(Screen.Test.route) }
    }

    // nextEnabled: 상태에 따라 업데이트
    LaunchedEffect(state.phase, state.canStop) {
        bottomNavState.nextEnabled = state.phase == TestPhase.RECORDED ||
                (state.phase == TestPhase.RECORDING && state.canStop)
    }

    // Back 키 → Home 확인
    BackHandler { showHomeDialog = true }

    // Home 확인 다이얼로그
    if (showHomeDialog) {
        AlertDialog(
            onDismissRequest = { showHomeDialog = false },
            title = { Text("시험 중단") },
            text = { Text("시험이 진행 중입니다. 나가시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showHomeDialog = false
                    viewModel.onHome()
                    onHome()
                }) { Text("나가기") }
            },
            dismissButton = {
                TextButton(onClick = { showHomeDialog = false }) { Text("계속") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.phase) {
            TestPhase.LOADING -> LoadingScreen(
                aiTopic = state.aiGeneratingTopic,
                aiProgress = state.aiGeneratingProgress,
                aiTotal = state.aiGeneratingTotal
            )
            else -> TestContent(state, viewModel)
        }
    }
}

@Composable
private fun LoadingScreen(
    aiTopic: String = "",
    aiProgress: Int = 0,
    aiTotal: Int = 0
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            if (aiTotal > 0 && aiTopic.isNotBlank()) {
                Text("AI가 문제를 생성하고 있습니다...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("「$aiTopic」", fontSize = 14.sp, color = OPicColors.Primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${aiProgress + 1} / $aiTotal 주제", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (aiProgress + 1).toFloat() / aiTotal },
                    modifier = Modifier.fillMaxWidth(),
                    color = OPicColors.Primary
                )
            } else {
                Text("문제 생성 중...")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestContent(
    state: TestUiState,
    viewModel: TestViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // --- 상단: Question X of N ---
        Text(
            text = "Question ${state.currentIndex + 1} of ${state.totalQuestions}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // --- 중앙: 2열 레이아웃 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 좌측 열: EVA + 타이머 + 버튼
            Column(
                modifier = Modifier.weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 레벨 이미지 (Report 연동)
                LevelImage(level = state.level, externalDir = state.levelImageDir)

                Spacer(modifier = Modifier.height(8.dp))

                // 타이머
                val timerColor = when {
                    state.countdownSeconds > 60 -> OPicColors.TimerGreen
                    state.countdownSeconds > 30 -> OPicColors.TimerOrange
                    else -> OPicColors.TimerRed
                }
                Text(
                    text = "%02d:%02d".format(state.countdownSeconds / 60, state.countdownSeconds % 60),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.phase == TestPhase.RECORDING) timerColor
                            else MaterialTheme.colorScheme.onSurface
                )

                // 타이머 프로그레스 바 (RECORDING 중에만)
                if (state.phase == TestPhase.RECORDING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.countdownSeconds / 120f },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = timerColor,
                        trackColor = OPicColors.Border,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Play + Record 버튼
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play 버튼
                    IconButton(
                        onClick = { viewModel.onPlay() },
                        enabled = state.phase == TestPhase.INITIAL,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (state.phase == TestPhase.INITIAL) OPicColors.PlayButton
                                        else OPicColors.DisabledBg,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White
                        )
                    }

                    // Record/Stop 버튼
                    IconButton(
                        onClick = { viewModel.onStopRecording() },
                        enabled = state.phase == TestPhase.RECORDING && state.canStop,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (state.phase == TestPhase.RECORDING) OPicColors.RecordActive
                                        else OPicColors.DisabledBg,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            if (state.phase == TestPhase.RECORDING) Icons.Filled.Stop
                            else Icons.Filled.Mic,
                            contentDescription = "Record",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

            }

            // 우측 열: 번호 그리드 (3열)
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.Center
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 3
                ) {
                    for (i in 0 until state.totalQuestions) {
                        val bgColor = when {
                            i == state.currentIndex -> OPicColors.GridCurrent
                            i in state.answeredIndices -> OPicColors.GridAnswered
                            else -> OPicColors.GridDefault
                        }
                        val textColor = when {
                            i == state.currentIndex -> Color.White
                            i in state.answeredIndices -> Color.White
                            else -> Color.Black
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .border(1.dp, OPicColors.Border, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${i + 1}",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 레벨 이미지: 외부 폴더 우선, 없으면 drawable fallback */
@Composable
private fun LevelImage(level: Int, externalDir: String) {
    val bitmap = remember(level, externalDir) {
        if (externalDir.isNotBlank()) {
            val file = File(externalDir, "level_$level.png")
            if (file.exists()) {
                try { BitmapFactory.decodeFile(file.absolutePath) }
                catch (_: Exception) { null }
            } else null
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Level $level",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(id = levelDrawable(level)),
            contentDescription = "Level $level",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
    }
}

private fun levelDrawable(level: Int): Int = when (level) {
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
