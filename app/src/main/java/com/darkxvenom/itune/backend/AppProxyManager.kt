package com.darkxvenom.itune.backend

import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Manages HTTP / SOCKS proxy configuration for all network calls in iTune.
 */
object AppProxyManager {

    init {
        // Install JVM-wide authenticator for SOCKS proxies
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val state = SettingsStore.state.value
                if (state.proxyEnabled && state.proxyUser.isNotBlank()) {
                    return PasswordAuthentication(state.proxyUser, state.proxyPass.toCharArray())
                }
                return super.getPasswordAuthentication()
            }
        })
    }

    val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val state = SettingsStore.state.value
            if (!state.proxyEnabled || state.proxyHost.isBlank() || state.proxyPort <= 0) {
                return listOf(Proxy.NO_PROXY)
            }
            return try {
                val type = if (state.proxyType.contains("SOCKS", ignoreCase = true)) {
                    Proxy.Type.SOCKS
                } else {
                    Proxy.Type.HTTP
                }
                val address = InetSocketAddress(state.proxyHost.trim(), state.proxyPort)
                listOf(Proxy(type, address))
            } catch (_: Exception) {
                listOf(Proxy.NO_PROXY)
            }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            // Ignored; fallback happens automatically
        }
    }

    val proxyAuthenticator: Authenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val state = SettingsStore.state.value
            if (!state.proxyEnabled || state.proxyUser.isBlank()) return null
            if (response.challenges().any { it.scheme.equals("Basic", ignoreCase = true) }) {
                val credential = Credentials.basic(state.proxyUser, state.proxyPass)
                return response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
            return null
        }
    }

    suspend fun testConnection(
        type: String,
        host: String,
        port: Int,
        user: String,
        pass: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (host.isBlank() || port <= 0) {
            return@withContext false to "Please enter valid Host and Port"
        }
        val startTime = System.currentTimeMillis()
        try {
            val proxyType = if (type.contains("SOCKS", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxy = Proxy(proxyType, InetSocketAddress(host.trim(), port))

            val clientBuilder = OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)

            if (user.isNotBlank()) {
                clientBuilder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(user, pass)
                    response.request.newBuilder().header("Proxy-Authorization", credential).build()
                }
            }

            val client = clientBuilder.build()
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .header("User-Agent", Net.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code in 200..399) {
                    true to "Connected successfully (${duration}ms)"
                } else {
                    false to "Proxy responded with HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Connection timed out")
        }
    }
}
