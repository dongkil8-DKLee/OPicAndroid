package com.opic.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.opic.android.util.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Python sounddevice + scipy.io.wavfile 대응.
 *
 * 44100Hz mono 16-bit PCM 녹음.
 * RMS 콜백: normalized = min(1.0, rms * 15.0)
 * 120초 자동 종료.
 * WAV 파일 StreamWriter로 실시간 저장.
 */
@Singleton
class AudioRecorder @Inject constructor() {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_DURATION_SEC = 120
        private const val RMS_SENSITIVITY = 15.0f
    }

    @Volatile
    var isRecording = false
        private set

    /**
     * 녹음 시작 (코루틴 내에서 호출).
     * IO 디스패처에서 실행되며, 녹음이 끝나면 반환.
     *
     * @param outputFile WAV 파일 출력 경로
     * @param onRmsLevel 100ms 간격 RMS 레벨 콜백 (0.0~1.0)
     */
    suspend fun record(
        outputFile: File,
        onRmsLevel: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            SAMPLE_RATE // 최소 1초 분량
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 초기화 실패")
            audioRecord.release()
            return@withContext
        }

        val writer = WavWriter.StreamWriter(outputFile)
        val buffer = ShortArray(bufferSize)
        isRecording = true

        try {
            audioRecord.startRecording()
            Log.d(TAG, "녹음 시작: ${outputFile.name}")

            val startTime = System.currentTimeMillis()
            var lastRmsTime = startTime

            while (isRecording && isActive) {
                val readCount = audioRecord.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    writer.appendPcm(buffer, readCount)

                    // RMS 계산 (~100ms 간격)
                    val now = System.currentTimeMillis()
                    if (now - lastRmsTime >= 100) {
                        val rms = calculateRms(buffer, readCount)
                        val normalized = min(1.0f, rms * RMS_SENSITIVITY)
                        onRmsLevel(normalized)
                        lastRmsTime = now
                    }

                    // 120초 자동 종료
                    if (now - startTime >= MAX_DURATION_SEC * 1000L) {
                        Log.d(TAG, "120초 자동 종료")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "녹음 중 오류", e)
        } finally {
            isRecording = false
            onRmsLevel(0f)
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (_: Exception) { }
            writer.finalize()
            Log.d(TAG, "녹음 완료: ${outputFile.name} (${outputFile.length()} bytes)")
        }
    }

    fun stop() {
        isRecording = false
    }

    /**
     * Python RMS 공식 1:1 이식:
     *   rms = sqrt(mean(samples^2))
     * 단, 16-bit PCM이므로 32767로 정규화.
     */
    private fun calculateRms(buffer: ShortArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE // -1.0 ~ 1.0
            sum += sample * sample
        }
        return sqrt(sum / count).toFloat()
    }
}
