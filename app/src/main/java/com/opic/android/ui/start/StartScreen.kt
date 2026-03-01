package com.opic.android.ui.start

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.R
import com.opic.android.ui.theme.OPicColors
import java.io.File

/**
 * OPIc 메인 화면.
 *
 * 레이아웃:
 *   설정 아이콘 (우상단)
 *   레벨 아바타 (외부 폴더 우선 → drawable fallback)
 *   "Level N" 텍스트
 *   게이지 바
 *   하단 고정 버튼 행: [Study] [Test >] [Review]
 */
@Composable
fun StartScreen(
    onStudy: () -> Unit,
    onNext: () -> Unit,
    onReview: () -> Unit,
    onReport: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: StartViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 중앙 콘텐츠 (아바타 + 텍스트 + 게이지)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 레벨 아바타
                LevelAvatar(
                    level = state.level,
                    externalDir = state.levelImageDir
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Level 텍스트
                Text(
                    text = "Level ${state.level}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OPicColors.TextOnLight
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.weight(1f))

                // 하단 버튼 행 (고정)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStudy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OPicColors.Secondary,
                            contentColor = OPicColors.StudyText
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Study", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OPicColors.Primary,
                            contentColor = OPicColors.PrimaryText
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                    ) {
                        Text("Test >", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    Button(
                        onClick = onReview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OPicColors.Secondary,
                            contentColor = OPicColors.ReviewText
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Review", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onReport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OPicColors.Secondary,
                            contentColor = OPicColors.TextOnLight
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Report", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 설정 아이콘: 향후 사용자 설정 추가 시 활성화
            // (현재 숨김 처리)
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
            modifier = Modifier.size(200.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(id = levelDrawable(level)),
            contentDescription = "Level $level Avatar",
            modifier = Modifier.size(200.dp),
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
