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

package com.darkxvenom.itune.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen player widget. All drawing and playback handling lives in [ITuneWidgetUpdater];
 * this class only forwards the system callbacks and the widget's button taps.
 */
class ITuneWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ITuneWidgetUpdater.refreshAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ITuneWidgetUpdater.refreshAll(context)
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        ITuneWidgetUpdater.refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != null && action in ITuneWidgetUpdater.CONTROL_ACTIONS) {
            // Keep the process alive while a cold start restores the session and resolves the stream.
            val pending = goAsync()
            ITuneWidgetUpdater.handleControl(context.applicationContext, action) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }
}
