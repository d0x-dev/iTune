package com.darkxvenom.itune.data.auth

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
