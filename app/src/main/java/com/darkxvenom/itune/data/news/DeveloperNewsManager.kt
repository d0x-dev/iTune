package com.darkxvenom.itune.data.news

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeveloperNewsManager {
    private const val DIRECT_NEWS_URL = "https://airbeats-54c06-default-rtdb.firebaseio.com/developer_news.json"
    private const val PREFS_NAME = "itune_developer_news"
    private const val KEY_CACHED_NEWS = "cached_news_json"
    private const val KEY_DISMISSED_POPUPS = "dismissed_popup_ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _newsList = MutableStateFlow<List<DeveloperNewsItem>>(emptyList())
    val newsList: StateFlow<List<DeveloperNewsItem>> = _newsList.asStateFlow()

    private val _activePopup = MutableStateFlow<DeveloperNewsItem?>(null)
    val activePopup: StateFlow<DeveloperNewsItem?> = _activePopup.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    var onOpenNewsRequested: (() -> Unit)? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load cached announcements for instant offline display
        loadCachedNews()

        // Background direct fetch from Firebase Realtime Database
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
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
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

    private fun parseNewsJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val items = mutableListOf<DeveloperNewsItem>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val item = DeveloperNewsItem(
                    id = obj.optString("id", key),
                    title = obj.optString("title", ""),
                    message = obj.optString("message", ""),
                    author = obj.optString("author", "").takeIf { it.isNotBlank() }
                        ?: obj.optString("authorName", "").takeIf { it.isNotBlank() },
                    imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                    actionUrl = obj.optString("actionUrl", "").takeIf { it.isNotBlank() },
                    actionText = obj.optString("actionText", "").takeIf { it.isNotBlank() },
                    tag = obj.optString("tag", "").takeIf { it.isNotBlank() },
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    showPopup = obj.optBoolean("showPopup", false),
                    priority = obj.optInt("priority", 0),
                    active = obj.optBoolean("active", true)
                )
                if (item.active) {
                    items.add(item)
                }
            }
            updateNews(items)
            cacheNewsJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val dismissed = getDismissedPopupIds().toMutableSet()
        val currentPopup = _activePopup.value

        if (currentPopup != null && dismissed.contains(currentPopup.id)) {
            _activePopup.value = null
        }

        // Find the single highest priority / latest candidate popup
        val candidate = items.firstOrNull { it.showPopup && it.id.isNotBlank() && !dismissed.contains(it.id) }

        if (candidate != null) {
            var changed = false
            // Mark older/lower priority popup items as dismissed so they do not chain-pop
            items.forEach { item ->
                if (item.id != candidate.id && item.id.isNotBlank() && !dismissed.contains(item.id)) {
                    dismissed.add(item.id)
                    changed = true
                }
            }
            if (changed) {
                prefs?.edit()?.putStringSet(KEY_DISMISSED_POPUPS, dismissed)?.apply()
            }

            if (_activePopup.value == null || candidate.priority > (_activePopup.value?.priority ?: 0)) {
                _activePopup.value = candidate
            }
        } else {
            _activePopup.value = null
        }
    }

    fun dismissPopup(newsId: String) {
        if (newsId.isBlank()) return
        val dismissed = getDismissedPopupIds().toMutableSet()
        dismissed.add(newsId)
        _newsList.value.forEach { item ->
            if (item.id.isNotBlank()) {
                dismissed.add(item.id)
            }
        }
        prefs?.edit()?.putStringSet(KEY_DISMISSED_POPUPS, dismissed)?.apply()
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
