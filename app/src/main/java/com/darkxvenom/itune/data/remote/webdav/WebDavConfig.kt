package com.darkxvenom.itune.data.remote.webdav

import java.util.Locale

object WebDavConfig {
    val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma", "aif", "aiff", "webm")

    fun normalizeUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) return ""
        return if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
    }

    fun isConfigured(raw: String): Boolean = runCatching {
        val parsed = java.net.URI(normalizeUrl(raw))
        parsed.scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)

    fun hostOf(raw: String): String? = runCatching { java.net.URI(normalizeUrl(raw)).host?.lowercase(Locale.ROOT) }.getOrNull()

    fun basicAuthHeader(username: String, password: String): String? {
        if (username.isBlank() && password.isBlank()) return null
        val value = java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $value"
    }

    fun isAudioFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions
}
