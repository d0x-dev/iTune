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
