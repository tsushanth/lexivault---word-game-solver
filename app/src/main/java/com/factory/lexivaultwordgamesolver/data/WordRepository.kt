package com.factory.lexivaultwordgamesolver.data

import com.factory.lexivaultwordgamesolver.data.db.SavedWordDao
import com.factory.lexivaultwordgamesolver.data.db.SavedWordEntity
import com.factory.lexivaultwordgamesolver.solver.AnagramSolver
import com.factory.lexivaultwordgamesolver.solver.PatternSolver
import com.factory.lexivaultwordgamesolver.solver.ScrabbleScorer
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WordRepository(
    private val dictionary: WordDictionary,
    private val savedWordDao: SavedWordDao
) {

    suspend fun findWordsFromLetters(
        rack: String,
        filters: AnagramSolver.Filters
    ): List<String> = withContext(Dispatchers.Default) {
        val wordsByLength = dictionary.wordsByLength()
        AnagramSolver.solve(rack, wordsByLength, filters)
    }

    suspend fun findWordsFromPattern(pattern: String): List<String> = withContext(Dispatchers.Default) {
        val wordsByLength = dictionary.wordsByLength()
        PatternSolver.solve(pattern, wordsByLength)
    }

    fun observeSavedWords(): Flow<List<SavedWordEntity>> = savedWordDao.observeAll()

    fun observeSavedWordSet(): Flow<Set<String>> = savedWordDao.observeSavedWords().map { it.toSet() }

    suspend fun saveWord(word: String) {
        val entity = SavedWordEntity(
            word = word,
            scrabbleScore = ScrabbleScorer.score(word, ScoringSystem.SCRABBLE),
            wordsWithFriendsScore = ScrabbleScorer.score(word, ScoringSystem.WORDS_WITH_FRIENDS),
            savedAt = System.currentTimeMillis()
        )
        savedWordDao.insert(entity)
    }

    suspend fun removeWord(word: String) {
        savedWordDao.deleteByWord(word)
    }

    suspend fun toggleSaved(word: String) {
        val normalized = word.lowercase()
        if (savedWordDao.isSaved(normalized)) {
            savedWordDao.deleteByWord(normalized)
        } else {
            saveWord(normalized)
        }
    }

    suspend fun removeSavedWord(entity: SavedWordEntity) {
        savedWordDao.delete(entity)
    }
}
