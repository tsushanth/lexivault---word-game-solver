package com.factory.lexivaultwordgamesolver.solver

/**
 * Finds every dictionary word that can be built from a rack of letters.
 * The rack may contain '?' characters, each of which acts as a blank tile
 * that can stand in for any single letter (as in Scrabble/Crossplay/Capture).
 */
object AnagramSolver {

    data class Filters(
        val minLength: Int = 2,
        val maxLength: Int = 15,
        val startsWith: String = "",
        val contains: String = "",
        val endsWith: String = ""
    )

    fun solve(
        rack: String,
        wordsByLength: Map<Int, List<String>>,
        filters: Filters
    ): List<String> {
        val normalizedRack = rack.lowercase().filter { it.isLetter() || it == '?' }
        if (normalizedRack.isEmpty()) return emptyList()

        val rackCounts = IntArray(26)
        var blanks = 0
        for (c in normalizedRack) {
            if (c == '?') blanks++ else rackCounts[c - 'a']++
        }

        val startsWith = filters.startsWith.lowercase()
        val contains = filters.contains.lowercase()
        val endsWith = filters.endsWith.lowercase()

        val upperBound = minOf(filters.maxLength, normalizedRack.length)
        if (upperBound < filters.minLength) return emptyList()

        val results = mutableListOf<String>()
        for (length in filters.minLength..upperBound) {
            val bucket = wordsByLength[length] ?: continue
            for (word in bucket) {
                if (startsWith.isNotEmpty() && !word.startsWith(startsWith)) continue
                if (endsWith.isNotEmpty() && !word.endsWith(endsWith)) continue
                if (contains.isNotEmpty() && !word.contains(contains)) continue
                if (canFormFromRack(word, rackCounts, blanks)) {
                    results.add(word)
                }
            }
        }
        return results
    }

    private fun canFormFromRack(word: String, rackCounts: IntArray, blanks: Int): Boolean {
        val wordCounts = IntArray(26)
        for (c in word) {
            val index = c - 'a'
            if (index !in 0..25) return false
            wordCounts[index]++
        }
        var blanksNeeded = 0
        for (i in 0..25) {
            val deficit = wordCounts[i] - rackCounts[i]
            if (deficit > 0) {
                blanksNeeded += deficit
                if (blanksNeeded > blanks) return false
            }
        }
        return true
    }
}
