package com.opic.android.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.android.ui.theme.OPicColors

/**
 * 두 파형 + 원본재생 + 동시재생 버튼 + 볼륨 밸런스 슬라이더 패널.
 *
 * @param originalWaveform 원본 음성 파형 데이터
 * @param userWaveform 사용자 녹음 파형 데이터
 * @param isPlaying 동시 재생 중 여부
 * @param originalProgress 동시재생 시 원본 진행률 (0~1)
 * @param userProgress 동시재생 시 사용자 진행률 (0~1, userStartFraction 이후 구간 기준)
 * @param balance 볼륨 밸런스 (0.0=원본만, 0.5=둘다, 1.0=녹음만)
 * @param enabled 비활성화 여부 (다른 오디오 활동 중)
 * @param onTogglePlayback 동시 재생 토글
 * @param onBalanceChange 밸런스 변경
 * @param isPlayingOriginal 원본 단독 재생 중 여부
 * @param onPlayOriginal 원본 단독 재생 콜백
 * @param onStopOriginal 원본 단독 재생 정지 콜백
 * @param originalPlayProgress 원본 단독 재생 진행률 (0~1, 파형 윈도우 기준)
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
    // 원본 파형 구간 마커 (주황=시작, 빨강=끝) — 드래그로 경계 조정
    segmentStartMarker: Float = 0f,
    segmentEndMarker: Float = 1f,
    onSegmentStartMarkerChange: ((Float) -> Unit)? = null,
    onSegmentEndMarkerChange: ((Float) -> Unit)? = null,
    // REC / PLAY 버튼 (선택적)
    isRecordingUser: Boolean = false,
    isPlayingUser: Boolean = false,
    hasUserAudio: Boolean = false,
    onStartRecording: (() -> Unit)? = null,
    onStopRecording: (() -> Unit)? = null,
    onPlayUser: (() -> Unit)? = null,
    onStopUser: (() -> Unit)? = null,
    // 원본 단독 재생
    isPlayingOriginal: Boolean = false,
    onPlayOriginal: (() -> Unit)? = null,
    onStopOriginal: (() -> Unit)? = null,
    originalPlayProgress: Float = 0f,
    // 자동 싱크: 사용자 파형에서 음성 시작점 자동 감지 (null이면 버튼 미표시)
    onAutoSync: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 마커 잠금 상태 (🔓 = 드래그 가능, 🔒 = 드래그 차단)
    var markersLocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // ── 헤더: REC + 동시재생 ─────────────────────────────────────
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
                        enabled = !isPlayingUser && !isPlaying && !isPlayingOriginal
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Rec", fontSize = 11.sp)
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

        // ── 원본 음성 Row: [▶/⏹ 원본] "원본 음성" 드래그힌트 [🔓/🔒] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 원본 단독 Play/Stop
            if (onPlayOriginal != null) {
                if (isPlayingOriginal) {
                    TextButton(
                        onClick = { onStopOriginal?.invoke() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" 원본", fontSize = 10.sp)
                    }
                } else {
                    TextButton(
                        onClick = onPlayOriginal,
                        enabled = !isPlaying && !isRecordingUser && !isPlayingUser,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" 원본", fontSize = 10.sp)
                    }
                }
            } else {
                Text("원본 음성", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 2.dp))
            }

            // 드래그 힌트 (잠금 해제 시, 재생 중이 아닐 때)
            if (!isPlaying && !isPlayingOriginal && !markersLocked && onSegmentStartMarkerChange != null) {
                Text(
                    text = " ▶ 주황=시작  빨강=끝",
                    fontSize = 9.sp, color = Color(0xFFFF9800)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🔓/🔒 잠금 버튼
            TextButton(
                onClick = { markersLocked = !markersLocked },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(if (markersLocked) "🔒" else "🔓", fontSize = 14.sp)
            }
        }

        // 원본 WaveformView (핀치 줌 활성화, 마커 잠금 반영)
        WaveformView(
            samples = originalWaveform,
            waveformColor = OPicColors.LevelGauge,
            playbackProgress = when {
                isPlayingOriginal -> originalPlayProgress
                isPlaying         -> originalProgress
                else              -> null
            },
            // 재생 중에는 마커 숨김, 정지 시에만 드래그 가능 (잠금이면 onChange=null)
            startMarkerFraction = if (!isPlaying && !isPlayingOriginal) segmentStartMarker else null,
            onStartMarkerChange = if (!isPlaying && !isPlayingOriginal && !markersLocked) onSegmentStartMarkerChange else null,
            endMarkerFraction   = if (!isPlaying && !isPlayingOriginal) segmentEndMarker   else null,
            onEndMarkerChange   = if (!isPlaying && !isPlayingOriginal && !markersLocked) onSegmentEndMarkerChange else null,
            height = 40.dp,
            zoomEnabled = true
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── 내 녹음 Row: [▶/⏹ 내녹음] "내 녹음" 드래그힌트 [Auto] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 내 녹음 단독 Play/Stop (헤더에서 이동)
            if (onPlayUser != null) {
                if (isPlayingUser) {
                    TextButton(
                        onClick = { onStopUser?.invoke() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" 내녹음", fontSize = 10.sp)
                    }
                } else {
                    TextButton(
                        onClick = onPlayUser,
                        enabled = hasUserAudio && !isRecordingUser && !isPlaying && !isPlayingOriginal,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(" 내녹음", fontSize = 10.sp)
                    }
                }
            } else {
                Text("내 녹음", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 2.dp))
            }

            // 드래그 힌트
            if (!isPlaying && !isPlayingUser && !isPlayingOriginal) {
                Text(
                    text = " ← 드래그로 시작 위치 조절",
                    fontSize = 9.sp,
                    color = Color(0xFFFF9800)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Auto Sync 버튼
            if (!isPlaying && !isPlayingUser && !isRecordingUser && !isPlayingOriginal && onAutoSync != null && hasUserAudio) {
                TextButton(
                    onClick = onAutoSync,
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Auto", fontSize = 9.sp, color = OPicColors.Primary)
                }
            }
        }

        // 사용자 파형 (시작 마커 드래그 가능)
        WaveformView(
            samples = userWaveform,
            waveformColor = OPicColors.TimerGreen,
            playbackProgress = when {
                isPlaying     -> (userStartFraction + userProgress * (1f - userStartFraction)).coerceIn(0f, 1f)
                isPlayingUser -> userPlayProgress
                else          -> null
            },
            startMarkerFraction = if (!isPlaying && !isPlayingUser) userStartFraction else null,
            onStartMarkerChange = if (!isPlaying && !isPlayingUser) onUserStartFractionChange else null,
            height = 40.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── 볼륨 밸런스 슬라이더 ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("원본", fontSize = 10.sp, color = Color.Gray)
            Slider(
                value = balance,
                onValueChange = onBalanceChange,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = OPicColors.Primary,
                    activeTrackColor = OPicColors.Primary,
                    inactiveTrackColor = OPicColors.Border
                )
            )
            Text("내 녹음", fontSize = 10.sp, color = Color.Gray)
        }

        // ── 속도 조절 (± 버튼, 범위 0.5~1.5, 0.1 단위) ──────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("속도", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 2.dp))
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { onComparisonSpeedChange((comparisonSpeed - 0.1f).coerceAtLeast(0.5f)) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("−", fontSize = 14.sp, color = OPicColors.TimerRed)
            }
            Text(
                text = String.format("%.1f", comparisonSpeed) + "x",
                fontSize = 11.sp,
                color = OPicColors.Primary,
                modifier = Modifier.width(36.dp),
            )
            TextButton(
                onClick = { onComparisonSpeedChange((comparisonSpeed + 0.1f).coerceAtMost(1.5f)) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("+", fontSize = 14.sp, color = OPicColors.TimerGreen)
            }
        }
    }
}
