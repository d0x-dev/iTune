package com.darkxvenom.itune.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Device-local opt-in; only sanitized entries enter the in-memory ring. */
internal object LiveLogs : Application.ActivityLifecycleCallbacks {
    private const val LIMIT = 500
    private const val CHAR_LIMIT = 96_000
    private var heldChars = 0
    private val entries = ArrayDeque<String>()
    private val activities = mutableMapOf<Activity, ComposeView?>()
    private var generation = 0L
    private var reader: Process? = null
    private var worker: Thread? = null
    private const val DIRECT_TAG = "iTuneLive"
    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile var enabled = false
        private set
    private var revision = 0L

    private fun consent(context: Context) = File(context.noBackupFilesDir, "real-time-logs")

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        if (CrashReports.isEnabled() && consent(app).exists()) setEnabled(app, true)
    }

    fun setEnabled(context: Context, requested: Boolean) {
        val active = requested && CrashReports.isEnabled()
        synchronized(this) {
            if (!active) consent(context).delete()
            if (active == enabled) return
            if (active) consent(context).createNewFile()
            enabled = active
            generation++
            entries.clear()
            heldChars = 0
            revision++
            worker?.interrupt()
            worker = null
            reader?.destroy()
            reader = null
            if (active) {
                val epoch = generation
                event("LiveLogs", "Capture enabled")
                worker = Thread({ followLogcat(epoch) }, "iTune-live-logs").apply {
                    isDaemon = true
                    start()
                }
            }
        }
        activities.keys.toList().forEach(::updateOverlay)
    }

    fun event(area: String, detail: String) = write(Log.DEBUG, area, detail)

    fun failure(area: String, cause: Throwable) {
        if (enabled) write(Log.ERROR, area, cause.stackTraceToString())
    }

    private fun write(priority: Int, area: String, message: String) {
        if (!enabled) return
        val safe = DiagnosticText.mask(message)
        synchronized(this) {
            if (!enabled) return
            val level = if (priority == Log.ERROR) "E" else "D"
            val pid = android.os.Process.myPid()
            val tid = android.os.Process.myTid()
            // Store our own instrumentation independently of the OS reader. Keep the
            // echo under a dedicated tag so logcat never inserts it a second time.
            val prefix = String.format(Locale.US, "%s %5d %5d %s %s: ",
                timestamp.format(Date()), pid, tid, level, DIRECT_TAG)
            for (line in safe.lineSequence()) {
                val record = "$area: $line"
                appendSafe(prefix + record)
                record.chunked(1000).forEach { Log.println(priority, DIRECT_TAG, it) }
            }
        }
    }

    private fun appendSafe(entry: String) {
        val bounded = entry.take(CHAR_LIMIT)
        while (entries.isNotEmpty() && (entries.size >= LIMIT || heldChars + bounded.length > CHAR_LIMIT)) {
            heldChars -= entries.removeFirst().length
        }
        entries.addLast(bounded)
        heldChars += bounded.length
        revision++
    }

    @Synchronized fun snapshot(after: Long): Pair<Long, List<String>>? =
        if (revision == after) null else revision to entries.toList().asReversed()

    @Synchronized private fun current(epoch: Long) = enabled && generation == epoch

    private fun followLogcat(epoch: Long) {
        val pid = android.os.Process.myPid()
        val header = Regex("""^\S+\s+\S+\s+$pid\s+\d+\s+[VDIWEFA]\s+([^:]+):""")
        var mainOnly = false
        var retryMs = 1000L
        while (current(epoch)) {
            var child: Process? = null
            var errorLine: String? = null
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                // "all" can include privileged buffers on vendor builds. Android's
                // default buffers suffice; fall back to just main and software PID matching.
                val args = mutableListOf("/system/bin/logcat", "-v", "threadtime", "-T", "1")
                if (mainOnly) args.addAll(listOf("-b", "main"))
                else if (Build.VERSION.SDK_INT >= 24) args.add("--pid=$pid")
                args.add("*:V")
                val process = ProcessBuilder(args).redirectErrorStream(true).start()
                child = process
                synchronized(this) {
                    if (!current(epoch)) return
                    reader = process
                }
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val match = header.find(line)
                        if (match == null) {
                            if (line.isNotBlank() && !line.startsWith("---------") &&
                                !line.first().isDigit()) errorLine = DiagnosticText.mask(line).take(512)
                            continue
                        }
                        if (match.groupValues[1].trim() == DIRECT_TAG) continue
                        val safe = DiagnosticText.mask(line)
                        synchronized(this) {
                            if (!current(epoch)) return
                            appendSafe(safe)
                        }
                    }
                }
                val code = process.waitFor()
                errorLine = "exit $code" + (errorLine?.let { " ($it)" } ?: "")
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                errorLine = DiagnosticText.mask(e.toString())
            } finally {
                child?.destroy()
                synchronized(this) { if (reader === child) reader = null }
            }
            synchronized(this) {
                if (!current(epoch)) return
                if (android.os.SystemClock.elapsedRealtime() - started > 60_000L) retryMs = 1000L
                appendSafe("logcat: $errorLine; retrying main buffer in ${retryMs / 1000}s")
            }
            mainOnly = true
            try {
                Thread.sleep(retryMs)
            } catch (_: InterruptedException) {
                return
            }
            retryMs = (retryMs * 2).coerceAtMost(30_000L)
        }
    }

    private fun updateOverlay(activity: Activity) {
        val previous = activities[activity]
        if (enabled && previous == null && activity is ComponentActivity && !activity.isFinishing) {
            val overlay = ComposeView(activity).apply {
                id = com.darkxvenom.itune.R.id.live_log_overlay
                setContent { LiveLogBubble() }
            }
            activity.addContentView(overlay, ViewGroup.LayoutParams(-1, -1))
            activities[activity] = overlay
        } else if (!enabled && previous != null) {
            (previous.parent as? ViewGroup)?.removeView(previous)
            activities[activity] = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !in activities) activities[activity] = null
        updateOverlay(activity)
        event("App", "Resumed ${activity.componentName.shortClassName}")
    }
    override fun onActivityPaused(activity: Activity) { event("App", "Paused ${activity.componentName.shortClassName}") }
    override fun onActivityDestroyed(activity: Activity) { activities.remove(activity) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
}
