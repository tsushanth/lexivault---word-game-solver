package com.factory.lexivaultwordgamesolver.solver

/**
 * Finds every dictionary word matching a fixed-length crossword pattern,
 * where '?' (or any non-letter placeholder) matches any single letter.
 * This is the "Crossplay"/crossword style lookup: known letters stay fixed,
 * unknown letters are wildcards.
 */
object PatternSolver {

    private const val WILDCARD_CANDIDATES = "?_.*"

    fun solve(
        pattern: String,
        wordsByLength: Map<Int, List<String>>
    ): List<String> {
        val normalized = pattern.lowercase().map { c ->
            if (c in WILDCARD_CANDIDATES) '?' else c
        }
        if (normalized.isEmpty()) return emptyList()

        val length = normalized.size
        val bucket = wordsByLength[length] ?: return emptyList()

        return bucket.filter { word -> matches(word, normalized) }
    }

    private fun matches(word: String, pattern: List<Char>): Boolean {
        for (i in pattern.indices) {
            val p = pattern[i]
            if (p != '?' && p != word[i]) return false
        }
        return true
    }
}
