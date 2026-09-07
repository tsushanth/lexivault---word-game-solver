@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.ui.finder

import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.solver.AnagramSolver
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import com.factory.lexivaultwordgamesolver.testutil.MainDispatcherRule
import io.mockk.coEvery
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

class WordFinderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: WordRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk()
    private val premiumFlow = MutableStateFlow(false)
    private val savedWordsFlow = MutableStateFlow<Set<String>>(emptySet())

    private fun createViewModel(): WordFinderViewModel {
        every { premiumManager.isPremiumFlow } returns premiumFlow
        every { repository.observeSavedWordSet() } returns savedWordsFlow
        return WordFinderViewModel(repository, premiumManager)
    }

    @Test
    fun `initial state has sensible defaults`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals("", state.rack)
        assertEquals(2, state.minLength)
        assertEquals(15, state.maxLength)
        assertEquals(SortOrder.SCORE_DESC, state.sortOrder)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isPremium)
    }

    @Test
    fun `premium flow updates isPremium in state`() = runTest {
        val viewModel = createViewModel()

        premiumFlow.value = true

        assertTrue(viewModel.uiState.value.isPremium)
    }

    @Test
    fun `saved words flow updates savedWords in state`() = runTest {
        val viewModel = createViewModel()

        savedWordsFlow.value = setOf("cat", "dog")

        assertEquals(setOf("cat", "dog"), viewModel.uiState.value.savedWords)
    }

    @Test
    fun `onRackChanged strips non letters and blanks but keeps question marks`() {
        val viewModel = createViewModel()

        viewModel.onRackChanged("c1a?t!")

        assertEquals("ca?t", viewModel.uiState.value.rack)
    }

    @Test
    fun `onRackChanged truncates to 20 characters`() {
        val viewModel = createViewModel()

        viewModel.onRackChanged("a".repeat(30))

        assertEquals(20, viewModel.uiState.value.rack.length)
    }

    @Test
    fun `onStartsWithChanged is ignored for free users`() {
        val viewModel = createViewModel()

        viewModel.onStartsWithChanged("ab")

        assertEquals("", viewModel.uiState.value.startsWith)
    }

    @Test
    fun `onStartsWithChanged applies for premium users`() {
        val viewModel = createViewModel()
        premiumFlow.value = true

        viewModel.onStartsWithChanged("ab1")

        assertEquals("ab", viewModel.uiState.value.startsWith)
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
    fun `onSortOrderChanged is ignored for free users unless SCORE_DESC`() {
        val viewModel = createViewModel()

        viewModel.onSortOrderChanged(SortOrder.ALPHABETICAL)

        assertEquals(SortOrder.SCORE_DESC, viewModel.uiState.value.sortOrder)
    }

    @Test
    fun `search does nothing when the rack is blank`() = runTest {
        val viewModel = createViewModel()

        viewModel.search()

        coVerify(exactly = 0) { repository.findWordsFromLetters(any(), any()) }
    }

    @Test
    fun `search populates sorted results on success`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRackChanged("tac")
        coEvery { repository.findWordsFromLetters("tac", any()) } returns listOf("act", "cat", "at")

        viewModel.search()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertEquals(listOf("act", "cat", "at"), state.results.sortedByDescending { it.length })
        assertEquals(3, state.results.size)
    }

    @Test
    fun `search surfaces a friendly error when the repository throws`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRackChanged("tac")
        coEvery { repository.findWordsFromLetters(any(), any()) } throws RuntimeException("boom")

        viewModel.search()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertEquals("Couldn't load the dictionary. Please try again.", state.error)
    }

    @Test
    fun `visibleResults are capped for free users`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRackChanged("tac")
        val manyWords = (1..(PremiumLimits.FREE_RESULT_LIMIT + 5)).map { "w$it" }
        coEvery { repository.findWordsFromLetters(any(), any()) } returns manyWords

        viewModel.search()

        assertEquals(PremiumLimits.FREE_RESULT_LIMIT, viewModel.uiState.value.visibleResults.size)
    }

    @Test
    fun `visibleResults are unrestricted for premium users`() = runTest {
        val viewModel = createViewModel()
        premiumFlow.value = true
        viewModel.onRackChanged("tac")
        val manyWords = (1..(PremiumLimits.FREE_RESULT_LIMIT + 5)).map { "w$it" }
        coEvery { repository.findWordsFromLetters(any(), any()) } returns manyWords

        viewModel.search()

        assertEquals(manyWords.size, viewModel.uiState.value.visibleResults.size)
    }

    @Test
    fun `toggleSaved refuses to save more words once a free user hits the cap`() {
        val viewModel = createViewModel()
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT).map { "w$it" }.toSet()

        val allowed = viewModel.toggleSaved("newword")

        assertFalse(allowed)
        coVerify(exactly = 0) { repository.toggleSaved(any()) }
    }

    @Test
    fun `toggleSaved allows removing an already saved word even at the cap`() = runTest {
        val viewModel = createViewModel()
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT).map { "w$it" }.toSet() + "existing"

        val allowed = viewModel.toggleSaved("existing")

        assertTrue(allowed)
        coVerify { repository.toggleSaved("existing") }
    }

    @Test
    fun `toggleSaved allows premium users past the free cap`() = runTest {
        val viewModel = createViewModel()
        premiumFlow.value = true
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT).map { "w$it" }.toSet()

        val allowed = viewModel.toggleSaved("newword")

        assertTrue(allowed)
        coVerify { repository.toggleSaved("newword") }
    }

    @Test
    fun `sort order ALPHABETICAL sorts results a to z for premium users`() = runTest {
        val viewModel = createViewModel()
        premiumFlow.value = true
        viewModel.onRackChanged("tac")
        coEvery { repository.findWordsFromLetters(any(), any()) } returns listOf("cat", "act", "at")

        viewModel.search()
        viewModel.onSortOrderChanged(SortOrder.ALPHABETICAL)

        assertEquals(listOf("act", "at", "cat"), viewModel.uiState.value.results)
    }

    @Test
    fun `sort order LENGTH_DESC sorts longest words first for premium users`() = runTest {
        val viewModel = createViewModel()
        premiumFlow.value = true
        viewModel.onRackChanged("tac")
        coEvery { repository.findWordsFromLetters(any(), any()) } returns listOf("at", "cat", "act")

        viewModel.search()
        viewModel.onSortOrderChanged(SortOrder.LENGTH_DESC)

        assertEquals(listOf("act", "cat", "at"), viewModel.uiState.value.results)
    }

    @Test
    fun `search builds filters from current state`() = runTest {
        val viewModel = createViewModel()
        premiumFlow.value = true
        viewModel.onRackChanged("tac")
        viewModel.onStartsWithChanged("a")
        viewModel.onLengthRangeChanged(2, 3)
        coEvery { repository.findWordsFromLetters(any(), any()) } returns emptyList()

        viewModel.search()

        coVerify {
            repository.findWordsFromLetters(
                "tac",
                AnagramSolver.Filters(minLength = 2, maxLength = 3, startsWith = "a", contains = "", endsWith = "")
            )
        }
    }
}
