package com.factory.lexivaultwordgamesolver.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_words")
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val scrabbleScore: Int,
    val wordsWithFriendsScore: Int,
    val savedAt: Long
)
