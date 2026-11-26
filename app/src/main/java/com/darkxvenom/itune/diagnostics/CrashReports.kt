package com.darkxvenom.itune.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import android.util.Base64
import androidx.annotation.RequiresApi
import com.darkxvenom.itune.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.util.Date

class ITuneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveLogs.install(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReports.install(this)
    }
}

internal object CrashReports {
    // Device-local opt-in. Neither the setting nor the pending trace travels in a backup.
    private fun consent(context: Context) = File(context.noBackupFilesDir, "crash-reports-since")
    private fun pending(context: Context) = AtomicFile(File(context.noBackupFilesDir, "last-crash.txt"))
    private fun exports(context: Context) = File(context.cacheDir, "crash-reports")
    private val processStarted = System.currentTimeMillis()
    @Volatile private var enabled = false

    fun isEnabled() = enabled

    @Synchronized fun setEnabled(context: Context, value: Boolean) {
        if (value == enabled) return
        if (value) {
            consent(context).writeText(System.currentTimeMillis().toString())
            enabled = true
        } else {
            enabled = false
            consent(context).delete()
            pending(context).delete()
            exports(context).listFiles()?.forEach { it.delete() }
        }
    }

    fun install(app: Application) {
        enabled = consent(app).isFile
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try {
                if (enabled) save(app, thread, failure)
            } finally {
                previous.uncaughtException(thread, failure)
            }
        }
    }

    @Synchronized private fun save(context: Context, thread: Thread, failure: Throwable) {
        if (!enabled) return
        val file = pending(context)
        val stream = file.startWrite()
        try {
            val writer = PrintWriter(stream.bufferedWriter())
            writer.println("iTune " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            writer.println("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") • " + Build.MANUFACTURER + " " + Build.MODEL)
            writer.println(Date().toString() + " • PID " + Process.myPid() + " • " + thread.name)
            failure.printStackTrace(writer)
            check(!writer.checkError())
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
        }
    }

    class Report(val read: () -> String)

    fun previousCrash(context: Context): Report? {
        if (!enabled) return null
        val app = context.applicationContext
        val file = pending(app)
        if (runCatching { file.openRead().use { } }.isSuccess) {
            return Report { file.openRead().bufferedReader().use { it.readText() } }
        }
        if (Build.VERSION.SDK_INT < 30) return null
        val since = runCatching { consent(app).readText().toLong() }.getOrNull() ?: return null
        return runCatching { SystemExits.previous(app, since) }.getOrNull()
    }

    @RequiresApi(30)
    private object SystemExits {
        fun previous(context: Context, since: Long): Report? {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
                .firstOrNull { it.processName == context.packageName && it.timestamp in (since + 1)..processStarted &&
                    it.reason in listOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR) }
                ?: return null
            return Report {
                buildString {
                    appendLine("Android process exit • " + Date(exit.timestamp))
                    appendLine(exit.toString())
                    runCatching {
                        exit.traceInputStream?.use { trace ->
                            if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                                appendLine("Native tombstone (Android protobuf, Base64)")
                                append(Base64.encodeToString(trace.readBytes(), Base64.NO_WRAP))
                            } else {
                                append(trace.bufferedReader().readText())
                            }
                        }
                    }
                }
            }
        }
    }

    fun dismiss(context: Context) {
        if (enabled) consent(context).writeText(maxOf(consent(context).readText().toLong(), processStarted).toString())
        pending(context).delete()
    }

    fun export(context: Context, text: String): android.net.Uri {
        val dir = exports(context).apply { mkdirs() }
        val file = File(dir, "itune-crash-" + processStarted + ".txt")
        file.writeText(text)
        return androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
