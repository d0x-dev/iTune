package com.darkxvenom.itune.data.auth

/**
 * Normalizes YouTube `DATASYNC_ID` into the `onBehalfOfUser` token expected by Innertube.
 * YouTube exposes `DATASYNC_ID` as `account||delegated`; the delegated half is the active
 * identity, while plain accounts may only expose the first half.
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
    return value.substring(0, separator).trim().takeIf { it.isNotBlank() }
}
