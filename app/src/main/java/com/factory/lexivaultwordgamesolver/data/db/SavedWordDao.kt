package com.factory.lexivaultwordgamesolver.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWordDao {

    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedWordEntity>>

    @Query("SELECT word FROM saved_words")
    fun observeSavedWords(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SavedWordEntity)

    @Delete
    suspend fun delete(entity: SavedWordEntity)

    @Query("DELETE FROM saved_words WHERE word = :word")
    suspend fun deleteByWord(word: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE word = :word)")
    suspend fun isSaved(word: String): Boolean
}
