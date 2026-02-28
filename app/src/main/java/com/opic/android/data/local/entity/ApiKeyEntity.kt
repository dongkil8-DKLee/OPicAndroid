package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Python Api_Keys 테이블 1:1 매핑. */
@Entity(tableName = "Api_Keys")
data class ApiKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "service_name")
    val serviceName: String,

    @ColumnInfo(name = "api_key")
    val apiKey: String
)
