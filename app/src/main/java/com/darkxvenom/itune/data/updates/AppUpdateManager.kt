package com.darkxvenom.itune.data.updates

import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import com.darkxvenom.itune.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class GithubReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val pageUrl: String,
    val downloadUrl: String,
    val hasApkAsset: Boolean,
)

object AppUpdateManager {
    private const val RELEASE_URL = "https://api.github.com/repos/d0x-dev/itune/releases/latest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _availableUpdate = MutableStateFlow<GithubReleaseInfo?>(null)
    val availableUpdate: StateFlow<GithubReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _latestRelease = MutableStateFlow<GithubReleaseInfo?>(null)
    val latestRelease: StateFlow<GithubReleaseInfo?> = _latestRelease.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    val installedVersion: String get() = BuildConfig.VERSION_NAME
    val installedVersionCode: Int get() = BuildConfig.VERSION_CODE

    fun checkOnAppOpen() {
        scope.launch {
            _isChecking.value = true
            try {
                val latest = withContext(Dispatchers.IO) { fetchLatestRelease() }
                _latestRelease.value = latest
                _availableUpdate.value = latest?.takeIf {
                    compareVersions(it.tag, BuildConfig.VERSION_NAME) > 0
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    private fun fetchLatestRelease(): GithubReleaseInfo? {
        val request = Request.Builder()
            .url(RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "iTune-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.optBoolean("draft", false)) return null
                val tag = json.optString("tag_name").trim()
                if (tag.isBlank()) return null

                val assets = json.optJSONArray("assets")
                val apkUrl = (0 until (assets?.length() ?: 0))
                    .mapNotNull { assets?.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
                    ?.takeIf(String::isNotBlank)
                val pageUrl = json.optString("html_url").ifBlank {
                    "https://github.com/d0x-dev/itune/releases/tag/$tag"
                }
                GithubReleaseInfo(
                    tag = tag,
                    name = json.optString("name").ifBlank { "iTune $tag" },
                    notes = json.optString("body").trim(),
                    publishedAt = json.optString("published_at"),
                    pageUrl = pageUrl,
                    downloadUrl = apkUrl ?: pageUrl,
                    hasApkAsset = apkUrl != null,
                )
            }
        }.getOrNull()
    }

    private fun compareVersions(first: String, second: String): Int {
        val left = numericVersionParts(first)
        val right = numericVersionParts(second)
        if (left.isEmpty() || right.isEmpty()) return 0
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun numericVersionParts(version: String): List<Int> {
        val numbers = Regex("\\d+(?:\\.\\d+)*").find(version)?.value ?: return emptyList()
        return numbers.split('.').mapNotNull(String::toIntOrNull)
    }

    fun openRelease(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun downloadApk(context: Context, release: GithubReleaseInfo) {
        if (!release.hasApkAsset) {
            openRelease(context, release.pageUrl)
            return
        }
        runCatching {
            val fileVersion = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("iTune update ${release.tag}")
                .setDescription("Downloading iTune APK")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "iTune-$fileVersion.apk",
                )
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        }
    }

    fun formattedPublishedDate(raw: String): String {
        if (raw.isBlank()) return "Not provided"
        return runCatching {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date: Date = input.parse(raw) ?: return "Not provided"
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }.getOrDefault("Not provided")
    }
}
