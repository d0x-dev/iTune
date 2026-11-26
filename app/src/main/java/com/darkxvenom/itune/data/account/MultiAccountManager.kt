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

package com.darkxvenom.itune.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.backend.MusicCacheManager
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.backup.AutoBackupScheduler
import com.darkxvenom.itune.data.discord.DiscordPresenceManager
import com.darkxvenom.itune.data.lastfm.LastFmIntegration
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.ListeningProfileStore
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.data.timetravel.TimeTravelDatabaseHelper
import com.darkxvenom.itune.data.timetravel.TimeTravelRepository
import com.darkxvenom.itune.island.DynamicIslandStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UserAccount(
    val id: String,
    val displayName: String,
    val avatarId: Int = 1,
    val avatarUrl: String = AvatarManager.getAvatarUrl(avatarId),
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("avatarId", avatarId)
        put("avatarUrl", avatarUrl)
        put("createdAt", createdAt)
        put("isDefault", isDefault)
    }

    companion object {
        fun fromJson(json: JSONObject): UserAccount {
            val id = json.optString("id", UUID.randomUUID().toString())
            val displayName = json.optString("displayName", "Listener")
            val avatarId = json.optInt("avatarId", 1).coerceIn(1, AvatarManager.TOTAL_AVATARS)
            val avatarUrl = json.optString("avatarUrl", AvatarManager.getAvatarUrl(avatarId))
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())
            val isDefault = json.optBoolean("isDefault", false)
            return UserAccount(
                id = id,
                displayName = displayName,
                avatarId = avatarId,
                avatarUrl = avatarUrl,
                createdAt = createdAt,
                isDefault = isDefault,
            )
        }
    }
}

/**
 * Manages multiple independent user accounts in iTune with complete data isolation,
 * seamless switching animations, and backup integration.
 */
object MultiAccountManager {
    private const val TAG = "MultiAccountManager"
    private const val PREFS_NAME = "itune_multi_accounts"
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"
    private const val ACCOUNTS_DIR = "itune_accounts"

    private val accountPrefs = listOf(
        "itune_auth_store", "itune_auth_meta", "lastfm_integration", "itune_discord_presence", "itune_avatar_prefs",
    )
    private val libraryPrefs = listOf(
        "itune_settings_prefs", "itune_audio_engine_prefs", "itune_audio_output_prefs",
        "itune_library_metadata", "itune_listening_profile", "itune_search_history_prefs", "itune_playback_session",
        "itune_lyrics_overrides", "itune_artist_store", "itune_dynamic_island_prefs", "itune_favorites",
        "music_cache_settings", "itune_online_radio_store",
    )

    private val _accounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val accounts: StateFlow<List<UserAccount>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<UserAccount?>(null)
    val activeAccount: StateFlow<UserAccount?> = _activeAccount.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val rawList = loadAccountsList(prefs)
        if (rawList.isEmpty()) {
            // Check if user already completed onboarding on an older version
            val settingsPrefs = app.getSharedPreferences("itune_settings_prefs", Context.MODE_PRIVATE)
            val welcomeCompleted = settingsPrefs.getBoolean(SettingsStore.KEY_WELCOME_COMPLETED, false)
            val existingName = settingsPrefs.getString(SettingsStore.KEY_DISPLAY_NAME, "")?.trim().orEmpty()
            val avatarPrefs = app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
            val existingAvatar = avatarPrefs.getInt("selected_avatar_id", 1).coerceIn(1, AvatarManager.TOTAL_AVATARS)

            if (welcomeCompleted && existingName.isNotEmpty()) {
                val primary = UserAccount(
                    id = "acc_primary",
                    displayName = existingName,
                    avatarId = existingAvatar,
                    avatarUrl = AvatarManager.getAvatarUrl(existingAvatar),
                    createdAt = System.currentTimeMillis(),
                    isDefault = true,
                )
                saveAccountsList(prefs, listOf(primary))
                prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, primary.id).apply()
                _accounts.value = listOf(primary)
                _activeAccount.value = primary
                snapshotAccount(app, primary.id)
            }
        } else {
            _accounts.value = rawList
            val activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, rawList.firstOrNull()?.id.orEmpty()).orEmpty()
            val active = rawList.firstOrNull { it.id == activeId } ?: rawList.firstOrNull()
            _activeAccount.value = active
            if (active != null && active.id != activeId) {
                prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, active.id).apply()
            }
        }
        initialized = true
    }

    fun getAccounts(): List<UserAccount> = _accounts.value

    fun getActiveAccount(): UserAccount? = _activeAccount.value

    fun hasMultipleAccounts(): Boolean = _accounts.value.size > 1

    /**
     * Ensures that the primary/initial user account exists in the multi-account registry
     * and is snapshotted before any secondary accounts are created.
     */
    fun ensurePrimaryAccountExists(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = loadAccountsList(prefs)
        if (currentList.isEmpty() || _accounts.value.isEmpty()) {
            val settingsPrefs = app.getSharedPreferences("itune_settings_prefs", Context.MODE_PRIVATE)
            val existingName = settingsPrefs.getString(SettingsStore.KEY_DISPLAY_NAME, "")?.trim().orEmpty()
            val avatarPrefs = app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
            val existingAvatar = avatarPrefs.getInt("selected_avatar_id", 1).coerceIn(1, AvatarManager.TOTAL_AVATARS)

            val primary = UserAccount(
                id = "acc_primary",
                displayName = existingName.ifEmpty { "Listener" },
                avatarId = existingAvatar,
                avatarUrl = AvatarManager.getAvatarUrl(existingAvatar),
                createdAt = System.currentTimeMillis(),
                isDefault = true,
            )
            val updated = listOf(primary)
            saveAccountsList(prefs, updated)
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, primary.id).apply()
            _accounts.value = updated
            _activeAccount.value = primary
            snapshotAccount(app, primary.id)
            Log.i(TAG, "Synthesized and snapshotted primary account: ${primary.id} (${primary.displayName})")
        }
    }

    /**
     * Called when the user completes onboarding / welcome screen to register their primary account.
     */
    fun registerInitialAccount(context: Context, displayName: String, avatarId: Int): UserAccount {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val safeAvatarId = avatarId.coerceIn(1, AvatarManager.TOTAL_AVATARS)
        val currentList = loadAccountsList(prefs)
        val existing = currentList.firstOrNull { it.isDefault } ?: currentList.firstOrNull()
        if (existing != null) {
            val updatedAcc = existing.copy(
                displayName = displayName.trim().ifEmpty { existing.displayName },
                avatarId = safeAvatarId,
                avatarUrl = AvatarManager.getAvatarUrl(safeAvatarId),
            )
            val updatedList = currentList.map { if (it.id == existing.id) updatedAcc else it }
            saveAccountsList(prefs, updatedList)
            _accounts.value = updatedList
            if (_activeAccount.value?.id == existing.id || _activeAccount.value == null) {
                _activeAccount.value = updatedAcc
                prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, updatedAcc.id).apply()
            }
            snapshotAccount(app, updatedAcc.id)
            return updatedAcc
        }

        val primary = UserAccount(
            id = "acc_primary",
            displayName = displayName.trim().ifEmpty { "Listener" },
            avatarId = safeAvatarId,
            avatarUrl = AvatarManager.getAvatarUrl(safeAvatarId),
            createdAt = System.currentTimeMillis(),
            isDefault = true,
        )
        val updated = listOf(primary)
        saveAccountsList(prefs, updated)
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, primary.id).apply()
        _accounts.value = updated
        _activeAccount.value = primary
        snapshotAccount(app, primary.id)
        Log.i(TAG, "Registered initial account: ${primary.id} (${primary.displayName})")
        return primary
    }

    /**
     * Keeps the active account's profile metadata in sync when edited from Settings.
     */
    fun updateActiveAccountProfile(context: Context, displayName: String? = null, avatarId: Int? = null) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = _activeAccount.value ?: return
        val safeAvatarId = avatarId?.coerceIn(1, AvatarManager.TOTAL_AVATARS) ?: active.avatarId
        val updated = active.copy(
            displayName = displayName?.trim()?.ifEmpty { active.displayName } ?: active.displayName,
            avatarId = safeAvatarId,
            avatarUrl = AvatarManager.getAvatarUrl(safeAvatarId),
        )
        val list = _accounts.value.map { if (it.id == active.id) updated else it }
        saveAccountsList(prefs, list)
        _accounts.value = list
        _activeAccount.value = updated
        snapshotAccount(app, updated.id)
    }

    /**
     * Creates a new account, switches to it immediately, and leaves it completely fresh.
     * Fully cuts off any currently playing audio from previous account.
     */
    fun createAccount(context: Context, displayName: String, avatarId: Int, isDefault: Boolean = false): UserAccount {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 0. Fully stop audio playback from old account
        MusicPlayerState.resetForAccountSwitch()
        try { com.darkxvenom.itune.radio.OnlineRadioController.closeSession() } catch (_: Exception) {}

        // Ensure the primary account is registered and snapshotted before adding a secondary one
        ensurePrimaryAccountExists(app)

        // 1. Snapshot the currently active account before creating and switching to the new one
        val current = _activeAccount.value
        if (current != null) {
            snapshotAccount(app, current.id)
        }

        // 2. Generate new account
        val safeAvatarId = avatarId.coerceIn(1, AvatarManager.TOTAL_AVATARS)
        val newAccount = UserAccount(
            id = "acc_" + UUID.randomUUID().toString().replace("-", "").take(12),
            displayName = displayName.trim().ifEmpty { "Listener" },
            avatarId = safeAvatarId,
            avatarUrl = AvatarManager.getAvatarUrl(safeAvatarId),
            createdAt = System.currentTimeMillis(),
            isDefault = isDefault,
        )

        val updated = _accounts.value + newAccount
        saveAccountsList(prefs, updated)
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, newAccount.id).apply()
        _accounts.value = updated
        _activeAccount.value = newAccount

        // 3. Clear existing active preference stores so the new account starts 100% fresh
        clearActiveStores(app)

        // 4. Initialize the new account's personal identity
        val settingsPrefs = app.getSharedPreferences("itune_settings_prefs", Context.MODE_PRIVATE)
        settingsPrefs.edit()
            .putString(SettingsStore.KEY_DISPLAY_NAME, newAccount.displayName)
            .putBoolean(SettingsStore.KEY_WELCOME_COMPLETED, true)
            .apply()

        val avatarPrefs = app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
        avatarPrefs.edit()
            .putInt("selected_avatar_id", newAccount.avatarId)
            .apply()

        AvatarManager.setAvatar(app, newAccount.avatarId)

        // 5. Snapshot the fresh account's initial state
        snapshotAccount(app, newAccount.id)

        // 6. Reload runtime singletons and Compose StateFlows
        reloadAllStores(app)

        Log.i(TAG, "Created fresh account: ${newAccount.id} (${newAccount.displayName})")
        return newAccount
    }

    /**
     * Switches the active account to targetAccountId with full data isolation.
     * Fully cuts off any currently playing audio from previous account.
     */
    fun switchAccount(context: Context, targetAccountId: String): Boolean {
        val app = context.applicationContext
        val target = _accounts.value.firstOrNull { it.id == targetAccountId } ?: return false
        val current = _activeAccount.value

        if (current?.id == target.id) return true

        // 0. Fully stop audio playback from old account
        MusicPlayerState.resetForAccountSwitch()
        try { com.darkxvenom.itune.radio.OnlineRadioController.closeSession() } catch (_: Exception) {}

        // 1. Save current account's active state
        if (current != null) {
            snapshotAccount(app, current.id)
        }

        // 2. Clear current in-memory / active preferences
        clearActiveStores(app)

        // 3. Restore target account's saved data
        restoreAccount(app, target.id)

        // 4. Update active account pointers
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, target.id).apply()
        _activeAccount.value = target

        // 5. Ensure AvatarManager has target's avatarId
        val targetAvatarId = target.avatarId.coerceIn(1, AvatarManager.TOTAL_AVATARS)
        app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("selected_avatar_id", targetAvatarId)
            .apply()
        AvatarManager.setAvatar(app, targetAvatarId)

        // 6. Reload all singleton stores to pick up the restored preferences
        reloadAllStores(app)

        Log.i(TAG, "Switched active account to: ${target.id} (${target.displayName})")
        return true
    }

    fun updateAccountProfile(context: Context, accountId: String, newName: String, newAvatarId: Int) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val safeAvatarId = newAvatarId.coerceIn(1, AvatarManager.TOTAL_AVATARS)
        val updated = _accounts.value.map { acc ->
            if (acc.id == accountId) {
                acc.copy(
                    displayName = newName.trim().ifEmpty { acc.displayName },
                    avatarId = safeAvatarId,
                    avatarUrl = AvatarManager.getAvatarUrl(safeAvatarId),
                )
            } else acc
        }
        saveAccountsList(prefs, updated)
        _accounts.value = updated
        if (_activeAccount.value?.id == accountId) {
            _activeAccount.value = updated.firstOrNull { it.id == accountId }
        }
    }

    fun deleteAccount(context: Context, accountId: String): Boolean {
        if (_accounts.value.size <= 1) return false
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val target = _accounts.value.firstOrNull { it.id == accountId } ?: return false

        val updated = _accounts.value.filter { it.id != accountId }
        saveAccountsList(prefs, updated)
        _accounts.value = updated

        if (_activeAccount.value?.id == accountId) {
            val fallback = updated.first()
            // Reset audio and switch to fallback without saving snapshot of deleted account
            MusicPlayerState.resetForAccountSwitch()
            try { com.darkxvenom.itune.radio.OnlineRadioController.closeSession() } catch (_: Exception) {}
            clearActiveStores(app)
            restoreAccount(app, fallback.id)
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, fallback.id).apply()
            _activeAccount.value = fallback
            val targetAvatarId = fallback.avatarId.coerceIn(1, AvatarManager.TOTAL_AVATARS)
            app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("selected_avatar_id", targetAvatarId)
                .apply()
            AvatarManager.setAvatar(app, targetAvatarId)
            reloadAllStores(app)
        }

        // Delete archive file
        val archiveFile = getAccountArchiveFile(app, accountId)
        if (archiveFile.exists()) archiveFile.delete()

        return true
    }

    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    /**
     * Snapshots the active SharedPreferences and playback history for an account into its archive.
     */
    fun snapshotAccount(context: Context, accountId: String) {
        try {
            val app = context.applicationContext
            val allPrefNames = accountPrefs + libraryPrefs
            val root = JSONObject()
            root.put("accountId", accountId)
            root.put("savedAt", System.currentTimeMillis())

            val prefsObject = JSONObject()
            allPrefNames.forEach { name ->
                val sp = app.getSharedPreferences(name, Context.MODE_PRIVATE)
                val group = JSONObject()
                sp.all.forEach { (key, value) ->
                    if (name == "itune_auth_store" && key == AutoBackupScheduler.PASSWORD_KEY) return@forEach
                    val actual = if (name == "itune_auth_store" && value is String) {
                        EncryptedAuthPrefs.getDecrypted(app, key) ?: value
                    } else value
                    val entry = when (actual) {
                        is String -> JSONObject().put("t", "s").put("v", actual)
                        is Boolean -> JSONObject().put("t", "b").put("v", actual)
                        is Int -> JSONObject().put("t", "i").put("v", actual)
                        is Long -> JSONObject().put("t", "l").put("v", actual)
                        is Float -> JSONObject().put("t", "f").put("v", actual.toDouble())
                        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(actual.mapNotNull { it?.toString() }))
                        else -> null
                    }
                    if (entry != null) group.put(key, entry)
                }
                prefsObject.put(name, group)
            }
            root.put("prefs", prefsObject)

            // Playback history
            root.put("playbackHistory", TimeTravelDatabaseHelper.getInstance(app).exportPlaybackHistory())

            val file = getAccountArchiveFile(app, accountId)
            file.parentFile?.mkdirs()
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error snapshotting account $accountId: ${e.message}")
        }
    }

    /**
     * Restores preference and history stores from a snapshot JSON object.
     */
    fun restoreFromJsonObject(context: Context, root: JSONObject) {
        val app = context.applicationContext
        val prefsObject = root.optJSONObject("prefs")
        if (prefsObject != null) {
            val keys = prefsObject.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val group = prefsObject.optJSONObject(name) ?: continue

                // Encrypted auth credentials require dedicated cipher write to prevent clearing collisions
                if (name == "itune_auth_store") {
                    val authSp = app.getSharedPreferences(name, Context.MODE_PRIVATE)
                    authSp.edit().clear().commit()
                    val entryKeys = group.keys()
                    while (entryKeys.hasNext()) {
                        val key = entryKeys.next()
                        val entry = group.optJSONObject(key) ?: continue
                        if (entry.optString("t") == "s") {
                            EncryptedAuthPrefs.putEncrypted(app, key, entry.optString("v"))
                        }
                    }
                    continue
                }

                val sp = app.getSharedPreferences(name, Context.MODE_PRIVATE)
                val editor = sp.edit().clear()
                val entryKeys = group.keys()
                while (entryKeys.hasNext()) {
                    val key = entryKeys.next()
                    val entry = group.optJSONObject(key) ?: continue
                    when (entry.optString("t")) {
                        "s" -> editor.putString(key, entry.optString("v"))
                        "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                        "i" -> editor.putInt(key, entry.optInt("v"))
                        "l" -> editor.putLong(key, entry.optLong("v"))
                        "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                        "ss" -> {
                            val array = entry.optJSONArray("v")
                            val set = (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it) }.toSet()
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.commit()
            }
        }

        // Restore playback history into SQLite database
        val history = root.optJSONArray("playbackHistory")
        if (history != null) {
            TimeTravelDatabaseHelper.getInstance(app).restorePlaybackHistory(history)
        } else {
            TimeTravelDatabaseHelper.getInstance(app).clearPlaybackHistory()
        }
    }

    private fun restoreAccount(context: Context, accountId: String) {
        val app = context.applicationContext
        val file = getAccountArchiveFile(app, accountId)
        if (!file.exists()) return

        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            restoreFromJsonObject(app, root)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring account $accountId: ${e.message}")
        }
    }

    /**
     * Restores backup account data into the current active account.
     */
    fun restoreIntoCurrentAccount(context: Context, accountData: JSONObject, profile: UserAccount?) {
        val app = context.applicationContext
        val current = _activeAccount.value ?: return

        // Stop music playback fully
        MusicPlayerState.resetForAccountSwitch()
        try { com.darkxvenom.itune.radio.OnlineRadioController.closeSession() } catch (_: Exception) {}

        // Clear current active stores first
        clearActiveStores(app)

        // Restore preferences and history from accountData
        restoreFromJsonObject(app, accountData)

        // Update active account metadata if profile provided
        if (profile != null) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updated = current.copy(
                displayName = profile.displayName,
                avatarId = profile.avatarId,
                avatarUrl = profile.avatarUrl,
            )
            val list = _accounts.value.map { if (it.id == current.id) updated else it }
            saveAccountsList(prefs, list)
            _accounts.value = list
            _activeAccount.value = updated

            val settingsPrefs = app.getSharedPreferences("itune_settings_prefs", Context.MODE_PRIVATE)
            settingsPrefs.edit().putString(SettingsStore.KEY_DISPLAY_NAME, updated.displayName).apply()

            val avatarPrefs = app.getSharedPreferences("itune_avatar_prefs", Context.MODE_PRIVATE)
            avatarPrefs.edit().putInt("selected_avatar_id", updated.avatarId).apply()
            AvatarManager.setAvatar(app, updated.avatarId)

            snapshotAccount(app, updated.id)
        }

        reloadAllStores(app)
        Log.i(TAG, "Restored profile into active account: ${current.displayName}")
    }

    /**
     * Imports multiple accounts from backup as separate new accounts without touching the active account.
     */
    fun importAccountsFromBackup(
        context: Context,
        accountsToImport: List<UserAccount>,
        accountsDataMap: Map<String, JSONObject>,
    ) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = _accounts.value.toMutableList()
        val currentActive = _activeAccount.value

        // Snapshot active account so its current state is safely preserved
        if (currentActive != null) {
            snapshotAccount(app, currentActive.id)
        }

        val dir = File(app.filesDir, ACCOUNTS_DIR)
        dir.mkdirs()

        accountsToImport.forEach { acc ->
            // Prevent colliding with existing account IDs or active account ID
            val targetId = if (currentList.any { it.id == acc.id }) {
                "acc_" + UUID.randomUUID().toString().replace("-", "").take(12)
            } else {
                acc.id
            }

            val finalAcc = acc.copy(id = targetId, isDefault = false)
            val data = accountsDataMap[acc.id]
            if (data != null) {
                val file = getAccountArchiveFile(app, targetId)
                data.put("accountId", targetId)
                file.writeText(data.toString(), Charsets.UTF_8)
            }

            currentList.add(finalAcc)
        }

        saveAccountsList(prefs, currentList)
        _accounts.value = currentList
        // Active account remains completely untouched!
        Log.i(TAG, "Imported ${accountsToImport.size} accounts from backup. Active account remains untouched.")
    }

    private fun clearActiveStores(context: Context) {
        val app = context.applicationContext
        val allNames = accountPrefs + libraryPrefs
        allNames.forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        // Isolate TimeTravel SQLite database per account
        TimeTravelDatabaseHelper.getInstance(app).clearPlaybackHistory()
        // Reset player state and preserve progress loop
        MusicPlayerState.resetForAccountSwitch()
    }

    private fun reloadAllStores(context: Context) {
        val app = context.applicationContext
        SettingsStore.reloadBackupState(app)
        AvatarManager.init(app)
        GoogleAuthStore.refreshAfterBackupRestore(app)
        LibraryStore.clearAllRemoteSnapshots(app)
        LibraryStore.reloadBackupState(app)
        SearchHistoryStore.reloadBackupState(app)
        ListeningProfileStore.reloadBackupState(app)
        AudioOutputManager.reloadBackupState()
        AudioEngineManager.reloadBackupState()
        DynamicIslandStore.reloadBackupState(app)
        DynamicIslandStore.refreshOverlayPermission(app)
        ArtistStateStore.reloadBackupState(app)
        MusicCacheManager.init(app)
        LastFmIntegration.initialize(app)
        DiscordPresenceManager.initialize(app)
        TimeTravelRepository.ensureInit(app)
        TimeTravelRepository.refresh()
    }

    /**
     * Called when a backup (.itune) is restored to keep the active account profile
     * synchronized with restored settings and avatar preferences.
     */
    fun onBackupRestored(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = _activeAccount.value ?: return
        val restoredName = SettingsStore.state.value.displayName
        val restoredAvatar = AvatarManager.currentAvatarId.value.coerceIn(1, AvatarManager.TOTAL_AVATARS)
        val updated = active.copy(
            displayName = restoredName.trim().ifEmpty { active.displayName },
            avatarId = restoredAvatar,
            avatarUrl = AvatarManager.getAvatarUrl(restoredAvatar),
        )
        val list = _accounts.value.map { if (it.id == active.id) updated else it }
        saveAccountsList(prefs, list)
        _accounts.value = list
        _activeAccount.value = updated
        snapshotAccount(app, updated.id)
        Log.i(TAG, "Synced active account after backup restore: ${updated.displayName}")
    }

    private fun getAccountArchiveFile(context: Context, accountId: String): File {
        val dir = File(context.filesDir, ACCOUNTS_DIR)
        return File(dir, "account_$accountId.json")
    }

    private fun loadAccountsList(prefs: SharedPreferences): List<UserAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { UserAccount.fromJson(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccountsList(prefs: SharedPreferences, list: List<UserAccount>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply()
    }

    /**
     * Exports a master backup containing all created accounts and their entire datasets.
     */
    fun exportAllAccountsBackup(context: Context): JSONObject {
        val app = context.applicationContext
        val active = _activeAccount.value
        if (active != null) {
            snapshotAccount(app, active.id)
        }

        val root = JSONObject()
        root.put("type", "multi_account_master")
        root.put("version", 1)
        root.put("activeAccountId", active?.id.orEmpty())

        val accountsArray = JSONArray()
        _accounts.value.forEach { accountsArray.put(it.toJson()) }
        root.put("accounts", accountsArray)

        val accountsData = JSONObject()
        _accounts.value.forEach { acc ->
            val file = getAccountArchiveFile(app, acc.id)
            if (file.exists()) {
                try {
                    accountsData.put(acc.id, JSONObject(file.readText(Charsets.UTF_8)))
                } catch (_: Exception) {}
            }
        }
        root.put("accountsData", accountsData)
        return root
    }

    /**
     * Restores a master multi-account backup.
     */
    fun restoreAllAccountsBackup(context: Context, masterJson: JSONObject) {
        val app = context.applicationContext
        val accountsArray = masterJson.optJSONArray("accounts") ?: return
        val restoredAccounts = (0 until accountsArray.length()).mapNotNull { i ->
            accountsArray.optJSONObject(i)?.let { UserAccount.fromJson(it) }
        }
        if (restoredAccounts.isEmpty()) return

        val accountsData = masterJson.optJSONObject("accountsData") ?: JSONObject()
        val dir = File(app.filesDir, ACCOUNTS_DIR)
        dir.mkdirs()

        restoredAccounts.forEach { acc ->
            val accData = accountsData.optJSONObject(acc.id)
            if (accData != null) {
                val file = getAccountArchiveFile(app, acc.id)
                file.writeText(accData.toString(), Charsets.UTF_8)
            }
        }

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveAccountsList(prefs, restoredAccounts)
        _accounts.value = restoredAccounts

        val targetActiveId = masterJson.optString("activeAccountId", restoredAccounts.first().id)
        val targetActive = restoredAccounts.firstOrNull { it.id == targetActiveId } ?: restoredAccounts.first()

        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, targetActive.id).apply()
        _activeAccount.value = targetActive

        clearActiveStores(app)
        restoreAccount(app, targetActive.id)
        reloadAllStores(app)
    }
}
