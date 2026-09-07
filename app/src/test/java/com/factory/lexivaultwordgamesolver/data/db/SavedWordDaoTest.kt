package com.factory.lexivaultwordgamesolver.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWordDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: SavedWordDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.savedWordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(word: String, savedAt: Long = 0L) = SavedWordEntity(
        word = word,
        scrabbleScore = word.length,
        wordsWithFriendsScore = word.length * 2,
        savedAt = savedAt
    )

    @Test
    fun `insert then observeAll returns the inserted word`() = runTest {
        dao.insert(entity("quiz"))

        val all = dao.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("quiz", all.first().word)
    }

    @Test
    fun `observeAll orders results by savedAt descending`() = runTest {
        dao.insert(entity("early", savedAt = 1L))
        dao.insert(entity("late", savedAt = 2L))

        val all = dao.observeAll().first()

        assertEquals(listOf("late", "early"), all.map { it.word })
    }

    @Test
    fun `insert does not dedupe by word since only the auto-generated id is a primary key`() = runTest {
        dao.insert(entity("duplicate", savedAt = 1L))
        dao.insert(entity("duplicate", savedAt = 2L))

        val all = dao.observeAll().first()

        assertEquals(2, all.size)
    }

    @Test
    fun `insert ignores a row whose primary key already exists`() = runTest {
        dao.insert(SavedWordEntity(id = 1, word = "first", scrabbleScore = 1, wordsWithFriendsScore = 1, savedAt = 1L))
        dao.insert(SavedWordEntity(id = 1, word = "second", scrabbleScore = 2, wordsWithFriendsScore = 2, savedAt = 2L))

        val all = dao.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("first", all.first().word)
    }

    @Test
    fun `observeSavedWords returns just the word column`() = runTest {
        dao.insert(entity("alpha"))
        dao.insert(entity("beta"))

        val words = dao.observeSavedWords().first()

        assertEquals(setOf("alpha", "beta"), words.toSet())
    }

    @Test
    fun `isSaved returns true only for a persisted word`() = runTest {
        dao.insert(entity("known"))

        assertTrue(dao.isSaved("known"))
        assertFalse(dao.isSaved("unknown"))
    }

    @Test
    fun `deleteByWord removes the matching row`() = runTest {
        dao.insert(entity("temp"))

        dao.deleteByWord("temp")

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `delete removes the exact entity`() = runTest {
        dao.insert(entity("removable"))
        val saved = dao.observeAll().first().first()

        dao.delete(saved)

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `delete does not affect other rows`() = runTest {
        dao.insert(entity("keep"))
        dao.insert(entity("remove"))
        val toRemove = dao.observeAll().first().first { it.word == "remove" }

        dao.delete(toRemove)

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals("keep", remaining.first().word)
    }
}
