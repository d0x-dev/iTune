package com.darkxvenom.itune.data.auth

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

private const val MUSIC_ORIGIN = "https://music.youtube.com"
private val API_SID_NAMES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

enum class WebSessionMode {
    SIGN_IN,
    SWITCH_CHANNEL,
}

enum class PlaylistStorageTarget {
    LOCAL,
    GOOGLE_ACCOUNT,
}

@Immutable
data class YouTubeAccountInfo(
    val name: String,
    val email: String = "",
    val handle: String = "",
    val avatarUrl: String? = null,
)

@Immutable
data class AccountChannel(
    val name: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val activeOnWeb: Boolean = false,
) {
    val key: String
        get() = pageId ?: dataSyncId ?: name
}

@Immutable
data class CapturedSession(
    val cookie: String,
    val dataSyncId: String?,
    val pageId: String?,
    val authUser: String?,
    val visitorData: String?,
    val clientVersion: String?,
    val loggedIn: Boolean,
)

@Immutable
data class YouTubeProfile(
    val profileId: String,
    val name: String,
    val handle: String = "",
    val avatar: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val authUser: String? = null,
    val isBrandAccount: Boolean = false,
)

@Immutable
data class GoogleAccountSession(
    val accountId: String,
    val cookie: String,
    val name: String = "",
    val email: String = "",
    val profiles: List<YouTubeProfile> = emptyList(),
    val activeProfileId: String? = null,
)

@Immutable
data class YouTubeAccountUiState(
    val isSignedIn: Boolean = false,
    val accountName: String = "",
    val accountEmail: String = "",
    val accountHandle: String = "",
    val avatarUrl: String? = null,
    val channelName: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val lastSyncError: String? = null,
    val channels: List<AccountChannel> = emptyList(),
    val accounts: List<GoogleAccountSession> = emptyList(),
)

/** Checks cookie entries by exact name for a SAPISID signing secret. */
fun hasApiSid(cookieHeader: String?): Boolean {
    if (cookieHeader.isNullOrBlank()) return false
    return parseCookieHeader(cookieHeader).keys.any { it in API_SID_NAMES }
}

/**
 * Checks that the cookie header contains both a SAPISID signing secret and a
 * real Google session cookie (`LOGIN_INFO`, `SID`, or `__Secure-*PSID`), avoiding
 * premature capture of half-signed redirect cookies.
 */
fun looksSignedInCookies(cookieHeader: String?): Boolean {
    if (cookieHeader.isNullOrBlank()) return false
    val jar = parseCookieHeader(cookieHeader)
    val hasSidSecret = jar.keys.any { it in API_SID_NAMES }
    if (!hasSidSecret) return false
    return jar.containsKey("LOGIN_INFO") ||
        jar.containsKey("SID") ||
        jar.containsKey("__Secure-1PSID") ||
        jar.containsKey("__Secure-3PSID")
}

/** Parses a raw Cookie header into a map preserving non-empty entries. */
fun parseCookieHeader(cookieHeader: String): Map<String, String> {
    if (cookieHeader.isBlank()) return emptyMap()
    val out = linkedMapOf<String, String>()
    cookieHeader.split(';').forEach { entry ->
        val sep = entry.indexOf('=')
        if (sep <= 0) return@forEach
        val name = entry.substring(0, sep).trim()
        val value = entry.substring(sep + 1).trim()
        if (name.isNotEmpty() && value.isNotEmpty()) {
            out[name] = value
        }
    }
    return out
}

/** Extracts the highest-priority signing secret from a YouTube cookie header. */
fun sapisidFrom(cookieHeader: String): String? {
    val jar = parseCookieHeader(cookieHeader)
    return API_SID_NAMES.firstNotNullOfOrNull { jar[it] }
}

/** Builds a single `<SCHEME> <ts>_<sha1>` token for a given secret and origin. */
fun buildSapisidHash(
    sapisid: String,
    origin: String = MUSIC_ORIGIN,
    timestampSeconds: Long = System.currentTimeMillis() / 1000L,
    scheme: String = "SAPISIDHASH",
): String {
    val digest = MessageDigest.getInstance("SHA-1")
        .digest("$timestampSeconds $sapisid $origin".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
    return "$scheme ${timestampSeconds}_$digest"
}

/**
 * Builds the complete Google Innertube Authorization header (`SAPISIDHASH`, `SAPISID1PHASH`,
 * `SAPISID3PHASH`) matching Chrome & iTube so partitioned `__Secure-*PAPISID` cookies always validate.
 */
fun buildFullSapisidAuthorization(
    cookieHeader: String,
    origin: String = MUSIC_ORIGIN,
    timestampSeconds: Long = System.currentTimeMillis() / 1000L,
): String? {
    val jar = parseCookieHeader(cookieHeader)
    val baseSecret = jar["SAPISID"] ?: jar["__Secure-3PAPISID"] ?: jar["__Secure-1PAPISID"] ?: return null
    val parts = linkedSetOf<String>()
    parts.add(buildSapisidHash(baseSecret, origin, timestampSeconds, "SAPISIDHASH"))
    jar["__Secure-1PAPISID"]?.let { secret1p ->
        parts.add(buildSapisidHash(secret1p, origin, timestampSeconds, "SAPISID1PHASH"))
    }
    jar["__Secure-3PAPISID"]?.let { secret3p ->
        parts.add(buildSapisidHash(secret3p, origin, timestampSeconds, "SAPISID3PHASH"))
    }
    return parts.joinToString(" ")
}

/**
 * Merges HTTP `Set-Cookie` response headers into an existing serialized cookie header,
 * preserving updated rotating tokens like `__Secure-1PSIDTS`, `__Secure-3PSIDTS`, and `SIDCC`.
 * Returns `null` if no effective changes were made.
 */
fun mergeSetCookies(existingCookieHeader: String?, setCookieHeaders: List<String>): String? {
    if (existingCookieHeader.isNullOrBlank() || setCookieHeaders.isEmpty()) return null
    val jar = parseCookieHeader(existingCookieHeader).toMutableMap()
    var changed = false
    setCookieHeaders.forEach { raw ->
        val firstPart = raw.substringBefore(';').trim()
        val eq = firstPart.indexOf('=')
        if (eq <= 0) return@forEach
        val name = firstPart.substring(0, eq).trim()
        val value = firstPart.substring(eq + 1).trim()
        val deleted = value.isEmpty() || raw.contains("max-age=0", ignoreCase = true)
        if (deleted) {
            if (jar.remove(name) != null) changed = true
        } else if (jar[name] != value) {
            jar[name] = value
            changed = true
        }
    }
    if (!changed || jar.isEmpty()) return null
    return jar.entries.joinToString("; ") { (k, v) -> "$k=$v" }
}

internal fun sessionId(cookie: String, dataSyncId: String?): String =
    dataSyncId?.takeIf { it.isNotBlank() } ?: sha256Hex(cookie).take(24)

internal fun profileId(pageId: String?, dataSyncId: String?, name: String): String =
    pageId ?: dataSyncId ?: "profile:${sha256Hex(name).take(16)}"

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(Locale.ROOT, it) }

internal fun List<GoogleAccountSession>.sessionsToJson(): String = JSONArray().apply {
    forEach { session ->
        put(
            JSONObject().apply {
                put("id", session.accountId)
                put("cookie", session.cookie)
                put("name", session.name)
                put("email", session.email)
                put("activeProfileId", session.activeProfileId ?: JSONObject.NULL)
                put(
                    "profiles",
                    JSONArray().apply {
                        session.profiles.forEach { profile ->
                            put(
                                JSONObject().apply {
                                    put("id", profile.profileId)
                                    put("name", profile.name)
                                    put("handle", profile.handle)
                                    put("avatar", profile.avatar ?: JSONObject.NULL)
                                    put("pageId", profile.pageId ?: JSONObject.NULL)
                                    put("dataSyncId", profile.dataSyncId ?: JSONObject.NULL)
                                    put("authUser", profile.authUser ?: JSONObject.NULL)
                                    put("brand", profile.isBrandAccount)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}.toString()

internal fun sessionsFromJson(raw: String?): List<GoogleAccountSession> = runCatching {
    val root = JSONArray(raw ?: "[]")
    buildList {
        for (i in 0 until root.length()) {
            val obj = root.optJSONObject(i) ?: continue
            val cookie = obj.optString("cookie")
            if (cookie.isBlank()) continue
            val profilesArr = obj.optJSONArray("profiles") ?: JSONArray()
            val profiles = buildList {
                for (p in 0 until profilesArr.length()) {
                    val po = profilesArr.optJSONObject(p) ?: continue
                    val pName = po.optString("name")
                    add(
                        YouTubeProfile(
                            profileId = po.optString("id").ifBlank { profileId(null, null, pName) },
                            name = pName,
                            handle = po.optString("handle"),
                            avatar = po.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
                            pageId = po.optString("pageId").takeIf { it.isNotBlank() && it != "null" },
                            dataSyncId = po.optString("dataSyncId").takeIf { it.isNotBlank() && it != "null" },
                            authUser = po.optString("authUser").takeIf { it.isNotBlank() && it != "null" },
                            isBrandAccount = po.optBoolean("brand", false),
                        ),
                    )
                }
            }
            add(
                GoogleAccountSession(
                    accountId = obj.optString("id").ifBlank { sessionId(cookie, null) },
                    cookie = cookie,
                    name = obj.optString("name"),
                    email = obj.optString("email"),
                    profiles = profiles,
                    activeProfileId = obj.optString("activeProfileId").takeIf { it.isNotBlank() && it != "null" },
                ),
            )
        }
    }
}.getOrDefault(emptyList())
