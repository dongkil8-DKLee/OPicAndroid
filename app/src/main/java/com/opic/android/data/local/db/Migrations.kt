package com.opic.android.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room DB 버전별 마이그레이션 정의.
 *
 * v1 → v2: User_Study_Progress.progress_id를 AUTOINCREMENT PK로 변경.
 *   SQLite는 기존 컬럼에 AUTOINCREMENT를 ALTER TABLE로 추가할 수 없으므로
 *   테이블 재생성(create-copy-drop-rename) 방식으로 처리.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 새 스키마로 임시 테이블 생성 (AUTOINCREMENT 포함)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `User_Study_Progress_new` (
                `progress_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `user_id` INTEGER,
                `question_id` INTEGER,
                `study_count` INTEGER DEFAULT 0,
                `last_modified` TEXT,
                `is_favorite` INTEGER DEFAULT 0,
                `stt_text` TEXT,
                `analysis_result` TEXT,
                FOREIGN KEY(`user_id`) REFERENCES `Users`(`user_id`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(`question_id`) REFERENCES `Questions`(`question_id`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """)

        // 2. 기존 데이터 복사 (progress_id는 AUTOINCREMENT가 자동 할당)
        database.execSQL("""
            INSERT INTO `User_Study_Progress_new`
                (`user_id`, `question_id`, `study_count`,
                 `last_modified`, `is_favorite`, `stt_text`, `analysis_result`)
            SELECT `user_id`, `question_id`, `study_count`,
                   `last_modified`, `is_favorite`, `stt_text`, `analysis_result`
            FROM `User_Study_Progress`
        """)

        // 3. 기존 테이블 삭제
        database.execSQL("DROP TABLE `User_Study_Progress`")

        // 4. 임시 테이블을 원래 이름으로 변경
        database.execSQL("ALTER TABLE `User_Study_Progress_new` RENAME TO `User_Study_Progress`")

        // 5. 인덱스 재생성
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_User_Study_Progress_user_id_question_id`
            ON `User_Study_Progress` (`user_id`, `question_id`)
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_User_Study_Progress_question_id`
            ON `User_Study_Progress` (`question_id`)
        """)
    }
}

/**
 * v2 → v3: Vocabulary 테이블 추가 (단어장/암기장).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `Vocabulary` (
                `wordId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `word` TEXT NOT NULL,
                `meaning` TEXT,
                `memo` TEXT,
                `pronunciation` TEXT,
                `isMemorized` INTEGER NOT NULL DEFAULT 0,
                `isFavorite` INTEGER NOT NULL DEFAULT 0,
                `sourceQuestionId` INTEGER,
                `createdAt` TEXT
            )
        """)
    }
}
