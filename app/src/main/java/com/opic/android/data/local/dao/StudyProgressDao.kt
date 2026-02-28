package com.opic.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.opic.android.data.local.entity.StudyProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyProgressDao {

    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId")
    fun getAllProgressForUser(userId: Int): Flow<List<StudyProgressEntity>>

    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId")
    suspend fun getAllProgressForUserSync(userId: Int): List<StudyProgressEntity>

    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId AND question_id = :questionId LIMIT 1")
    suspend fun getProgress(userId: Int, questionId: Int): StudyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(progress: StudyProgressEntity): Long

    @Update
    suspend fun update(progress: StudyProgressEntity)

    @Query("""
        INSERT OR REPLACE INTO User_Study_Progress (user_id, question_id, study_count, last_modified, is_favorite, stt_text, analysis_result)
        VALUES (:userId, :questionId, :studyCount, :lastModified,
            COALESCE((SELECT is_favorite FROM User_Study_Progress WHERE user_id = :userId AND question_id = :questionId), 0),
            :sttText,
            COALESCE((SELECT analysis_result FROM User_Study_Progress WHERE user_id = :userId AND question_id = :questionId), NULL))
    """)
    suspend fun upsertStudyCount(
        userId: Int,
        questionId: Int,
        studyCount: Int,
        lastModified: String,
        sttText: String?
    )

    /** Python study_count + 1, last_modified = now 대응 */
    @Query("""
        UPDATE User_Study_Progress
        SET study_count = study_count + 1, last_modified = datetime('now')
        WHERE user_id = :userId AND question_id = :questionId
    """)
    suspend fun incrementStudyCount(userId: Int, questionId: Int)

    @Query("""
        UPDATE User_Study_Progress
        SET is_favorite = CASE WHEN is_favorite = 1 THEN 0 ELSE 1 END
        WHERE user_id = :userId AND question_id = :questionId
    """)
    suspend fun toggleFavorite(userId: Int, questionId: Int)

    @Query("""
        UPDATE User_Study_Progress
        SET is_favorite = :isFavorite
        WHERE user_id = :userId AND question_id = :questionId
    """)
    suspend fun updateFavorite(userId: Int, questionId: Int, isFavorite: Int)

    /** StudyDecay용: study_count > 0이고 last_modified가 있는 모든 레코드 (userId 무관) */
    @Query("SELECT * FROM User_Study_Progress WHERE study_count > 0 AND last_modified IS NOT NULL")
    suspend fun getDecayCandidates(): List<StudyProgressEntity>

    /** StudyDecay용: progress_id로 study_count만 갱신 */
    @Query("UPDATE User_Study_Progress SET study_count = :count WHERE progress_id = :id")
    suspend fun updateStudyCount(id: Int, count: Int)

    /** LevelCalculator용: 전체 study_count 목록 */
    @Query("SELECT study_count FROM User_Study_Progress")
    suspend fun getAllStudyCounts(): List<Int?>

    @Query("SELECT * FROM User_Study_Progress WHERE user_id = :userId AND study_count > 0 AND last_modified IS NOT NULL")
    suspend fun getProgressWithStudyCount(userId: Int): List<StudyProgressEntity>

    @Query("UPDATE User_Study_Progress SET stt_text = :sttText WHERE user_id = :userId AND question_id = :questionId")
    suspend fun updateSttText(userId: Int, questionId: Int, sttText: String)

    @Query("UPDATE User_Study_Progress SET analysis_result = :analysisResult WHERE user_id = :userId AND question_id = :questionId")
    suspend fun updateAnalysisResult(userId: Int, questionId: Int, analysisResult: String)
}
