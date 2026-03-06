package com.opic.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.opic.android.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Query("SELECT * FROM Vocabulary ORDER BY createdAt DESC")
    fun getAllWords(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM Vocabulary WHERE memoryLevel < 2 ORDER BY createdAt DESC")
    fun getUnmemorizedWords(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM Vocabulary WHERE word = :word LIMIT 1")
    suspend fun getWordByText(word: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(entity: VocabularyEntity): Long

    @Update
    suspend fun updateWord(entity: VocabularyEntity)

    @Delete
    suspend fun deleteWord(entity: VocabularyEntity)

    @Query("UPDATE Vocabulary SET memoryLevel = (memoryLevel + 1) % 3 WHERE wordId = :wordId")
    suspend fun cycleMemoryLevel(wordId: Int)

    @Query("UPDATE Vocabulary SET isFavorite = NOT isFavorite WHERE wordId = :wordId")
    suspend fun toggleFavorite(wordId: Int)

    @Query("SELECT * FROM Vocabulary ORDER BY createdAt DESC")
    suspend fun getAllWordsSync(): List<VocabularyEntity>
}
