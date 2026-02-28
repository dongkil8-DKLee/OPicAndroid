package com.opic.android.util

import org.json.JSONArray
import org.json.JSONObject

data class AnalysisResult(
    val accuracyPercent: Float,
    val grade: String,
    val expectedWordCount: Int,
    val actualWordCount: Int,
    val matchedCount: Int,
    val missingCount: Int,
    val extraCount: Int,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val feedback: String
)

object SpeechAnalyzer {

    fun analyze(expectedText: String, actualText: String): AnalysisResult {
        val segments = WordDiff.computeWordDiff(expectedText, actualText)

        val matchedWords = segments.filter { it.type == DiffType.MATCH }
        val missingWords = segments.filter { it.type == DiffType.MISSING }
        val extraWords = segments.filter { it.type == DiffType.EXTRA }

        val matchedCount = matchedWords.size
        val missingCount = missingWords.size
        val extraCount = extraWords.size
        val expectedWordCount = matchedCount + missingCount
        val actualWordCount = matchedCount + extraCount

        val denominator = maxOf(expectedWordCount, actualWordCount)
        val accuracyPercent = if (denominator > 0) {
            (matchedCount.toFloat() / denominator) * 100f
        } else {
            0f
        }

        val grade = when {
            accuracyPercent >= 90f -> "A"
            accuracyPercent >= 75f -> "B"
            accuracyPercent >= 60f -> "C"
            accuracyPercent >= 40f -> "D"
            else -> "F"
        }

        val feedback = when (grade) {
            "A" -> "훌륭합니다! 거의 완벽하게 전달했습니다."
            "B" -> "잘했어요! 대부분의 내용을 잘 전달했습니다."
            "C" -> "괜찮아요. 핵심 내용은 전달했지만 누락된 부분이 있습니다."
            "D" -> "더 연습이 필요합니다. 주요 내용을 다시 확인해 보세요."
            else -> "기초부터 다시 연습해 보세요. 스크립트를 충분히 숙지한 후 도전하세요."
        }

        return AnalysisResult(
            accuracyPercent = accuracyPercent,
            grade = grade,
            expectedWordCount = expectedWordCount,
            actualWordCount = actualWordCount,
            matchedCount = matchedCount,
            missingCount = missingCount,
            extraCount = extraCount,
            missingWords = missingWords.map { it.text },
            extraWords = extraWords.map { it.text },
            feedback = feedback
        )
    }

    fun toJson(result: AnalysisResult): String {
        val json = JSONObject()
        json.put("accuracyPercent", result.accuracyPercent.toDouble())
        json.put("grade", result.grade)
        json.put("expectedWordCount", result.expectedWordCount)
        json.put("actualWordCount", result.actualWordCount)
        json.put("matchedCount", result.matchedCount)
        json.put("missingCount", result.missingCount)
        json.put("extraCount", result.extraCount)
        json.put("missingWords", JSONArray(result.missingWords))
        json.put("extraWords", JSONArray(result.extraWords))
        json.put("feedback", result.feedback)
        return json.toString()
    }

    fun fromJson(jsonStr: String): AnalysisResult? {
        return try {
            val json = JSONObject(jsonStr)
            AnalysisResult(
                accuracyPercent = json.getDouble("accuracyPercent").toFloat(),
                grade = json.getString("grade"),
                expectedWordCount = json.getInt("expectedWordCount"),
                actualWordCount = json.getInt("actualWordCount"),
                matchedCount = json.getInt("matchedCount"),
                missingCount = json.getInt("missingCount"),
                extraCount = json.getInt("extraCount"),
                missingWords = (0 until json.getJSONArray("missingWords").length()).map {
                    json.getJSONArray("missingWords").getString(it)
                },
                extraWords = (0 until json.getJSONArray("extraWords").length()).map {
                    json.getJSONArray("extraWords").getString(it)
                },
                feedback = json.getString("feedback")
            )
        } catch (e: Exception) {
            null
        }
    }
}
