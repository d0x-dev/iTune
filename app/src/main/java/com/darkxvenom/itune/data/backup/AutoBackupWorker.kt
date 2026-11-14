package com.darkxvenom.itune.data.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Creates the user's daily encrypted backup in Downloads/itune/backups. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val context = applicationContext
        val prefs = AutoBackupScheduler.settings(context)
        if (!prefs.getBoolean(AutoBackupScheduler.KEY_ENABLED, false)) return Result.success()
        val password = EncryptedAuthPrefs.getDecrypted(context, AutoBackupScheduler.PASSWORD_KEY)
            ?: return Result.failure()
        val bytes = BackupRestoreManager.createEncryptedBackup(
            context,
            setOf(BackupRestoreManager.CATEGORY_ALL),
            password.toCharArray(),
        )
        val filename = "iTune-${LocalDate.now()}.itune"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/itune/backups")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the backup in Downloads/itune/backups")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("Could not write the automatic backup")
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            } catch (failure: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw failure
            }
        } else {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) error("Storage permission is required for automatic backups on this Android version")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "itune/backups")
            check(directory.exists() || directory.mkdirs()) { "Could not create Downloads/itune/backups" }
            File(directory, filename).writeBytes(bytes)
        }
        prefs.edit().putLong(AutoBackupScheduler.KEY_LAST_SUCCESS, System.currentTimeMillis()).apply()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

object AutoBackupScheduler {
    const val PASSWORD_KEY = "itune_auto_backup_password"
    const val KEY_ENABLED = "enabled"
    const val KEY_LAST_SUCCESS = "last_success_millis"
    private const val PREFS_NAME = "itune_auto_backup_settings"
    private const val WORK_NAME = "itune_daily_encrypted_backup"

    fun settings(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun configure(context: Context, enabled: Boolean, password: String): Boolean {
        val app = context.applicationContext
        val preferences = settings(app)
        if (enabled) {
            if (password.length < 8 || !EncryptedAuthPrefs.putEncryptedStrict(app, PASSWORD_KEY, password)) return false
            preferences.edit().putBoolean(KEY_ENABLED, true).apply()
            schedule(app)
        } else {
            preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            EncryptedAuthPrefs.putEncrypted(app, PASSWORD_KEY, null)
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        }
        return true
    }

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val preferences = settings(app)
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        val hasPassword = EncryptedAuthPrefs.getDecrypted(app, PASSWORD_KEY) != null
        if (enabled && hasPassword) {
            schedule(app)
        } else {
            if (enabled && !hasPassword) preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        }
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
