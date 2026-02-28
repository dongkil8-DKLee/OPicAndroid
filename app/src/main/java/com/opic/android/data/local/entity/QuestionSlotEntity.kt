package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Python Question_Slots 테이블 1:1 매핑. */
@Entity(
    tableName = "Question_Slots",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["question_id"],
            childColumns = ["question_id"]
        )
    ],
    indices = [Index(value = ["question_id"])]
)
data class QuestionSlotEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot_id")
    val slotId: Int,

    @ColumnInfo(name = "question_id")
    val questionId: Int?,

    @ColumnInfo(name = "slot_number")
    val slotNumber: Int?
)
