/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.data.news

import android.content.Context
import android.content.SharedPreferences
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object DeveloperNewsManager {
    private const val DIRECT_NEWS_URL = "https://community.airbeats.org/api/community/news"
    private const val PREFS_NAME = "itune_developer_news"
    private const val KEY_CACHED_NEWS = "cached_news_json"
    private const val KEY_DISMISSED_POPUPS = "dismissed_popup_ids"
    private const val KEY_LAST_DISMISSED_TIMESTAMP = "last_dismissed_news_timestamp"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _newsList = MutableStateFlow<List<DeveloperNewsItem>>(emptyList())
    val newsList: StateFlow<List<DeveloperNewsItem>> = _newsList.asStateFlow()

    private val _activePopup = MutableStateFlow<DeveloperNewsItem?>(null)
    val activePopup: StateFlow<DeveloperNewsItem?> = _activePopup.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    var onOpenNewsRequested: (() -> Unit)? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (prefs == null) {
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadCachedNews()
        }
        checkOnAppOpen()
    }

    fun checkOnAppOpen() {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _isSyncing.value = true
            try {
                fetchDirectRestApi()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun fetchDirectRestApi() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(DIRECT_NEWS_URL)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", Net.USER_AGENT)
                .get()
                .build()

            Net.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank() && body != "null" && !body.contains("\"error\"")) {
                        parseNewsJson(body)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private fun parseNewsJson(jsonString: String) {
        try {
            val items = parseNewsItems(jsonString)
            updateNews(items)
            cacheNewsJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun parseNewsItems(jsonString: String): List<DeveloperNewsItem> {
        val items = mutableListOf<DeveloperNewsItem>()
        try {
            val root = JSONObject(jsonString)
            if (root.has("success") && !root.optBoolean("success")) return emptyList()
            val newsArray = root.optJSONArray("news")
            val objects = mutableListOf<Pair<String, JSONObject>>()
            if (newsArray != null) {
                for (index in 0 until newsArray.length()) {
                    newsArray.optJSONObject(index)?.let { objects += index.toString() to it }
                }
            } else {
                // Continue reading existing cached payloads during migration.
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    root.optJSONObject(key)?.let { objects += key to it }
                }
            }
            for ((key, obj) in objects) {
                val item = DeveloperNewsItem(
                    id = obj.optString("id", key),
                    title = obj.optString("title", ""),
                    message = obj.optString("message", obj.optString("content", "")),
                    author = obj.optString("author", "").takeIf { it.isNotBlank() }
                        ?: obj.optString("authorName", "").takeIf { it.isNotBlank() },
                    imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                    actionUrl = obj.optString("actionUrl", "").takeIf { it.isNotBlank() },
                    actionText = obj.optString("actionText", "").takeIf { it.isNotBlank() },
                    tag = obj.optString("tag", "").takeIf { it.isNotBlank() },
                    timestamp = obj.optLong("timestamp", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                    showPopup = obj.optBoolean("showPopup", false),
                    priority = obj.optInt("priority", 0),
                    active = obj.optBoolean("active", obj.optBoolean("isActive", true))
                )
                if (item.active && isRelevantToITune(item)) {
                    items.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    internal fun isRelevantToITune(item: DeveloperNewsItem): Boolean {
        val tag = item.tag.orEmpty()
        val id = item.id
        val title = item.title
        val message = item.message
        val url = item.actionUrl.orEmpty()

        if (tag.contains("itune", ignoreCase = true) ||
            id.contains("itune", ignoreCase = true) ||
            title.contains("itune", ignoreCase = true) ||
            message.contains("itune", ignoreCase = true) ||
            url.contains("itune", ignoreCase = true)
        ) {
            return true
        }

        if (tag.isBlank() || tag.equals("announcement", ignoreCase = true) || tag.equals("news", ignoreCase = true)) {
            val combined = (title + " " + message).lowercase()
            return !combined.contains("airbeats")
        }

        return false
    }

    private fun updateNews(items: List<DeveloperNewsItem>) {
        val sorted = items.sortedWith(
            compareByDescending<DeveloperNewsItem> { it.priority }
                .thenByDescending { it.timestamp }
        )
        _newsList.value = sorted
        recalculateActivePopup(sorted)
    }

    private fun recalculateActivePopup(items: List<DeveloperNewsItem>) {
        val currentPrefs = prefs ?: return
        val dismissed = getDismissedPopupIds()
        val lastDismissedTimestamp = currentPrefs.getLong(KEY_LAST_DISMISSED_TIMESTAMP, 0L)

        val candidate = findActivePopupCandidate(items, dismissed, lastDismissedTimestamp)

        if (candidate != null) {
            val current = _activePopup.value
            if (current == null ||
                candidate.priority > current.priority ||
                (candidate.priority == current.priority && candidate.timestamp > current.timestamp)
            ) {
                _activePopup.value = candidate
            }
        } else {
            val current = _activePopup.value
            if (current != null) {
                val currentInstanceKey = "${current.id}_${current.timestamp}"
                if (dismissed.contains(current.id) ||
                    dismissed.contains(currentInstanceKey) ||
                    (lastDismissedTimestamp > 0L && current.timestamp <= lastDismissedTimestamp)
                ) {
                    _activePopup.value = null
                }
            }
        }
    }

    internal fun findActivePopupCandidate(
        items: List<DeveloperNewsItem>,
        dismissedKeys: Set<String>,
        lastDismissedTimestamp: Long
    ): DeveloperNewsItem? {
        val popupCandidates = items.filter { it.showPopup && it.active && it.id.isNotBlank() }

        return popupCandidates.firstOrNull { item ->
            val instanceKey = "${item.id}_${item.timestamp}"
            val instanceDismissed = dismissedKeys.contains(instanceKey)
            if (instanceDismissed) return@firstOrNull false

            if (lastDismissedTimestamp > 0L) {
                // If user previously skipped/dismissed news, only show if this item is strictly NEWER!
                item.timestamp > lastDismissedTimestamp
            } else {
                // If lastDismissedTimestamp has not been initialized yet:
                // Any item whose specific timestamped instance (instanceKey) was not dismissed is eligible
                true
            }
        }
    }

    fun dismissPopup(newsId: String, timestamp: Long = 0L) {
        if (newsId.isBlank()) return
        val currentPrefs = prefs ?: return
        val dismissed = getDismissedPopupIds().toMutableSet()
        dismissed.add(newsId)

        val itemTimestamp = if (timestamp > 0L) {
            timestamp
        } else {
            _newsList.value.firstOrNull { it.id == newsId }?.timestamp ?: 0L
        }

        if (itemTimestamp > 0L) {
            dismissed.add("${newsId}_$itemTimestamp")
        }

        val previousLastDismissed = currentPrefs.getLong(KEY_LAST_DISMISSED_TIMESTAMP, 0L)
        val newLastDismissed = if (itemTimestamp > 0L) {
            maxOf(previousLastDismissed, itemTimestamp)
        } else {
            previousLastDismissed
        }

        currentPrefs.edit()
            .putStringSet(KEY_DISMISSED_POPUPS, dismissed)
            .putLong(KEY_LAST_DISMISSED_TIMESTAMP, newLastDismissed)
            .apply()

        _activePopup.value = null
    }

    fun requestOpenNews() {
        onOpenNewsRequested?.invoke()
    }

    private fun getDismissedPopupIds(): Set<String> {
        return prefs?.getStringSet(KEY_DISMISSED_POPUPS, emptySet()) ?: emptySet()
    }

    private fun cacheNewsJson(jsonString: String) {
        prefs?.edit()?.putString(KEY_CACHED_NEWS, jsonString)?.apply()
    }

    private fun loadCachedNews() {
        val cached = prefs?.getString(KEY_CACHED_NEWS, null)
        if (!cached.isNullOrBlank()) {
            parseNewsJson(cached)
        }
    }
}
