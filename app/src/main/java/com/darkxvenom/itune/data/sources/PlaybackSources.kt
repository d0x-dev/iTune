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
package com.darkxvenom.itune.data.sources

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.audio.StreamTrace
import com.darkxvenom.itune.audio.StreamTraceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import com.darkxvenom.itune.data.sources.module.ModuleManager
import com.darkxvenom.itune.data.sources.module.ModuleIndex
import com.darkxvenom.itune.data.sources.module.SpineModule

data class MusicAddon(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    /** Non-null when this entry is an installable iTune/Spine JavaScript extension. */
    val moduleId: String? = null,
    val version: String = "",
    val author: String = "",
    val description: String = "",
)

data class ResolvedAddonStream(
    val url: String,
    val addonName: String,
    val quality: String,
    val steps: List<String>,
)

internal fun describeStreamQuality(codec: String, bitrateKbps: Int, sampleRateHz: Int, bitDepth: Int): String = buildString {
    append(codec.uppercase())
    if (bitrateKbps > 0) append(" • $bitrateKbps kbps")
    if (sampleRateHz > 0) append(" • ${"%.1f".format(sampleRateHz / 1000.0)} kHz")
    if (bitDepth > 0) append(" • ${bitDepth}-bit")
    val isLosslessCodec = codec.contains("flac", true) || codec.contains("alac", true) ||
        codec.contains("wav", true) || codec.contains("pcm", true)
    if (isLosslessCodec) append(if (sampleRateHz >= 88_200 || bitDepth >= 24) " • Hi-Res Lossless" else " • Lossless")
    else if (bitrateKbps >= 256) append(" • High bitrate (lossy)")
}

/** User-installed JSON music addons. Addons provide data over HTTP; this registry never executes addon code. */
object PlaybackSources {
    private const val PREFS = "playback_sources"
    private const val KEY_ADDONS = "addons_v1"
    private const val KEY_ORDER = "source_order_v1"
    private const val KEY_CAPTCHA = "captcha_guidance_v1"
    private const val KEY_YOUTUBE = "youtube_enabled_v1"
    private const val TAG = "PlaybackSources"
    private val http = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .build()
    private val moduleManagers = ConcurrentHashMap<String, ModuleManager>()
    private val _addons = MutableStateFlow<List<MusicAddon>>(emptyList())
    val addons: StateFlow<List<MusicAddon>> = _addons.asStateFlow()
    private val _order = MutableStateFlow(listOf("jiosaavn", "youtube"))
    val order: StateFlow<List<String>> = _order.asStateFlow()
    private val _captchaGuidance = MutableStateFlow(true)
    val captchaGuidance: StateFlow<Boolean> = _captchaGuidance.asStateFlow()
    private val _youtubeEnabled = MutableStateFlow(true)
    val youtubeEnabled: StateFlow<Boolean> = _youtubeEnabled.asStateFlow()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val p = prefs ?: return
            _addons.value = runCatching {
                val array = org.json.JSONArray(p.getString(KEY_ADDONS, "[]"))
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { obj ->
                        val id = obj.optString("id")
                        val url = normalizeBaseUrl(obj.optString("url")) ?: return@let null
                        MusicAddon(
                            id = id,
                            name = obj.optString("name", id).ifBlank { id },
                            baseUrl = url,
                            enabled = obj.optBoolean("enabled", true),
                            moduleId = obj.optString("moduleId").takeIf(String::isNotBlank),
                            version = obj.optString("version"),
                            author = obj.optString("author"),
                            description = obj.optString("description"),
                        )
                    }
                }
            }.getOrDefault(emptyList())
            val defaults = _addons.value.map { it.id } + listOf("jiosaavn", "youtube")
            _order.value = runCatching {
                val array = org.json.JSONArray(p.getString(KEY_ORDER, ""))
                (0 until array.length()).map { array.optString(it) }.filter { it in defaults }.distinct() + defaults.filterNot { id ->
                    (0 until array.length()).any { array.optString(it) == id }
                }
            }.getOrDefault(defaults)
            _captchaGuidance.value = p.getBoolean(KEY_CAPTCHA, true)
            _youtubeEnabled.value = p.getBoolean(KEY_YOUTUBE, true)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _addons.value = _addons.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistAddons()
    }

    fun setCaptchaGuidance(enabled: Boolean) {
        _captchaGuidance.value = enabled
        prefs?.edit()?.putBoolean(KEY_CAPTCHA, enabled)?.apply()
    }

    fun setYoutubeEnabled(enabled: Boolean) {
        _youtubeEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_YOUTUBE, enabled)?.apply()
    }

    fun move(id: String, offset: Int) {
        val current = _order.value.toMutableList()
        val from = current.indexOf(id)
        val to = (from + offset).coerceIn(0, current.lastIndex)
        if (from < 0 || from == to) return
        val entry = current.removeAt(from)
        current.add(to, entry)
        setOrder(current)
    }

    fun setOrder(ids: List<String>) {
        val available = _addons.value.map { it.id } + listOf("jiosaavn", "youtube")
        _order.value = ids.filter { it in available }.distinct() + available.filterNot(ids::contains)
        val array = org.json.JSONArray().apply { _order.value.forEach(::put) }
        prefs?.edit()?.putString(KEY_ORDER, array.toString())?.apply()
    }

    suspend fun discoverAddons(input: String): Result<List<MusicAddon>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(input) ?: error("Enter a valid http or https addon address.")
            // iTune extensions are published in a Spine module index. Read and
            // validate the actual descriptors so the user can choose which
            // extensions to install instead of silently adding a whole index.
            val indexJson = runCatching {
                Request.Builder().url(base).header("User-Agent", "iTune-Android/1.0").get().build().let { request ->
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        response.body?.string().orEmpty()
                    }
                }
            }.getOrNull()
            if (indexJson != null && runCatching { JSONObject(indexJson).keys().asSequence().any { it.startsWith("category:") } }.getOrDefault(false)) {
                val modules = ModuleIndex.parseModules(indexJson)
                require(modules.isNotEmpty()) { "This extension index contains no installable music extensions." }
                return@runCatching modules.map { module ->
                    val id = "extension-" + MessageDigest.getInstance("SHA-256")
                        .digest("${module.id}@$base".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
                    MusicAddon(id, module.name.ifBlank { module.id }, base, true, module.id, module.version, module.author, module.description)
                }
            }
            val manifest = runCatching { getJson("$base/manifest.json") }.getOrElse { failure ->
                if (!failure.message.orEmpty().contains("HTTP 404")) throw failure
                val probe = getJson("$base/search?q=iTune%20addon%20probe")
                require(probe.optJSONArray("tracks") != null) { "No addon manifest or compatible search response was found." }
                JSONObject().put("id", base.toHttpUrlOrNull()?.host.orEmpty())
            }
            val addonId = manifest.optString("id").trim().ifBlank { base.toHttpUrlOrNull()?.host.orEmpty() }
            require(addonId.isNotBlank()) { "The addon manifest is missing its id." }
            val resources = manifest.optJSONArray("resources")
            if (resources != null && (0 until resources.length()).none { resources.optString(it).equals("search", true) }) {
                error("This addon does not declare search support.")
            }
            val addonName = manifest.optString("name").trim().ifBlank { addonId }
            val id = "addon-" + MessageDigest.getInstance("SHA-256")
                .digest("$addonId@$base".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
            listOf(MusicAddon(id, addonName, base))
        }
    }

    suspend fun installAddons(addons: List<MusicAddon>) {
        if (addons.isEmpty()) return
        _addons.value = (_addons.value.filterNot { old -> addons.any { it.id == old.id } } + addons)
        _order.value = addons.map { it.id } + _order.value.filterNot { id -> addons.any { it.id == id } }
        persistAddons()
        persistOrder()
    }

    fun removeAddon(id: String) {
        _addons.value = _addons.value.filterNot { it.id == id }
        _order.value = _order.value.filterNot { it == id }
        persistAddons()
        persistOrder()
    }

    suspend fun resolveAddon(trackId: String, title: String, artist: String, addonId: String, qualityTier: String): ResolvedAddonStream? = withContext(Dispatchers.IO) {
        for (addon in _addons.value.filter { it.enabled && it.id == addonId }) {
            if (addon.moduleId != null) return@withContext resolveExtension(trackId, title, artist, addon, qualityTier)
            val steps = mutableListOf("Searching ${addon.name} for a matching recording")
            try {
                val query = URLEncoder.encode("$title $artist", Charsets.UTF_8.name())
                val results = getJson("${addon.baseUrl}/search?q=$query").optJSONArray("tracks") ?: continue
                val wantedTitle = normalize(title)
                val wantedArtist = normalize(artist)
                val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
                val hit = candidates.maxByOrNull { row ->
                    val rowTitle = normalize(row.optString("title"))
                    val rowArtist = normalize(row.optString("artist"))
                    (if (wantedTitle == rowTitle) 8 else if (wantedTitle in rowTitle || rowTitle in wantedTitle) 4 else 0) +
                        (if (wantedArtist == rowArtist) 6 else if (wantedArtist.split(' ').any { it.length > 2 && it in rowArtist }) 2 else 0)
                }?.takeIf { row ->
                    val scoreTitle = normalize(row.optString("title"))
                    scoreTitle == wantedTitle || scoreTitle.contains(wantedTitle) || wantedTitle.contains(scoreTitle)
                } ?: continue
                val remoteId = hit.optString("id").takeIf(String::isNotBlank) ?: continue
                steps += "Found ${candidates.size} addon result(s); matched ${hit.optString("title")}"
                val direct = hit.optString("streamURL").takeIf(String::isNotBlank)
                val stream = if (direct != null) JSONObject().put("url", direct) else {
                    getJson("${addon.baseUrl}/stream/${URLEncoder.encode(remoteId, Charsets.UTF_8.name())}?quality=${URLEncoder.encode(qualityTier, Charsets.UTF_8.name())}")
                }
                val declaredError = stream.optString("error").takeIf(String::isNotBlank)
                if (declaredError != null) {
                    steps += "${addon.name} rejected the stream: $declaredError"
                    continue
                }
                val encrypted = stream.opt("encrypted")
                val drmProtected = when (encrypted) {
                    is Boolean -> encrypted
                    null, JSONObject.NULL -> false
                    else -> encrypted.toString().let { it.isNotBlank() && !it.equals("false", true) && !it.equals("none", true) }
                }
                if (drmProtected) {
                    steps += "${addon.name} returned an encrypted/DRM stream; it was skipped"
                    continue
                }
                val url = stream.optString("url").takeIf(String::isNotBlank) ?: continue
                val parsed = url.toHttpUrlOrNull() ?: continue
                if (parsed.scheme !in setOf("http", "https")) continue
                val codec = listOf("codec", "fileCodec", "format")
                    .firstNotNullOfOrNull { key -> stream.optString(key).takeIf(String::isNotBlank) }
                    ?: hit.optString("format", "audio")
                val declaredTier = listOf("quality", "streamQuality", "audioQuality")
                    .firstNotNullOfOrNull { key -> stream.optString(key).takeIf(String::isNotBlank) }
                val bitrateValue = stream.optDouble("bitrate", stream.optDouble("bitrateKbps", 0.0))
                val bitrate = if (bitrateValue > 20_000) (bitrateValue / 1000).toInt() else bitrateValue.toInt()
                val sampleRateValue = stream.optDouble("sampleRate", stream.optDouble("sampleRateHz", 0.0))
                val sampleRate = sampleRateValue.toInt()
                val bitDepth = stream.optDouble("bitDepth", 0.0).toInt()
                val quality = describeStreamQuality(codec, bitrate, sampleRate, bitDepth)
                if (declaredTier != null) steps += "Addon-reported tier: $declaredTier; label is based on stream codec and measured properties"
                steps += "Selected $quality from ${addon.name}"
                StreamTraceStore.put(trackId, StreamTrace("Addon · ${addon.name}", quality, steps))
                return@withContext ResolvedAddonStream(url, addon.name, quality, steps)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                steps += "${addon.name} unavailable: ${error.message ?: "request failed"}"
                StreamTraceStore.put(trackId, StreamTrace(addon.name, "", steps))
                Log.w(TAG, "Addon ${addon.name} stream lookup failed", error)
            }
        }
        null
    }

    private suspend fun resolveExtension(trackId: String, title: String, artist: String, extension: MusicAddon, qualityTier: String): ResolvedAddonStream? {
        val manager = moduleManagers.getOrPut(extension.baseUrl) { ModuleManager() }
        val steps = mutableListOf("Loading ${extension.name} extension")
        return try {
            val modules = manager.fetchIndex(extension.baseUrl).getOrThrow()
            val indexedModule = modules.firstOrNull { it.id == extension.moduleId } ?: return null
            // QuickJS pools are keyed by module id; namespace it by installed
            // extension so two indexes using the same upstream id cannot share
            // each other's JavaScript engine.
            val module = indexedModule.copy(id = "${extension.id}:${indexedModule.id}")
            val base = extension.baseUrl.substringBeforeLast("/")
            val loaded = manager.loadModule(module) { base }.getOrThrow()
            val search = manager.searchTracks(loaded, "$title $artist", 20).getOrThrow()
            val wantedTitle = normalize(title)
            val wantedArtist = normalize(artist)
            val match = search.tracks.maxByOrNull { track ->
                val t = normalize(track.title); val a = normalize(track.artist)
                (if (t == wantedTitle) 8 else if (t.contains(wantedTitle) || wantedTitle.contains(t)) 4 else 0) +
                    (if (a == wantedArtist) 6 else if (wantedArtist.split(' ').any { it.length > 2 && it in a }) 2 else 0)
            }?.takeIf { normalize(it.title).let { t -> t == wantedTitle || t.contains(wantedTitle) || wantedTitle.contains(t) } }
                ?: return null
            steps += "Matched ${match.title} by ${match.artist} (${search.tracks.size} result(s))"
            val response = manager.getStreamUrl(loaded, match.id, qualityTier).getOrThrow()
            val url = response.streamUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
            val metadata = response.track
            val codec = metadata?.mimeType?.substringAfterLast('/')?.substringBefore(';')
                ?: metadata?.audioQuality?.takeIf(String::isNotBlank)
                ?: match.format.ifBlank { "audio" }
            val sampleRate = metadata?.sampleRate?.toInt()?.let { if (it in 1..999) it * 1000 else it } ?: 0
            val bitrate = metadata?.bitrate?.let { if (it > 20_000) it / 1000 else it } ?: 0
            val quality = describeStreamQuality(
                when (codec.lowercase()) {
                    "mp4", "mp4a" -> "AAC"
                    "x-flac" -> "FLAC"
                    else -> codec
                },
                bitrate,
                sampleRate,
                metadata?.bitDepth ?: 0,
            )
            metadata?.mimeType?.takeIf(String::isNotBlank)?.let { steps += "Stream MIME type: $it" }
            if (bitrate > 0) steps += "Stream bitrate: $bitrate kbps"
            if (sampleRate > 0) steps += "Stream sample rate: ${sampleRate / 1000.0} kHz"
            metadata?.bitDepth?.takeIf { it > 0 }?.let { steps += "Stream bit depth: $it-bit" }
            steps += "Extension reported ${metadata?.audioQuality?.ifBlank { null } ?: match.audioQuality.ifBlank { "unknown tier" }}"
            steps += "Selected $quality from ${extension.name}"
            StreamTraceStore.put(trackId, StreamTrace("Extension · ${extension.name}", quality, steps))
            ResolvedAddonStream(url, extension.name, quality, steps)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            steps += "${extension.name} unavailable: ${error.message ?: "extension failed"}"
            StreamTraceStore.put(trackId, StreamTrace("Extension · ${extension.name}", "", steps))
            Log.w(TAG, "Extension ${extension.name} stream lookup failed", error)
            null
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "iTune-Android/1.0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return JSONObject(body)
        }
    }

    private fun normalizeBaseUrl(input: String): String? {
        val raw = input.trim().removeSuffix("/").removeSuffix("/manifest.json")
        val parsed = raw.toHttpUrlOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https") || parsed.host.isBlank() ||
            parsed.username.isNotEmpty() || parsed.password.isNotEmpty()
        ) return null
        return parsed.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun persistAddons() {
        val array = org.json.JSONArray().apply {
            _addons.value.forEach { addon -> put(JSONObject().put("id", addon.id).put("name", addon.name).put("url", addon.baseUrl).put("enabled", addon.enabled).put("moduleId", addon.moduleId).put("version", addon.version).put("author", addon.author).put("description", addon.description)) }
        }
        prefs?.edit()?.putString(KEY_ADDONS, array.toString())?.apply()
    }
    private fun persistOrder() {
        val array = org.json.JSONArray().apply { _order.value.forEach(::put) }
        prefs?.edit()?.putString(KEY_ORDER, array.toString())?.apply()
    }
}
