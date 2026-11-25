/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.ui.spotify


import androidx.compose.ui.res.stringResource
import com.darkxvenom.itune.R
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPOTIFY_LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

// Firefox Android mobile UA: lets both accounts.spotify.com and "Continue with Google"
// (accounts.google.com OAuth) complete smoothly without triggering Google's WebView block.
private const val SPOTIFY_BROWSER_UA =
    "Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0"

private val SPOTIFY_COOKIE_URLS = listOf(
    "https://open.spotify.com",
    "https://accounts.spotify.com",
    "https://www.spotify.com",
    "https://spotify.com",
)

private val SpotifyAccent = Color(0xFF1DB954)

/**
 * Signs the login WebView out of Spotify without touching Google/YouTube cookies.
 */
fun clearSpotifyWebSession() {
    runCatching {
        val cookies = CookieManager.getInstance()
        for (url in SPOTIFY_COOKIE_URLS) {
            val names = cookies.getCookie(url)?.split(";")
                ?.map { it.substringBefore("=").trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            for (name in names) {
                val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                cookies.setCookie(url, expired)
                cookies.setCookie(url, "$expired; Domain=.spotify.com")
            }
        }
        cookies.flush()
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private fun readSpotifySpdcCookie(): String? = runCatching {
    val cm = CookieManager.getInstance()
    cm.flush()
    for (url in SPOTIFY_COOKIE_URLS) {
        val raw = cm.getCookie(url) ?: continue
        val token = raw.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
        if (token != null) return@runCatching token
    }
    null
}.getOrNull()

/**
 * Full-screen in-app Spotify sign-in browser matching GoogleLoginScreen's architecture.
 * Supports Email/Password as well as "Continue with Google" OAuth and captures `sp_dc`
 * cleanly as soon as login finishes.
 *
 * Host directly as a full-screen overlay outside `hazeSource`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    onConnected: (String) -> Unit,
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

    fun finishCapture(manualTrigger: Boolean) {
        if (isCapturing || (capturedOnce && !manualTrigger)) return
        val token = readSpotifySpdcCookie()
        if (token.isNullOrBlank()) {
            if (manualTrigger) {
                Toast.makeText(context, context.getString(R.string.toast_complete_spotify_sign_in_first), Toast.LENGTH_SHORT).show()
            }
            return
        }
        capturedOnce = true
        isCapturing = true
        scope.launch {
            delay(250L)
            onConnected(token)
            isCapturing = false
        }
    }

    fun inspectSession(url: String?) {
        val token = readSpotifySpdcCookie()
        val hasToken = !token.isNullOrBlank()
        canConfirm = hasToken
        val landedOnWebPlayer = url?.startsWith("https://open.spotify.com") == true
        if (hasToken && landedOnWebPlayer && !capturedOnce && !isCapturing) {
            scope.launch {
                delay(500L)
                if (!capturedOnce && !isCapturing) {
                    finishCapture(manualTrigger = false)
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
        // Apple Dark Liquid-Glass Top Bar (same design as GoogleLoginScreen)
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
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = stringResource(R.string.ui_continue_with_spotify),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isCapturing) {
                            "Connecting Spotify account..."
                        } else {
                            "Sign in to enable Spotify Canvas & playlists"
                        },
                        color = AppleTextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isCapturing) {
                    CircularProgressIndicator(
                        color = SpotifyAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 10.dp).size(20.dp),
                    )
                } else if (canConfirm) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SpotifyAccent)
                            .clickable { finishCapture(manualTrigger = true) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_confirm),
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (pageLoading || isCapturing) {
                LinearProgressIndicator(
                    color = SpotifyAccent,
                    trackColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    clearSpotifyWebSession()
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        clearCache(true)
                        clearHistory()
                        clearFormData()
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            @Suppress("DEPRECATION")
                            databaseEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            userAgentString = SPOTIFY_BROWSER_UA
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        setBackgroundColor(0xFF121212.toInt())
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        val parentWebView = this
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean {
                                // Keep OAuth popups (Google / Facebook / Apple) inside this same WebView
                                val popup = WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            val popupUrl = request?.url?.toString()
                                            if (!popupUrl.isNullOrBlank() &&
                                                (popupUrl.startsWith("https://") || popupUrl.startsWith("http://"))
                                            ) {
                                                parentWebView.loadUrl(popupUrl)
                                            }
                                            v?.destroy()
                                            return true
                                        }
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase() ?: return false
                                // Prevent spotify:// or intent:// links from jumping out to the Spotify app
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                pageLoading = true
                                inspectSession(url)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                inspectSession(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                pageLoading = false
                                inspectSession(url)
                            }
                        }

                        webView = this
                        loadUrl(SPOTIFY_LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
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
                            color = SpotifyAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.ui_connecting_spotify),
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ui_enabling_spotify_canvas_playlists),
                            color = AppleTextSecondary,
                            fontSize = 12.5.sp,
                        )
                    }
                }
            }
        }
    }
}
