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
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    /**
     * Initializes the avatar manager from SharedPreferences.
     * Always reloads the saved avatar to guarantee fresh state across account switches.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getInt(KEY_SELECTED_AVATAR, 1)
        _currentAvatarId.intValue = savedId.coerceIn(1, TOTAL_AVATARS)
    }

    /**
     * Forces a reload of the avatar ID from SharedPreferences.
     */
    fun reload(context: Context) {
        init(context)
    }

    /**
     * Saves the chosen avatar permanently to SharedPreferences, updates reactive state,
     * and synchronizes with the active user account.
     */
    fun setAvatar(context: Context, avatarId: Int) {
        val safeId = avatarId.coerceIn(1, TOTAL_AVATARS)
        _currentAvatarId.intValue = safeId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_AVATAR, safeId)
            .apply()
        com.darkxvenom.itune.data.account.MultiAccountManager.updateActiveAccountProfile(context, avatarId = safeId)
    }

    /**
     * Constructs the high-resolution GitHub raw URL for the given avatar ID.
     */
    fun getAvatarUrl(id: Int): String {
        val safeId = id.coerceIn(1, TOTAL_AVATARS)
        return "$GITHUB_AVATAR_BASE_URL/avatar_$safeId.png"
    }
}
