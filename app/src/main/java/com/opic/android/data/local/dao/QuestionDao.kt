package com.opic.android.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.opic.android.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

/** StudyScreen 필터용: 문제 요약 + 학습 진도 JOIN 결과 */
data class QuestionSummary(
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "q_set") val set: String?,
    @ColumnInfo(name = "q_type") val type: String?,
    @ColumnInfo(name = "study_count") val studyCount: Int?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Int?,
    @ColumnInfo(name = "last_modified") val lastModified: String?
)

/** StudyScreen: 문제 상세 + 학습 진도 JOIN */
data class QuestionWithProgress(
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "q_set") val set: String?,
    @ColumnInfo(name = "q_type") val type: String?,
    @ColumnInfo(name = "combo") val combo: String?,
    @ColumnInfo(name = "question_text") val questionText: String?,
    @ColumnInfo(name = "answer_script") val answerScript: String?,
    @ColumnInfo(name = "question_audio") val questionAudio: String?,
    @ColumnInfo(name = "answer_audio") val answerAudio: String?,
    @ColumnInfo(name = "user_script") val userScript: String?,
    @ColumnInfo(name = "study_count") val studyCount: Int?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Int?,
    @ColumnInfo(name = "last_modified") val lastModified: String?,
    @ColumnInfo(name = "stt_text") val sttText: String?,
    @ColumnInfo(name = "analysis_result") val analysisResult: String?
)

@Dao
interface QuestionDao {

    @Query("SELECT * FROM Questions ORDER BY question_id ASC")
    fun getAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM Questions ORDER BY question_id ASC")
    suspend fun getAllQuestionsOnce(): List<QuestionEntity>

    @Query("SELECT * FROM Questions WHERE question_id = :questionId LIMIT 1")
    suspend fun getQuestionById(questionId: Int): QuestionEntity?

    @Query("SELECT * FROM Questions WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): QuestionEntity?

    @Query("SELECT * FROM Questions WHERE title = 'Self Introduction' LIMIT 1")
    suspend fun getSelfIntroduction(): QuestionEntity?

    /** QuestionGenerator용: set/type/combo 있는 일반 문제만 */
    @Query("""
        SELECT * FROM Questions
        WHERE "set" IS NOT NULL AND type IS NOT NULL AND combo IS NOT NULL
          AND title != 'Self Introduction'
        ORDER BY "set", type, combo, question_id
    """)
    suspend fun getAllValidQuestions(): List<QuestionEntity>

    @Query("SELECT DISTINCT \"set\" FROM Questions WHERE \"set\" IS NOT NULL ORDER BY \"set\" ASC")
    suspend fun getAllSets(): List<String>

    @Query("SELECT DISTINCT type FROM Questions WHERE type IS NOT NULL ORDER BY type ASC")
    suspend fun getAllTypes(): List<String>

    @Query("""SELECT * FROM Questions WHERE "set" = :set AND type = :type ORDER BY question_id""")
    suspend fun getBySetAndType(set: String, type: String): List<QuestionEntity>

    @Query("SELECT * FROM Questions WHERE type = :type ORDER BY question_id")
    suspend fun getQuestionsByType(type: String): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM Questions")
    suspend fun getQuestionCount(): Int

    /** LevelCalculator용: MAX(question_id) */
    @Query("SELECT MAX(question_id) FROM Questions")
    suspend fun getMaxQuestionId(): Int?

    @Query("""
        SELECT Q.* FROM Questions Q
        INNER JOIN User_Study_Progress P ON Q.question_id = P.question_id
        WHERE P.user_id = :userId AND P.is_favorite = 1
        ORDER BY Q.question_id
    """)
    fun getFavoriteQuestions(userId: Int): Flow<List<QuestionEntity>>

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("UPDATE Questions SET question_text = :text WHERE question_id = :id")
    suspend fun updateQuestionText(id: Int, text: String)

    @Query("UPDATE Questions SET answer_script = :script WHERE question_id = :id")
    suspend fun updateAnswerScript(id: Int, script: String)

    @Query("UPDATE Questions SET user_script = :script WHERE question_id = :id")
    suspend fun updateUserScript(id: Int, script: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Query("DELETE FROM Questions WHERE question_id = :questionId")
    suspend fun deleteById(questionId: Int)

    @Upsert
    suspend fun upsert(question: QuestionEntity)

    // ==================== StudyScreen 필터 체인용 ====================

    /** 특정 set에 속하는 type 목록 */
    @Query("""SELECT DISTINCT type FROM Questions WHERE "set" = :set AND type IS NOT NULL ORDER BY type ASC""")
    suspend fun getTypesForSet(set: String): List<String>

    /** 특정 type에 속하는 set 목록 */
    @Query("""SELECT DISTINCT "set" FROM Questions WHERE type = :type AND "set" IS NOT NULL ORDER BY "set" ASC""")
    suspend fun getSetsForType(type: String): List<String>

    /** 전체 문제 요약 + 학습 진도 (필터 체인 타이틀 목록용) */
    @Query("""
        SELECT q.question_id, q.title, q."set" AS q_set, q.type AS q_type,
               IFNULL(usp.study_count, 0) AS study_count,
               IFNULL(usp.is_favorite, 0) AS is_favorite,
               usp.last_modified
        FROM Questions q
        LEFT JOIN User_Study_Progress usp ON q.question_id = usp.question_id AND usp.user_id = :userId
        WHERE q.title IS NOT NULL AND q."set" IS NOT NULL AND q.type IS NOT NULL
        ORDER BY q.title ASC
    """)
    suspend fun getAllQuestionsWithProgress(userId: Int): List<QuestionSummary>

    /** 타이틀로 문제 상세 + 학습 진도 로드 */
    @Query("""
        SELECT q.question_id, q.title, q."set" AS q_set, q.type AS q_type, q.combo,
               q.question_text, q.answer_script, q.question_audio, q.answer_audio, q.user_script,
               IFNULL(usp.study_count, 0) AS study_count,
               IFNULL(usp.is_favorite, 0) AS is_favorite,
               usp.last_modified, usp.stt_text, usp.analysis_result
        FROM Questions q
        LEFT JOIN User_Study_Progress usp ON q.question_id = usp.question_id AND usp.user_id = :userId
        WHERE q.title = :title LIMIT 1
    """)
    suspend fun getQuestionWithProgress(title: String, userId: Int): QuestionWithProgress?
}
