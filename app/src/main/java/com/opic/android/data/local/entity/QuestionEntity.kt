package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Python Questions 테이블 1:1 매핑.
 * 컬럼명·타입·nullable 모두 opic.db pragma 기준.
 */
@Entity(tableName = "Questions")
data class QuestionEntity(
    @PrimaryKey
    @ColumnInfo(name = "question_id")
    val questionId: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "set")
    val set: String?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "combo")
    val combo: String?,

    @ColumnInfo(name = "question_text")
    val questionText: String?,

    @ColumnInfo(name = "answer_script")
    val answerScript: String?,

    @ColumnInfo(name = "question_audio")
    val questionAudio: String?,

    @ColumnInfo(name = "answer_audio")
    val answerAudio: String?,

    @ColumnInfo(name = "user_script")
    val userScript: String?
)
