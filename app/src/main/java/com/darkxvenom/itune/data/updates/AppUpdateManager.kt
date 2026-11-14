package com.darkxvenom.itune.data.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.darkxvenom.itune.BuildConfig
import com.darkxvenom.itune.ui.update.UpdateInstallActivity
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
import java.io.File
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

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateDownloadState
    data class Completed(val apkFile: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

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

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

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
        val intent = Intent(context, AppUpdateDownloadService::class.java).apply {
            action = AppUpdateDownloadService.ACTION_DOWNLOAD
            putExtra(AppUpdateDownloadService.EXTRA_URL, release.downloadUrl)
            putExtra(AppUpdateDownloadService.EXTRA_TAG, release.tag)
        }
        // The request may spend a few seconds following GitHub's asset redirect.
        // Keep the UI indeterminate until the first bytes arrive instead of implying 0%.
        _downloadState.value = UpdateDownloadState.Downloading(-1, 0L, -1L)
        runCatching {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }.onFailure {
            _downloadState.value = UpdateDownloadState.Failed(it.localizedMessage ?: "Could not start the APK download")
        }
    }

    internal fun publishDownloadState(state: UpdateDownloadState) {
        _downloadState.value = state
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrDefault(false)

    fun launchInstallerFlow(context: Context, apkFile: File) {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            _downloadState.value = UpdateDownloadState.Failed("The downloaded APK is missing or empty")
            return
        }
        runCatching {
            context.startActivity(Intent(context, UpdateInstallActivity::class.java).apply {
                putExtra(UpdateInstallActivity.EXTRA_APK_PATH, apkFile.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            _downloadState.value = UpdateDownloadState.Failed(it.localizedMessage ?: "Could not open the installer")
        }
    }

    internal fun createApkUri(context: Context, apkFile: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)

    internal fun isSignedLikeInstalledApp(context: Context, apk: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val downloaded = runCatching { context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) }
            .getOrNull() ?: return false
        if (downloaded.packageName != context.packageName) return false
        if (BuildConfig.DEBUG) return true

        @Suppress("DEPRECATION")
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, flags) }.getOrNull()
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installedSigners = installed.signingInfo?.apkContentsSigners ?: return false
            val downloadedSigners = downloaded.signingInfo?.apkContentsSigners ?: return false
            installedSigners.any { current -> downloadedSigners.any { candidate -> current.toByteArray().contentEquals(candidate.toByteArray()) } }
        } else {
            @Suppress("DEPRECATION")
            val installedSigners = installed.signatures ?: return false
            @Suppress("DEPRECATION")
            val downloadedSigners = downloaded.signatures ?: return false
            installedSigners.any { current -> downloadedSigners.any { candidate -> current.toByteArray().contentEquals(candidate.toByteArray()) } }
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
