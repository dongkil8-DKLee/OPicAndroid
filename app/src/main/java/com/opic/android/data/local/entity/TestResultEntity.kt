package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Python Test_Results 테이블 1:1 매핑. */
@Entity(
    tableName = "Test_Results",
    foreignKeys = [
        ForeignKey(
            entity = TestSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"]
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["question_id"],
            childColumns = ["question_id"]
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["question_id"])
    ]
)
data class TestResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "result_id")
    val resultId: Int,

    @ColumnInfo(name = "session_id")
    val sessionId: Int?,

    @ColumnInfo(name = "question_id")
    val questionId: Int?,

    @ColumnInfo(name = "question_number")
    val questionNumber: Int?,

    @ColumnInfo(name = "user_audio_path")
    val userAudioPath: String?,

    @ColumnInfo(name = "similarity_score")
    val similarityScore: Double?,

    @ColumnInfo(name = "stt_result")
    val sttResult: String?
)
