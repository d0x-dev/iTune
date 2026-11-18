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

package com.darkxvenom.itune.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
        if (id < 0) return

        when (intent.action) {
            ACTION_PAUSE_RESUME -> AudioDownloader.togglePause(context, id)
            ACTION_CANCEL -> AudioDownloader.cancel(context, id)
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    companion object {
        const val ACTION_PAUSE_RESUME = "com.darkxvenom.itune.action.DOWNLOAD_PAUSE_RESUME"
        const val ACTION_CANCEL = "com.darkxvenom.itune.action.DOWNLOAD_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "com.darkxvenom.itune.extra.DOWNLOAD_ID"
    }
}
