package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Python User_Study_Progress 테이블 1:1 매핑.
 *
 * pragma 기준:
 *   study_count  INTEGER DEFAULT 0  (notnull=0)
 *   last_modified TEXT               (DATETIME→TEXT 변환 완료)
 *   is_favorite  INTEGER DEFAULT 0  (notnull=0)
 */
@Entity(
    tableName = "User_Study_Progress",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"]
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["question_id"],
            childColumns = ["question_id"]
        )
    ],
    indices = [
        Index(value = ["user_id", "question_id"], unique = true),
        Index(value = ["question_id"])
    ]
)
data class StudyProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "progress_id")
    val progressId: Int,

    @ColumnInfo(name = "user_id")
    val userId: Int?,

    @ColumnInfo(name = "question_id")
    val questionId: Int?,

    @ColumnInfo(name = "study_count", defaultValue = "0")
    val studyCount: Int?,

    @ColumnInfo(name = "last_modified")
    val lastModified: String?,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Int?,

    @ColumnInfo(name = "stt_text")
    val sttText: String?,

    @ColumnInfo(name = "analysis_result")
    val analysisResult: String?
)
