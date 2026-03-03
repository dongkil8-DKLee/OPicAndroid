package com.opic.android.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.ui.theme.OPicColors

/**
 * 두 파형 + 동시재생 버튼 + 볼륨 밸런스 슬라이더 패널.
 *
 * @param originalWaveform 원본 음성 파형 데이터
 * @param userWaveform 사용자 녹음 파형 데이터
 * @param isPlaying 동시 재생 중 여부
 * @param originalProgress 원본 재생 진행률
 * @param userProgress 사용자 재생 진행률 (0~1, userStartFraction 이후 구간 기준)
 * @param balance 볼륨 밸런스 (0.0=원본만, 0.5=둘다, 1.0=녹음만)
 * @param enabled 비활성화 여부 (다른 오디오 활동 중)
 * @param onTogglePlayback 동시 재생 토글
 * @param onBalanceChange 밸런스 변경
 * @param userPlayProgress PLAY 단독 재생 진행률 (0~1)
 * @param comparisonSpeed 동시재생 속도
 * @param onComparisonSpeedChange 속도 변경 콜백
 */
@Composable
fun WaveformComparisonPanel(
    originalWaveform: FloatArray,
    userWaveform: FloatArray,
    isPlaying: Boolean,
    originalProgress: Float,
    userProgress: Float,
    balance: Float,
    enabled: Boolean,
    onTogglePlayback: () -> Unit,
    onBalanceChange: (Float) -> Unit,
    userStartFraction: Float = 0f,
    onUserStartFractionChange: (Float) -> Unit = {},
    userPlayProgress: Float = 0f,
    comparisonSpeed: Float = 1.0f,
    onComparisonSpeedChange: (Float) -> Unit = {},
    // REC / PLAY 버튼 (선택적)
    isRecordingUser: Boolean = false,
    isPlayingUser: Boolean = false,
    hasUserAudio: Boolean = false,
    onStartRecording: (() -> Unit)? = null,
    onStopRecording: (() -> Unit)? = null,
    onPlayUser: (() -> Unit)? = null,
    onStopUser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 헤더: REC + PLAY + 동시재생
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // REC 버튼
            if (onStartRecording != null) {
                if (isRecordingUser) {
                    TextButton(onClick = { onStopRecording?.invoke() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                        Text(" Stop", fontSize = 11.sp, color = OPicColors.RecordActive)
                    }
                } else {
                    TextButton(
                        onClick = onStartRecording,
                        enabled = !isPlayingUser && !isPlaying
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Rec", fontSize = 11.sp)
                    }
                }
            }

            // PLAY 버튼 (내 녹음 단독 재생, 항상 처음부터)
            if (onPlayUser != null) {
                if (isPlayingUser) {
                    TextButton(onClick = { onStopUser?.invoke() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Stop", fontSize = 11.sp)
                    }
                } else {
                    TextButton(
                        onClick = onPlayUser,
                        enabled = hasUserAudio && !isRecordingUser && !isPlaying
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Play", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 동시재생 버튼
            if (isPlaying) {
                TextButton(onClick = onTogglePlayback) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = OPicColors.RecordActive)
                    Text(" 정지", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = onTogglePlayback,
                    enabled = enabled && userWaveform.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" 동시재생", fontSize = 11.sp)
                }
            }
        }

        // 원본 파형
        Text(
            text = "원본 음성",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 2.dp)
        )
        WaveformView(
            samples = originalWaveform,
            waveformColor = OPicColors.LevelGauge,
            playbackProgress = if (isPlaying) originalProgress else null,
            height = 40.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 사용자 파형 (시작 마커 드래그 가능)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "내 녹음",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 2.dp)
            )
            if (!isPlaying) {
                Text(
                    text = " ← 드래그로 시작 위치 조절",
                    fontSize = 9.sp,
                    color = Color(0xFFFF9800)
                )
            }
        }
        WaveformView(
            samples = userWaveform,
            waveformColor = OPicColors.TimerGreen,
            playbackProgress = when {
                // 동시재생: userProgress(0~1)를 userStartFraction 기준 캔버스 좌표로 변환
                isPlaying -> (userStartFraction + userProgress * (1f - userStartFraction)).coerceIn(0f, 1f)
                // 단독 PLAY: 전체 파형 기준 진행 바
                isPlayingUser -> userPlayProgress
                else -> null
            },
            startMarkerFraction = if (!isPlaying && !isPlayingUser) userStartFraction else null,
            onStartMarkerChange = if (!isPlaying && !isPlayingUser) onUserStartFractionChange else null,
            height = 40.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 볼륨 밸런스 슬라이더 (항상 활성화)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("원본", fontSize = 10.sp, color = Color.Gray)
            Slider(
                value = balance,
                onValueChange = onBalanceChange,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                enabled = true,
                colors = SliderDefaults.colors(
                    thumbColor = OPicColors.Primary,
                    activeTrackColor = OPicColors.Primary,
                    inactiveTrackColor = OPicColors.Border
                )
            )
            Text("내 녹음", fontSize = 10.sp, color = Color.Gray)
        }

        // 속도 조절 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("속도", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 2.dp))
            Spacer(modifier = Modifier.width(4.dp))
            listOf(0.6f, 0.7f, 0.8f, 0.9f, 1.0f).forEach { speed ->
                val selected = comparisonSpeed == speed
                TextButton(
                    onClick = { onComparisonSpeedChange(speed) },
                    modifier = Modifier.padding(horizontal = 0.dp)
                ) {
                    Text(
                        text = if (speed == 1.0f) "1x" else "${speed}x",
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) OPicColors.Primary else Color.Gray
                    )
                }
            }
        }
    }
}
