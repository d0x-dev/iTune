package com.darkxvenom.itune.recognition

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.darkxvenom.itune.R

class MusicRecognitionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MusicRecognitionTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val launchAction = Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }

        if (isLocked) {
            unlockAndRun(launchAction)
        } else {
            launchAction.run()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_recognition_mic)
        tile.label = getString(R.string.tile_identify_music)
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Tap to identify"
        }
        tile.updateTile()
    }

    companion object {
        const val ACTION_START_RECOGNITION = "com.darkxvenom.itune.action.START_RECOGNITION"
        const val ACTION_PLAY_RECOGNIZED = "com.darkxvenom.itune.action.PLAY_RECOGNIZED"
        const val EXTRA_SONG_TITLE = "extra_song_title"
        const val EXTRA_SONG_ARTIST = "extra_song_artist"
    }
}
