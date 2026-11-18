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

package com.darkxvenom.itune.data.auth
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * Extracts the USER_SESSION_ID from YouTube's DATASYNC_ID for SAPISIDHASH authorization.
 * YouTube exposes primary accounts as USER_SESSION_ID|| and secondary channels as
 * DELEGATED_SESSION_ID||USER_SESSION_ID. The delegated component is parsed separately.
 */
fun normalizeDataSyncId(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.contains("||")) return value
    return value.substringAfter("||").trim().takeIf { it.isNotBlank() }
        ?: value.substringBefore("||").trim().takeIf { it.isNotBlank() }
}

/**
 * Extracts YouTube's delegated channel session id from DATASYNC_ID.
 * Primary accounts have USER_SESSION_ID|| and therefore no delegated id;
 * secondary channels use DELEGATED_SESSION_ID||USER_SESSION_ID.
 */
fun delegatedSessionId(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val separator = value.indexOf("||")
    if (separator <= 0) return null
    // A primary account is USER_SESSION_ID|| (empty right-hand side), not a
    // delegated channel. Secondary channels provide both parts.
    val userSessionId = value.substring(separator + 2).trim()
    if (userSessionId.isBlank()) return null
    return value.substring(0, separator).trim().takeIf { it.isNotBlank() }
}
