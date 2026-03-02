package com.opic.android.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 오디오 파일에서 PCM 샘플을 읽어 파형 표시용 FloatArray로 변환.
 * - WAV: 직접 PCM 파싱 (빠름)
 * - MP3/기타: MediaExtractor + MediaCodec 디코딩 (Android API)
 * Peak 기반 다운샘플링으로 displayPoints 개수만큼 0.0~1.0 정규화된 값 반환.
 */
object WavSampleReader {

    private const val TAG = "WavSampleReader"
    private const val CODEC_TIMEOUT_US = 10_000L

    private data class WavHeader(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataSize: Int,
        val dataOffset: Int
    )

    /**
     * 외부 파일 시스템의 오디오 파일에서 파형 데이터 추출.
     * WAV → 직접 파싱, MP3 등 → MediaCodec 디코딩.
     */
    fun readFromFile(
        filePath: String,
        displayPoints: Int = 300,
        startMs: Long? = null,
        endMs: Long? = null
    ): FloatArray = runCatching {
        val file = File(filePath)
        if (!file.exists() || file.length() < 44) return@runCatching FloatArray(0)

        // WAV 직접 파싱 시도 (빠름)
        if (!filePath.lowercase().endsWith(".mp3")) {
            val wavResult = readWavFromFile(filePath, displayPoints, startMs, endMs)
            if (wavResult.isNotEmpty()) return@runCatching wavResult
        }

        // Fallback: MediaCodec 디코딩 (MP3, AAC 등)
        decodeFromFile(filePath, displayPoints, startMs, endMs)
    }.getOrElse { e ->
        Log.w(TAG, "readFromFile failed: $filePath", e)
        FloatArray(0)
    }

    /**
     * assets 내 오디오 파일에서 파형 데이터 추출.
     * WAV → 직접 파싱, MP3 등 → MediaCodec 디코딩.
     */
    fun readFromAssets(
        context: Context,
        assetPath: String,
        displayPoints: Int = 300,
        startMs: Long? = null,
        endMs: Long? = null
    ): FloatArray = runCatching {
        // WAV 직접 파싱 시도 (빠름)
        if (!assetPath.lowercase().endsWith(".mp3")) {
            val wavResult = readWavFromAssets(context, assetPath, displayPoints, startMs, endMs)
            if (wavResult.isNotEmpty()) return@runCatching wavResult
        }

        // Fallback: MediaCodec 디코딩 (MP3, AAC 등)
        decodeFromAssets(context, assetPath, displayPoints, startMs, endMs)
    }.getOrElse { e ->
        Log.w(TAG, "readFromAssets failed: $assetPath", e)
        FloatArray(0)
    }

    // ==================== 선행 묵음 감지 ====================

    /**
     * WAV 파일의 선행 묵음 구간(ms) 감지.
     * 진폭이 threshold(최대값 대비 %)를 초과하는 첫 프레임까지의 시간을 반환.
     *
     * @param filePath WAV 파일 경로
     * @param thresholdPercent 묵음 판정 임계값 (기본 3% — 녹음 노이즈 허용)
     * @return 선행 묵음 시간(ms), 오류/비WAV 시 0
     */
    fun detectLeadingSilenceMs(filePath: String, thresholdPercent: Float = 3f): Long = runCatching {
        val file = File(filePath)
        if (!file.exists() || file.length() < 44) return@runCatching 0L

        FileInputStream(file).use { fis ->
            val headerBytes = ByteArray(44)
            if (fis.read(headerBytes) < 44) return@runCatching 0L

            val header = parseHeader(headerBytes) ?: return@runCatching 0L
            if (header.bitsPerSample != 16) return@runCatching 0L

            val frameSize = (header.bitsPerSample / 8) * header.channels
            val threshold = (Short.MAX_VALUE * thresholdPercent / 100f).toInt()

            // 100ms 단위 청크로 읽으며 스캔 (메모리 효율)
            val chunkFrames = header.sampleRate / 10  // 0.1초 분량
            val chunkBytes = chunkFrames * frameSize
            val buf = ByteArray(chunkBytes)
            var frameIndex = 0L

            while (true) {
                val read = fis.read(buf, 0, chunkBytes)
                if (read <= 0) break

                val frames = read / frameSize
                val bb = ByteBuffer.wrap(buf, 0, read).order(ByteOrder.LITTLE_ENDIAN)

                for (f in 0 until frames) {
                    val sample = kotlin.math.abs(bb.getShort((f * frameSize).toInt()).toInt())
                    if (sample > threshold) {
                        val silenceMs = frameIndex * 1000 / header.sampleRate
                        Log.d(TAG, "선행 묵음 감지: ${silenceMs}ms (frame $frameIndex)")
                        return@runCatching silenceMs
                    }
                    frameIndex++
                }
            }

            // 전체가 묵음 → 0 반환 (트리밍 불필요)
            0L
        }
    }.getOrElse { 0L }

    // ==================== WAV 직접 파싱 (기존 로직) ====================

    private fun readWavFromFile(
        filePath: String,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        val file = File(filePath)
        if (!file.exists() || file.length() < 44) return FloatArray(0)
        return FileInputStream(file).use { fis ->
            readWavFromStream(fis, displayPoints, startMs, endMs)
        }
    }

    private fun readWavFromAssets(
        context: Context,
        assetPath: String,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        return context.assets.open(assetPath).use { stream ->
            readWavFromStream(stream, displayPoints, startMs, endMs)
        }
    }

    private fun readWavFromStream(
        inputStream: InputStream,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        val headerBytes = ByteArray(44)
        val headerRead = inputStream.read(headerBytes)
        if (headerRead < 44) return FloatArray(0)

        val header = parseHeader(headerBytes) ?: return FloatArray(0)

        val bytesPerSample = header.bitsPerSample / 8
        val frameSize = bytesPerSample * header.channels
        val totalFrames = header.dataSize / frameSize

        val startFrame = if (startMs != null) {
            ((startMs * header.sampleRate / 1000).coerceIn(0, totalFrames.toLong())).toInt()
        } else 0

        val endFrame = if (endMs != null) {
            ((endMs * header.sampleRate / 1000).coerceIn(0, totalFrames.toLong())).toInt()
        } else totalFrames

        val frameCount = (endFrame - startFrame).coerceAtLeast(0)
        if (frameCount == 0) return FloatArray(0)

        val skipBytes = (startFrame * frameSize).toLong()
        if (skipBytes > 0) {
            var skipped = 0L
            while (skipped < skipBytes) {
                val s = inputStream.skip(skipBytes - skipped)
                if (s <= 0) break
                skipped += s
            }
        }

        val dataBytes = ByteArray(frameCount * frameSize)
        var totalRead = 0
        while (totalRead < dataBytes.size) {
            val r = inputStream.read(dataBytes, totalRead, dataBytes.size - totalRead)
            if (r <= 0) break
            totalRead += r
        }

        val actualFrames = totalRead / frameSize
        if (actualFrames == 0) return FloatArray(0)

        return downsamplePcm16(dataBytes, totalRead, frameSize, header.bitsPerSample, actualFrames, displayPoints)
    }

    private fun parseHeader(bytes: ByteArray): WavHeader? {
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (String(bytes, 0, 4) != "RIFF") return null
        if (String(bytes, 8, 4) != "WAVE") return null

        val channels = buf.getShort(22).toInt()
        val sampleRate = buf.getInt(24)
        val bitsPerSample = buf.getShort(34).toInt()
        val dataSize = buf.getInt(40)

        if (channels <= 0 || sampleRate <= 0 || (bitsPerSample != 8 && bitsPerSample != 16)) {
            return null
        }

        return WavHeader(channels, sampleRate, bitsPerSample, dataSize, 44)
    }

    // ==================== MediaCodec 디코딩 (MP3 등) ====================

    private fun decodeFromFile(
        filePath: String,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            return decodeWithExtractor(extractor, displayPoints, startMs, endMs)
        } finally {
            extractor.release()
        }
    }

    private fun decodeFromAssets(
        context: Context,
        assetPath: String,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        val afd = context.assets.openFd(assetPath)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            return decodeWithExtractor(extractor, displayPoints, startMs, endMs)
        } finally {
            extractor.release()
        }
    }

    /**
     * MediaExtractor → MediaCodec으로 오디오를 PCM 디코딩하여 파형 추출.
     */
    private fun decodeWithExtractor(
        extractor: MediaExtractor,
        displayPoints: Int,
        startMs: Long?,
        endMs: Long?
    ): FloatArray {
        // 오디오 트랙 찾기
        val audioTrackIndex = findAudioTrack(extractor) ?: return FloatArray(0)
        extractor.selectTrack(audioTrackIndex)

        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
        val sampleRate = format.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
        val channels = format.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)

        // 시작 위치로 seek
        if (startMs != null && startMs > 0) {
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        // MediaCodec 디코더 생성
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmSamples = mutableListOf<Short>()
        val endUs = if (endMs != null) endMs * 1000 else Long.MAX_VALUE
        val startUs = (startMs ?: 0) * 1000
        var inputDone = false
        var outputDone = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (!outputDone) {
                // 입력 버퍼에 데이터 공급
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            if (presentationTimeUs > endUs) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            }
                            extractor.advance()
                        }
                    }
                }

                // 출력 버퍼에서 PCM 추출
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }

                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frameCount = shortBuf.remaining() / channels
                            // 첫 번째 채널만 사용 (mono 변환)
                            for (i in 0 until frameCount) {
                                pcmSamples.add(shortBuf.get(i * channels))
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* 포맷 변경 무시 */ }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        if (pcmSamples.isEmpty()) return FloatArray(0)

        // ShortArray → peak 다운샘플링
        val totalFrames = pcmSamples.size
        val points = displayPoints.coerceAtMost(totalFrames)
        if (points == 0) return FloatArray(0)

        val result = FloatArray(points)
        val framesPerBucket = totalFrames.toFloat() / points
        val maxVal = Short.MAX_VALUE.toFloat()

        for (i in 0 until points) {
            val bucketStart = (i * framesPerBucket).toInt()
            val bucketEnd = ((i + 1) * framesPerBucket).toInt().coerceAtMost(totalFrames)
            var peak = 0f
            for (f in bucketStart until bucketEnd) {
                val abs = kotlin.math.abs(pcmSamples[f].toFloat())
                if (abs > peak) peak = abs
            }
            result[i] = (peak / maxVal).coerceIn(0f, 1f)
        }

        Log.d(TAG, "MediaCodec 디코딩 완료: ${pcmSamples.size} samples → $points points")
        return result
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun MediaFormat.getIntOrDefault(key: String, default: Int): Int {
        return if (containsKey(key)) getInteger(key) else default
    }

    // ==================== 공용 다운샘플링 ====================

    private fun downsamplePcm16(
        dataBytes: ByteArray,
        totalRead: Int,
        frameSize: Int,
        bitsPerSample: Int,
        actualFrames: Int,
        displayPoints: Int
    ): FloatArray {
        val bytesPerSample = bitsPerSample / 8
        val points = displayPoints.coerceAtMost(actualFrames)
        if (points == 0) return FloatArray(0)

        val result = FloatArray(points)
        val buf = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        val framesPerBucket = actualFrames.toFloat() / points
        val maxVal = if (bitsPerSample == 16) Short.MAX_VALUE.toFloat() else 127f

        for (i in 0 until points) {
            val bucketStart = (i * framesPerBucket).toInt()
            val bucketEnd = ((i + 1) * framesPerBucket).toInt().coerceAtMost(actualFrames)
            var peak = 0f

            for (f in bucketStart until bucketEnd) {
                val pos = f * frameSize
                if (pos + bytesPerSample > totalRead) break

                val sample = if (bitsPerSample == 16) {
                    buf.getShort(pos).toFloat()
                } else {
                    (buf.get(pos).toInt() - 128).toFloat()
                }
                val abs = kotlin.math.abs(sample)
                if (abs > peak) peak = abs
            }
            result[i] = (peak / maxVal).coerceIn(0f, 1f)
        }
        return result
    }
}
