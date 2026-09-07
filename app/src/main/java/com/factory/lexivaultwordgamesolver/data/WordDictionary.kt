package com.factory.lexivaultwordgamesolver.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads the bundled word list once and keeps it in memory, bucketed by length
 * so both the anagram and pattern solvers can query it without rescanning the
 * full dictionary on every keystroke.
 */
class WordDictionary(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var wordsByLength: Map<Int, List<String>>? = null

    @Volatile
    private var validWordSet: Set<String>? = null

    suspend fun wordsByLength(): Map<Int, List<String>> {
        ensureLoaded()
        return wordsByLength!!
    }

    suspend fun isValidWord(word: String): Boolean {
        ensureLoaded()
        return validWordSet!!.contains(word.lowercase())
    }

    private suspend fun ensureLoaded() {
        if (wordsByLength != null) return
        mutex.withLock {
            if (wordsByLength != null) return
            val loaded = withContext(Dispatchers.IO) { loadFromAssets() }
            wordsByLength = loaded.groupBy { it.length }
            validWordSet = loaded.toHashSet()
        }
    }

    private fun loadFromAssets(): List<String> {
        val words = ArrayList<String>(210_000)
        context.assets.open(DICTIONARY_ASSET).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) words.add(trimmed)
                }
            }
        }
        return words
    }

    private companion object {
        const val DICTIONARY_ASSET = "words.txt"
    }
}
