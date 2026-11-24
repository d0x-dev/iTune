package com.darkxvenom.itune.data.remote.webdav

/** Credentials are scoped to the configured host before attaching to playback requests. */
object WebDavAuth {
    @Volatile private var host: String? = null
    @Volatile private var header: String? = null

    fun update(url: String, username: String, password: String) {
        host = WebDavConfig.hostOf(url)
        header = WebDavConfig.basicAuthHeader(username, password)
    }

    fun headerFor(requestHost: String): String? = header.takeIf { host != null && host.equals(requestHost, ignoreCase = true) }
}
