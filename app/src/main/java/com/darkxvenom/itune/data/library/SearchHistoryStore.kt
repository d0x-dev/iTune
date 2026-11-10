package com.darkxvenom.itune.data.library

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persistent store for recent Search queries ordered newest-first.
 */
object SearchHistoryStore {
    private const val PREFS_NAME = "itune_search_history_prefs"
    private const val KEY_QUERIES = "recent_queries_v1"
    private const val MAX_HISTORY_ITEMS = 25

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _history.value = parseQueries(p.getString(KEY_QUERIES, null))
        }
    }

    fun addQuery(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        ensureInit(context)
        val updated = buildList {
            add(trimmed)
            for (existing in _history.value) {
                if (!existing.equals(trimmed, ignoreCase = true)) {
                    add(existing)
                }
                if (size >= MAX_HISTORY_ITEMS) break
            }
        }
