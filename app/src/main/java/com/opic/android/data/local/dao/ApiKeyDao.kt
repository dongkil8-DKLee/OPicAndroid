package com.opic.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.opic.android.data.local.entity.ApiKeyEntity

/** Python Api_Keys 테이블 CRUD. */
@Dao
interface ApiKeyDao {

    @Query("SELECT api_key FROM Api_Keys WHERE service_name = :serviceName LIMIT 1")
    suspend fun getApiKey(serviceName: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(apiKey: ApiKeyEntity)
}
