package com.darkxvenom.itune.data.sources.module

import android.util.Log

internal object SourceLog {
    fun d(tag: String, message: String) { Log.d(tag, message) }
    fun i(tag: String, message: String) { Log.i(tag, message) }
    fun w(tag: String, message: String) { Log.w(tag, message) }
    fun e(tag: String, message: String, error: Throwable? = null) { Log.e(tag, message, error) }
}
