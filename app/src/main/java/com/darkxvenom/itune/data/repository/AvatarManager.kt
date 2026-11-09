package com.darkxvenom.itune.data.repository

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf

/**
 * Manages user avatar selection and persistence across app launches.
 * Hosts 20 premium neon-lit headphone avatars uploaded to GitHub.
 */
object AvatarManager {
    const val TOTAL_AVATARS = 20
    private const val PREFS_NAME = "itune_avatar_prefs"
    private const val KEY_SELECTED_AVATAR = "selected_avatar_id"
    private const val GITHUB_AVATAR_BASE_URL = "https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars"

    // Observable Compose state for instant multi-screen UI reactivity
    private val _currentAvatarId = mutableIntStateOf(1)
    val currentAvatarId: State<Int> get() = _currentAvatarId

    private var isInitialized = false

    /**
     * Initializes the avatar manager from SharedPreferences.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getInt(KEY_SELECTED_AVATAR, 1)
        _currentAvatarId.intValue = savedId.coerceIn(1, TOTAL_AVATARS)
        isInitialized = true
    }

    /**
     * Saves the chosen avatar permanently to SharedPreferences and updates reactive state.
     */
    fun setAvatar(context: Context, avatarId: Int) {
        val safeId = avatarId.coerceIn(1, TOTAL_AVATARS)
        _currentAvatarId.intValue = safeId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_AVATAR, safeId)
            .apply()
    }

    /**
     * Constructs the high-resolution GitHub raw URL for the given avatar ID.
     */
    fun getAvatarUrl(id: Int): String {
        val safeId = id.coerceIn(1, TOTAL_AVATARS)
        return "$GITHUB_AVATAR_BASE_URL/avatar_$safeId.png"
    }
}
