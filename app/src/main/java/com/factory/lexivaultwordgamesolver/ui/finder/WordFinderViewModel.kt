package com.factory.lexivaultwordgamesolver.ui.finder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.solver.AnagramSolver
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    SCORE_DESC("Highest score"),
    LENGTH_DESC("Longest first"),
    ALPHABETICAL("A to Z")
}

data class WordFinderUiState(
    val rack: String = "",
    val startsWith: String = "",
    val contains: String = "",
    val endsWith: String = "",
    val minLength: Int = 2,
    val maxLength: Int = 15,
    val sortOrder: SortOrder = SortOrder.SCORE_DESC,
    val scoringSystem: ScoringSystem = ScoringSystem.SCRABBLE,
    val results: List<String> = emptyList(),
    val savedWords: Set<String> = emptySet(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val isPremium: Boolean = false,
    val error: String? = null
) {
    val visibleResults: List<String>
        get() = if (isPremium) results else results.take(PremiumLimits.FREE_RESULT_LIMIT)

    val canSaveMoreWords: Boolean
        get() = isPremium || savedWords.size < PremiumLimits.FREE_SAVED_WORDS_LIMIT
}

class WordFinderViewModel(
    private val repository: WordRepository,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordFinderUiState())
    val uiState: StateFlow<WordFinderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSavedWordSet().collectLatest { saved ->
                _uiState.value = _uiState.value.copy(savedWords = saved)
            }
        }
        viewModelScope.launch {
            premiumManager.isPremiumFlow.collectLatest { isPremium ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium)
            }
        }
    }

    fun onRackChanged(value: String) {
        _uiState.value = _uiState.value.copy(rack = value.filter { it.isLetter() || it == '?' }.take(20))
    }

    fun onStartsWithChanged(value: String) {
        if (!_uiState.value.isPremium) return
        _uiState.value = _uiState.value.copy(startsWith = value.filter { it.isLetter() }.take(15))
    }

    fun onContainsChanged(value: String) {
        if (!_uiState.value.isPremium) return
        _uiState.value = _uiState.value.copy(contains = value.filter { it.isLetter() }.take(15))
    }

    fun onEndsWithChanged(value: String) {
        if (!_uiState.value.isPremium) return
        _uiState.value = _uiState.value.copy(endsWith = value.filter { it.isLetter() }.take(15))
    }

    fun onLengthRangeChanged(min: Int, max: Int) {
        _uiState.value = _uiState.value.copy(minLength = min, maxLength = max)
    }

    fun onSortOrderChanged(order: SortOrder) {
        if (!_uiState.value.isPremium && order != SortOrder.SCORE_DESC) return
        _uiState.value = _uiState.value.copy(sortOrder = order, results = sortResults(_uiState.value.results, order))
    }

    fun onScoringSystemChanged(system: ScoringSystem) {
        if (!_uiState.value.isPremium && system == ScoringSystem.WORDS_WITH_FRIENDS) return
        _uiState.value = _uiState.value.copy(scoringSystem = system)
    }

    fun search() {
        val state = _uiState.value
        if (state.rack.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            val filters = AnagramSolver.Filters(
                minLength = state.minLength,
                maxLength = state.maxLength,
                startsWith = state.startsWith,
                contains = state.contains,
                endsWith = state.endsWith
            )
            try {
                val found = repository.findWordsFromLetters(state.rack, filters)
                _uiState.value = _uiState.value.copy(
                    results = sortResults(found, state.sortOrder),
                    isSearching = false,
                    hasSearched = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    error = "Couldn't load the dictionary. Please try again."
                )
            }
        }
    }

    /** Returns false (without saving) if a free user has hit the saved-words cap and the word isn't already saved. */
    fun toggleSaved(word: String): Boolean {
        val state = _uiState.value
        if (!state.savedWords.contains(word) && !state.canSaveMoreWords) return false
        viewModelScope.launch { repository.toggleSaved(word) }
        return true
    }

    private fun sortResults(words: List<String>, order: SortOrder): List<String> {
        return when (order) {
            SortOrder.SCORE_DESC -> words.sortedWith(
                compareByDescending<String> { com.factory.lexivaultwordgamesolver.solver.ScrabbleScorer.score(it, _uiState.value.scoringSystem) }
                    .thenByDescending { it.length }
            )
            SortOrder.LENGTH_DESC -> words.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            SortOrder.ALPHABETICAL -> words.sorted()
        }
    }
}
