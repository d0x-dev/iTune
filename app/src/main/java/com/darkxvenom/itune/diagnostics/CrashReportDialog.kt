package com.darkxvenom.itune.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.darkxvenom.itune.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CrashReportDialog(report: CrashReports.Report, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.crash_report_title)
    val unavailable = stringResource(R.string.crash_report_unavailable)
    val body by produceState<String?>(null, report) {
        value = withContext(Dispatchers.IO) { runCatching { report.read() }.getOrNull() ?: unavailable }
    }
    var actionError by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF1C1C1E),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                val text = body
                if (text == null) {
                    CircularProgressIndicator(Modifier.padding(20.dp))
                } else {
                    val chunks = remember(text) { text.lineSequence().flatMap { line ->
                        if (line.isEmpty()) sequenceOf("") else line.chunked(2048).asSequence()
                    }.toList() }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(16.dp)) {
                        items(chunks) { line ->
                            Text(line, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
                if (actionError) Text(stringResource(R.string.crash_action_failed), color = Color.White)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(enabled = text != null, onClick = {
                        if (text != null) scope.launch {
                            actionError = runCatching {
                                val clip = if (text.length < 64000) ClipData.newPlainText(title, text)
                                else ClipData.newUri(context.contentResolver, title, withContext(Dispatchers.IO) { CrashReports.export(context, text) })
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            }.isFailure
                        }
                    }) { Text(stringResource(R.string.ui_copy), color = Color(0xFF64A8FF)) }
                    TextButton(enabled = text != null, onClick = {
                        if (text != null) scope.launch {
                            actionError = runCatching {
                                val uri = withContext(Dispatchers.IO) { CrashReports.export(context, text) }
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                send.clipData = ClipData.newRawUri(title, uri)
                                context.startActivity(Intent.createChooser(send, title))
                            }.isFailure
                        }
                    }) { Text(stringResource(R.string.share), color = Color(0xFF64A8FF)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close), color = Color.White) }
                }
            }
        }
    }
}
