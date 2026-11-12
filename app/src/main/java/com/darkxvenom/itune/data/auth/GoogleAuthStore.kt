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

    fun activeSessionKey(): String? = _state.value.activeSessionKey

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
        mode: WebSessionMode = WebSessionMode.SIGN_IN,
    ) {
        ensureInit(context)
        val app = context.applicationContext
        synchronized(this) {
            val mp = meta(app)
            val name = accountInfo?.name?.takeIf { it.isNotBlank() } ?: "Google Account"
            val email = accountInfo?.email.orEmpty()
            val handle = accountInfo?.handle.orEmpty()
            val avatarUrl = accountInfo?.avatarUrl

            val existingSessions = readSessions(app).toMutableList()
            val currentActiveAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
            val candidateAccId = sessionId(captured.cookie, captured.dataSyncId)

            val matchIndex = when (mode) {
                WebSessionMode.SWITCH_CHANNEL -> existingSessions.indexOfFirst { it.accountId == currentActiveAccId }
                    .takeIf { it >= 0 }
                    ?: existingSessions.indexOfFirst {
                        it.accountId == candidateAccId || (email.isNotBlank() && it.email.equals(email, ignoreCase = true))
                    }
                WebSessionMode.SIGN_IN -> existingSessions.indexOfFirst {
                    (email.isNotBlank() && it.email.equals(email, ignoreCase = true)) ||
                        it.accountId == candidateAccId
                }
            }

            val previous = if (matchIndex >= 0) existingSessions[matchIndex] else null
            val accId = previous?.accountId ?: candidateAccId

            val activeChannel = channels.firstOrNull { it.activeOnWeb }
                ?: channels.firstOrNull { it.pageId == captured.pageId && it.dataSyncId == captured.dataSyncId }

            val detectedProfiles = if (channels.isNotEmpty()) {
                channels.map { ch ->
                    val isSelected = activeChannel != null &&
                        ch.pageId == activeChannel.pageId &&
                        ch.dataSyncId == activeChannel.dataSyncId &&
                        ch.name == activeChannel.name
                    YouTubeProfile(
                        profileId = profileId(ch.pageId, ch.dataSyncId, ch.name),
                        name = ch.name,
                        handle = ch.subtitle.ifBlank { if (isSelected) email else "" },
                        avatar = ch.thumbnailUrl ?: if (isSelected) avatarUrl else null,
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

            val resolvedActiveProfile = detectedProfiles.firstOrNull {
                it.pageId == (activeChannel?.pageId ?: captured.pageId) &&
                    it.dataSyncId == (activeChannel?.dataSyncId ?: captured.dataSyncId)
            } ?: detectedProfiles.first()

            val hasRealIdentity = detectedProfiles.any { it.pageId != null || it.dataSyncId != null }
            val mergedProfiles = previous?.profiles.orEmpty().filterNot { known ->
                detectedProfiles.any { it.profileId == known.profileId } ||
                    (hasRealIdentity && known.profileId.startsWith("profile:"))
            } + detectedProfiles

            val newSession = GoogleAccountSession(
                accountId = accId,
                cookie = captured.cookie,
                name = if (mode == WebSessionMode.SWITCH_CHANNEL && previous?.name?.isNotBlank() == true) {
                    previous.name
                } else {
                    name
                },
                email = email.ifBlank { previous?.email.orEmpty() },
                profiles = mergedProfiles,
                activeProfileId = resolvedActiveProfile.profileId,
                visitorData = captured.visitorData ?: previous?.visitorData,
            )

            if (matchIndex >= 0) {
                existingSessions[matchIndex] = newSession
            } else {
                existingSessions.add(0, newSession)
            }

            EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, existingSessions.sessionsToJson())
            mp.edit().remove(KEY_LAST_SYNCED_MS).apply()
            applyActiveSessionLocked(
                app = app,
                mp = mp,
                session = newSession,
                profile = resolvedActiveProfile,
                channelsOverride = channels.takeIf { it.isNotEmpty() },
                visitorOverride = captured.visitorData,
                clientVersion = captured.clientVersion,
            )
            _state.value = _state.value.copy(lastSyncError = null, lastSyncedAtMs = 0L)
        }
    }

    fun selectChannel(context: Context, channel: AccountChannel) {
        ensureInit(context)
        val targetPid = profileId(channel.pageId, channel.dataSyncId, channel.name)
        val activeAccId = activeAccountId() ?: return
        switchProfile(context, activeAccId, targetPid, suppliedChannel = channel)
    }

    fun switchProfile(
        context: Context,
        accountId: String,
        profileId: String? = null,
        suppliedChannel: AccountChannel? = null,
    ): Boolean {
        ensureInit(context)
        val app = context.applicationContext
        return synchronized(this) {
            val mp = meta(app)
            val sessions = readSessions(app).toMutableList()
            val idx = sessions.indexOfFirst { it.accountId == accountId }
            if (idx < 0) return false
            val session = sessions[idx]
            val targetPid = profileId ?: session.activeProfileId ?: session.profiles.firstOrNull()?.profileId
            val existingProfile = session.profiles.firstOrNull { it.profileId == targetPid }
            val profile = existingProfile ?: suppliedChannel?.let { ch ->
                YouTubeProfile(
                    profileId = profileId(ch.pageId, ch.dataSyncId, ch.name),
                    name = ch.name,
                    handle = ch.subtitle,
                    avatar = ch.thumbnailUrl,
                    pageId = ch.pageId,
                    dataSyncId = ch.dataSyncId,
                    authUser = mp.getString(KEY_CHANNEL_AUTH_USER, null),
                    isBrandAccount = ch.pageId != null,
                )
            } ?: session.profiles.firstOrNull()
                ?: YouTubeProfile(
                    profileId = profileId(null, null, session.name.ifBlank { "Google Account" }),
                    name = session.name.ifBlank { "Google Account" },
                    handle = session.email,
                )

            val updatedProfiles = if (existingProfile != null) {
                session.profiles
            } else {
                session.profiles + profile
            }
            val updatedSession = session.copy(
                profiles = updatedProfiles,
                activeProfileId = profile.profileId,
            )
            sessions[idx] = updatedSession
            EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, sessions.sessionsToJson())
            mp.edit().remove(KEY_LAST_SYNCED_MS).apply()

            applyActiveSessionLocked(
                app = app,
                mp = mp,
                session = updatedSession,
                profile = profile,
                visitorOverride = updatedSession.visitorData,
            )
            _state.value = _state.value.copy(isSyncing = false, lastSyncError = null, lastSyncedAtMs = 0L)
            true
        }
    }

    fun removeAccount(context: Context, accountId: String): Boolean {
        ensureInit(context)
        val app = context.applicationContext
        return synchronized(this) {
            val mp = meta(app)
            val currentActiveId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
            val remaining = readSessions(app).filterNot { it.accountId == accountId }
            if (remaining.isEmpty()) {
                signOut(app)
                return false
            }
            EncryptedAuthPrefs.putEncrypted(app, KEY_SESSIONS, remaining.sessionsToJson())
            if (currentActiveId == accountId || currentActiveId == null) {
                val fallback = remaining.first()
                val profile = fallback.profiles.firstOrNull { it.profileId == fallback.activeProfileId }
                    ?: fallback.profiles.firstOrNull()
                    ?: YouTubeProfile(
                        profileId = profileId(null, null, fallback.name.ifBlank { "Google Account" }),
                        name = fallback.name.ifBlank { "Google Account" },
                        handle = fallback.email,
                    )
                mp.edit().remove(KEY_LAST_SYNCED_MS).apply()
                applyActiveSessionLocked(
                    app = app,
                    mp = mp,
                    session = fallback,
                    profile = profile,
                    visitorOverride = fallback.visitorData,
                )
                _state.value = _state.value.copy(isSyncing = false, lastSyncError = null, lastSyncedAtMs = 0L)
            } else {
                publishState()
            }
            true
        }
    }

    fun updateSyncStatus(
        syncing: Boolean? = null,
        error: String? = null,
        syncedNow: Boolean = false,
        channels: List<AccountChannel>? = null,
        accountInfo: YouTubeAccountInfo? = null,
        expectedSessionKey: String? = null,
    ) {
        if (expectedSessionKey != null && _state.value.activeSessionKey != expectedSessionKey) return
        val ctx = appContext
        synchronized(this) {
            if (expectedSessionKey != null && _state.value.activeSessionKey != expectedSessionKey) return
            if (ctx != null) {
                val mp = meta(ctx)
                val activeAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
                val sessions = readSessions(ctx).toMutableList()
                val idx = sessions.indexOfFirst { it.accountId == activeAccId }

                if (accountInfo != null) {
                    mp.edit().apply {
                        if (accountInfo.name.isNotBlank()) putString(KEY_ACCOUNT_NAME, accountInfo.name)
                        if (accountInfo.email.isNotBlank()) putString(KEY_ACCOUNT_EMAIL, accountInfo.email)
                        if (accountInfo.handle.isNotBlank()) putString(KEY_ACCOUNT_HANDLE, accountInfo.handle)
                        if (!accountInfo.avatarUrl.isNullOrBlank()) putString(KEY_ACCOUNT_AVATAR, accountInfo.avatarUrl)
                    }.apply()
                }
                if (idx >= 0 && (accountInfo != null || !channels.isNullOrEmpty())) {
                    val cur = sessions[idx]
                    val currentActivePid = mp.getString(KEY_ACTIVE_PROFILE, cur.activeProfileId)
                    val detectedProfiles = channels.orEmpty().map { ch ->
                        YouTubeProfile(
                            profileId = profileId(ch.pageId, ch.dataSyncId, ch.name),
                            name = ch.name,
                            handle = ch.subtitle,
                            avatar = ch.thumbnailUrl,
                            pageId = ch.pageId,
                            dataSyncId = ch.dataSyncId,
                            authUser = mp.getString(KEY_CHANNEL_AUTH_USER, null),
                            isBrandAccount = ch.pageId != null,
                        )
                    }
                    val hasReal = detectedProfiles.any { it.pageId != null || it.dataSyncId != null }
                    val mergedProfiles = if (detectedProfiles.isNotEmpty()) {
                        cur.profiles.filterNot { known ->
                            detectedProfiles.any { it.profileId == known.profileId } ||
                                (hasReal && known.profileId.startsWith("profile:"))
                        } + detectedProfiles
                    } else {
                        cur.profiles.map { p ->
                            if (p.profileId == currentActivePid && accountInfo != null) {
                                p.copy(
                                    name = accountInfo.name.ifBlank { p.name },
                                    handle = accountInfo.handle.ifBlank { p.handle },
                                    avatar = accountInfo.avatarUrl ?: p.avatar,
                                )
                            } else {
                                p
                            }
                        }
                    }
                    sessions[idx] = cur.copy(
                        name = accountInfo?.name?.takeIf { it.isNotBlank() } ?: cur.name,
                        email = accountInfo?.email?.takeIf { it.isNotBlank() } ?: cur.email,
                        profiles = mergedProfiles,
                    )
                    EncryptedAuthPrefs.putEncrypted(ctx, KEY_SESSIONS, sessions.sessionsToJson())
                }
            }
            val syncedMs = if (syncedNow && ctx != null) {
                System.currentTimeMillis().also { now ->
                    meta(ctx).edit().putLong(KEY_LAST_SYNCED_MS, now).apply()
                }
            } else {
                _state.value.lastSyncedAtMs
            }
            val latestSessions = if (ctx != null) readSessions(ctx) else _state.value.accounts
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
                accounts = latestSessions,
            )
        }
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

    fun restoreClientScope(context: Context) {
        ensureInit(context)
        val app = context.applicationContext
        synchronized(this) {
            restoreToClient(app, meta(app))
        }
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

    private fun applyActiveSessionLocked(
        app: Context,
        mp: SharedPreferences,
        session: GoogleAccountSession,
        profile: YouTubeProfile,
        channelsOverride: List<AccountChannel>? = null,
        visitorOverride: String? = null,
        clientVersion: String? = null,
    ) {
        EncryptedAuthPrefs.putEncrypted(app, KEY_COOKIE, session.cookie)
        val resolvedDataSyncId = profile.dataSyncId
        val resolvedPageId = profile.pageId
        val resolvedAuthUser = profile.authUser
        val resolvedVisitor = visitorOverride ?: session.visitorData
        val resolvedAvatar = profile.avatar ?: mp.getString(KEY_ACCOUNT_AVATAR, null)
            .takeIf { mp.getString(KEY_ACTIVE_ACCOUNT, null) == session.accountId }

        mp.edit()
            .putString(KEY_ACTIVE_ACCOUNT, session.accountId)
            .putString(KEY_ACTIVE_PROFILE, profile.profileId)
            .putString(KEY_CHANNEL_PAGE_ID, resolvedPageId)
            .putString(KEY_CHANNEL_DATASYNC_ID, resolvedDataSyncId)
            .putString(KEY_CHANNEL_NAME, profile.name)
            .putString(KEY_CHANNEL_AUTH_USER, resolvedAuthUser)
            .putString(KEY_ACCOUNT_NAME, profile.name.ifBlank { session.name.ifBlank { "Google Account" } })
            .putString(KEY_ACCOUNT_EMAIL, session.email.ifBlank { profile.handle })
            .putString(KEY_ACCOUNT_HANDLE, profile.handle)
            .apply {
                if (resolvedAvatar.isNullOrBlank()) remove(KEY_ACCOUNT_AVATAR)
                else putString(KEY_ACCOUNT_AVATAR, resolvedAvatar)
                if (resolvedVisitor.isNullOrBlank()) remove(KEY_VISITOR_DATA)
                else putString(KEY_VISITOR_DATA, resolvedVisitor)
            }
            .apply()

        ArtistInnertubeClient.adoptSessionScope(
            cookie = session.cookie,
            pageId = resolvedPageId,
            dataSyncId = resolvedDataSyncId,
            authUser = resolvedAuthUser,
            visitorData = resolvedVisitor,
            clientVersion = clientVersion,
            loggedIn = true,
        )
        if (resolvedPageId != null || resolvedDataSyncId != null) {
            ArtistInnertubeClient.selectChannel(
                pageId = resolvedPageId,
                dataSyncId = resolvedDataSyncId,
                authUser = resolvedAuthUser,
            )
        }
        publishState(channels = channelsOverride)
    }

    private fun publishState(channels: List<AccountChannel>? = null) {
        val app = appContext ?: return
        val mp = meta(app)
        val sessions = readSessions(app)
        val activeAccId = mp.getString(KEY_ACTIVE_ACCOUNT, null)
        val activeSession = sessions.firstOrNull { it.accountId == activeAccId } ?: sessions.firstOrNull()
        val activeProfId = mp.getString(KEY_ACTIVE_PROFILE, activeSession?.activeProfileId)
        val cookie = activeSession?.cookie ?: EncryptedAuthPrefs.getDecrypted(app, KEY_COOKIE)
        val signedIn = hasApiSid(cookie)
        val derivedChannels = channels ?: activeSession?.profiles?.map { p ->
            AccountChannel(
                name = p.name,
                subtitle = p.handle,
                thumbnailUrl = p.avatar,
                pageId = p.pageId,
                dataSyncId = p.dataSyncId,
                activeOnWeb = p.profileId == (activeProfId ?: activeSession.activeProfileId),
            )
        }.orEmpty()
        _state.value = _state.value.copy(
            isSignedIn = signedIn,
            activeAccountId = activeSession?.accountId ?: activeAccId,
            activeProfileId = activeProfId ?: activeSession?.activeProfileId,
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
