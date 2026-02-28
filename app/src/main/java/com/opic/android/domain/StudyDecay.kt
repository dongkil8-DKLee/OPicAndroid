package com.opic.android.domain

import android.util.Log
import com.opic.android.data.local.dao.StudyProgressDao
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python apply_study_decay() 1:1 이식.
 *
 * 규칙:
 * - study_count > 0 이고 last_modified IS NOT NULL 인 레코드 대상
 * - days_passed = (now - last_modified).days
 * - days_passed >= 7 이면 decay_amount = days_passed / 7
 * - new_study_count = max(0, study_count - decay_amount)
 * - last_modified는 변경하지 않음
 */
@Singleton
class StudyDecay @Inject constructor(
    private val studyProgressDao: StudyProgressDao
) {

    companion object {
        private const val TAG = "StudyDecay"
        private val FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
        )
    }

    suspend fun apply(): Int {
        val candidates = studyProgressDao.getDecayCandidates()
        if (candidates.isEmpty()) return 0

        val now = LocalDateTime.now()
        var updatedCount = 0

        for (record in candidates) {
            val studyCount = record.studyCount ?: continue
            val lastModifiedStr = record.lastModified ?: continue

            val lastModified = parseDateTime(lastModifiedStr) ?: continue
            val daysPassed = ChronoUnit.DAYS.between(lastModified, now).toInt()

            if (daysPassed >= 7) {
                val decayAmount = daysPassed / 7
                val newStudyCount = maxOf(0, studyCount - decayAmount)
                if (newStudyCount != studyCount) {
                    studyProgressDao.updateStudyCount(record.progressId, newStudyCount)
                    updatedCount++
                }
            }
        }

        if (updatedCount > 0) {
            Log.d(TAG, "${updatedCount}개 항목에 학습 횟수 자동 감소 적용")
        }
        return updatedCount
    }

    private fun parseDateTime(str: String): LocalDateTime? {
        for (formatter in FORMATTERS) {
            try {
                return LocalDateTime.parse(str, formatter)
            } catch (_: Exception) { }
        }
        Log.w(TAG, "날짜 파싱 실패: $str")
        return null
    }
}
