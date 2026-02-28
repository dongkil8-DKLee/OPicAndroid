package com.opic.android.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.opic.android.data.local.entity.TestResultEntity
import com.opic.android.data.local.entity.TestSessionEntity
import kotlinx.coroutines.flow.Flow

/** ReviewListScreen용 세션 요약 POJO. */
data class SessionSummary(
    @ColumnInfo(name = "session_id") val sessionId: Int,
    @ColumnInfo(name = "timestamp") val timestamp: String?,
    @ColumnInfo(name = "question_count") val questionCount: Int
)

/** ReviewScreen용 JOIN 결과 POJO. */
data class TestResultWithQuestion(
    @ColumnInfo(name = "result_id") val resultId: Int,
    @ColumnInfo(name = "session_id") val sessionId: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "question_number") val questionNumber: Int,
    @ColumnInfo(name = "user_audio_path") val userAudioPath: String?,
    @ColumnInfo(name = "similarity_score") val similarityScore: Double?,
    @ColumnInfo(name = "stt_result") val sttResult: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "question_text") val questionText: String?,
    @ColumnInfo(name = "answer_script") val answerScript: String?,
    @ColumnInfo(name = "question_audio") val questionAudio: String?,
    @ColumnInfo(name = "answer_audio") val answerAudio: String?,
    @ColumnInfo(name = "user_script") val userScript: String?,
    @ColumnInfo(name = "q_set") val set: String?,
    @ColumnInfo(name = "q_type") val type: String?
)

@Dao
interface TestDao {

    // ---- ID 채번 ----
    @Query("SELECT IFNULL(MAX(session_id), 0) FROM Test_Sessions")
    suspend fun getMaxSessionId(): Int

    @Query("SELECT IFNULL(MAX(result_id), 0) FROM Test_Results")
    suspend fun getMaxResultId(): Int

    // ---- Test Sessions ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TestSessionEntity): Long

    @Query("SELECT * FROM Test_Sessions ORDER BY session_id DESC")
    fun getAllSessions(): Flow<List<TestSessionEntity>>

    @Query("SELECT * FROM Test_Sessions ORDER BY session_id DESC LIMIT 1")
    suspend fun getLastSession(): TestSessionEntity?

    @Query("SELECT * FROM Test_Sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Int): TestSessionEntity?

    // ---- Test Results ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: TestResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<TestResultEntity>)

    @Query("SELECT * FROM Test_Results WHERE session_id = :sessionId ORDER BY question_number ASC")
    suspend fun getResultsForSession(sessionId: Int): List<TestResultEntity>

    @Query("SELECT * FROM Test_Results WHERE session_id = :sessionId ORDER BY question_number ASC")
    fun getResultsForSessionFlow(sessionId: Int): Flow<List<TestResultEntity>>

    @Query("UPDATE Test_Results SET stt_result = :sttResult WHERE result_id = :resultId")
    suspend fun updateSttResult(resultId: Int, sttResult: String)

    /** Python get_last_test_session_data() 대응 — 마지막 세션의 결과 + 문제 정보 JOIN */
    @Query("""
        SELECT tr.result_id, tr.session_id, tr.question_id, tr.question_number,
               tr.user_audio_path, tr.similarity_score, tr.stt_result,
               q.title, q.question_text, q.answer_script,
               q.question_audio, q.answer_audio, q.user_script,
               q."set" AS q_set, q.type AS q_type
        FROM Test_Results tr
        JOIN Questions q ON tr.question_id = q.question_id
        WHERE tr.session_id = (SELECT MAX(session_id) FROM Test_Sessions)
        ORDER BY tr.question_number
    """)
    suspend fun getLastSessionResults(): List<TestResultWithQuestion>

    /** 전체 세션 요약 (ReviewListScreen용) */
    @Query("""
        SELECT ts.session_id, ts.timestamp, COUNT(tr.result_id) AS question_count
        FROM Test_Sessions ts
        LEFT JOIN Test_Results tr ON ts.session_id = tr.session_id
        GROUP BY ts.session_id
        ORDER BY ts.session_id DESC
    """)
    suspend fun getAllSessionSummaries(): List<SessionSummary>

    /** 특정 세션 결과 + 문제 정보 JOIN */
    @Query("""
        SELECT tr.result_id, tr.session_id, tr.question_id, tr.question_number,
               tr.user_audio_path, tr.similarity_score, tr.stt_result,
               q.title, q.question_text, q.answer_script,
               q.question_audio, q.answer_audio, q.user_script,
               q."set" AS q_set, q.type AS q_type
        FROM Test_Results tr
        JOIN Questions q ON tr.question_id = q.question_id
        WHERE tr.session_id = :sessionId
        ORDER BY tr.question_number
    """)
    suspend fun getSessionResults(sessionId: Int): List<TestResultWithQuestion>

    // ---- Delete ----
    @Query("DELETE FROM Test_Sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: Int)

    @Query("DELETE FROM Test_Results WHERE session_id = :sessionId")
    suspend fun deleteResultsBySession(sessionId: Int)

    @Query("SELECT user_audio_path FROM Test_Results WHERE session_id = :sessionId AND user_audio_path IS NOT NULL")
    suspend fun getAudioPathsForSession(sessionId: Int): List<String>

    // Python _update_recording_in_db() 대응
    @Query("UPDATE Test_Results SET user_audio_path = :path WHERE session_id = :sessionId AND question_id = :questionId")
    suspend fun updateAudioPath(sessionId: Int, questionId: Int, path: String)

    // STT 결과 업데이트 (questionId 기반)
    @Query("UPDATE Test_Results SET stt_result = :sttResult WHERE session_id = :sessionId AND question_id = :questionId")
    suspend fun updateSttResultByQuestion(sessionId: Int, questionId: Int, sttResult: String)
}
