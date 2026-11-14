package com.darkxvenom.itune.ui.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.darkxvenom.itune.data.updates.AppUpdateManager
import java.io.File

/** Opens Android's package installer and resumes the install flow after the unknown-app setting. */
class UpdateInstallActivity : ComponentActivity() {
    private var apkFile: File? = null
    private val installPermissionSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (AppUpdateManager.canRequestPackageInstalls(this)) {
            launchPackageInstaller()
        } else {
            Toast.makeText(this, "Allow iTune to install updates to continue", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_APK_PATH).orEmpty()
        val expectedDirectory = File(cacheDir, "updates").canonicalFile
        val candidate = runCatching { File(path).canonicalFile }.getOrNull()
        if (candidate == null || !candidate.isFile || candidate.length() <= 0L || candidate.parentFile != expectedDirectory) {
            Toast.makeText(this, "Downloaded update APK is unavailable", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        apkFile = candidate
        if (AppUpdateManager.canRequestPackageInstalls(this)) {
            launchPackageInstaller()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            installPermissionSettings.launch(settingsIntent)
        } else {
            launchPackageInstaller()
        }
    }

    private fun launchPackageInstaller() {
        val apk = apkFile ?: return finish()
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(this, it.localizedMessage ?: "Could not open Android's installer", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_APK_PATH = "update_apk_path"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
