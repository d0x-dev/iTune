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

import androidx.annotation.Keep
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Keep
data class DeveloperNewsItem(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val author: String? = null,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val actionText: String? = null,
    val tag: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val showPopup: Boolean = false,
    val priority: Int = 0,
    val active: Boolean = true
)
