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
    // false이면 하단 5개 버튼 Row를 숨김 (외부 공유 버튼 행으로 이동 시)
    showButtonRow: Boolean = true,
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

        Spacer(modifier = Modifier.height(2.dp))

        // ── 볼륨 밸런스 슬라이더 (세로 높이 축소) ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
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
        if (showButtonRow) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ▶/⏹ 원본재생 — 아이콘만 (2x: 40dp)
            if (onPlayOriginal != null) {
                if (isPlayingOriginal) {
                    IconButton(
                        onClick = { onStopOriginal?.invoke() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "원본 정지",
                            modifier = Modifier.size(28.dp))
                    }
                } else {
                    IconButton(
                        onClick = onPlayOriginal,
                        enabled = !isBusy && !isPlaying && !isPlayingUser,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "원본 재생",
                            modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 🔁/⏹ 구간재생 — 아이콘만 (2x: 40dp)
            if (onToggleLoop != null) {
                if (isLoopPlaying) {
                    IconButton(
                        onClick = onToggleLoop,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "구간 정지",
                            tint = OPicColors.TimerRed, modifier = Modifier.size(28.dp))
                    }
                } else {
                    IconButton(
                        onClick = onToggleLoop,
                        enabled = !isPlaying && !isPlayingOriginal && !isPlayingUser && !isRecordingUser,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = "구간 반복",
                            modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 🎤/⏹ 녹음 — 아이콘 2x: 44dp (container 56dp)
            // ★ MIC_BUTTON_SIZE = 56.dp  : 클릭 영역
            // ★ MIC_ICON_SIZE   = 44.dp  : 아이콘 크기 (원래 28dp → 2배)
            if (onStartRecording != null) {
                val recEnabled = !isPlayingUser && !isPlaying && !isPlayingOriginal && !isLoopPlaying
                IconButton(
                    onClick  = { if (isRecordingUser) onStopRecording?.invoke() else onStartRecording() },
                    enabled  = isRecordingUser || recEnabled,
                    modifier = Modifier.size(28.dp)                        // ★ MIC_BUTTON_SIZE
                ) {
                    Icon(
                        imageVector = if (isRecordingUser) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecordingUser) "녹음 중지" else "녹음",
                        tint = if (isRecordingUser || recEnabled) OPicColors.RecordActive else Color.Gray,
                        modifier = Modifier.size(44.dp)                    // ★ MIC_ICON_SIZE
                    )
                }
            }

            // ⭕/⏹ 녹음재생 — 원형 outline (2x: 원 48dp, 아이콘 40dp)
            // ★ CIRCLE_SIZE  = 48.dp  : 원 전체 크기 (원래 24dp → 2배)
            // ★ BORDER_WIDTH = 2.dp   : 테두리 두께
            // ★ ICON_SIZE    = 40.dp  : 삼각형/정지 아이콘 크기
            if (onPlayUser != null) {
                val isUserPlayEnabled = hasUserAudio && !isRecordingUser && !isPlaying && !isPlayingOriginal && !isLoopPlaying
                val circleColor = when {
                    isPlayingUser     -> OPicColors.RecordActive
                    isUserPlayEnabled -> OPicColors.PlayButton
                    else              -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)                                       // ★ CIRCLE_SIZE
                        .border(2.dp, circleColor, CircleShape)            // ★ BORDER_WIDTH
                        .clickable(enabled = isPlayingUser || isUserPlayEnabled) {
                            if (isPlayingUser) onStopUser?.invoke() else onPlayUser.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingUser) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlayingUser) "녹음 정지" else "녹음 재생",
                        tint     = circleColor,
                        modifier = Modifier.size(40.dp)                    // ★ ICON_SIZE
                    )
                }
            }

            // ⇄/⏹ 동시재생 — 아이콘만 (2x: 40dp)
            if (isPlaying) {
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "동시 정지",
                        tint = OPicColors.RecordActive, modifier = Modifier.size(28.dp))
                }
            } else {
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = enabled && userWaveform.isNotEmpty() && !isPlayingOriginal && !isLoopPlaying && !isRecordingUser,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "동시 재생",
                        modifier = Modifier.size(28.dp))
                }
            }

        }
    }
}

/** 원본 파형 마커 설정 모드 */
private enum class MarkerMode { None, Start, End }
