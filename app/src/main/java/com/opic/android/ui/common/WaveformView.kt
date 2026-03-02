package com.opic.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opic.android.ui.theme.OPicColors

/**
 * Compose Canvas 기반 파형 렌더링.
 * 중앙선 + 대칭 진폭 바 + 재생 위치 표시선.
 *
 * @param samples -1.0~1.0 정규화된 파형 데이터 (0.0~1.0 peak 값)
 * @param waveformColor 파형 바 색상
 * @param centerLineColor 중앙선 색상
 * @param positionIndicatorColor 재생 위치 표시선 색상
 * @param playbackProgress 재생 진행률 (0.0~1.0), null이면 표시선 미표시
 * @param height 뷰 높이
 */
@Composable
fun WaveformView(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    waveformColor: Color = OPicColors.LevelGauge,
    centerLineColor: Color = OPicColors.Border,
    positionIndicatorColor: Color = OPicColors.Primary,
    playbackProgress: Float? = null,
    startMarkerFraction: Float? = null,
    onStartMarkerChange: ((Float) -> Unit)? = null,
    height: Dp = 50.dp
) {
    val gestureModifier = if (onStartMarkerChange != null) {
        Modifier.pointerInput(onStartMarkerChange) {
            detectDragGestures(
                onDragStart = { offset ->
                    onStartMarkerChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                },
                onDrag = { change, _ ->
                    change.consume()
                    onStartMarkerChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            )
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(gestureModifier)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        // 중앙선
        drawLine(
            color = centerLineColor,
            start = Offset(0f, centerY),
            end = Offset(canvasWidth, centerY),
            strokeWidth = 1f
        )

        if (samples.isNotEmpty()) {
            val barWidth = canvasWidth / samples.size
            val halfBar = (barWidth * 0.7f).coerceAtLeast(1f)
            val maxAmplitude = canvasHeight / 2f * 0.9f  // 90% of half height

            samples.forEachIndexed { index, amplitude ->
                val x = index * barWidth + barWidth / 2f
                val barHeight = amplitude * maxAmplitude

                if (barHeight > 0.5f) {
                    drawLine(
                        color = waveformColor,
                        start = Offset(x, centerY - barHeight),
                        end = Offset(x, centerY + barHeight),
                        strokeWidth = halfBar,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 시작 위치 마커 (주황 수직선 + 상단 삼각 핸들)
        if (startMarkerFraction != null && startMarkerFraction in 0f..1f) {
            val markerX = startMarkerFraction * canvasWidth
            val markerColor = Color(0xFFFF9800)
            drawLine(
                color = markerColor,
                start = Offset(markerX, 0f),
                end = Offset(markerX, canvasHeight),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            // 상단 핸들 (작은 삼각형)
            val hs = 8f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX, 0f)
                lineTo(markerX - hs, -hs)
                lineTo(markerX + hs, -hs)
                close()
            }
            drawPath(path, markerColor)
        }

        // 재생 위치 표시선 (Primary 수직선)
        if (playbackProgress != null && playbackProgress in 0f..1f) {
            val posX = playbackProgress * canvasWidth
            drawLine(
                color = positionIndicatorColor,
                start = Offset(posX, 0f),
                end = Offset(posX, canvasHeight),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}
