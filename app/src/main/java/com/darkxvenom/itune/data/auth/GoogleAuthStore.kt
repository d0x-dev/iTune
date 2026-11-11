package com.darkxvenom.itune.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central source of truth for Google / YouTube Music authentication state and
 * channel selection across Settings, Welcome, Player, Search, and Library sync.
 */
object GoogleAuthStore {
    private const val PREFS_META = "itune_auth_meta"
    private const val KEY_COOKIE = "ytm_cookie"
    private const val KEY_SESSIONS = "ytm_sessions_v2"
    private const val KEY_ACTIVE_ACCOUNT = "ytm_active_account_id"
    private const val KEY_ACTIVE_PROFILE = "ytm_active_profile_id"
    private const val KEY_VISITOR_DATA = "ytm_visitor_data"
    private const val KEY_CHANNEL_PAGE_ID = "ytm_channel_page_id"
    private const val KEY_CHANNEL_DATASYNC_ID = "ytm_channel_datasync_id"
    private const val KEY_CHANNEL_NAME = "ytm_channel_name"
    private const val KEY_CHANNEL_AUTH_USER = "ytm_channel_auth_user"
    private const val KEY_ACCOUNT_NAME = "ytm_account_name"
    private const val KEY_ACCOUNT_EMAIL = "ytm_account_email"
    private const val KEY_ACCOUNT_HANDLE = "ytm_account_handle"
    private const val KEY_ACCOUNT_AVATAR = "ytm_account_avatar"
    private const val KEY_LAST_SYNCED_MS = "ytm_last_synced_ms"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var metaPrefs: SharedPreferences? = null

    private val _state = MutableStateFlow(YouTubeAccountUiState())
    val state: StateFlow<YouTubeAccountUiState> = _state.asStateFlow()

    val isSignedIn: Boolean
        get() = _state.value.isSignedIn

    fun ensureInit(context: Context) {
        if (metaPrefs != null) return
        synchronized(this) {
            if (metaPrefs != null) return
            val app = context.applicationContext
            appContext = app
            val p = app.getSharedPreferences(PREFS_META, Context.MODE_PRIVATE)
            metaPrefs = p
            EncryptedAuthPrefs.open(app)
            restoreToClient(app, p)
            publishState()
        }
    }

    fun activeCookie(): String? = _state.value.accounts
        .firstOrNull { it.accountId == activeAccountId() }
        ?.cookie
        ?: activeLegacyCookie()

    /**
     * Absorbs rotated Google session cookies (`Set-Cookie` headers like `__Secure-*PSIDTS`)
     * into the encrypted cookie jar and live Innertube client without altering UI state.
     */
    fun absorbSetCookies(setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val app = appContext ?: return
        synchronized(this) {
            val currentCookie = ArtistInnertubeClient.cookie ?: activeCookie() ?: return
            val merged = mergeSetCookies(currentCookie, setCookieHeaders) ?: return
            EncryptedAuthPrefs.putEncrypted(app, KEY_COOKIE, merged)
            val sessions = readSessions(app).toMutableList()
            val activeAccId = activeAccountId()
            val idx = sessions.indexOfFirst { it.accountId == activeAccId }
            if (idx >= 0) {
                sessions[idx] = sessions[idx].copy(cookie = merged)
                EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, sessions.sessionsToJson())
            }
            ArtistInnertubeClient.updateCookie(merged)
        }
    }

    /** Persists a working fallback identity scope if the initial capture carried an invalid delegated scope. */
    fun updateActiveScope(pageId: String?, dataSyncId: String?) {
        val app = appContext ?: return
        synchronized(this) {
            val mp = meta(app)
            val activeAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
            mp.edit().apply {
                if (pageId.isNullOrBlank()) remove(KEY_CHANNEL_PAGE_ID) else putString(KEY_CHANNEL_PAGE_ID, pageId)
                if (dataSyncId.isNullOrBlank()) remove(KEY_CHANNEL_DATASYNC_ID) else putString(KEY_CHANNEL_DATASYNC_ID, dataSyncId)
            }.apply()
            val sessions = readSessions(app).toMutableList()
            val idx = sessions.indexOfFirst { it.accountId == activeAccId }
            if (idx >= 0) {
                val activePid = sessions[idx].activeProfileId
                val updatedProfiles = sessions[idx].profiles.map { p ->
                    if (p.profileId == activePid) p.copy(pageId = pageId, dataSyncId = dataSyncId) else p
                }
                sessions[idx] = sessions[idx].copy(profiles = updatedProfiles)
                EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, sessions.sessionsToJson())
            }
        }
    }

    fun saveCapturedSession(
        context: Context,
        captured: CapturedSession,
        accountInfo: YouTubeAccountInfo?,
        channels: List<AccountChannel> = emptyList(),
    ) {
        ensureInit(context)
        val app = context.applicationContext
        val name = accountInfo?.name?.takeIf { it.isNotBlank() } ?: "Google Account"
        val email = accountInfo?.email.orEmpty()
        val handle = accountInfo?.handle.orEmpty()
        val avatarUrl = accountInfo?.avatarUrl

        val existingSessions = readSessions(app).toMutableList()
        val activeChannel = channels.firstOrNull { it.activeOnWeb }
            ?: channels.firstOrNull { it.pageId == captured.pageId && it.dataSyncId == captured.dataSyncId }

        val profiles = if (channels.isNotEmpty()) {
            channels.map { ch ->
                YouTubeProfile(
                    profileId = profileId(ch.pageId, ch.dataSyncId, ch.name),
                    name = ch.name,
                    handle = ch.subtitle,
                    avatar = ch.thumbnailUrl,
                    pageId = ch.pageId,
                    dataSyncId = ch.dataSyncId,
                    authUser = captured.authUser,
                    isBrandAccount = ch.pageId != null,
                )
            }
        } else {
            listOf(
                YouTubeProfile(
                    profileId = profileId(captured.pageId, captured.dataSyncId, name),
                    name = name,
                    handle = handle.ifBlank { email },
                    avatar = avatarUrl,
                    pageId = captured.pageId,
                    dataSyncId = captured.dataSyncId,
                    authUser = captured.authUser,
                    isBrandAccount = captured.pageId != null,
                ),
            )
        }

        val resolvedActiveProfile = profiles.firstOrNull {
            it.pageId == (activeChannel?.pageId ?: captured.pageId) &&
                it.dataSyncId == (activeChannel?.dataSyncId ?: captured.dataSyncId)
        } ?: profiles.first()

        val accId = sessionId(captured.cookie, captured.dataSyncId)
        val newSession = GoogleAccountSession(
            accountId = accId,
            cookie = captured.cookie,
            name = name,
            email = email,
            profiles = profiles,
            activeProfileId = resolvedActiveProfile.profileId,
        )

        val matchIndex = existingSessions.indexOfFirst {
            it.accountId == accId || (email.isNotBlank() && it.email.equals(email, ignoreCase = true))
        }
        if (matchIndex >= 0) {
            existingSessions[matchIndex] = newSession
        } else {
            existingSessions.add(0, newSession)
        }

        EncryptedAuthPrefs.putEncrypted(app, KEY_COOKIE, captured.cookie)
        EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, existingSessions.sessionsToJson())

        val mp = meta(app)
        mp.edit()
            .putString(KEY_ACTIVE_ACCOUNT, accId)
            .putString(KEY_ACTIVE_PROFILE, resolvedActiveProfile.profileId)
            .putString(KEY_CHANNEL_PAGE_ID, resolvedActiveProfile.pageId)
            .putString(KEY_CHANNEL_DATASYNC_ID, resolvedActiveProfile.dataSyncId ?: captured.dataSyncId)
            .putString(KEY_CHANNEL_NAME, resolvedActiveProfile.name)
            .putString(KEY_CHANNEL_AUTH_USER, captured.authUser)
            .putString(KEY_ACCOUNT_NAME, name)
            .putString(KEY_ACCOUNT_EMAIL, email)
            .putString(KEY_ACCOUNT_HANDLE, handle)
            .putString(KEY_ACCOUNT_AVATAR, resolvedActiveProfile.avatar ?: avatarUrl)
            .apply {
                if (!captured.visitorData.isNullOrBlank()) {
                    putString(KEY_VISITOR_DATA, captured.visitorData)
                }
            }
            .apply()

        ArtistInnertubeClient.adoptSessionScope(
            cookie = captured.cookie,
            pageId = resolvedActiveProfile.pageId,
            dataSyncId = resolvedActiveProfile.dataSyncId ?: captured.dataSyncId,
            authUser = captured.authUser,
            visitorData = captured.visitorData,
            clientVersion = captured.clientVersion,
            loggedIn = captured.loggedIn,
        )
        ArtistInnertubeClient.selectChannel(
            pageId = resolvedActiveProfile.pageId,
            dataSyncId = resolvedActiveProfile.dataSyncId ?: captured.dataSyncId,
            authUser = captured.authUser,
        )
        publishState(channels = channels)
    }

    fun selectChannel(context: Context, channel: AccountChannel) {
        ensureInit(context)
        val app = context.applicationContext
        val mp = meta(app)
        val currentAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
        val sessions = readSessions(app).toMutableList()
        val chosenPid = profileId(channel.pageId, channel.dataSyncId, channel.name)
        val authUser = mp.getString(KEY_CHANNEL_AUTH_USER, null)

        val idx = sessions.indexOfFirst { it.accountId == currentAccId }
        if (idx >= 0) {
            sessions[idx] = sessions[idx].copy(activeProfileId = chosenPid)
            EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, sessions.sessionsToJson())
        }

        mp.edit()
            .putString(KEY_ACTIVE_PROFILE, chosenPid)
            .putString(KEY_CHANNEL_PAGE_ID, channel.pageId)
            .putString(KEY_CHANNEL_DATASYNC_ID, channel.dataSyncId)
            .putString(KEY_CHANNEL_NAME, channel.name)
            .apply {
                if (!channel.thumbnailUrl.isNullOrBlank()) {
                    putString(KEY_ACCOUNT_AVATAR, channel.thumbnailUrl)
                }
            }
            .apply()

        ArtistInnertubeClient.selectChannel(
            pageId = channel.pageId,
            dataSyncId = channel.dataSyncId,
            authUser = authUser,
        )
        publishState()
    }

    fun updateSyncStatus(
        syncing: Boolean? = null,
        error: String? = null,
        syncedNow: Boolean = false,
        channels: List<AccountChannel>? = null,
        accountInfo: YouTubeAccountInfo? = null,
    ) {
        val ctx = appContext
        if (ctx != null && accountInfo != null) {
            meta(ctx).edit().apply {
                if (accountInfo.name.isNotBlank()) putString(KEY_ACCOUNT_NAME, accountInfo.name)
                if (accountInfo.email.isNotBlank()) putString(KEY_ACCOUNT_EMAIL, accountInfo.email)
                if (accountInfo.handle.isNotBlank()) putString(KEY_ACCOUNT_HANDLE, accountInfo.handle)
                if (!accountInfo.avatarUrl.isNullOrBlank()) putString(KEY_ACCOUNT_AVATAR, accountInfo.avatarUrl)
            }.apply()
        }
        val syncedMs = if (syncedNow && ctx != null) {
            System.currentTimeMillis().also { now ->
                meta(ctx).edit().putLong(KEY_LAST_SYNCED_MS, now).apply()
            }
        } else {
            _state.value.lastSyncedAtMs
        }
        val current = _state.value
        _state.value = current.copy(
            accountName = accountInfo?.name?.takeIf { it.isNotBlank() } ?: current.accountName,
            accountEmail = accountInfo?.email?.takeIf { it.isNotBlank() } ?: current.accountEmail,
            accountHandle = accountInfo?.handle?.takeIf { it.isNotBlank() } ?: current.accountHandle,
            avatarUrl = accountInfo?.avatarUrl?.takeIf { it.isNotBlank() } ?: current.avatarUrl,
            isSyncing = syncing ?: current.isSyncing,
            lastSyncError = error,
            lastSyncedAtMs = syncedMs,
            channels = channels ?: current.channels,
        )
    }

    fun signOut(context: Context) {
        ensureInit(context)
        val app = context.applicationContext
        EncryptedAuthPrefs.putEncrypted(app, KEY_COOKIE, null)
        EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, null)
        meta(app).edit()
            .remove(KEY_ACTIVE_ACCOUNT)
            .remove(KEY_ACTIVE_PROFILE)
            .remove(KEY_VISITOR_DATA)
            .remove(KEY_CHANNEL_PAGE_ID)
            .remove(KEY_CHANNEL_DATASYNC_ID)
            .remove(KEY_CHANNEL_NAME)
            .remove(KEY_CHANNEL_AUTH_USER)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCOUNT_HANDLE)
            .remove(KEY_ACCOUNT_AVATAR)
            .remove(KEY_LAST_SYNCED_MS)
            .apply()
        BrowserSession.clearGoogleCookies()
        ArtistInnertubeClient.clearAuthSession()
        _state.value = YouTubeAccountUiState()
    }

    private fun restoreToClient(app: Context, mp: SharedPreferences) {
        val sessions = readSessions(app)
        val activeAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
        val activeSession = sessions.firstOrNull { it.accountId == activeAccId } ?: sessions.firstOrNull()
        val cookie = activeSession?.cookie ?: EncryptedAuthPrefs.getDecrypted(app, KEY_COOKIE)
        if (cookie.isNullOrBlank() || !hasApiSid(cookie)) {
            ArtistInnertubeClient.clearAuthSession()
            return
        }
        val activeProfile = activeSession?.profiles?.firstOrNull { it.profileId == mp.getString(KEY_ACTIVE_PROFILE, null) }
            ?: activeSession?.profiles?.firstOrNull()
        val pageId = mp.getString(KEY_CHANNEL_PAGE_ID, activeProfile?.pageId)
        val dataSyncId = mp.getString(KEY_CHANNEL_DATASYNC_ID, activeProfile?.dataSyncId)
        val authUser = mp.getString(KEY_CHANNEL_AUTH_USER, activeProfile?.authUser)
        val visitor = mp.getString(KEY_VISITOR_DATA, null)
        ArtistInnertubeClient.adoptSessionScope(
            cookie = cookie,
            pageId = pageId,
            dataSyncId = dataSyncId,
            authUser = authUser,
            visitorData = visitor,
            clientVersion = null,
            loggedIn = true,
        )
        if (pageId != null || dataSyncId != null) {
            ArtistInnertubeClient.selectChannel(pageId = pageId, dataSyncId = dataSyncId, authUser = authUser)
        }
    }

    private fun publishState(channels: List<AccountChannel>? = null) {
        val app = appContext ?: return
        val mp = meta(app)
        val sessions = readSessions(app)
        val activeAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
        val activeSession = sessions.firstOrNull { it.accountId == activeAccId } ?: sessions.firstOrNull()
        val cookie = activeSession?.cookie ?: EncryptedAuthPrefs.getDecrypted(app, KEY_COOKIE)
        val signedIn = hasApiSid(cookie)
        val derivedChannels = channels ?: activeSession?.profiles?.map { p ->
            AccountChannel(
                name = p.name,
                subtitle = p.handle,
                thumbnailUrl = p.avatar,
                pageId = p.pageId,
                dataSyncId = p.dataSyncId,
                activeOnWeb = p.profileId == activeSession.activeProfileId,
            )
        }.orEmpty()
        _state.value = _state.value.copy(
            isSignedIn = signedIn,
            accountName = mp.getString(KEY_ACCOUNT_NAME, null).orEmpty(),
            accountEmail = mp.getString(KEY_ACCOUNT_EMAIL, null).orEmpty(),
            accountHandle = mp.getString(KEY_ACCOUNT_HANDLE, null).orEmpty(),
            avatarUrl = mp.getString(KEY_ACCOUNT_AVATAR, null),
            channelName = mp.getString(KEY_CHANNEL_NAME, null),
            lastSyncedAtMs = mp.getLong(KEY_LAST_SYNCED_MS, 0L),
            channels = derivedChannels,
            accounts = sessions,
        )
    }

    private fun readSessions(app: Context): List<GoogleAccountSession> =
        sessionsFromJson(EncryptedAuthPrefs.getDecrypted(app, KEY_SESSIONS))

    private fun activeAccountId(): String? = appContext?.let { meta(it).getString(KEY_ACTIVE_ACCOUNT, null) }

    private fun activeLegacyCookie(): String? = appContext?.let { EncryptedAuthPrefs.getDecrypted(it, KEY_COOKIE) }

    private fun meta(context: Context): SharedPreferences =
        metaPrefs ?: context.applicationContext.getSharedPreferences(PREFS_META, Context.MODE_PRIVATE)
}
