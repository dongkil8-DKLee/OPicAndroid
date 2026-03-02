package com.opic.android.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 두 MediaPlayer 인스턴스를 관리하는 동시 재생 매니저.
 * 원본 음성과 사용자 녹음을 동시에 재생하며 볼륨 밸런스 조절 가능.
 */
@Singleton
class DualPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DualPlaybackManager"
        private const val POSITION_UPDATE_INTERVAL = 50L
    }

    private var originalPlayer: MediaPlayer? = null
    private var userPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionRunnable: Runnable? = null
    private var rangeStopRunnable: Runnable? = null
    private var onComplete: (() -> Unit)? = null
    private var isPlaying = false

    /**
     * 원본 + 사용자 녹음 동시 재생.
     *
     * @param originalSource 원본 오디오 소스 (AssetPath 또는 FilePath)
     * @param originalStartMs 원본 구간 시작 (ms)
     * @param originalEndMs 원본 구간 끝 (ms)
     * @param userFilePath 사용자 녹음 파일 경로
     * @param userStartMs 사용자 녹음 시작 오프셋 (ms) — 선행 묵음 트리밍용
     * @param initialBalance 초기 볼륨 밸런스 (0.0=원본만, 0.5=둘다, 1.0=녹음만)
     * @param onPositionUpdate 위치 업데이트 콜백 (origProgress, userProgress)
     * @param onComplete 재생 완료 콜백
     */
    fun playSimultaneous(
        originalSource: AudioSource?,
        originalStartMs: Long,
        originalEndMs: Long,
        userFilePath: String,
        userStartMs: Long = 0L,
        initialBalance: Float = 0.5f,
        onPositionUpdate: ((Float, Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        this.onComplete = onComplete

        runCatching {
            // 원본 플레이어 준비
            val origPlayer = createOriginalPlayer(originalSource) ?: run {
                Log.w(TAG, "원본 플레이어 생성 실패")
                onComplete?.invoke()
                return
            }
            originalPlayer = origPlayer

            // 사용자 플레이어 준비
            val usrPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(userFilePath)
                    prepare()
                }
            }.getOrNull() ?: run {
                Log.w(TAG, "사용자 플레이어 생성 실패")
                runCatching { origPlayer.release() }
                originalPlayer = null
                onComplete?.invoke()
                return
            }
            userPlayer = usrPlayer

            // 볼륨 밸런스 적용
            applyBalance(initialBalance)

            // 원본 구간 재생
            val origDurationMs = originalEndMs - originalStartMs
            runCatching { origPlayer.seekTo(originalStartMs.toInt()) }

            // 사용자 녹음 묵음 트리밍: startMs 이후부터 재생
            if (userStartMs > 0) {
                runCatching { usrPlayer.seekTo(userStartMs.toInt()) }
            }

            // 동시 시작
            runCatching { origPlayer.start() }
            runCatching { usrPlayer.start() }
            isPlaying = true

            // 원본 구간 종료 예약 (사용자 녹음은 트리밍 후 실질 길이)
            val userTotalDuration = runCatching { usrPlayer.duration.toLong() }.getOrElse { origDurationMs }
            val userEffectiveDuration = (userTotalDuration - userStartMs).coerceAtLeast(0)
            val maxDuration = maxOf(origDurationMs, userEffectiveDuration)
            scheduleStop(maxDuration)

            // 완료 리스너
            origPlayer.setOnCompletionListener { handleCompletion() }
            usrPlayer.setOnCompletionListener { handleCompletion() }

            // 위치 추적
            if (onPositionUpdate != null) {
                startPositionTracking(origDurationMs, originalStartMs, userStartMs, userEffectiveDuration, onPositionUpdate)
            }

            Log.d(TAG, "동시 재생 시작 — orig:[${originalStartMs}ms-${originalEndMs}ms], user:$userFilePath (trim ${userStartMs}ms)")
        }.onFailure { e ->
            Log.e(TAG, "동시 재생 실패", e)
            stop()
            onComplete?.invoke()
        }
    }

    private fun createOriginalPlayer(source: AudioSource?): MediaPlayer? {
        if (source == null) return null
        return runCatching {
            when (source) {
                is AudioSource.AssetPath -> {
                    val afd = context.assets.openFd(source.path)
                    MediaPlayer().apply {
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        prepare()
                    }
                }
                is AudioSource.FilePath -> {
                    MediaPlayer().apply {
                        setDataSource(source.path)
                        prepare()
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * 볼륨 밸런스 설정.
     * @param balance 0.0=원본만, 0.5=둘다 동일, 1.0=녹음만
     */
    fun setBalance(balance: Float) {
        val (origVol, userVol) = calculateVolumes(balance)
        runCatching { originalPlayer?.setVolume(origVol, origVol) }
        runCatching { userPlayer?.setVolume(userVol, userVol) }
    }

    private fun calculateVolumes(balance: Float): Pair<Float, Float> {
        val clamped = balance.coerceIn(0f, 1f)
        val origVol = if (clamped <= 0.5f) 1.0f else (1f - clamped) * 2f
        val userVol = if (clamped >= 0.5f) 1.0f else clamped * 2f
        return origVol to userVol
    }

    private fun applyBalance(balance: Float) {
        val (origVol, userVol) = calculateVolumes(balance)
        runCatching { originalPlayer?.setVolume(origVol, origVol) }
        runCatching { userPlayer?.setVolume(userVol, userVol) }
    }

    private fun startPositionTracking(
        origDurationMs: Long,
        originalStartMs: Long,
        userStartMs: Long,
        userEffectiveDuration: Long,
        onPositionUpdate: (Float, Float) -> Unit
    ) {
        stopPositionTracking()

        positionRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying) return
                runCatching {
                    val origPos = originalPlayer?.currentPosition?.toLong() ?: 0L
                    val origProgress = if (origDurationMs > 0) {
                        ((origPos - originalStartMs).toFloat() / origDurationMs).coerceIn(0f, 1f)
                    } else 0f

                    val userPos = userPlayer?.currentPosition?.toLong() ?: 0L
                    val userProgress = if (userEffectiveDuration > 0) {
                        ((userPos - userStartMs).toFloat() / userEffectiveDuration).coerceIn(0f, 1f)
                    } else 0f

                    onPositionUpdate(origProgress, userProgress)
                }
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL)
            }
        }
        handler.post(positionRunnable!!)
    }

    private fun stopPositionTracking() {
        positionRunnable?.let { handler.removeCallbacks(it) }
        positionRunnable = null
    }

    private fun scheduleStop(durationMs: Long) {
        cancelScheduledStop()
        rangeStopRunnable = Runnable { handleCompletion() }
        handler.postDelayed(rangeStopRunnable!!, durationMs + 200) // 200ms 버퍼
    }

    private fun cancelScheduledStop() {
        rangeStopRunnable?.let { handler.removeCallbacks(it) }
        rangeStopRunnable = null
    }

    private fun handleCompletion() {
        if (!isPlaying) return
        isPlaying = false
        stopPositionTracking()
        cancelScheduledStop()
        val callback = onComplete
        onComplete = null

        runCatching { originalPlayer?.release() }
        runCatching { userPlayer?.release() }
        originalPlayer = null
        userPlayer = null

        callback?.invoke()
    }

    /** 두 플레이어 모두 정지 + 리소스 해제. */
    fun stop() {
        isPlaying = false
        stopPositionTracking()
        cancelScheduledStop()
        onComplete = null

        runCatching {
            originalPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        runCatching {
            userPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        originalPlayer = null
        userPlayer = null
    }
}
