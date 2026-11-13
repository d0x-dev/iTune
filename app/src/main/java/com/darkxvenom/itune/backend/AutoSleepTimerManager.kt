package com.darkxvenom.itune.backend

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.darkxvenom.itune.data.library.SettingsStore
import java.util.Calendar

/**
 * Manages the permanent, recurring Auto Sleep Timer schedule.
 * When enabled, automatically pauses playback if current time is within
 * the user's scheduled sleep window on selected days.
 */
object AutoSleepTimerManager {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastPausedNoticeMs = 0L

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        scheduleNextCheck(context.applicationContext)
    }

    private fun scheduleNextCheck(appContext: Context) {
        handler.postDelayed({
            checkAndEnforce(appContext)
            scheduleNextCheck(appContext)
        }, 15_000L)
    }

    fun isSleepWindowActive(): Boolean {
        val state = SettingsStore.state.value
        if (!state.autoSleepTimerEnabled) return false

        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val dayName = when (dayOfWeek) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> ""
        }

        val days = state.autoSleepDays
        val isDaySelected = days.contains("All") || days.contains(dayName)
        if (!isDaySelected) return false

        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMin = state.autoSleepStartHour * 60 + state.autoSleepStartMinute
        val endMin = state.autoSleepEndHour * 60 + state.autoSleepEndMinute

        return if (startMin <= endMin) {
            nowMin in startMin until endMin
        } else {
            // Spans midnight, e.g. 23:00 (11 PM) to 07:00 (7 AM)
            nowMin >= startMin || nowMin < endMin
        }
    }

    fun checkAndEnforce(context: Context) {
        if (isSleepWindowActive() && MusicPlayerState.isPlaying) {
            MusicPlayerState.pause()
            val now = System.currentTimeMillis()
            if (now - lastPausedNoticeMs > 60_000L) {
                lastPausedNoticeMs = now
                Toast.makeText(
                    context,
                    "Auto Sleep Timer active · Playback paused",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
