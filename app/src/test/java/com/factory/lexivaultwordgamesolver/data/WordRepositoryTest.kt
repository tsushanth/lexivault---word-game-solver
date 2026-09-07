package com.factory.lexivaultwordgamesolver.data

import com.factory.lexivaultwordgamesolver.data.db.SavedWordDao
import com.factory.lexivaultwordgamesolver.data.db.SavedWordEntity
import com.factory.lexivaultwordgamesolver.solver.AnagramSolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WordRepositoryTest {

    private val dictionary: WordDictionary = mockk()
    private val savedWordDao: SavedWordDao = mockk(relaxed = true)
    private lateinit var repository: WordRepository

    @Before
    fun setUp() {
        repository = WordRepository(dictionary, savedWordDao)
    }

    @Test
    fun `findWordsFromLetters returns words the rack can build`() = runTest {
        coEvery { dictionary.wordsByLength() } returns mapOf(3 to listOf("cat", "dog", "act"))

        val result = repository.findWordsFromLetters("tac", AnagramSolver.Filters())

        assertEquals(setOf("cat", "act"), result.toSet())
    }

    @Test
    fun `findWordsFromLetters propagates a dictionary load failure`() = runTest {
        coEvery { dictionary.wordsByLength() } throws IllegalStateException("assets missing")

        try {
            repository.findWordsFromLetters("tac", AnagramSolver.Filters())
            org.junit.Assert.fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertEquals("assets missing", e.message)
        }
    }

    @Test
    fun `findWordsFromPattern returns words matching the pattern`() = runTest {
        coEvery { dictionary.wordsByLength() } returns mapOf(3 to listOf("cat", "car", "cop"))

        val result = repository.findWordsFromPattern("ca?")

        assertEquals(setOf("cat", "car"), result.toSet())
    }

    @Test
    fun `observeSavedWordSet maps entities down to a set of words`() = runTest {
        every { savedWordDao.observeSavedWords() } returns flowOf(listOf("alpha", "beta", "alpha"))

        val result = repository.observeSavedWordSet().first()

        assertEquals(setOf("alpha", "beta"), result)
    }

    @Test
    fun `observeSavedWords delegates directly to the dao`() = runTest {
        val entities = listOf(SavedWordEntity(word = "x", scrabbleScore = 1, wordsWithFriendsScore = 1, savedAt = 0L))
        every { savedWordDao.observeAll() } returns flowOf(entities)

        val result = repository.observeSavedWords().first()

        assertEquals(entities, result)
    }

    @Test
    fun `saveWord inserts an entity with computed scores`() = runTest {
        val slot = io.mockk.slot<SavedWordEntity>()
        coEvery { savedWordDao.insert(capture(slot)) } returns Unit

        repository.saveWord("cat")

        assertEquals("cat", slot.captured.word)
        assertTrue(slot.captured.scrabbleScore > 0)
        assertTrue(slot.captured.wordsWithFriendsScore > 0)
    }

    @Test
    fun `removeWord deletes by word`() = runTest {
        repository.removeWord("cat")

        coVerify { savedWordDao.deleteByWord("cat") }
    }

    @Test
    fun `toggleSaved saves a word that is not yet saved`() = runTest {
        coEvery { savedWordDao.isSaved("cat") } returns false

        repository.toggleSaved("cat")

        coVerify { savedWordDao.insert(match { it.word == "cat" }) }
        coVerify(exactly = 0) { savedWordDao.deleteByWord(any()) }
    }

    @Test
    fun `toggleSaved removes a word that is already saved`() = runTest {
        coEvery { savedWordDao.isSaved("cat") } returns true

        repository.toggleSaved("cat")

        coVerify { savedWordDao.deleteByWord("cat") }
        coVerify(exactly = 0) { savedWordDao.insert(any()) }
    }

    @Test
    fun `toggleSaved normalizes casing before checking and saving`() = runTest {
        coEvery { savedWordDao.isSaved("cat") } returns false

        repository.toggleSaved("CAT")

        coVerify { savedWordDao.isSaved("cat") }
        coVerify { savedWordDao.insert(match { it.word == "cat" }) }
    }

    @Test
    fun `removeSavedWord deletes the given entity`() = runTest {
        val entity = SavedWordEntity(id = 5, word = "x", scrabbleScore = 1, wordsWithFriendsScore = 1, savedAt = 0L)

        repository.removeSavedWord(entity)

        coVerify { savedWordDao.delete(entity) }
    }
}
