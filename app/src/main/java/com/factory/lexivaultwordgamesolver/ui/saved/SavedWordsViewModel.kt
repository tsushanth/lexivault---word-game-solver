package com.factory.lexivaultwordgamesolver.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.data.db.SavedWordEntity
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SavedWordsUiState(
    val savedWords: List<SavedWordEntity> = emptyList(),
    val scoringSystem: ScoringSystem = ScoringSystem.SCRABBLE,
    val query: String = "",
    val isPremium: Boolean = false
) {
    val filteredWords: List<SavedWordEntity>
        get() = if (query.isBlank()) savedWords else savedWords.filter { it.word.contains(query.lowercase()) }

    val hasReachedFreeLimit: Boolean
        get() = !isPremium && savedWords.size >= PremiumLimits.FREE_SAVED_WORDS_LIMIT
}

class SavedWordsViewModel(
    private val repository: WordRepository,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedWordsUiState())
    val uiState: StateFlow<SavedWordsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSavedWords().collectLatest { words ->
                _uiState.value = _uiState.value.copy(savedWords = words)
            }
        }
        viewModelScope.launch {
            premiumManager.isPremiumFlow.collectLatest { isPremium ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium)
            }
        }
    }

    fun onQueryChanged(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun onScoringSystemChanged(system: ScoringSystem) {
        if (!_uiState.value.isPremium && system == ScoringSystem.WORDS_WITH_FRIENDS) return
        _uiState.value = _uiState.value.copy(scoringSystem = system)
    }

    fun removeWord(entity: SavedWordEntity) {
        viewModelScope.launch { repository.removeSavedWord(entity) }
    }
}
