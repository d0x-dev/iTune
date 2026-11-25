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

package com.darkxvenom.itune.recognition


import com.darkxvenom.itune.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.darkxvenom.itune.data.library.AppLocaleManager

/**
 * Transparent invisible trampoline activity invoked by Quick Settings tile.
 * Verifies permissions and launches the floating recognition overlay over any app.
 */
class MusicRecognitionTrampolineActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkPermissionsAndLaunch()
        } else {
            Toast.makeText(this, this.getString(R.string.toast_microphone_permission_is_needed_to_identify_music), Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
        }
    }

    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (hasOverlay) {
            checkPermissionsAndLaunch()
        } else {
            Toast.makeText(this, this.getString(R.string.toast_display_over_other_apps_permission_is_required_for), Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
