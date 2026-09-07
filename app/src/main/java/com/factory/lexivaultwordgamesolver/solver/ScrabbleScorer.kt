package com.factory.lexivaultwordgamesolver.solver

/**
 * Letter point values for the two supported scoring systems. Word finders in
 * Scrabble, Crossplay, and Capture all use one of these two standard tile sets.
 */
enum class ScoringSystem {
    SCRABBLE,
    WORDS_WITH_FRIENDS
}

object ScrabbleScorer {

    private val scrabbleValues: Map<Char, Int> = mapOf(
        'a' to 1, 'b' to 3, 'c' to 3, 'd' to 2, 'e' to 1, 'f' to 4, 'g' to 2,
        'h' to 4, 'i' to 1, 'j' to 8, 'k' to 5, 'l' to 1, 'm' to 3, 'n' to 1,
        'o' to 1, 'p' to 3, 'q' to 10, 'r' to 1, 's' to 1, 't' to 1, 'u' to 1,
        'v' to 4, 'w' to 4, 'x' to 8, 'y' to 4, 'z' to 10
    )

    private val wordsWithFriendsValues: Map<Char, Int> = mapOf(
        'a' to 1, 'b' to 4, 'c' to 4, 'd' to 2, 'e' to 1, 'f' to 4, 'g' to 3,
        'h' to 3, 'i' to 1, 'j' to 10, 'k' to 5, 'l' to 2, 'm' to 4, 'n' to 2,
        'o' to 1, 'p' to 4, 'q' to 10, 'r' to 1, 's' to 1, 't' to 1, 'u' to 2,
        'v' to 5, 'w' to 4, 'x' to 8, 'y' to 3, 'z' to 10
    )

    fun score(word: String, system: ScoringSystem): Int {
        val table = if (system == ScoringSystem.SCRABBLE) scrabbleValues else wordsWithFriendsValues
        return word.lowercase().sumOf { table[it] ?: 0 }
    }

    fun letterValue(letter: Char, system: ScoringSystem): Int {
        val table = if (system == ScoringSystem.SCRABBLE) scrabbleValues else wordsWithFriendsValues
        return table[letter.lowercaseChar()] ?: 0
    }
}
