@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.ui.pattern

import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
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

class PatternSolverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: WordRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk()
    private val premiumFlow = MutableStateFlow(false)
    private val savedWordsFlow = MutableStateFlow<Set<String>>(emptySet())

    private fun createViewModel(): PatternSolverViewModel {
        every { premiumManager.isPremiumFlow } returns premiumFlow
        every { repository.observeSavedWordSet() } returns savedWordsFlow
        return PatternSolverViewModel(repository, premiumManager)
    }

    @Test
    fun `initial state has sensible defaults`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals("", state.pattern)
        assertEquals(ScoringSystem.SCRABBLE, state.scoringSystem)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isPremium)
    }

    @Test
    fun `onPatternChanged filters non letters and truncates to 15`() {
        val viewModel = createViewModel()

        viewModel.onPatternChanged("c1a?t!" + "x".repeat(20))

        assertTrue(viewModel.uiState.value.pattern.length <= 15)
        assertTrue(viewModel.uiState.value.pattern.startsWith("ca?t"))
    }

    @Test
    fun `onScoringSystemChanged blocks Words With Friends for free users`() {
        val viewModel = createViewModel()

        viewModel.onScoringSystemChanged(ScoringSystem.WORDS_WITH_FRIENDS)

        assertEquals(ScoringSystem.SCRABBLE, viewModel.uiState.value.scoringSystem)
    }

    @Test
    fun `onScoringSystemChanged allows Words With Friends for premium users`() {
        val viewModel = createViewModel()
        premiumFlow.value = true

        viewModel.onScoringSystemChanged(ScoringSystem.WORDS_WITH_FRIENDS)

        assertEquals(ScoringSystem.WORDS_WITH_FRIENDS, viewModel.uiState.value.scoringSystem)
    }

    @Test
    fun `search does nothing for a blank pattern`() = runTest {
        val viewModel = createViewModel()

        viewModel.search()

        coVerify(exactly = 0) { repository.findWordsFromPattern(any()) }
    }

    @Test
    fun `search populates results sorted by score then alphabetically`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPatternChanged("ca?")
        coEvery { repository.findWordsFromPattern("ca?") } returns listOf("cap", "car", "cat")

        viewModel.search()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertEquals(3, state.results.size)
    }

    @Test
    fun `search surfaces a friendly error on failure`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPatternChanged("ca?")
        coEvery { repository.findWordsFromPattern(any()) } throws RuntimeException("boom")

        viewModel.search()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertEquals("Couldn't load the dictionary. Please try again.", state.error)
    }

    @Test
    fun `visibleResults are capped for free users`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPatternChanged("ca?")
        val words = (1..(PremiumLimits.FREE_RESULT_LIMIT + 3)).map { "w$it" }
        coEvery { repository.findWordsFromPattern(any()) } returns words

        viewModel.search()

        assertEquals(PremiumLimits.FREE_RESULT_LIMIT, viewModel.uiState.value.visibleResults.size)
    }

    @Test
    fun `toggleSaved refuses new saves once a free user hits the cap`() {
        val viewModel = createViewModel()
        savedWordsFlow.value = (1..PremiumLimits.FREE_SAVED_WORDS_LIMIT).map { "w$it" }.toSet()

        val allowed = viewModel.toggleSaved("newword")

        assertFalse(allowed)
        coVerify(exactly = 0) { repository.toggleSaved(any()) }
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
}
