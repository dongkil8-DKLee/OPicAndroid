package com.opic.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opic.android.data.local.dao.ApiKeyDao
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.StudyProgressDao
import com.opic.android.data.local.dao.TestDao
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.local.entity.ApiKeyEntity
import com.opic.android.data.local.entity.QuestionEntity
import com.opic.android.data.local.entity.QuestionSlotEntity
import com.opic.android.data.local.entity.StudyProgressEntity
import com.opic.android.data.local.entity.TestResultEntity
import com.opic.android.data.local.entity.TestSessionEntity
import com.opic.android.data.local.entity.UserEntity
import com.opic.android.data.local.entity.VocabularyEntity

/**
 * Room Database — Python opic.db 7개 테이블 1:1 매핑.
 * createFromAsset("opic.db")로 Python DB를 그대로 사용.
 */
@Database(
    entities = [
        UserEntity::class,
        QuestionEntity::class,
        QuestionSlotEntity::class,
        StudyProgressEntity::class,
        TestSessionEntity::class,
        TestResultEntity::class,
        ApiKeyEntity::class,
        VocabularyEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OPicDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyProgressDao(): StudyProgressDao
    abstract fun testDao(): TestDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun vocabularyDao(): VocabularyDao
}
