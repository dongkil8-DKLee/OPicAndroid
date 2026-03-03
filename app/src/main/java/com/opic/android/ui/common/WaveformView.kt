package com.opic.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlin.math.abs

/**
 * Compose Canvas 기반 파형 렌더링.
 *
 * @param samples            정규화된 파형 데이터 (0.0~1.0)
 * @param playbackProgress   재생 진행률 (null이면 미표시)
 * @param startMarkerFraction 시작 마커 위치 (주황)
 * @param endMarkerFraction   끝   마커 위치 (빨강)
 * @param onStartMarkerChange 시작 마커 드래그 콜백 — 사용자 파형 전용
 * @param onEndMarkerChange   끝   마커 드래그 콜백 — 사용자 파형 전용
 * @param onTap               탭 위치 fraction 콜백 — [시작]/[종료] 버튼+탭 마커 설정용
 * @param zoomEnabled         스와이프 줌 활성화 (원본 파형에만 true)
 *                            - 오른쪽 스와이프: 시작 마커(주황)를 중앙으로 이동하며 확대
 *                            - 왼쪽  스와이프: 종료 마커(빨강)를 중앙으로 이동하며 확대
 *                            - 반대 방향 스와이프: 비례 축소
 *                            - 더블탭: 1x 리셋
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
    onTap: ((Float) -> Unit)? = null,
    height: Dp = 50.dp,
    zoomEnabled: Boolean = false
) {
    // 핀치 줌 상태 (samples 바뀌면 리셋)
    var zoomScale by remember(samples) { mutableFloatStateOf(1f) }
    var zoomWindowStart by remember(samples) { mutableFloatStateOf(0f) }

    // pointerInput key=Unit 고정 → 제스처 재시작 없이 최신 값 참조
    val latestZoomScale by rememberUpdatedState(zoomScale)
    val latestZoomWindowStart by rememberUpdatedState(zoomWindowStart)
    val latestOnStartMarkerChange by rememberUpdatedState(onStartMarkerChange)
    val latestOnEndMarkerChange by rememberUpdatedState(onEndMarkerChange)
    val latestStartMarkerFraction by rememberUpdatedState(startMarkerFraction)
    val latestEndMarkerFraction by rememberUpdatedState(endMarkerFraction)
    val latestOnTap by rememberUpdatedState(onTap)

    // ── 마커 드래그 (사용자 파형 전용, zoomEnabled=false 환경) ────────────
    val dragModifier = Modifier.pointerInput(Unit) {
        var shouldProcess = false
        var draggingStart = true
        detectDragGestures(
            onDragStart = { offset ->
                shouldProcess = latestOnStartMarkerChange != null || latestOnEndMarkerChange != null
                if (!shouldProcess) return@detectDragGestures
                val viewFrac = offset.x / size.width.toFloat()
                val fullFrac = latestZoomWindowStart + viewFrac / latestZoomScale
                draggingStart = when {
                    latestOnStartMarkerChange != null && latestOnEndMarkerChange != null -> {
                        val distStart = abs(fullFrac - (latestStartMarkerFraction ?: 0f))
                        val distEnd   = abs(fullFrac - (latestEndMarkerFraction   ?: 1f))
                        distStart <= distEnd
                    }
                    else -> latestOnStartMarkerChange != null
                }
                val clamped = fullFrac.coerceIn(0f, 1f)
                if (draggingStart) latestOnStartMarkerChange?.invoke(clamped)
                else               latestOnEndMarkerChange?.invoke(clamped)
            },
            onDrag = { change, _ ->
                if (!shouldProcess) return@detectDragGestures
                change.consume()
                val viewFrac = change.position.x / size.width.toFloat()
                val fullFrac = (latestZoomWindowStart + viewFrac / latestZoomScale).coerceIn(0f, 1f)
                if (draggingStart) latestOnStartMarkerChange?.invoke(fullFrac)
                else               latestOnEndMarkerChange?.invoke(fullFrac)
            }
        )
    }

    // ── 스와이프 줌 + 탭 + 더블탭 리셋 (원본 파형 전용) ─────────────────
    // 첫 이동 방향이 앵커를 결정:
    //   오른쪽 스와이프 → 시작 마커(주황)를 중앙으로 이동하며 확대
    //   왼쪽  스와이프 → 종료 마커(빨강)를 중앙으로 이동하며 확대
    //   반대 방향 스와이프 → 같은 앵커 유지하며 비례 축소
    //   더블탭 → 1x 리셋
    val zoomAndTapModifier = if (zoomEnabled) {
        Modifier.pointerInput(Unit) {
            val touchSlop = viewConfiguration.touchSlop
            var lastTapTime = 0L
            var savedAnchorLeft = true  // ★ 확대 상태에서 앵커 방향 기억

            awaitEachGesture {
                // 첫 번째 포인터 다운 감지
                val event0 = awaitPointerEvent()
                val down = event0.changes.firstOrNull() ?: return@awaitEachGesture
                if (!down.pressed) return@awaitEachGesture

                val downPos  = down.position
                val downTime = System.currentTimeMillis()

                // 더블탭 → 줌 리셋
                if (downTime - lastTapTime < 300L) {
                    zoomScale       = 1f
                    zoomWindowStart = 0f
                    lastTapTime     = 0L
                    down.consume()
                    return@awaitEachGesture
                }

                var moved            = false
                var anchorLeft       = savedAnchorLeft  // ★ 기본값: 이전 앵커
                var anchorDetermined = false
                var accDeltaX        = 0f

                while (true) {
                    val event = awaitPointerEvent()
                    val ptr   = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!ptr.pressed) {
                        // 손가락 뗌
                        if (!moved) {
                            // 짧은 탭 → 마커 위치 설정
                            lastTapTime = downTime
                            val viewFrac = downPos.x / size.width.toFloat()
                            val fullFrac = (latestZoomWindowStart + viewFrac / latestZoomScale).coerceIn(0f, 1f)
                            latestOnTap?.invoke(fullFrac)
                        }
                        break
                    }

                    val deltaX = ptr.position.x - ptr.previousPosition.x
                    accDeltaX += deltaX

                    // ★ 앵커 결정 로직: 1x 상태에서만 첫 방향으로 앵커 갱신
                    if (!anchorDetermined && abs(accDeltaX) > touchSlop) {
                        if (latestZoomScale <= 1f) {
                            // 1x 상태: 첫 방향으로 앵커 결정 및 저장
                            anchorLeft      = accDeltaX > 0  // 오른쪽 = 시작마커, 왼쪽 = 종료마커
                            savedAnchorLeft = anchorLeft
                        } else {
                            // 확대 상태: 이전 앵커 유지
                            anchorLeft = savedAnchorLeft
                        }
                        anchorDetermined = true
                        moved            = true
                    }

                    if (moved) {
                        ptr.consume()

                        // 앵커 마커 위치 (시작 = 주황, 종료 = 빨강)
                        val anchorFrac = if (anchorLeft) {
                            latestStartMarkerFraction ?: 0f
                        } else {
                            latestEndMarkerFraction ?: 1f
                        }

                        // 앵커 방향 기준 (항상 일관):
                        //   시작 마커(anchorLeft=true)  → 우측 스와이프=확대, 좌측=축소
                        //   끝   마커(anchorLeft=false) → 좌측 스와이프=확대, 우측=축소
                        val zoomDelta = if (anchorLeft) deltaX else -deltaX
                        val factor    = 1f + zoomDelta / size.width * 4f
                        val newScale  = (latestZoomScale * factor).coerceIn(1f, 8f)
                        val ws        = 1f / newScale

                        val newWindowStart = (anchorFrac - ws / 2f).coerceIn(0f, 1f - ws)

                        zoomScale       = newScale
                        zoomWindowStart = newWindowStart
                    }
                }
            }
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(dragModifier)
            .then(zoomAndTapModifier)
    ) {
        val canvasWidth  = size.width
        val canvasHeight = size.height
        val centerY      = canvasHeight / 2f

        drawLine(color = centerLineColor, start = Offset(0f, centerY), end = Offset(canvasWidth, centerY), strokeWidth = 1f)

        if (samples.isNotEmpty()) {
            val startIdx       = (zoomWindowStart * samples.size).toInt().coerceIn(0, samples.size - 1)
            val count          = (samples.size / zoomScale).toInt().coerceAtLeast(1)
            val endIdx         = (startIdx + count).coerceAtMost(samples.size)
            val displaySamples = samples.sliceArray(startIdx until endIdx)

            val barWidth     = canvasWidth / displaySamples.size
            val halfBar      = (barWidth * 0.7f).coerceAtLeast(1f)
            val maxAmplitude = canvasHeight / 2f * 0.9f

            displaySamples.forEachIndexed { index, amplitude ->
                val x         = index * barWidth + barWidth / 2f
                val barHeight = amplitude * maxAmplitude
                if (barHeight > 0.5f) {
                    drawLine(color = waveformColor, start = Offset(x, centerY - barHeight), end = Offset(x, centerY + barHeight), strokeWidth = halfBar, cap = StrokeCap.Round)
                }
            }
        }

        fun toViewX(fraction: Float): Float =
            ((fraction - zoomWindowStart) * zoomScale * canvasWidth).coerceIn(0f, canvasWidth)

        val windowSize = 1f / zoomScale
        val windowEnd  = zoomWindowStart + windowSize

        fun isInWindow(fraction: Float): Boolean = fraction >= zoomWindowStart && fraction <= windowEnd

        // 시작 마커: 주황 수직선 + 상단 삼각 핸들
        if (startMarkerFraction != null && startMarkerFraction in 0f..1f && isInWindow(startMarkerFraction)) {
            val markerX     = toViewX(startMarkerFraction)
            val markerColor = Color(0xFFFF9800)
            drawLine(color = markerColor, start = Offset(markerX, 0f), end = Offset(markerX, canvasHeight), strokeWidth = 2.5f, cap = StrokeCap.Round)
            val hs   = 8f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX, 0f); lineTo(markerX - hs, -hs); lineTo(markerX + hs, -hs); close()
            }
            drawPath(path, markerColor)
        }

        // 끝 마커: 빨간 수직선 + 하단 삼각 핸들
        if (endMarkerFraction != null && endMarkerFraction in 0f..1f && isInWindow(endMarkerFraction)) {
            val markerX     = toViewX(endMarkerFraction)
            val markerColor = Color(0xFFE53935)
            drawLine(color = markerColor, start = Offset(markerX, 0f), end = Offset(markerX, canvasHeight), strokeWidth = 2.5f, cap = StrokeCap.Round)
            val hs   = 8f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX, canvasHeight); lineTo(markerX - hs, canvasHeight + hs); lineTo(markerX + hs, canvasHeight + hs); close()
            }
            drawPath(path, markerColor)
        }

        // 재생 위치 표시선
        if (playbackProgress != null && playbackProgress in 0f..1f && isInWindow(playbackProgress)) {
            val posX = toViewX(playbackProgress)
            drawLine(color = positionIndicatorColor, start = Offset(posX, 0f), end = Offset(posX, canvasHeight), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}
