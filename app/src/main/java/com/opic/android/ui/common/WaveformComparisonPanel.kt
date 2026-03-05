package com.opic.android.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 두 파형 + 재생 버튼 + 볼륨 밸런스 슬라이더 패널.
 *
 * 원본 파형 위 2줄:
 *   Line 1: [타이밍] [시작] Spacer [종료]
 *   Line 2: [《][-] {1000ms / 1000ms} [-][》]  ← 타이밍 ON 시만 표시
 *
 * 하단 버튼 1줄:
 *   [▶원본] [🔁구간] [🎤Rec] [▶녹음] [⇄동시]  Spacer  속도:[−][1.0x][+]
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
    // 원본 파형 구간 마커 (주황=시작, 빨강=끝)
    segmentStartMarker: Float = 0f,
    segmentEndMarker: Float = 1f,
    onSegmentStartMarkerChange: ((Float) -> Unit)? = null,
    onSegmentEndMarkerChange: ((Float) -> Unit)? = null,
    // REC 버튼 (선택적)
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
    // 구간 반복
    isLoopPlaying: Boolean = false,
    onToggleLoop: (() -> Unit)? = null,
    // 자동 싱크 (null이면 버튼 미표시)
    onAutoSync: (() -> Unit)? = null,
    // 타이밍 모드 (파형 확장 컨트롤)
    isTimingModeEnabled: Boolean = false,
    onToggleTimingMode: (() -> Unit)? = null,
    expandBeforeMs: Long = 1000L,
    expandAfterMs: Long = 1000L,
    onExpandBeforeChange: ((Long) -> Unit)? = null,
    onExpandAfterChange: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var markerMode by remember { mutableStateOf(MarkerMode.None) }
    if (isPlaying || isPlayingOriginal || isLoopPlaying) markerMode = MarkerMode.None

    val isBusy = isPlaying || isPlayingOriginal || isLoopPlaying || isRecordingUser

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // ── Line 1: [타이밍] [시작]  탭힌트  Spacer  [종료] ────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [타이밍] 토글
            if (onToggleTimingMode != null) {
                TextButton(
                    onClick = onToggleTimingMode,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "타이밍",
                        fontSize = 10.sp,
                        color = if (isTimingModeEnabled) OPicColors.Primary else Color.Gray
                    )
                }
            }

            // [시작] 마커 버튼
            if (onSegmentStartMarkerChange != null && !isBusy) {
                TextButton(
                    onClick = {
                        markerMode = if (markerMode == MarkerMode.Start) MarkerMode.None else MarkerMode.Start
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "시작",
                        fontSize = 10.sp,
                        color = if (markerMode == MarkerMode.Start) Color(0xFFFF9800) else Color.Gray
                    )
                }
            }

            // 탭 힌트 (마커 모드 활성 시)
            if (!isBusy && markerMode != MarkerMode.None) {
                Text(
                    text = "↑탭",
                    fontSize = 9.sp,
                    color = when (markerMode) {
                        MarkerMode.Start -> Color(0xFFFF9800)
                        MarkerMode.End   -> Color(0xFFE53935)
                        MarkerMode.None  -> Color.Transparent
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // [종료] 마커 버튼
            if (onSegmentEndMarkerChange != null && !isBusy) {
                TextButton(
                    onClick = {
                        markerMode = if (markerMode == MarkerMode.End) MarkerMode.None else MarkerMode.End
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "종료",
                        fontSize = 10.sp,
                        color = if (markerMode == MarkerMode.End) Color(0xFFE53935) else Color.Gray
                    )
                }
            }
        }

        // ── Line 2: [《][-] {1000ms / 1000ms} [-][》]  ← 타이밍 ON 시만 ──
        if (isTimingModeEnabled && (onExpandBeforeChange != null || onExpandAfterChange != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 《 (+expandBefore)
                if (onExpandBeforeChange != null) {
                    TextButton(
                        onClick = { onExpandBeforeChange(expandBeforeMs + 500L) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("《", fontSize = 11.sp,
                            color = if (!isBusy) OPicColors.Primary else Color.Gray.copy(alpha = 0.4f))
                    }
                    // − (-expandBefore)
                    TextButton(
                        onClick = { onExpandBeforeChange(expandBeforeMs - 500L) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("−", fontSize = 12.sp,
                            color = if (!isBusy) OPicColors.TimerRed else Color.Gray.copy(alpha = 0.4f))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 현재 값 표시
                Text(
                    "${expandBeforeMs}ms",
                    fontSize = 10.sp,
                    color = OPicColors.Primary
                )
                Text("  /  ", fontSize = 10.sp, color = Color.Gray)
                Text(
                    "${expandAfterMs}ms",
                    fontSize = 10.sp,
                    color = OPicColors.Primary
                )

                Spacer(modifier = Modifier.weight(1f))

                // − (-expandAfter) + 》 (+expandAfter)
                if (onExpandAfterChange != null) {
                    TextButton(
                        onClick = { onExpandAfterChange(expandAfterMs - 500L) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("−", fontSize = 12.sp,
                            color = if (!isBusy) OPicColors.TimerRed else Color.Gray.copy(alpha = 0.4f))
                    }
                    TextButton(
                        onClick = { onExpandAfterChange(expandAfterMs + 500L) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("》", fontSize = 11.sp,
                            color = if (!isBusy) OPicColors.Primary else Color.Gray.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // 원본 WaveformView
        WaveformView(
            samples = originalWaveform,
            waveformColor = OPicColors.LevelGauge,
            playbackProgress = when {
                isPlayingOriginal || isLoopPlaying -> originalPlayProgress
                isPlaying -> (segmentStartMarker + originalProgress * (segmentEndMarker - segmentStartMarker))
                    .coerceIn(0f, 1f)
                else -> null
            },
            startMarkerFraction = if (!isBusy) segmentStartMarker else null,
            endMarkerFraction   = if (!isBusy) segmentEndMarker   else null,
            onStartMarkerChange = null,
            onEndMarkerChange   = null,
            onTap = if (!isBusy && markerMode != MarkerMode.None) { fraction ->
                when (markerMode) {
                    MarkerMode.Start -> {
                        onSegmentStartMarkerChange?.invoke(fraction)
                        markerMode = MarkerMode.None
                    }
                    MarkerMode.End -> {
                        onSegmentEndMarkerChange?.invoke(fraction)
                        markerMode = MarkerMode.None
                    }
                    MarkerMode.None -> {}
                }
            } else null,
            height = 40.dp,
            zoomEnabled = true
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── 내 녹음 Row: Spacer [Auto] ─────────────────────────────────
        if (onAutoSync != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onAutoSync,
                    enabled = !isBusy && !isPlayingUser && hasUserAudio,
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

        // ── 볼륨 밸런스 슬라이더 (50% 축소) ─────────────────────────────
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
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
                Text("녹음", fontSize = 10.sp, color = Color.Gray)
            }
        }

        // ── 속도 조절 (버튼 위 우측) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text("속도", fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(2.dp))
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
                modifier = Modifier.width(36.dp)
            )
            TextButton(
                onClick = { onComparisonSpeedChange((comparisonSpeed + 0.1f).coerceAtMost(1.5f)) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("+", fontSize = 14.sp, color = OPicColors.TimerGreen)
            }
        }

        // ── 하단: [▶원본재생] [🔁구간재생] [🎤] [⭕녹음재생] [⇄동시재생] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ▶/⏹ 원본재생
            if (onPlayOriginal != null) {
                if (isPlayingOriginal) {
                    TextButton(
                        onClick = { onStopOriginal?.invoke() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(" 원본재생", fontSize = 11.sp)
                    }
                } else {
                    TextButton(
                        onClick = onPlayOriginal,
                        enabled = !isBusy && !isPlaying && !isPlayingUser,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(" 원본재생", fontSize = 11.sp)
                    }
                }
            }

            // 🔁/⏹ 구간재생
            if (onToggleLoop != null) {
                if (isLoopPlaying) {
                    TextButton(
                        onClick = onToggleLoop,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = OPicColors.TimerRed)
                        Text(" 구간재생", fontSize = 11.sp, color = OPicColors.TimerRed)
                    }
                } else {
                    TextButton(
                        onClick = onToggleLoop,
                        enabled = !isPlaying && !isPlayingOriginal && !isPlayingUser && !isRecordingUser,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(" 구간재생", fontSize = 11.sp)
                    }
                }
            }

            // 🎤/⏹ 녹음
            if (onStartRecording != null) {
                val recEnabled = !isPlayingUser && !isPlaying && !isPlayingOriginal && !isLoopPlaying
                IconButton(
                    onClick  = { if (isRecordingUser) onStopRecording?.invoke() else onStartRecording() },
                    enabled  = isRecordingUser || recEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isRecordingUser) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecordingUser) "녹음 중지" else "녹음",
                        tint = if (isRecordingUser || recEnabled) OPicColors.RecordActive else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // ⭕/⏹ 녹음재생 — 원형 outline 버튼 + 텍스트
            // ★ CIRCLE_SIZE  = 24.dp  : 원 전체 크기
            // ★ BORDER_WIDTH = 1.5.dp : 테두리 두께
            // ★ ICON_SIZE    = 28.dp  : 삼각형/정지 아이콘 크기
            if (onPlayUser != null) {
                val isUserPlayEnabled = hasUserAudio && !isRecordingUser && !isPlaying && !isPlayingOriginal && !isLoopPlaying
                val circleColor = when {
                    isPlayingUser     -> OPicColors.RecordActive
                    isUserPlayEnabled -> OPicColors.PlayButton
                    else              -> Color.Gray
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)                                   // ★ CIRCLE_SIZE
                            .border(1.5.dp, circleColor, CircleShape)      // ★ BORDER_WIDTH
                            .clickable(enabled = isPlayingUser || isUserPlayEnabled) {
                                if (isPlayingUser) onStopUser?.invoke() else onPlayUser.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingUser) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlayingUser) "녹음 정지" else "녹음 재생",
                            tint     = circleColor,
                            modifier = Modifier.size(28.dp)                // ★ ICON_SIZE
                        )
                    }
                    Text("녹음재생", fontSize = 10.sp, color = circleColor)
                }
            }

            // ⇄/⏹ 동시재생
            if (isPlaying) {
                TextButton(
                    onClick = onTogglePlayback,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = OPicColors.RecordActive)
                    Text(" 정지", fontSize = 11.sp, color = OPicColors.RecordActive)
                }
            } else {
                TextButton(
                    onClick = onTogglePlayback,
                    enabled = enabled && userWaveform.isNotEmpty() && !isPlayingOriginal && !isLoopPlaying && !isRecordingUser,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(" 동시재생", fontSize = 11.sp)
                }
            }

        }
    }
}

/** 원본 파형 마커 설정 모드 */
private enum class MarkerMode { None, Start, End }
