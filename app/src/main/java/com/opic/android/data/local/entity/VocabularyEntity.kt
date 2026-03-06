package com.opic.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Vocabulary")
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "wordId")
    val wordId: Int = 0,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "meaning")
    val meaning: String? = null,

    @ColumnInfo(name = "memo")
    val memo: String? = null,

    @ColumnInfo(name = "pronunciation")
    val pronunciation: String? = null,

    @ColumnInfo(name = "isMemorized")
    val isMemorized: Boolean = false,

    // 0=X(모름), 1=△(애매), 2=O(암기완료)
    @ColumnInfo(name = "memoryLevel")
    val memoryLevel: Int = 0,

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "sourceQuestionId")
    val sourceQuestionId: Int? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: String? = null
)
