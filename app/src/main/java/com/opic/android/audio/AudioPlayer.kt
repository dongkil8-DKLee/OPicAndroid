package com.opic.android.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python pygame.mixer 대응.
 * MediaPlayer 래퍼 — assets/Sound/ 재생 + 파일 재생 + 완료 콜백.
 */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var onComplete: (() -> Unit)? = null
    private var currentSpeed: Float = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private var rangeStopRunnable: Runnable? = null

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val currentPosition: Int get() = mediaPlayer?.currentPosition ?: 0
    val duration: Int get() = mediaPlayer?.duration ?: 0

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.playbackParams = PlaybackParams().setSpeed(speed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set playback speed: $speed", e)
        }
    }

    /**
     * assets/ 경로의 오디오 파일 재생.
     * @param assetPath "Sound/AL_01_Q_00.mp3" 형태
     */
    fun playFromAssets(assetPath: String, onComplete: (() -> Unit)? = null) {
        stop()
        this.onComplete = onComplete
        try {
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { handleCompletion() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    handleCompletion()
                    true
                }
                prepare()
                start()
                if (currentSpeed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(currentSpeed)
                }
            }
            Log.d(TAG, "Playing from assets: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play from assets: $assetPath", e)
            onComplete?.invoke()
        }
    }

    /**
     * 파일 시스템 경로의 오디오 파일 재생 (사용자 녹음 파일).
     * @param filePath 절대 경로
     */
    fun playFromFile(filePath: String, onComplete: (() -> Unit)? = null) {
        stop()
        this.onComplete = onComplete
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener { handleCompletion() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    handleCompletion()
                    true
                }
                prepare()
                start()
                if (currentSpeed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(currentSpeed)
                }
            }
            Log.d(TAG, "Playing from file: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play from file: $filePath", e)
            onComplete?.invoke()
        }
    }

    /**
     * assets 오디오의 특정 구간만 재생 (문장 연습용).
     * seekTo(startMs) → start → (endMs - startMs)/speed 후 자동 stop.
     */
    fun playRangeFromAssets(assetPath: String, startMs: Long, endMs: Long, onComplete: (() -> Unit)? = null) {
        stop()
        this.onComplete = onComplete
        try {
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { handleCompletion() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    handleCompletion()
                    true
                }
                prepare()
                seekTo(startMs.toInt())
                start()
                if (currentSpeed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(currentSpeed)
                }
            }
            scheduleRangeStop(endMs - startMs)
            Log.d(TAG, "Playing range from assets: $assetPath [${startMs}ms - ${endMs}ms]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play range from assets: $assetPath", e)
            onComplete?.invoke()
        }
    }

    /**
     * 파일 시스템 오디오의 특정 구간만 재생 (문장 연습용).
     */
    fun playRangeFromFile(filePath: String, startMs: Long, endMs: Long, onComplete: (() -> Unit)? = null) {
        stop()
        this.onComplete = onComplete
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener { handleCompletion() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    handleCompletion()
                    true
                }
                prepare()
                seekTo(startMs.toInt())
                start()
                if (currentSpeed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(currentSpeed)
                }
            }
            scheduleRangeStop(endMs - startMs)
            Log.d(TAG, "Playing range from file: $filePath [${startMs}ms - ${endMs}ms]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play range from file: $filePath", e)
            onComplete?.invoke()
        }
    }

    /** (durationMs / currentSpeed)ms 후 자동 stop + onComplete 호출 */
    private fun scheduleRangeStop(durationMs: Long) {
        cancelRangeStop()
        val delayMs = (durationMs / currentSpeed).toLong()
        rangeStopRunnable = Runnable { handleCompletion() }
        handler.postDelayed(rangeStopRunnable!!, delayMs)
    }

    private fun cancelRangeStop() {
        rangeStopRunnable?.let { handler.removeCallbacks(it) }
        rangeStopRunnable = null
    }

    fun stop() {
        cancelRangeStop()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping player", e)
        }
        mediaPlayer = null
        onComplete = null
    }

    private fun handleCompletion() {
        cancelRangeStop()
        val callback = onComplete
        onComplete = null
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        callback?.invoke()
    }
}
