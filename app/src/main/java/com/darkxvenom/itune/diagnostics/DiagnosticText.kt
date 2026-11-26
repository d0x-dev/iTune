package com.darkxvenom.itune.diagnostics

import org.json.JSONObject

/** Keep diagnostic prose and stack frames, masking credentials before the capture buffer. */
internal object DiagnosticText {
    private val secret = """(?:[\w.-]*(?:token|cookie|password|passwd|secret|authorization|credential)|(?:proxy-)?authorization|api[-_]?key|key|signature|sig|lsig|policy|x-amz-credential|x-amz-signature|x-goog-credential|x-goog-signature|session(?:id|_id)?|sp_dc|sp_key|(?:__Secure-[\w-]+|(?:SAPISID|APISID|SIDCC|HSID|SSID|SID))|x-goog-authuser)"""
    private val header = Regex("""(?i)(?<![\w-])((?:set-cookie2?|cookie2?|(?:proxy-)?authorization)\s*[:=]\s*).*""")
    private val field = Regex("""(?i)(?<![\w-])(["']?$secret["']?\s*[:=]\s*)(?:"(?:\\.|[^"\\])*(?:"|$)|'(?:\\.|[^'\\])*(?:'|$)|[^\s,;&}\]]+)""")
    private val bearer = Regex("""(?i)\b(Bearer|Basic|SAPISIDHASH)\s+[\w.+/=_:-]+""")
    private val authCode = Regex("""(?i)([?&#](?:code|oauth_verifier|x-amz-security-token)=)[^&#]*""")
    private val jwt = Regex("""\beyJ[\w-]+\.[\w-]+\.[\w-]+""")
    private val url = Regex("""(?i)\b(?:https?|smb|ftp)://[^\s"'<>]+""")

    fun mask(text: String): String {
        val endpoints = url.replace(text) { match ->
            val value = match.value
            val base = value.substringBefore('?').substringBefore('#')
            val scheme = base.substringBefore("://")
            val rest = base.substringAfter("://")
            val host = rest.substringBefore('/').substringAfterLast('@')
            val path = rest.indexOf('/').takeIf { it >= 0 }?.let { rest.substring(it) }.orEmpty()
            // CDN paths may themselves contain an expiring signature.
            val safePath = if (host.endsWith(".googlevideo.com", true) || host.endsWith(".akamaized.net", true))
                "/…" else path
            val suffix = authCode.replace(value.substring(base.length)) { it.groupValues[1] + "[redacted]" }
            "$scheme://$host$safePath" + suffix
        }
        val withoutFields = field.replace(endpoints) {
            val key = it.groupValues[1]
            val quote = it.value.getOrNull(key.length)
            if (quote == '"' || quote == '\'') "$key$quote[redacted]$quote" else "$key[redacted]"
        }
        val withoutHeaders = header.replace(withoutFields) { it.groupValues[1] + "[redacted]" }
        return jwt.replace(bearer.replace(withoutHeaders) { it.groupValues[1] + " [redacted]" }, "[redacted]")
    }

    /** Only read provider error fields, never authentication payloads or playable stream URLs. */
    fun responseReason(body: String, contentType: String? = null): String? {
        if (contentType?.substringBefore(';')?.trim().equals("text/plain", ignoreCase = true) &&
            !body.trimStart().startsWith('{')) {
            return body.take(2048).trim().takeIf { it.isNotEmpty() }
        }
        if (body.length > 1_048_576 || body.trimStart().firstOrNull() != '{') return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val playability = (json.optJSONObject("playerResponse") ?: json).optJSONObject("playabilityStatus")
        if (playability != null && playability.optString("status") != "OK") {
            return playability.toString()
        }
        // Preserve nested provider errors and their field names. A successful player
        // response also contains private stream URLs and is never dumped wholesale.
        if (json.has("error") || json.has("error_description") || (contentType != null && playability == null)) {
            return body
        }
        return null
    }
}
