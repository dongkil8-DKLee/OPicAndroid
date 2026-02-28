package com.opic.android.util

/**
 * Python SubtitleSync.build_uniform_from_text() 1:1 포팅.
 * 문장 분리 + 문자 수 비율 proportional 시간 분배.
 */

data class SentenceSegment(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
)

object SentenceSplitter {

    private val SENTENCE_SPLIT_REGEX = Regex("""(?<=[.?!])\s+""")

    /**
     * 텍스트를 문장 단위로 분리하고 totalDurationMs를 문자 수 비율로 분배.
     *
     * @param text 분리할 텍스트 (Answer Script)
     * @param totalDurationMs 전체 오디오 길이(ms)
     * @return 문장별 시간 범위 리스트
     */
    fun split(text: String, totalDurationMs: Long): List<SentenceSegment> {
        if (text.isBlank() || totalDurationMs <= 0) return emptyList()

        val sentences = SENTENCE_SPLIT_REGEX.split(text.trim()).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return emptyList()

        if (sentences.size == 1) {
            return listOf(
                SentenceSegment(
                    index = 0,
                    text = sentences[0],
                    startMs = 0,
                    endMs = totalDurationMs,
                    durationMs = totalDurationMs
                )
            )
        }

        val totalChars = sentences.sumOf { it.length }
        if (totalChars == 0) return emptyList()

        val segments = mutableListOf<SentenceSegment>()
        var currentMs = 0L

        sentences.forEachIndexed { index, sentence ->
            val startMs = currentMs
            val endMs = if (index == sentences.size - 1) {
                // 마지막 문장: totalDurationMs까지 확장 (반올림 오차 방지)
                totalDurationMs
            } else {
                val proportionalMs = (sentence.length.toDouble() / totalChars * totalDurationMs).toLong()
                currentMs + proportionalMs
            }
            val durationMs = endMs - startMs

            segments.add(
                SentenceSegment(
                    index = index,
                    text = sentence,
                    startMs = startMs,
                    endMs = endMs,
                    durationMs = durationMs
                )
            )
            currentMs = endMs
        }

        return segments
    }
}
