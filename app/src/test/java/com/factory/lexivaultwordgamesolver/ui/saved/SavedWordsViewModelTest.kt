@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.ui.saved

import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.data.db.SavedWordEntity
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import com.factory.lexivaultwordgamesolver.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedWordsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: WordRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk()
    private val premiumFlow = MutableStateFlow(false)
    private val savedWordsFlow = MutableStateFlow<List<SavedWordEntity>>(emptyList())

    private fun entity(word: String) = SavedWordEntity(word = word, scrabbleScore = 1, wordsWithFriendsScore = 1, savedAt = 0L)

    private fun createViewModel(): SavedWordsViewModel {
        every { premiumManager.isPremiumFlow } returns premiumFlow
        every { repository.observeSavedWords() } returns savedWordsFlow
        return SavedWordsViewModel(repository, premiumManager)
    }

    @Test
    fun `initial state is empty and not premium`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.savedWords.isEmpty())
        assertEquals(ScoringSystem.SCRABBLE, state.scoringSystem)
        assertFalse(state.isPremium)
    }

    @Test
    fun `saved words flow updates state`() {
        val viewModel = createViewModel()

        savedWordsFlow.value = listOf(entity("cat"), entity("dog"))

        assertEquals(2, viewModel.uiState.value.savedWords.size)
    }

    @Test
    fun `filteredWords returns everything when the query is blank`() {
        val viewModel = createViewModel()
        savedWordsFlow.value = listOf(entity("cat"), entity("dog"))

        assertEquals(2, viewModel.uiState.value.filteredWords.size)
    }

    @Test
    fun `filteredWords filters case-insensitively by the query`() {
        val viewModel = createViewModel()
        savedWordsFlow.value = listOf(entity("cat"), entity("dog"), entity("catapult"))

        viewModel.onQueryChanged("CAT")

        assertEquals(listOf("cat", "catapult"), viewModel.uiState.value.filteredWords.map { it.word })
    }

    @Test
    fun `hasReachedFreeLimit is true once a free user saves the limit`() {
        val viewModel = createViewModel()
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT).map { entity("w$it") }

        assertTrue(viewModel.uiState.value.hasReachedFreeLimit)
    }

    @Test
    fun `hasReachedFreeLimit is always false for premium users`() {
        val viewModel = createViewModel()
        premiumFlow.value = true
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT + 10).map { entity("w$it") }

        assertFalse(viewModel.uiState.value.hasReachedFreeLimit)
    }

    @Test
    fun `onScoringSystemChanged blocks Words With Friends scoring for free users`() {
        val viewModel = createViewModel()

        viewModel.onScoringSystemChanged(ScoringSystem.WORDS_WITH_FRIENDS)

        assertEquals(ScoringSystem.SCRABBLE, viewModel.uiState.value.scoringSystem)
    }

    @Test
    fun `onScoringSystemChanged allows Words With Friends scoring for premium users`() {
        val viewModel = createViewModel()
        premiumFlow.value = true

        viewModel.onScoringSystemChanged(ScoringSystem.WORDS_WITH_FRIENDS)

        assertEquals(ScoringSystem.WORDS_WITH_FRIENDS, viewModel.uiState.value.scoringSystem)
    }

    @Test
    fun `removeWord delegates to the repository`() = runTest {
        val viewModel = createViewModel()
        val target = entity("cat")

        viewModel.removeWord(target)

        coVerify { repository.removeSavedWord(target) }
    }
}
