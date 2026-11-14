package com.darkxvenom.itune.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.discord.DiscordPresenceManager
import com.darkxvenom.itune.data.lastfm.LastFmIntegration
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.ListeningProfileStore
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.data.timetravel.TimeTravelDatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted, portable iTune backup container. */
object BackupRestoreManager {
    const val CATEGORY_ACCOUNT = "account"
    const val CATEGORY_LIBRARY = "library"
    const val CATEGORY_ALL = "all"

    private val MAGIC = "ITUNE01\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 310_000
    private val accountPrefs = listOf(
        "itune_auth_store", "itune_auth_meta", "lastfm_integration", "itune_discord_presence", "itune_avatar_prefs",
    )
    private val libraryPrefs = listOf(
        // App settings and every independent settings surface must travel with the library backup.
        "itune_settings_prefs", "itune_audio_engine_prefs", "itune_audio_output_prefs",
        "itune_library_metadata", "itune_listening_profile", "itune_search_history_prefs", "itune_playback_session",
        "itune_lyrics_overrides", "itune_artist_store", "itune_dynamic_island_prefs", "itune_favorites",
        "music_cache_settings", "itune_online_radio_store", "itune_developer_news",
    )

    data class ScannedBackup(
        val payload: JSONObject,
        val categories: Set<String>,
        val accountDescription: String,
        val libraryDescription: String,
    )

    fun createEncryptedBackup(context: Context, selected: Set<String>, password: CharArray): ByteArray {
        try {
            require(password.size >= 8) { "Use a backup password with at least 8 characters" }
            val categories = expand(selected)
            require(categories.isNotEmpty()) { "Select at least one backup category" }
            val payload = JSONObject().put("format", "iTuneBackup").put("version", 1)
                .put("createdAt", System.currentTimeMillis())
            val data = JSONObject()
            categories.forEach { category ->
                val names = if (category == CATEGORY_ACCOUNT) accountPrefs else libraryPreferenceNames(context)
                val group = JSONObject()
                names.forEach { name ->
                    val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    group.put(name, encodePrefs(context, name, prefs))
                }
                if (category == CATEGORY_LIBRARY) {
                    data.put("playbackHistory", TimeTravelDatabaseHelper.getInstance(context).exportPlaybackHistory())
                }
                data.put(category, group)
            }
            payload.put("categories", JSONArray(categories.toList())).put("data", data)
            return encrypt(payload.toString().toByteArray(Charsets.UTF_8), password)
        } finally {
            password.fill('\u0000')
        }
    }

    fun scanEncryptedBackup(bytes: ByteArray, password: CharArray): ScannedBackup {
        val payload = JSONObject(String(decrypt(bytes, password), Charsets.UTF_8))
        require(payload.optString("format") == "iTuneBackup" && payload.optInt("version") == 1) {
            "This is not a supported iTune backup file"
        }
        val categories = payload.optJSONArray("categories")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.toSet()
        }.orEmpty()
        require(categories.any { it == CATEGORY_ACCOUNT || it == CATEGORY_LIBRARY }) { "The backup has no restorable data" }
        val data = payload.optJSONObject("data") ?: JSONObject()
        val accountDescription = data.optJSONObject(CATEGORY_ACCOUNT)?.let(::describeAccount).orEmpty()
        val libraryDescription = data.optJSONObject(CATEGORY_LIBRARY)?.let { describeLibrary(it, data.optJSONArray("playbackHistory")?.length() ?: 0) }.orEmpty()
        return ScannedBackup(payload, categories, accountDescription, libraryDescription)
    }

    fun restore(context: Context, scanned: ScannedBackup, selected: Set<String>) {
        val categories = expand(selected).intersect(scanned.categories)
        require(categories.isNotEmpty()) { "Select data that exists in this backup" }
        val app = context.applicationContext
        val data = scanned.payload.getJSONObject("data")
        categories.forEach { category ->
            val group = data.optJSONObject(category) ?: return@forEach
            val names = if (category == CATEGORY_ACCOUNT) accountPrefs else libraryNamesInBackup(group)
            names.forEach { name -> group.optJSONObject(name)?.let { restorePrefs(app, name, it) } }
            if (category == CATEGORY_LIBRARY) {
                TimeTravelDatabaseHelper.getInstance(app).restorePlaybackHistory(data.optJSONArray("playbackHistory") ?: JSONArray())
                com.darkxvenom.itune.data.timetravel.TimeTravelRepository.ensureInit(app)
                com.darkxvenom.itune.data.timetravel.TimeTravelRepository.refresh()
            }
        }
    }

    /** Rebind runtime caches and Compose state after the file and database writes complete. */
    fun refreshRestoredState(context: Context, selected: Set<String>) {
        val app = context.applicationContext
        val categories = expand(selected)
        AutoBackupScheduler.ensureScheduled(app)
        if (CATEGORY_LIBRARY in categories) {
            com.darkxvenom.itune.data.library.SettingsStore.reloadBackupState(app)
            LibraryStore.reloadBackupState(app)
            SearchHistoryStore.reloadBackupState(app)
            ListeningProfileStore.reloadBackupState(app)
            com.darkxvenom.itune.audio.AudioOutputManager.reloadBackupState()
            com.darkxvenom.itune.audio.AudioEngineManager.reloadBackupState()
            com.darkxvenom.itune.island.DynamicIslandStore.reloadBackupState(app)
            com.darkxvenom.itune.island.DynamicIslandStore.syncServiceState(app)
            com.darkxvenom.itune.data.artist.ArtistStateStore.reloadBackupState(app)
            com.darkxvenom.itune.backend.MusicCacheManager.init(app)
        }
        if (CATEGORY_ACCOUNT in categories) {
            GoogleAuthStore.refreshAfterBackupRestore(app)
            LastFmIntegration.initialize(app)
            DiscordPresenceManager.initialize(app)
        }
    }

    private fun encodePrefs(context: Context, name: String, prefs: SharedPreferences): JSONObject = JSONObject().apply {
        prefs.all.forEach { (key, value) ->
            // Never embed the key used to unlock the backup inside that same backup file.
            if (name == "itune_auth_store" && key == AutoBackupScheduler.PASSWORD_KEY) return@forEach
            val actual = if (name == "itune_auth_store" && value is String) {
                EncryptedAuthPrefs.getDecrypted(context, key) ?: value
            } else value
            val entry = when (actual) {
                is String -> JSONObject().put("t", "s").put("v", actual)
                is Boolean -> JSONObject().put("t", "b").put("v", actual)
                is Int -> JSONObject().put("t", "i").put("v", actual)
                is Long -> JSONObject().put("t", "l").put("v", actual)
                is Float -> JSONObject().put("t", "f").put("v", actual.toDouble())
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(actual.filterIsInstance<String>()))
                else -> null
            }
            if (entry != null) put(key, entry)
        }
    }

    private fun restorePrefs(context: Context, name: String, encoded: JSONObject) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (name == "itune_auth_store") {
            prefs.edit().clear().commit()
            encoded.keys().forEach { key ->
                val entry = encoded.optJSONObject(key) ?: return@forEach
                if (entry.optString("t") == "s") EncryptedAuthPrefs.putEncrypted(context, key, entry.optString("v"))
            }
            return
        }
        val editor = prefs.edit().clear()
        encoded.keys().forEach { key ->
            val entry = encoded.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "s" -> {
                    val value = entry.optString("v")
                    editor.putString(key, value)
                }
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "ss" -> editor.putStringSet(key, entry.optJSONArray("v").asStringSet())
            }
        }
        editor.apply()
    }

    private fun JSONArray?.asStringSet(): Set<String> = this?.let { (0 until it.length()).map { i -> it.optString(i) }.toSet() }.orEmpty()

    private fun describeAccount(group: JSONObject): String {
        val chunks = mutableListOf<String>()
        val meta = group.optJSONObject("itune_auth_meta")
        meta?.optJSONObject("ytm_account_name")?.optString("v")?.takeIf { it.isNotBlank() }?.let { chunks += "Google: $it" }
        if (group.optJSONObject("lastfm_integration")?.length()?.let { it > 0 } == true) chunks += "Last.fm account"
        if (group.optJSONObject("itune_discord_presence")?.length()?.let { it > 0 } == true) chunks += "Discord account"
        if (group.optJSONObject("itune_auth_store")?.length()?.let { it > 0 } == true) chunks += "Google sign-in data"
        if (group.optJSONObject("itune_avatar_prefs")?.length()?.let { it > 0 } == true) chunks += "Profile"
        return chunks.distinct().joinToString(" · ").ifBlank { "No linked account details found" }
    }

    private fun describeLibrary(group: JSONObject, playbackHistoryCount: Int): String {
        val prefs = group.optJSONObject("itune_library_metadata")
        fun size(source: JSONObject?, key: String): Int {
            val raw = source?.optJSONObject(key)?.optString("v").orEmpty()
            return runCatching { JSONArray(raw).length() }.getOrDefault(0)
        }
        val playlists = size(prefs, "user_playlists_json")
        val likes = size(prefs, "liked_songs_json")
        val albums = size(prefs, "saved_albums_json")
        val history = size(prefs, "recent_songs_json") + size(prefs, "ytm_history_songs_json")
        val profile = group.optJSONObject("itune_listening_profile")
        val stats = size(profile, "songs_v1") + size(profile, "artists_v1")
        val settingsCount = group.optJSONObject("itune_settings_prefs")?.length() ?: 0
        val effectCount = group.optJSONObject("itune_audio_engine_prefs")?.length() ?: 0
        val lyricsSettings = group.optJSONObject("itune_settings_prefs")?.let { values ->
            values.keys().asSequence().count { it.startsWith("lyrics_") }
        } ?: 0
        return "$settingsCount settings · $effectCount audio controls · $lyricsSettings lyrics settings · $playlists playlists · $likes liked songs · $albums albums · ${history + playbackHistoryCount} history entries · $stats stats records"
    }

    private fun expand(selected: Set<String>): Set<String> = when {
        CATEGORY_ALL in selected -> setOf(CATEGORY_ACCOUNT, CATEGORY_LIBRARY)
        else -> selected.intersect(setOf(CATEGORY_ACCOUNT, CATEGORY_LIBRARY))
    }

    private fun libraryPreferenceNames(context: Context): List<String> {
        val discovered = File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
            .orEmpty()
            .map { it.name.removeSuffix(".xml") }
            .filter(::isRestorableLibraryPreference)
        return (libraryPrefs + discovered).distinct().sorted()
    }

    private fun libraryNamesInBackup(group: JSONObject): List<String> =
        group.keys().asSequence().filter(::isRestorableLibraryPreference).toList()

    private fun isRestorableLibraryPreference(name: String): Boolean {
        if (name in accountPrefs) return false
        if (name in setOf("music_cache_settings")) return true
        return name.matches(Regex("itune_[A-Za-z0-9_.-]+"))
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return ByteArrayOutputStream().apply { write(MAGIC); write(salt); write(iv); write(encrypted) }.toByteArray()
    }

    private fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        require(file.size > MAGIC.size + SALT_BYTES + IV_BYTES + 16 && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "This file is not a valid .itune backup"
        }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val dataStart = ivStart + IV_BYTES
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, file.copyOfRange(saltStart, ivStart)),
            GCMParameterSpec(128, file.copyOfRange(ivStart, dataStart)),
        )
        return try { cipher.doFinal(file.copyOfRange(dataStart, file.size)) }
        catch (_: Exception) { throw IllegalArgumentException("Wrong backup password or damaged file") }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }
}
