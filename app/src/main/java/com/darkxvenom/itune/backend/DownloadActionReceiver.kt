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

    companion object {
        const val ACTION_PAUSE_RESUME = "com.darkxvenom.itune.action.DOWNLOAD_PAUSE_RESUME"
        const val ACTION_CANCEL = "com.darkxvenom.itune.action.DOWNLOAD_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "com.darkxvenom.itune.extra.DOWNLOAD_ID"
    }
}
