package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Python Test_Sessions 테이블 1:1 매핑.
 * 주의: Python에 difficulty 컬럼 없음 → 제거됨.
 * timestamp: DATETIME → TEXT 변환 완료.
 */
@Entity(
    tableName = "Test_Sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"]
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class TestSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: Int,

    @ColumnInfo(name = "user_id")
    val userId: Int?,

    @ColumnInfo(name = "timestamp", defaultValue = "CURRENT_TIMESTAMP")
    val timestamp: String?
)
