package com.darkxvenom.itune.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.auth.BrowserSession
import com.darkxvenom.itune.data.auth.CapturedSession
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.YouTubeAccountParsers
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.auth.delegatedSessionId
import com.darkxvenom.itune.data.auth.hasApiSid
import com.darkxvenom.itune.data.auth.looksSignedInCookies
import com.darkxvenom.itune.data.auth.normalizeDataSyncId
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val MUSIC_ORIGIN = "https://music.youtube.com"
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&prompt=select_account&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val YTCFG_PROBE = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var get = function (key) {
      var value = window.ytcfg.get(key);
      return (value === undefined || value === null || value === '') ? null : String(value);
    };
    return {
      loggedIn: String(!!window.ytcfg.get('LOGGED_IN')),
      pageId: get('DELEGATED_SESSION_ID'),
      dataSyncId: get('DATASYNC_ID'),
      authUser: get('SESSION_INDEX'),
      visitorData: get('VISITOR_DATA'),
      clientVersion: get('INNERTUBE_CLIENT_VERSION')
    };
  } catch (e) {
    return null;
  }
})()
"""

/**
 * In-app Google / YouTube Music sign-in & channel picker WebView using a
 * `ytcfg` probe and partitioned SAPISID cookie capture flow.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleLoginScreen(
    mode: WebSessionMode = WebSessionMode.SIGN_IN,
    initialCookie: String? = null,
    returnImmediatelyOnCapture: Boolean = false,
    onSignedIn: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var canConfirm by remember { mutableStateOf(false) }
    var capturedOnce by remember { mutableStateOf(false) }

    fun finishCapture(view: WebView, manualTrigger: Boolean) {
        if (isCapturing || (capturedOnce && !manualTrigger)) return
        runCatching { CookieManager.getInstance().flush() }
        val mergedCookies = BrowserSession.readMergedYouTubeCookies()
            ?: CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
        val cookieReady = if (manualTrigger) {
            hasApiSid(mergedCookies)
        } else {
            looksSignedInCookies(mergedCookies)
        }
        if (mergedCookies == null || !cookieReady) {
            if (manualTrigger) {
                Toast.makeText(context, "Complete Google sign-in first", Toast.LENGTH_SHORT).show()
            }
            return
        }
        view.evaluateJavascript(YTCFG_PROBE) { raw ->
            val obj = raw?.takeIf { it != "null" }?.let { runCatching { JSONObject(it) }.getOrNull() }
            val loggedIn = obj?.optString("loggedIn") == "true"
            if (obj == null || !loggedIn) {
                if (manualTrigger) {
                    Toast.makeText(context, "Waiting for YouTube Music session...", Toast.LENGTH_SHORT).show()
                }
                return@evaluateJavascript
            }
            val rawDataSyncId = obj.optString("dataSyncId").takeIf { it.isNotBlank() && it != "null" }
            val pageId = obj.optString("pageId").takeIf { it.isNotBlank() && it != "null" }
                ?: delegatedSessionId(rawDataSyncId)
            // DATASYNC_ID is USER_SESSION_ID (or DELEGATED_SESSION_ID||USER_SESSION_ID).
            // Keep it independent from DELEGATED_SESSION_ID, which is X-Goog-PageId.
            val dataSyncId = normalizeDataSyncId(rawDataSyncId)
            val initialCaptured = CapturedSession(
                cookie = mergedCookies,
                dataSyncId = dataSyncId,
                pageId = pageId,
                authUser = obj.optString("authUser").takeIf { it.isNotBlank() && it != "null" },
                visitorData = obj.optString("visitorData").takeIf { it.isNotBlank() && it != "null" },
                clientVersion = obj.optString("clientVersion").takeIf { it.isNotBlank() && it != "null" },
                loggedIn = true,
            )
            capturedOnce = true
            val appCtx = context.applicationContext
            isCapturing = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        verifyAndPersistCapturedSession(appCtx, initialCaptured, mode)
                    }
                    YouTubeLibrarySyncManager.launchFullSync(appCtx)
                    onSignedIn()
                } catch (e: Throwable) {
                    capturedOnce = false
                    Toast.makeText(context, e.message ?: "Sign-in verification failed", Toast.LENGTH_LONG).show()
                } finally {
                    isCapturing = false
                }
            }
        }
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack() && !isCapturing) {
            wv.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
            .navigationBarsPadding(),
    ) {
        // Apple Dark Liquid-Glass Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141418))
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = if (mode == WebSessionMode.SWITCH_CHANNEL) "Switch Channel" else "Continue with Google",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isCapturing) {
                            "Syncing YouTube Music account..."
                        } else if (mode == WebSessionMode.SWITCH_CHANNEL) {
                            "Pick your channel on YouTube Music, then tap Confirm"
                        } else {
                            "Sign in to sync likes, playlists, artists & history"
                        },
                        color = AppleTextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isCapturing) {
                    CircularProgressIndicator(
                        color = Color(0xFFFA2D48),
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 10.dp).size(20.dp),
                    )
                } else if (canConfirm) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFA2D48))
                            .clickable {
                                webView?.let { finishCapture(it, manualTrigger = true) }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Confirm",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (pageLoading || isCapturing) {
                LinearProgressIndicator(
                    color = Color(0xFFFA2D48),
                    trackColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    if (mode == WebSessionMode.SIGN_IN) {
                        BrowserSession.clearGoogleCookies()
                    } else if (!initialCookie.isNullOrBlank()) {
                        BrowserSession.installGoogleCookies(initialCookie)
                    }
                    WebView(ctx).apply {
                        if (mode == WebSessionMode.SIGN_IN) {
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // Strip WebView marker so Google treats the OAuth session as a standard browser.
                            userAgentString = userAgentString.replace("; wv", "")
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                pageLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                pageLoading = false
                                val onMusic = url?.startsWith(MUSIC_ORIGIN) == true
                                if (onMusic && view != null) {
                                    runCatching { CookieManager.getInstance().flush() }
                                    val cookie = BrowserSession.readMergedYouTubeCookies()
                                    val hasSid = hasApiSid(cookie)
                                    val fullLogin = looksSignedInCookies(cookie)
                                    canConfirm = hasSid
                                    if (fullLogin && mode == WebSessionMode.SIGN_IN && !capturedOnce) {
                                        scope.launch {
                                            delay(if (returnImmediatelyOnCapture) 1600L else 850L)
                                            if (!capturedOnce && !isCapturing) {
                                                finishCapture(view, manualTrigger = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        webView = this
                        loadUrl(if (mode == WebSessionMode.SIGN_IN) LOGIN_URL else "$MUSIC_ORIGIN/")
                    }
                },
            )

            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1C1C22))
                            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFA2D48),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Connecting YouTube Music...",
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Loading artists, playlists & history",
                            color = AppleTextSecondary,
                            fontSize = 12.5.sp,
                        )
                    }
                }
            }
        }
    }
}

internal suspend fun verifyAndPersistCapturedSession(
    context: Context,
    initialCaptured: CapturedSession,
    mode: WebSessionMode,
) {
    val previousSessionKey = GoogleAuthStore.activeSessionKey()
    ArtistInnertubeClient.adoptSessionScope(
        cookie = initialCaptured.cookie,
        pageId = initialCaptured.pageId,
        dataSyncId = initialCaptured.dataSyncId,
        authUser = initialCaptured.authUser,
        visitorData = initialCaptured.visitorData,
        clientVersion = initialCaptured.clientVersion,
        loggedIn = true,
    )
    // Do not mark a WebView cookie as a working YouTube account until private
    // library requests succeed with the captured browser session. If this is a
    // failed attempt to add another account, restore the previously active client.
    try {
        ArtistInnertubeClient.verifyAuthenticatedLibraryAccess(persistScope = false)
    } catch (e: CancellationException) {
        GoogleAuthStore.restoreClientScope(context)
        throw e
    } catch (e: Throwable) {
        GoogleAuthStore.restoreClientScope(context)
        throw e
    }
    val menuResult = runCatching { ArtistInnertubeClient.accountMenu() }
    val info = menuResult.getOrNull()?.let { YouTubeAccountParsers.parseAccountInfo(it) }
    val listResult = runCatching { ArtistInnertubeClient.accountsList() }
    val channels = run {
        val list = listResult.getOrNull()
            ?.let { YouTubeAccountParsers.parseAccountChannels(it) }
            .orEmpty()
        list.ifEmpty {
            runCatching {
                YouTubeAccountParsers.parseAccountChannels(ArtistInnertubeClient.accountSwitcher())
            }.getOrDefault(emptyList())
        }
    }
    // account_menu/accounts_list are optional account-profile metadata APIs.
    // Private library access above is the sign-in verification signal.

    val resolvedCaptured = initialCaptured.copy(
        cookie = ArtistInnertubeClient.cookie ?: initialCaptured.cookie,
        dataSyncId = ArtistInnertubeClient.activeDataSyncId(),
        pageId = ArtistInnertubeClient.activePageId(),
    )
    GoogleAuthStore.saveCapturedSession(
        context = context,
        captured = resolvedCaptured,
        accountInfo = info,
        channels = channels,
        mode = mode,
    )
    val nextSessionKey = GoogleAuthStore.activeSessionKey()
    if (previousSessionKey != nextSessionKey) {
        YouTubeLibrarySyncManager.onAccountSessionChanged(
            context = context,
            previousSessionKey = previousSessionKey,
            newSessionKey = nextSessionKey,
            launchSync = false,
        )
    }
}
