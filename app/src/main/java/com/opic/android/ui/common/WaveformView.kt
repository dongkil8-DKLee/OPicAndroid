package com.opic.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
 * @param samples        정규화된 파형 데이터 (0.0~1.0)
 * @param playbackProgress 재생 진행률 (0.0~1.0), null이면 미표시
 * @param startMarkerFraction 시작 마커 위치 (주황), null이면 미표시
 * @param endMarkerFraction   끝   마커 위치 (빨강), null이면 미표시
 * @param onStartMarkerChange 시작 마커 드래그 콜백
 * @param onEndMarkerChange   끝   마커 드래그 콜백
 * @param height         뷰 높이
 * @param zoomEnabled    핀치 줌 활성화 (원본 파형에만 true)
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
    endMarkerFraction: Float? = null,
    onEndMarkerChange: ((Float) -> Unit)? = null,
    height: Dp = 50.dp,
    zoomEnabled: Boolean = false
) {
    // 핀치 줌 상태 (samples가 바뀌면 자동 리셋 — 문장 이동 시 초기화)
    var zoomScale by remember(samples) { mutableFloatStateOf(1f) }
    var zoomWindowStart by remember(samples) { mutableFloatStateOf(0f) }

    // 제스처 코루틴 안에서 최신 값을 읽기 위해 rememberUpdatedState 사용
    val latestZoomScale by rememberUpdatedState(zoomScale)
    val latestZoomWindowStart by rememberUpdatedState(zoomWindowStart)

    // 마커 드래그 제스처 (단일 손가락) — 줌 좌표 역매핑 적용
    val dragModifier = if (onStartMarkerChange != null || onEndMarkerChange != null) {
        Modifier.pointerInput(onStartMarkerChange, onEndMarkerChange) {
            var draggingStart = true
            detectDragGestures(
                onDragStart = { offset ->
                    val viewFrac = offset.x / size.width.toFloat()
                    val fullFrac = latestZoomWindowStart + viewFrac / latestZoomScale
                    draggingStart = when {
                        onStartMarkerChange != null && onEndMarkerChange != null -> {
                            val distStart = kotlin.math.abs(fullFrac - (startMarkerFraction ?: 0f))
                            val distEnd   = kotlin.math.abs(fullFrac - (endMarkerFraction   ?: 1f))
                            distStart <= distEnd
                        }
                        else -> onStartMarkerChange != null
                    }
                    val clamped = fullFrac.coerceIn(0f, 1f)
                    if (draggingStart) onStartMarkerChange?.invoke(clamped)
                    else              onEndMarkerChange?.invoke(clamped)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val viewFrac = change.position.x / size.width.toFloat()
                    val fullFrac = (latestZoomWindowStart + viewFrac / latestZoomScale).coerceIn(0f, 1f)
                    if (draggingStart) onStartMarkerChange?.invoke(fullFrac)
                    else              onEndMarkerChange?.invoke(fullFrac)
                }
            )
        }
    } else Modifier

    // 핀치 줌 제스처 (두 손가락, zoomEnabled=true 인 경우만)
    val pinchModifier = if (zoomEnabled) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, _, zoomFactor, _ ->
                val pivotViewFrac = centroid.x / size.width.toFloat()
                val pivotFullFrac = latestZoomWindowStart + pivotViewFrac / latestZoomScale
                val newScale = (latestZoomScale * zoomFactor).coerceIn(1f, 8f)
                val windowSize = 1f / newScale
                val newWindowStart = (pivotFullFrac - windowSize / 2f).coerceIn(0f, 1f - windowSize)
                zoomScale = newScale
                zoomWindowStart = newWindowStart
            }
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(dragModifier)
            .then(pinchModifier)
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
            // 줌 적용: 현재 윈도우에 해당하는 샘플 슬라이싱
            val startIdx = (zoomWindowStart * samples.size).toInt().coerceIn(0, samples.size - 1)
            val count = (samples.size / zoomScale).toInt().coerceAtLeast(1)
            val endIdx = (startIdx + count).coerceAtMost(samples.size)
            val displaySamples = samples.sliceArray(startIdx until endIdx)

            val barWidth = canvasWidth / displaySamples.size
            val halfBar = (barWidth * 0.7f).coerceAtLeast(1f)
            val maxAmplitude = canvasHeight / 2f * 0.9f

            displaySamples.forEachIndexed { index, amplitude ->
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

        // 전체 fraction → 뷰 X 좌표 변환 (줌 적용)
        // toViewX(f) = (f - windowStart) * zoomScale * canvasWidth
        fun toViewX(fraction: Float): Float =
            ((fraction - zoomWindowStart) * zoomScale * canvasWidth).coerceIn(0f, canvasWidth)

        val windowSize = 1f / zoomScale
        val windowEnd = zoomWindowStart + windowSize

        // 현재 줌 윈도우 안에 있는지 확인
        fun isInWindow(fraction: Float): Boolean =
            fraction >= zoomWindowStart && fraction <= windowEnd

        // 시작 마커: 주황 수직선 + 상단 삼각 핸들 (▼)
        if (startMarkerFraction != null && startMarkerFraction in 0f..1f && isInWindow(startMarkerFraction)) {
            val markerX = toViewX(startMarkerFraction)
            val markerColor = Color(0xFFFF9800)
            drawLine(color = markerColor, start = Offset(markerX, 0f), end = Offset(markerX, canvasHeight), strokeWidth = 2.5f, cap = StrokeCap.Round)
            val hs = 8f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX, 0f); lineTo(markerX - hs, -hs); lineTo(markerX + hs, -hs); close()
            }
            drawPath(path, markerColor)
        }

        // 끝 마커: 빨간 수직선 + 하단 삼각 핸들 (▲)
        if (endMarkerFraction != null && endMarkerFraction in 0f..1f && isInWindow(endMarkerFraction)) {
            val markerX = toViewX(endMarkerFraction)
            val markerColor = Color(0xFFE53935)
            drawLine(color = markerColor, start = Offset(markerX, 0f), end = Offset(markerX, canvasHeight), strokeWidth = 2.5f, cap = StrokeCap.Round)
            val hs = 8f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX, canvasHeight); lineTo(markerX - hs, canvasHeight + hs); lineTo(markerX + hs, canvasHeight + hs); close()
            }
            drawPath(path, markerColor)
        }

        // 재생 위치 표시선 (Primary 수직선)
        if (playbackProgress != null && playbackProgress in 0f..1f && isInWindow(playbackProgress)) {
            val posX = toViewX(playbackProgress)
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
