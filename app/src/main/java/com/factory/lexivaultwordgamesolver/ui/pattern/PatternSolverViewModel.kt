package com.factory.lexivaultwordgamesolver.ui.pattern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import com.factory.lexivaultwordgamesolver.solver.ScrabbleScorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PatternSolverUiState(
    val pattern: String = "",
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

class PatternSolverViewModel(
    private val repository: WordRepository,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatternSolverUiState())
    val uiState: StateFlow<PatternSolverUiState> = _uiState.asStateFlow()

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

    fun onPatternChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            pattern = value.filter { it.isLetter() || it == '?' }.take(15)
        )
    }

    fun onScoringSystemChanged(system: ScoringSystem) {
        if (!_uiState.value.isPremium && system == ScoringSystem.WORDS_WITH_FRIENDS) return
        _uiState.value = _uiState.value.copy(scoringSystem = system)
    }

    fun search() {
        val pattern = _uiState.value.pattern
        if (pattern.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                val found = repository.findWordsFromPattern(pattern)
                    .sortedWith(
                        compareByDescending<String> { ScrabbleScorer.score(it, _uiState.value.scoringSystem) }
                            .thenBy { it }
                    )
                _uiState.value = _uiState.value.copy(
                    results = found,
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
}
