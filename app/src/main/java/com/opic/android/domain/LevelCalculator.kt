package com.opic.android.domain

import android.util.Log
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.StudyProgressDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Python _calculate_user_level() 1:1 이식.
 *
 * XP 공식:
 *   study_count를 min(count, 7)로 cap
 *   xp = count * (count + 1) / 2  (삼각수)
 *   max_xp_per_question = 28 (count=7일 때)
 *
 * 레벨 공식:
 *   mastery = total_xp / total_possible_xp
 *   level = floor(sqrt(mastery) * 10) + 1   (clamp 1..10)
 *
 * 게이지 공식:
 *   mastery_start = ((level-1)/10)^2
 *   mastery_end   = (level/10)^2
 *   gauge = (mastery - mastery_start) / (mastery_end - mastery_start)
 */
@Singleton
class LevelCalculator @Inject constructor(
    private val questionDao: QuestionDao,
    private val studyProgressDao: StudyProgressDao
) {

    data class LevelInfo(
        val level: Int,
        val gaugePercent: Int,
        val totalXp: Double,
        val totalPossibleXp: Double
    )

    companion object {
        private const val TAG = "LevelCalculator"
        private const val MAX_XP_PER_QUESTION = 28.0 // study_count=7 → 7*8/2
    }

    suspend fun calculate(): LevelInfo {
        try {
            val totalQuestionCount = questionDao.getMaxQuestionId() ?: 0
            if (totalQuestionCount == 0) return LevelInfo(1, 0, 0.0, 0.0)

            val allCounts = studyProgressDao.getAllStudyCounts()

            var totalXp = 0.0
            for (rawCount in allCounts) {
                val count = minOf(rawCount ?: 0, 7)
                totalXp += count * (count + 1) / 2.0
            }

            val totalPossibleXp = totalQuestionCount * MAX_XP_PER_QUESTION
            if (totalPossibleXp == 0.0) return LevelInfo(1, 0, 0.0, 0.0)

            val masteryPercentage = totalXp / totalPossibleXp

            val weightedMastery = if (masteryPercentage > 0) sqrt(masteryPercentage) else 0.0

            var level = floor(weightedMastery * 10).toInt() + 1
            level = level.coerceIn(1, 10)

            // 게이지 계산
            val masteryStart: Double
            val masteryEnd: Double
            if (level == 10) {
                masteryStart = (9.0 / 10.0) * (9.0 / 10.0) // 0.81
                masteryEnd = 1.0
            } else {
                masteryStart = ((level - 1).toDouble() / 10.0).let { it * it }
                masteryEnd = (level.toDouble() / 10.0).let { it * it }
            }

            val levelTotalMastery = masteryEnd - masteryStart
            val levelCurrentMastery = masteryPercentage - masteryStart

            val gaugePercentage = if (levelTotalMastery == 0.0) {
                if (masteryPercentage >= masteryStart) 1.0 else 0.0
            } else {
                (levelCurrentMastery / levelTotalMastery).coerceIn(0.0, 1.0)
            }

            var gaugePercent = (gaugePercentage * 100).toInt()

            // mastery 100%이면 레벨 10, 게이지 100%
            if (masteryPercentage == 1.0) {
                level = 10
                gaugePercent = 100
            }

            Log.d(TAG, "XP=$totalXp/$totalPossibleXp (${String.format("%.2f%%", masteryPercentage * 100)}), Level=$level, Gauge=$gaugePercent%")

            return LevelInfo(level, gaugePercent, totalXp, totalPossibleXp)
        } catch (e: Exception) {
            Log.e(TAG, "레벨 계산 실패", e)
            return LevelInfo(1, 0, 0.0, 0.0)
        }
    }
}
