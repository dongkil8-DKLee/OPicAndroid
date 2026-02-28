package com.opic.android.util

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM raw data → WAV 파일 작성.
 * Python scipy.io.wavfile.write() 대응.
 *
 * 포맷: 44100Hz, mono, 16-bit signed PCM (RIFF/WAVE).
 */
object WavWriter {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    /**
     * Short array(PCM 16-bit) → WAV 파일.
     * 녹음 완료 후 호출.
     */
    fun write(file: File, pcmData: ShortArray) {
        val dataSize = pcmData.size * 2 // 16-bit = 2 bytes per sample
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                // RIFF header
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVE".toByteArray())
                // fmt sub-chunk
                put("fmt ".toByteArray())
                putInt(16)                  // Sub-chunk size
                putShort(1)                 // PCM format
                putShort(CHANNELS.toShort())
                putInt(SAMPLE_RATE)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(BITS_PER_SAMPLE.toShort())
                // data sub-chunk
                put("data".toByteArray())
                putInt(dataSize)
            }
            fos.write(header.array())

            // PCM data (little-endian shorts)
            val buf = ByteBuffer.allocate(dataSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                pcmData.forEach { putShort(it) }
            }
            fos.write(buf.array())
        }
    }

    /**
     * Streaming 방식: 먼저 헤더(placeholder)를 쓰고,
     * 녹음 중 appendPcm()으로 데이터 추가,
     * 녹음 종료 후 finalize()로 헤더 크기 패치.
     */
    class StreamWriter(private val file: File) {
        private val fos = FileOutputStream(file)
        private var dataSize = 0

        init {
            // 44-byte placeholder header
            fos.write(ByteArray(44))
        }

        fun appendPcm(buffer: ShortArray, readCount: Int) {
            val byteBuffer = ByteBuffer.allocate(readCount * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until readCount) putShort(buffer[i])
            }
            fos.write(byteBuffer.array())
            dataSize += readCount * 2
        }

        fun finalize() {
            fos.close()
            // Patch header with actual sizes
            RandomAccessFile(file, "rw").use { raf ->
                val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
                val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

                val header = ByteBuffer.allocate(44).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put("RIFF".toByteArray())
                    putInt(36 + dataSize)
                    put("WAVE".toByteArray())
                    put("fmt ".toByteArray())
                    putInt(16)
                    putShort(1)
                    putShort(CHANNELS.toShort())
                    putInt(SAMPLE_RATE)
                    putInt(byteRate)
                    putShort(blockAlign.toShort())
                    putShort(BITS_PER_SAMPLE.toShort())
                    put("data".toByteArray())
                    putInt(dataSize)
                }
                raf.seek(0)
                raf.write(header.array())
            }
        }
    }
}
