package com.darkxvenom.itune.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Patterns

data class SharedMusicInput(val uri: Uri? = null, val text: String? = null)

object ShareIntentParser {
    private val urlRegex = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)

    fun parse(context: Context, intent: Intent): SharedMusicInput? {
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                streams?.firstOrNull() ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }

        if (uri != null) {
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            return SharedMusicInput(uri = uri)
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (sharedText.isNullOrBlank()) return null

        val url = urlRegex.find(sharedText)?.value?.trimEnd('.', ',', ')', ']', '!')
            ?: sharedText.takeIf { Patterns.WEB_URL.matcher(it.trim()).matches() }?.trim()
        return SharedMusicInput(text = url ?: sharedText)
    }
}
