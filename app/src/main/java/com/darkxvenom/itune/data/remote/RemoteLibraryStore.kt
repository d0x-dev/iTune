package com.darkxvenom.itune.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.remote.smb.SmbAuth
import com.darkxvenom.itune.data.remote.smb.SmbClient
import com.darkxvenom.itune.data.remote.smb.SmbConfig
import com.darkxvenom.itune.data.remote.webdav.WebDavAuth
import com.darkxvenom.itune.data.remote.webdav.WebDavClient
import com.darkxvenom.itune.data.remote.webdav.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URLDecoder

data class RemoteLibrarySettings(
    val webDavUrl: String = "", val webDavUser: String = "", val webDavPassword: String = "",
    val smbHost: String = "", val smbShare: String = "", val smbFolder: String = "",
    val smbUser: String = "", val smbPassword: String = "",
)

object RemoteLibraryStore {
    private const val PREFS = "remote_libraries_encrypted"
    private val _settings = MutableStateFlow(RemoteLibrarySettings())
    val settings = _settings.asStateFlow()
    @Volatile private var prefs: SharedPreferences? = null

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val store = EncryptedSharedPreferences.create(
                context, PREFS, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            prefs = store
            _settings.value = RemoteLibrarySettings(
                webDavUrl = store.getString("webdav_url", "").orEmpty(),
                webDavUser = store.getString("webdav_user", "").orEmpty(),
                webDavPassword = store.getString("webdav_password", "").orEmpty(),
                smbHost = store.getString("smb_host", "").orEmpty(),
                smbShare = store.getString("smb_share", "").orEmpty(),
                smbFolder = store.getString("smb_folder", "").orEmpty(),
                smbUser = store.getString("smb_user", "").orEmpty(),
                smbPassword = store.getString("smb_password", "").orEmpty(),
            )
            publishAuth()
        }
    }

    fun saveWebDav(url: String, user: String, password: String) {
        _settings.value = _settings.value.copy(webDavUrl = WebDavConfig.normalizeUrl(url), webDavUser = user, webDavPassword = password)
        prefs?.edit()?.putString("webdav_url", _settings.value.webDavUrl)?.putString("webdav_user", user)?.putString("webdav_password", password)?.apply()
        publishAuth()
    }

    fun saveSmb(host: String, share: String, folder: String, user: String, password: String) {
        _settings.value = _settings.value.copy(smbHost = host, smbShare = share, smbFolder = folder, smbUser = user, smbPassword = password)
        prefs?.edit()?.putString("smb_host", host)?.putString("smb_share", share)?.putString("smb_folder", folder)
            ?.putString("smb_user", user)?.putString("smb_password", password)?.apply()
        publishAuth()
    }

    fun clearWebDav() = saveWebDav("", "", "")
    fun clearSmb() = saveSmb("", "", "", "", "")

    suspend fun testWebDav() = WebDavClient.testConnection(_settings.value.webDavUrl, _settings.value.webDavUser, _settings.value.webDavPassword)

    suspend fun testSmb() = SmbClient.testConnection(
        _settings.value.smbHost, _settings.value.smbShare, _settings.value.smbFolder,
        _settings.value.smbUser, _settings.value.smbPassword,
    )

    suspend fun listWebDav(): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        val s = _settings.value
        WebDavClient.listAudioFiles(s.webDavUrl, s.webDavUser, s.webDavPassword).map { rows ->
            rows.map { entry ->
                val decoded = runCatching { URLDecoder.decode(entry.url.substringAfterLast('/'), "UTF-8") }.getOrDefault(entry.displayName)
                val stem = decoded.substringBeforeLast('.')
                val parts = stem.split(" - ", limit = 2)
                MusicTrack(
                    id = "webdav:${entry.url}", title = parts.lastOrNull().orEmpty().ifBlank { stem },
                    artists = if (parts.size == 2) parts[0] else "Unknown Artist",
                    album = runCatching { java.net.URI(entry.url).path.substringBeforeLast('/').substringAfterLast('/') }.getOrNull(),
                )
            }
        }
    }

    suspend fun listSmb(): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        val s = _settings.value
        SmbClient.testAndList(s.smbHost, s.smbShare, s.smbFolder, s.smbUser, s.smbPassword).map { listing ->
            val host = SmbConfig.normalizeHost(s.smbHost)
            listing.audio.map { entry ->
                val filename = entry.path.substringAfterLast('/').substringAfterLast('\\')
                val stem = filename.substringBeforeLast('.')
                val parts = stem.split(" - ", limit = 2)
                MusicTrack(
                    id = "smb://$host/${s.smbShare.trim('/')}/${entry.path.trim('/')}",
                    title = parts.lastOrNull().orEmpty().ifBlank { stem },
                    artists = if (parts.size == 2) parts[0] else "Unknown Artist",
                    album = entry.path.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { null },
                )
            }
        }
    }

    private fun publishAuth() {
        val s = _settings.value
        WebDavAuth.update(s.webDavUrl, s.webDavUser, s.webDavPassword)
        SmbAuth.update(s.smbHost, s.smbShare, s.smbFolder, s.smbUser, s.smbPassword)
    }
}
