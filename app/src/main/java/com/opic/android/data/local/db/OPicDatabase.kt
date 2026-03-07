package com.opic.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Questions ADD COLUMN ai_answer TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // memoryLevel: 0=X, 1=△, 2=O
        // 기존 isMemorized=1(true) → memoryLevel=2(O)
        database.execSQL("ALTER TABLE Vocabulary ADD COLUMN memoryLevel INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE Vocabulary SET memoryLevel = 2 WHERE isMemorized = 1")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Questions ADD COLUMN is_ai_generated INTEGER NOT NULL DEFAULT 0")
    }
}

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
    version = 6,
    exportSchema = false
)
abstract class OPicDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyProgressDao(): StudyProgressDao
    abstract fun testDao(): TestDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun vocabularyDao(): VocabularyDao
}
