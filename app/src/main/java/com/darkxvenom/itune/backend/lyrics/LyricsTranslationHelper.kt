package com.darkxvenom.itune.backend.lyrics

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * AI-powered lyrics translation helper.
 * Supports OpenRouter, Google Gemini, OpenAI/ChatGPT, and Claude (Anthropic).
 * Translates song lyrics line-by-line upon individual user request from the player popup.
 * Supports inbuilt prompt modes (Literal, Hinglish, Romanized, Transcribed) and custom prompts.
 */
object LyricsTranslationHelper {

    private const val TAG = "LyricsTranslationHelper"

    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        data class Translating(val logs: List<String> = emptyList()) : TranslationStatus()
        object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _activeSongId = MutableStateFlow<String?>(null)
    val activeSongId: StateFlow<String?> = _activeSongId.asStateFlow()

    // songId -> translated lines
    private val _currentSongTranslations = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val currentSongTranslations: StateFlow<Map<String, List<String>>> = _currentSongTranslations.asStateFlow()

    private var translationJob: Job? = null

    // In-memory cache: "songId|lang" -> list of translated lines
    private val memoryCache = ConcurrentHashMap<String, List<String>>()

    private val aiClient: OkHttpClient by lazy {
        Net.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Provider IDs */
    const val PROVIDER_OPENROUTER = "OpenRouter"
    const val PROVIDER_GEMINI = "Gemini"
    const val PROVIDER_OPENAI = "OpenAI"
    const val PROVIDER_CLAUDE = "Claude"

    val SUPPORTED_PROVIDERS: List<Pair<String, String>> = listOf(
        PROVIDER_OPENROUTER to "OpenRouter",
        PROVIDER_GEMINI to "Google Gemini",
        PROVIDER_OPENAI to "OpenAI / ChatGPT",
        PROVIDER_CLAUDE to "Claude (Anthropic)",
    )

    /** Inbuilt prompt translation modes */
    const val MODE_LITERAL = "Literal"
    const val MODE_HINGLISH = "Hinglish"
    const val MODE_ROMANIZED = "Romanized"
    const val MODE_TRANSCRIBED = "Transcribed"

    val TRANSLATION_MODES: List<Pair<String, String>> = listOf(
        MODE_LITERAL to "Literal (Preserve poetic rhyme & rhythm)",
        MODE_HINGLISH to "Hinglish (Hindi written in English alphabet)",
        MODE_ROMANIZED to "Romanized (Latin alphabet transliteration)",
        MODE_TRANSCRIBED to "Transcribed (Phonetic script of language)",
    )

    /** Supported target languages matching AirBeats */
    val AiTranslationLanguages: LinkedHashMap<String, String> = linkedMapOf(
        "hi-Latn" to "Hinglish (Hindi in English)",
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "hi" to "हिन्दी (Hindi)",
        "es" to "Español (Spanish)",
        "fr" to "Français (French)",
        "de" to "Deutsch (German)",
        "ja" to "日本語 (Japanese)",
        "ko" to "한국어 (Korean)",
        "zh" to "中文 (Chinese)",
        "ar" to "العربية (Arabic)",
        "ru" to "Русский (Russian)",
        "pt" to "Português (Portuguese)",
        "it" to "Italiano (Italian)",
        "tr" to "Türkçe (Turkish)",
        "ur" to "اردو (Urdu)",
        "pa" to "ਪੰਜਾਬੀ (Punjabi)",
        "bn" to "বাংলা (Bengali)",
        "mr" to "मराठी (Marathi)",
        "gu" to "ગુજરાતી (Gujarati)",
        "ta" to "தமிழ் (Tamil)",
        "te" to "తెలుగు (Telugu)",
        "kn" to "ಕನ್ನಡ (Kannada)",
        "ml" to "മലയാളം (Malayalam)",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Malaysia",
        "vi" to "Tiếng Việt",
        "th" to "ไทย (Thai)",
        "pl" to "Polski",
        "nl" to "Nederlands",
        "sv" to "Svenska",
        "no" to "Norsk",
        "da" to "Dansk",
        "fi" to "Suomi",
        "el" to "Ελληνικά (Greek)",
        "he" to "עברית (Hebrew)",
        "fa" to "فارسی (Persian)",
        "ro" to "Română",
        "hu" to "Magyar",
        "cs" to "Čeština",
    )

    fun defaultBaseUrl(provider: String): String = when (provider) {
        PROVIDER_OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
        PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        PROVIDER_OPENAI -> "https://api.openai.com/v1/chat/completions"
        PROVIDER_CLAUDE -> "https://api.anthropic.com/v1/messages"
        else -> "https://openrouter.ai/api/v1/chat/completions"
    }

    /**
     * Defaults must be models that are still being served: retired ids (e.g. `gemini-2.0-flash`,
     * `claude-3-haiku-20240307`) answer with HTTP 404, which was the "translation returns 404" bug.
     */
    fun defaultModel(provider: String): String = when (provider) {
        PROVIDER_OPENROUTER -> "openai/gpt-4o-mini"
        PROVIDER_GEMINI -> "gemini-2.5-flash"
        PROVIDER_OPENAI -> "gpt-4o-mini"
        PROVIDER_CLAUDE -> "claude-haiku-4-5"
        else -> ""
    }

    /** Offline fallback shown in the model picker when the live model list can't be fetched. */
    fun suggestedModels(provider: String): List<String> = when (provider) {
        PROVIDER_OPENROUTER -> listOf(
            "openai/gpt-4o-mini", "openai/gpt-4o", "anthropic/claude-haiku-4.5",
            "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat",
        )
        PROVIDER_GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")
        PROVIDER_OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1")
        PROVIDER_CLAUDE -> listOf("claude-haiku-4-5", "claude-sonnet-4-5", "claude-opus-4-1")
        else -> emptyList()
    }

    /**
     * Accepts a full endpoint or just an API root ("https://api.openai.com/v1") and returns the
     * actual chat endpoint, so a trimmed custom Base URL no longer ends in a 404.
     */
    fun resolveChatUrl(provider: String, baseUrl: String): String {
        val raw = baseUrl.trim().trimEnd('/')
        if (raw.isBlank()) return defaultBaseUrl(provider)
        return when {
            provider == PROVIDER_CLAUDE ->
                if (raw.endsWith("/messages")) raw else if (raw.endsWith("/v1")) "$raw/messages" else "$raw/v1/messages"
            raw.endsWith("/chat/completions") -> raw
            else -> "$raw/chat/completions"
        }
    }

    private fun modelsUrl(provider: String, baseUrl: String): String {
        val chat = resolveChatUrl(provider, baseUrl)
        return if (provider == PROVIDER_CLAUDE) {
            chat.removeSuffix("/messages") + "/models?limit=100"
        } else {
            chat.removeSuffix("/chat/completions") + "/models"
        }
    }

    /** Live list of models the key can use, so the user picks a valid id instead of typing one. */
    suspend fun fetchModels(provider: String, apiKey: String, baseUrl: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = apiKey.trim()
                val builder = Request.Builder().url(modelsUrl(provider, baseUrl)).get()
                if (provider == PROVIDER_CLAUDE) {
                    builder.addHeader("x-api-key", key).addHeader("anthropic-version", "2023-06-01")
                } else if (key.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $key")
                }
                aiClient.newCall(builder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(describeHttpError(response.code, body, provider, "", ""))
                    val data = JSONObject(body).optJSONArray("data") ?: error("Provider returned no model list.")
                    val ids = (0 until data.length()).mapNotNull {
                        data.optJSONObject(it)?.optString("id")?.removePrefix("models/")?.takeIf { id -> id.isNotBlank() }
                    }
                    ids.filter { isChatModel(provider, it) }.distinct().sorted()
                        .ifEmpty { error("No chat models available for this key.") }
                }
            }
        }

    private fun isChatModel(provider: String, id: String): Boolean {
        val l = id.lowercase()
        val blocked = listOf(
            "embedding", "embed", "tts", "whisper", "dall-e", "image", "imagen", "audio",
            "realtime", "transcribe", "moderation", "veo", "aqa", "babbage", "davinci",
        )
        if (blocked.any { l.contains(it) }) return false
        return when (provider) {
            PROVIDER_GEMINI -> l.startsWith("gemini") || l.startsWith("gemma")
            PROVIDER_OPENAI -> l.startsWith("gpt") || l.startsWith("chatgpt") || Regex("^o\\d").containsMatchIn(l)
            else -> true
        }
    }

    private fun describeHttpError(code: Int, body: String, provider: String, model: String, url: String): String {
        val apiMessage = runCatching {
            val trimmed = body.trim()
            val obj = if (trimmed.startsWith("[")) JSONArray(trimmed).optJSONObject(0) else JSONObject(trimmed)
            obj?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val hint = when (code) {
            401, 403 -> "Check your $provider API key."
            404 -> "Model \"$model\" or the endpoint was not found. Open Settings > Lyrics > Model and pick a model from the list" +
                if (url.isNotBlank()) " (endpoint: $url)." else "."
            429 -> "Rate limit or quota reached. Try again shortly or pick another model."
            else -> ""
        }
        return listOfNotNull("HTTP $code", apiMessage, hint.ifBlank { null }).joinToString(" · ")
    }

    private fun getCacheFile(context: Context, songId: String, lang: String): File {
        val dir = File(context.filesDir, "lyrics_translations")
        if (!dir.exists()) dir.mkdirs()
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "${safeSongId}_${lang}.json")
    }

    fun hasCachedTranslation(context: Context, songId: String, lang: String? = null): Boolean {
        if (songId.isBlank()) return false
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        if (!lang.isNullOrBlank()) {
            val file = getCacheFile(context, songId, lang)
            if (file.exists() && file.length() > 2) return true
        }

        // Check memory
        if (memoryCache.keys.any { it.startsWith("${safeSongId}|") }) return true

        // Check files directory for any language cache for this song
        val dir = File(context.filesDir, "lyrics_translations")
        if (dir.exists()) {
            val matching = dir.listFiles { _, name -> name.startsWith("${safeSongId}_") && name.endsWith(".json") }
            return matching?.any { it.length() > 2 } == true
        }
        return false
    }

    fun getTranslationsForSong(context: Context, songId: String, lang: String? = null): List<String>? {
        if (songId.isBlank()) return null
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // 1. Check in-memory state
        _currentSongTranslations.value[songId]?.let { return it }

        // 2. Check memory cache by lang
        if (!lang.isNullOrBlank()) {
            memoryCache["${safeSongId}|${lang}"]?.let { return it }
        }
        memoryCache.entries.firstOrNull { it.key.startsWith("${safeSongId}|") }?.value?.let { return it }

        // 3. Check disk cache
        if (!lang.isNullOrBlank()) {
            val file = getCacheFile(context, songId, lang)
            if (file.exists() && file.length() > 2) {
                val list = readJsonList(file)
                if (list != null && list.isNotEmpty()) {
                    memoryCache["${safeSongId}|${lang}"] = list
                    updateCurrentTranslations(songId, list)
                    return list
                }
            }
        }

        val dir = File(context.filesDir, "lyrics_translations")
        if (dir.exists()) {
            val files = dir.listFiles { _, name -> name.startsWith("${safeSongId}_") && name.endsWith(".json") }
            val firstValid = files?.firstOrNull { it.length() > 2 }
            if (firstValid != null) {
                val list = readJsonList(firstValid)
                if (list != null && list.isNotEmpty()) {
                    val fileLang = firstValid.name.removePrefix("${safeSongId}_").removeSuffix(".json")
                    memoryCache["${safeSongId}|${fileLang}"] = list
                    updateCurrentTranslations(songId, list)
                    return list
                }
            }
        }
        return null
    }

    private fun readJsonList(file: File): List<String>? = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { array.optString(it) }
    }.getOrNull()

    private fun updateCurrentTranslations(songId: String, lines: List<String>) {
        val current = _currentSongTranslations.value.toMutableMap()
        current[songId] = lines
        _currentSongTranslations.value = current
    }

    fun clearSongTranslation(context: Context, songId: String) {
        if (songId.isBlank()) return
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        memoryCache.keys.removeAll { it.startsWith("${safeSongId}|") }

        val current = _currentSongTranslations.value.toMutableMap()
        current.remove(songId)
        _currentSongTranslations.value = current

        runCatching {
            val dir = File(context.filesDir, "lyrics_translations")
            if (dir.exists()) {
                dir.listFiles { _, name -> name.startsWith("${safeSongId}_") && name.endsWith(".json") }
                    ?.forEach { it.delete() }
            }
        }
        _status.value = TranslationStatus.Idle
    }

    fun clearCacheForSong(songId: String) {
        if (songId.isBlank()) return
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        memoryCache.keys.removeAll { it.startsWith("${safeSongId}|") }
        val current = _currentSongTranslations.value.toMutableMap()
        current.remove(songId)
        _currentSongTranslations.value = current
    }

    fun clearCache() {
        memoryCache.clear()
        _currentSongTranslations.value = emptyMap()
        _status.value = TranslationStatus.Idle
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        translationJob = null
        _status.value = TranslationStatus.Idle
    }

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    /**
     * Translates a specific song upon explicit user action from the popup menu.
     */
    fun translateSong(
        context: Context,
        songId: String,
        lines: List<String>,
        targetLanguageCode: String,
        mode: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        customPrompt: String?,
        scope: CoroutineScope,
    ) {
        cancelTranslation()

        if (lines.isEmpty()) {
            _status.value = TranslationStatus.Error("No lyrics lines available to translate.")
            return
        }

        if (apiKey.isBlank()) {
            _status.value = TranslationStatus.Error("API Key is required. Please configure it in Settings > Lyrics Providers.")
            return
        }

        _activeSongId.value = songId
        _status.value = TranslationStatus.Translating(listOf("Connecting to $provider..."))

        val targetLanguageName = AiTranslationLanguages[targetLanguageCode] ?: targetLanguageCode
        val effectiveBaseUrl = resolveChatUrl(provider, baseUrl)
        val effectiveModel = model.ifBlank { defaultModel(provider) }
        val safeSongId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        translationJob = scope.launch(Dispatchers.IO) {
            val result = executeTranslationRequest(
                lines = lines,
                targetLanguageCode = targetLanguageCode,
                targetLanguageName = targetLanguageName,
                mode = mode,
                provider = provider,
                apiKey = apiKey.trim(),
                baseUrl = effectiveBaseUrl,
                model = effectiveModel,
                customPrompt = customPrompt,
                onLog = { msg ->
                    val cur = _status.value
                    if (cur is TranslationStatus.Translating) {
                        _status.value = cur.copy(logs = cur.logs + msg)
                    }
                }
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { translatedLines ->
                    // Save to memory cache
                    memoryCache["${safeSongId}|${targetLanguageCode}"] = translatedLines
                    updateCurrentTranslations(songId, translatedLines)

                    // Save to disk cache
                    runCatching {
                        val file = getCacheFile(context, songId, targetLanguageCode)
                        val array = JSONArray()
                        translatedLines.forEach { array.put(it) }
                        file.writeText(array.toString())
                    }

                    _status.value = TranslationStatus.Success
                }.onFailure { err ->
                    Log.e(TAG, "Lyrics translation failed", err)
                    _status.value = TranslationStatus.Error(err.message ?: "Translation error occurred.")
                }
            }
        }
    }

    private suspend fun executeTranslationRequest(
        lines: List<String>,
        targetLanguageCode: String,
        targetLanguageName: String,
        mode: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        customPrompt: String?,
        onLog: (String) -> Unit,
    ): Result<List<String>> {
        val lineCount = lines.size
        val inputText = lines.joinToString("\n")
        val isHinglish = targetLanguageCode == "hi-Latn" || mode == MODE_HINGLISH || targetLanguageName.contains("Hinglish", ignoreCase = true)

        val systemPrompt = buildString {
            append("You are an expert song lyrics translator.\n")
            append("CRITICAL RULES:\n")
            append("1. Output MUST be ONLY a valid JSON array of strings: [\"line 1\", \"line 2\", ...]\n")
            append("2. Output EXACTLY $lineCount items in the array (one output line per input line).\n")
            append("3. If an input line is empty or purely whitespace, output an empty string \"\".\n")
            append("4. Maintain the rhythm, poetic flow, and emotional feeling of the song lyrics.\n")
            append("5. DO NOT wrap with markdown, DO NOT write any explanations, intro, or outro text.\n")
            if (isHinglish) {
                append("6. For HINGLISH: You MUST translate into natural conversational Hindi using ONLY English/Latin alphabet letters (e.g., 'Tum mere paas ho', 'Dil ki dhadkan', 'Zindagi ek safar hai'). NEVER use Devanagari script for Hinglish.\n")
            }
        }

        val userPrompt = buildString {
            if (isHinglish) {
                append("Translate the following $lineCount song lyrics lines into conversational HINGLISH (Hindi written with standard English/Latin alphabet letters).\n")
                append("Make it poetic, singable, and easy to read phonetically. Do not use Devanagari script.\n")
            } else when (mode) {
                MODE_ROMANIZED -> {
                    append("Romanize/transliterate the following $lineCount lines into basic Latin alphabet characters without special diacritics.\n")
                }
                MODE_TRANSCRIBED -> {
                    append("Transcribe the phonetic sound of the following $lineCount lines into $targetLanguageName script.\n")
                }
                else -> {
                    append("Translate the following $lineCount song lyrics lines into $targetLanguageName.\n")
                    append("Preserve the rhyme, rhythm, and artistic meaning suitable for singing.\n")
                }
            }

            if (!customPrompt.isNullOrBlank()) {
                append("\nUser Custom Instructions:\n$customPrompt\n")
            }

            append("\nOriginal Lyrics ($lineCount lines):\n")
            append(inputText)
            append("\n\nReturn EXACTLY a JSON array of $lineCount strings.")
        }

        onLog("Connecting to $provider...")

        if (provider == PROVIDER_CLAUDE) {
            val body = JSONObject().apply {
                put("model", model.ifBlank { defaultModel(PROVIDER_CLAUDE) })
                put("max_tokens", (lineCount * 80).coerceAtLeast(1024))
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }
            return performHttpRequest(
                url = baseUrl,
                apiKey = apiKey,
                jsonBody = body.toString(),
                isClaude = true,
                lineCount = lineCount,
                model = model.ifBlank { defaultModel(PROVIDER_CLAUDE) },
                provider = provider,
                onLog = onLog
            )
        } else {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            val body = JSONObject().apply {
                if (model.isNotBlank()) put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", (lineCount * 80).coerceAtLeast(1024))
            }
            return performHttpRequest(
                url = baseUrl,
                apiKey = apiKey,
                jsonBody = body.toString(),
                isClaude = false,
                lineCount = lineCount,
                model = model,
                provider = provider,
                onLog = onLog
            )
        }
    }

    private suspend fun performHttpRequest(
        url: String,
        apiKey: String,
        jsonBody: String,
        isClaude: Boolean,
        lineCount: Int,
        model: String,
        provider: String,
        onLog: (String) -> Unit,
        maxRetries: Int = 3,
    ): Result<List<String>> {
        var attempt = 0
        var lastError: String? = null
        while (attempt < maxRetries) {
            try {
                onLog("Sending request (Attempt ${attempt + 1})...")
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")

                if (isClaude) {
                    requestBuilder.addHeader("x-api-key", apiKey)
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                } else {
                    if (apiKey.isNotBlank()) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    }
                }

                val request = requestBuilder.post(jsonBody.toRequestBody(JSON_MEDIA_TYPE)).build()
                val response = aiClient.newCall(request).execute()
                val bodyText = response.use { it.body?.string().orEmpty() }

                if (!response.isSuccessful) {
                    if (response.code >= 500 || response.code == 429) {
                        lastError = describeHttpError(response.code, bodyText, provider, model, url)
                        attempt++
                        delay(1000L * attempt)
                        continue
                    }
                    return Result.failure(
                        Exception(describeHttpError(response.code, bodyText, provider, model, url)),
                    )
                }

                val parsed = if (isClaude) {
                    parseClaudeResponse(bodyText, lineCount)
                } else {
                    parseOpenAiCompatResponse(bodyText, lineCount)
                }

                if (parsed != null) {
                    onLog("Successfully translated $lineCount lines")
                    return Result.success(parsed)
                }
                lastError = "The model returned an unreadable answer. Try a different model."
            } catch (e: Exception) {
                Log.w(TAG, "AI attempt $attempt failed", e)
                if (attempt == maxRetries - 1) return Result.failure(e)
            }
            attempt++
            delay(1000L * attempt)
        }
        return Result.failure(Exception(lastError ?: "Failed to translate lyrics after $maxRetries attempts."))
    }

    private fun parseOpenAiCompatResponse(responseBody: String, expectedCount: Int): List<String>? {
        return runCatching {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            val content = message.optString("content").trim()
            extractJsonArray(content, expectedCount)
        }.getOrNull()
    }

    private fun parseClaudeResponse(responseBody: String, expectedCount: Int): List<String>? {
        return runCatching {
            val root = JSONObject(responseBody)
            val content = root.optJSONArray("content") ?: return null
            val firstBlock = content.getJSONObject(0)
            val text = firstBlock.optString("text").trim()
            extractJsonArray(text, expectedCount)
        }.getOrNull()
    }

    private fun extractJsonArray(content: String, expectedCount: Int): List<String>? {
        var cleaned = content
        if (cleaned.startsWith("```json")) cleaned = cleaned.removePrefix("```json")
        else if (cleaned.startsWith("```")) cleaned = cleaned.removePrefix("```")
        if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```")
        cleaned = cleaned.trim()

        var list: List<String>? = runCatching {
            val array = JSONArray(cleaned)
            (0 until array.length()).map { array.optString(it) }
        }.getOrNull()

        if (list == null) {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start != -1 && end != -1 && end > start) {
                list = runCatching {
                    val array = JSONArray(cleaned.substring(start, end + 1))
                    (0 until array.length()).map { array.optString(it) }
                }.getOrNull()
            }
        }

        if (list == null) {
            list = cleaned.lines()
                .filter { it.isNotBlank() }
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
        }

        val mutable = (list ?: emptyList()).toMutableList()
        while (mutable.size < expectedCount) mutable.add("")
        return mutable.take(expectedCount)
    }
}
