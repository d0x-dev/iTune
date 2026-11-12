package com.darkxvenom.itune.recognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Transparent invisible trampoline activity invoked by Quick Settings tile.
 * Verifies permissions and launches the floating recognition overlay over any app.
 */
class MusicRecognitionTrampolineActivity : ComponentActivity() {

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkPermissionsAndLaunch()
        } else {
            Toast.makeText(this, "Microphone permission is needed to identify music", Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
        }
    }

    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkPermissionsAndLaunch()
        } else {
            Toast.makeText(this, "Display over other apps permission is required for the recognition popup", Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        checkPermissionsAndLaunch()
    }

    private fun checkPermissionsAndLaunch() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermissionLauncher.launch(intent)
            return
        }

        // Both permissions granted: launch floating bottom recognition overlay
        MusicRecognitionOverlayManager.show(applicationContext)
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}
