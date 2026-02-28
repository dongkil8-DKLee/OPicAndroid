package com.opic.android.util

enum class DiffType { MATCH, MISSING, EXTRA }

data class DiffSegment(val text: String, val type: DiffType)

object WordDiff {

    /** Tokenize text into lowercase words, stripping punctuation (keep apostrophes). */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /** Compute LCS indices using dynamic programming. Returns list of (indexInA, indexInB). */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to find matched pairs
        val result = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    result.add(Pair(i - 1, j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] >= dp[i][j - 1] -> i--
                else -> j--
            }
        }
        return result.reversed()
    }

    /**
     * Compute word-level diff between expected and actual text.
     * - MATCH: word present in both (green)
     * - MISSING: word in expected but not in actual (red)
     * - EXTRA: word in actual but not in expected (blue)
     *
     * Uses original-case words from the expected/actual texts for display.
     */
    fun computeWordDiff(expected: String, actual: String): List<DiffSegment> {
        if (expected.isBlank() && actual.isBlank()) return emptyList()

        val expectedWords = expected.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val actualWords = actual.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        val expectedLower = expectedWords.map { it.lowercase().replace(Regex("[^a-z0-9']"), "") }
        val actualLower = actualWords.map { it.lowercase().replace(Regex("[^a-z0-9']"), "") }

        val matched = lcs(expectedLower, actualLower)

        val result = mutableListOf<DiffSegment>()

        val matchedExpIdx = matched.map { it.first }.toSet()
        val matchedActIdx = matched.map { it.second }.toSet()

        var ei = 0
        var ai = 0
        var mi = 0

        while (ei < expectedWords.size || ai < actualWords.size) {
            if (mi < matched.size) {
                val (me, ma) = matched[mi]

                // MISSING words before this match in expected
                while (ei < me) {
                    result.add(DiffSegment(expectedWords[ei], DiffType.MISSING))
                    ei++
                }

                // EXTRA words before this match in actual
                while (ai < ma) {
                    result.add(DiffSegment(actualWords[ai], DiffType.EXTRA))
                    ai++
                }

                // MATCH
                result.add(DiffSegment(expectedWords[me], DiffType.MATCH))
                ei = me + 1
                ai = ma + 1
                mi++
            } else {
                // Remaining MISSING
                while (ei < expectedWords.size) {
                    result.add(DiffSegment(expectedWords[ei], DiffType.MISSING))
                    ei++
                }
                // Remaining EXTRA
                while (ai < actualWords.size) {
                    result.add(DiffSegment(actualWords[ai], DiffType.EXTRA))
                    ai++
                }
            }
        }

        return result
    }
}
