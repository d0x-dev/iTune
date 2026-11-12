package com.darkxvenom.itune.data.timetravel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TimeTravelRepository {

    private const val TAG = "TimeTravelRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var dbHelper: TimeTravelDatabaseHelper? = null

    private val _selectedPeriod = MutableStateFlow(ListeningPeriod.ALL_TIME)
    val selectedPeriod: StateFlow<ListeningPeriod> = _selectedPeriod.asStateFlow()

    private val _stats = MutableStateFlow(TimeTravelStats(period = ListeningPeriod.ALL_TIME))
    val stats: StateFlow<TimeTravelStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun ensureInit(context: Context) {
        if (dbHelper != null) return
        synchronized(this) {
            if (dbHelper != null) return
            val helper = TimeTravelDatabaseHelper.getInstance(context)
            dbHelper = helper

            scope.launch {
                helper.bootstrapExistingDataIfEmpty(context)
                refresh()
            }
        }
    }

    fun selectPeriod(period: ListeningPeriod) {
        if (_selectedPeriod.value == period) return
        _selectedPeriod.value = period
        refresh()
    }

    fun refresh() {
        val helper = dbHelper ?: return
        scope.launch {
            _isLoading.value = true
            try {
                val currentPeriod = _selectedPeriod.value
                val loadedStats = helper.getStats(currentPeriod)
                _stats.value = loadedStats
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Time Travel stats", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun recordPlayback(record: PlaybackRecord) {
        val helper = dbHelper ?: return
        scope.launch {
            try {
                helper.insertOrUpdatePlaybackSession(record)
                // If the record falls within the currently selected period, refresh stats
                val startTime = _selectedPeriod.value.getStartTimeMs()
                if (record.timestamp >= startTime) {
                    refresh()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record playback in TimeTravel", e)
            }
        }
    }
}
