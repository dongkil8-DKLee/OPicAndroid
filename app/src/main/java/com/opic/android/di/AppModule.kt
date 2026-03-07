package com.opic.android.di

import android.content.Context
import androidx.room.Room
import com.opic.android.data.local.db.MIGRATION_1_2
import com.opic.android.data.local.db.MIGRATION_2_3
import com.opic.android.data.local.db.MIGRATION_3_4
import com.opic.android.data.local.db.MIGRATION_4_5
import com.opic.android.data.local.db.MIGRATION_5_6
import com.opic.android.data.local.db.OPicDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOPicDatabase(@ApplicationContext context: Context): OPicDatabase {
        return Room.databaseBuilder(context, OPicDatabase::class.java, "opic.db")
            .createFromAsset("opic.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Provides fun provideQuestionDao(db: OPicDatabase) = db.questionDao()
    @Provides fun provideStudyProgressDao(db: OPicDatabase) = db.studyProgressDao()
    @Provides fun provideTestDao(db: OPicDatabase) = db.testDao()
    @Provides fun provideApiKeyDao(db: OPicDatabase) = db.apiKeyDao()
    @Provides fun provideVocabularyDao(db: OPicDatabase) = db.vocabularyDao()
}
