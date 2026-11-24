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
package com.darkxvenom.itune.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.remote.RemoteLibraryStore
import com.darkxvenom.itune.data.sources.MusicAddon
import com.darkxvenom.itune.data.sources.PlaybackSources
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val SourceAccent = Color(0xFFFF375F)

@Composable
fun SourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    LaunchedEffect(Unit) { PlaybackSources.ensureInit(context) }
    LaunchedEffect(Unit) { RemoteLibraryStore.ensureInit(context) }
    val addons by PlaybackSources.addons.collectAsState()
    val order by PlaybackSources.order.collectAsState()
    val settings by SettingsStore.state.collectAsState()
    val captchaGuidance by PlaybackSources.captchaGuidance.collectAsState()
    val youtubeEnabled by PlaybackSources.youtubeEnabled.collectAsState()
    val remoteSettings by RemoteLibraryStore.settings.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MusicAddon?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableIntStateOf(0) }
    var remoteEditor by remember { mutableStateOf<String?>(null) }
    var remoteTracks by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp).clickable(onClick = onBack))
            Column(Modifier.weight(1f)) {
                Text("Sources", color = MaterialTheme.colorScheme.onBackground, fontSize = 27.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
                Text("Choose where music comes from", color = MaterialTheme.colorScheme.onSurface.copy(.58f), fontSize = 13.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { addDialog = true }.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = SourceAccent, modifier = Modifier.size(18.dp))
                Text("Add", color = SourceAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SourceSection(title = "STREAMING SOURCES", caption = "Hold and drag an installed extension to set playback priority") {
                    val rows = buildList {
                        order.forEach { id ->
                            when {
                                id == "youtube" -> add(SourceRow(id, "YouTube Music", "Full music catalogue", youtubeEnabled, "Built in"))
                                id == "jiosaavn" -> add(SourceRow(id, "JioSaavn", "High Quality AAC · up to 320 kbps", settings.jioSaavnProvider, "Built in"))
                                else -> addons.firstOrNull { it.id == id }?.let { addon ->
                                    val description = if (addon.moduleId != null) {
                                        buildString {
                                            append("Extension")
                                            if (addon.author.isNotBlank()) append(" · ${addon.author}")
                                            if (addon.version.isNotBlank()) append(" · v${addon.version}")
                                        }
                                    } else addon.baseUrl
                                    add(SourceRow(addon.id, addon.name, description, addon.enabled,
                                        if (addon.moduleId != null) "Extension" else "Addon", addon))
                                }
                            }
                        }
                    }
                    Column {
                        rows.forEachIndexed { index, row ->
                            SourceRowView(
                                row = row,
                                position = index + 1,
                                dragging = draggingId == row.id,
                                onOpen = { row.addon?.let { detail = it } },
                                onEnabled = { enabled ->
                                    when (row.id) {
                                        "youtube" -> PlaybackSources.setYoutubeEnabled(enabled)
                                        "jiosaavn" -> SettingsStore.setBoolean(context, SettingsStore.KEY_JIOSAAVN_PROVIDER, enabled)
                                        else -> PlaybackSources.setEnabled(row.id, enabled)
                                    }
                                },
                                onRemove = if (row.addon != null) ({ PlaybackSources.removeAddon(row.id) }) else null,
                                onDrag = { amount ->
                                    dragOffset += amount
                                    val slot = (68.dp.value * density).toInt().coerceAtLeast(1)
                                    val steps = dragOffset / slot
                                    if (steps != 0) {
                                        val next = order.toMutableList()
                                        val from = next.indexOf(row.id)
                                        if (from >= 0) {
                                            val to = (from + steps).coerceIn(0, next.lastIndex)
                                            val item = next.removeAt(from)
                                            next.add(to, item)
                                            PlaybackSources.setOrder(next)
                                            dragOffset -= steps * slot
                                        }
                                    }
                                },
                                onDragStart = { draggingId = row.id; dragOffset = 0 },
                                onDragEnd = { draggingId = null; dragOffset = 0 },
                            )
                            if (index < rows.lastIndex) SourceDivider()
                        }
                    }
                }
            }

            item {
                SourceSection(title = "NETWORK LIBRARIES", caption = "Play music from your own servers") {
                    SettingInfoRow(Icons.Rounded.Cloud, "WebDAV", if (remoteSettings.webDavUrl.isBlank()) "Connect a WebDAV or Nextcloud music folder" else remoteSettings.webDavUrl, onClick = { remoteEditor = "webdav" })
                    SourceDivider()
                    SettingInfoRow(Icons.Rounded.Storage, "SMB", if (remoteSettings.smbHost.isBlank()) "Connect a NAS or Windows network share" else "${remoteSettings.smbHost} · ${remoteSettings.smbShare}", onClick = { remoteEditor = "smb" })
                }
            }

            item {
                SourceSection(title = "PRIVACY", caption = "Control challenge guidance shown during playback") {
                    SettingSwitchRow(
                        title = "YouTube challenge guidance",
                        subtitle = "Show help when YouTube requests sign-in or CAPTCHA verification",
                        checked = captchaGuidance,
                        onChecked = PlaybackSources::setCaptchaGuidance,
                    )
                }
            }

            item {
                Text(
                    "Extensions run in a sandbox and can provide catalogue search and stream URLs. Install extensions from a compatible iTune module index, or add an addon server URL.",
                    color = MaterialTheme.colorScheme.onSurface.copy(.48f), fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }

    if (addDialog) AddExtensionDialog(
        onDismiss = { addDialog = false },
        onInstall = { selected -> addDialog = false },
    )
    detail?.let { addon ->
        ExtensionDetailsDialog(addon, onDismiss = { detail = null }, onRemove = {
            PlaybackSources.removeAddon(addon.id)
            detail = null
        })
    }
    remoteEditor?.let { type ->
        RemoteLibraryDialog(
            type = type,
            initial = remoteSettings,
            onDismiss = { remoteEditor = null },
            onSave = { form ->
                if (type == "webdav") RemoteLibraryStore.saveWebDav(form.webDavUrl, form.webDavUser, form.webDavPassword)
                else RemoteLibraryStore.saveSmb(form.smbHost, form.smbShare, form.smbFolder, form.smbUser, form.smbPassword)
            },
            onBrowse = {
                remoteEditor = null
                remoteLoading = true; remoteError = ""; remoteTracks = null
                scope.launch {
                    val result = if (type == "webdav") RemoteLibraryStore.listWebDav() else RemoteLibraryStore.listSmb()
                    result.fold(onSuccess = { remoteTracks = it }, onFailure = { remoteError = it.message ?: "Could not list this server." })
                    remoteLoading = false
                }
            },
        )
    }
    if (remoteLoading || remoteTracks != null || remoteError.isNotBlank()) {
        RemoteTracksDialog(
            tracks = remoteTracks.orEmpty(), loading = remoteLoading, error = remoteError,
            onDismiss = { remoteTracks = null; remoteLoading = false; remoteError = "" },
            onPlay = { track ->
                val queue = remoteTracks.orEmpty()
                com.darkxvenom.itune.backend.MusicPlayerState.play(track, newQueue = queue)
                remoteTracks = null; remoteEditor = null; onBack()
            },
        )
    }
}

private data class SourceRow(
    val id: String,
    val name: String,
    val subtitle: String,
    val enabled: Boolean,
    val type: String,
    val addon: MusicAddon? = null,
)

@Composable
private fun SourceSection(title: String, caption: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurface.copy(.5f), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp,
            modifier = Modifier.padding(start = 13.dp, bottom = 7.dp))
        SettingsUniformGreyCard(cornerRadius = 14.dp) { content() }
        if (caption != null) Text(caption, color = MaterialTheme.colorScheme.onSurface.copy(.44f),
            fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 13.dp, top = 6.dp, end = 12.dp))
    }
}

@Composable
private fun SourceRowView(
    row: SourceRow,
    position: Int,
    dragging: Boolean,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    onDrag: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp)
            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant.copy(.5f) else Color.Transparent)
            .clickable(enabled = row.addon != null, onClick = onOpen)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (row.addon != null) {
            Icon(Icons.Rounded.DragHandle, "Hold to prioritize", tint = MaterialTheme.colorScheme.onSurface.copy(.45f),
                modifier = Modifier.size(19.dp).pointerInput(row.id, position) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, delta -> change.consume(); onDrag(delta.y.toInt()) },
                    )
                })
        } else {
            Box(Modifier.size(19.dp), contentAlignment = Alignment.Center) {
                Text(position.toString(), color = MaterialTheme.colorScheme.onSurface.copy(.4f), fontSize = 11.sp)
            }
        }
        Icon(
            imageVector = when (row.type) {
                "Extension" -> Icons.Rounded.Extension
                "Addon" -> Icons.Rounded.Public
                else -> Icons.Rounded.MusicNote
            },
            contentDescription = null,
            tint = if (row.enabled) SourceAccent else MaterialTheme.colorScheme.onSurface.copy(.42f),
            modifier = Modifier.size(21.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.addon != null) Text("${position}", color = MaterialTheme.colorScheme.onSurface.copy(.4f), fontSize = 10.sp)
            }
            Text(row.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(.53f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) Icon(Icons.Rounded.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.onSurface.copy(.48f),
            modifier = Modifier.size(20.dp).clickable(onClick = onRemove))
        Switch(
            checked = row.enabled,
            onCheckedChange = onEnabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SourceAccent),
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
        )
    }
}

@Composable
private fun SettingInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(.6f), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(.52f), fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.4f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(.52f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SourceAccent))
    }
}

@Composable
private fun SourceDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(start = 54.dp), thickness = .5.dp, color = MaterialTheme.colorScheme.onSurface.copy(.09f),
    )
}

@Composable
private fun AddExtensionDialog(onDismiss: () -> Unit, onInstall: (List<MusicAddon>) -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<MusicAddon>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun dismissAnimated(after: () -> Unit = onDismiss) {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch { delay(190); after() }
    }
    Dialog(onDismissRequest = { if (!installing) dismissAnimated() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = .94f) + slideInVertically(tween(190)) { it / 18 },
            exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = .96f) + slideOutVertically(tween(150)) { it / 22 }) {
            Column(
                Modifier.fillMaxWidth(.91f).shadow(30.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)).background(Color(0xFF1C1C1E))
                    .border(.7.dp, Color.White.copy(.14f), RoundedCornerShape(26.dp)).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Rounded.Extension, null, tint = SourceAccent, modifier = Modifier.size(21.dp))
                    Text(if (found.isEmpty()) "Add an extension" else "Available extensions", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (found.isEmpty()) "Paste an iTune module index, an extension URL, or a compatible JSON addon server."
                    else "Choose the extensions to install. They will be added to your playback priority list.",
                    color = Color.White.copy(.58f), fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 7.dp, bottom = 14.dp),
                )
                if (found.isEmpty()) {
                    BasicTextField(
                        value = url, onValueChange = { url = it; error = "" }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.08f)).padding(13.dp)) {
                                if (url.isBlank()) Text("https://…/index.json", color = Color.White.copy(.34f), fontSize = 13.sp)
                                inner()
                            }
                        },
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        itemsIndexed(found, key = { _, item -> item.id }) { _, addon ->
                            val isSelected = addon.id in selected
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) SourceAccent.copy(.15f) else Color.Transparent)
                                .clickable { selected = if (isSelected) selected - addon.id else selected + addon.id }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Extension, null, tint = if (isSelected) SourceAccent else Color.White.copy(.52f), modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(addon.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(addon.author, addon.version.takeIf { it.isNotBlank() }?.let { "v$it" }, addon.description)
                                        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { addon.baseUrl },
                                        color = Color.White.copy(.5f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(if (isSelected) "✓" else "", color = SourceAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF7688), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (found.isEmpty()) "Cancel" else "Back", color = Color.White.copy(.68f),
                        modifier = Modifier.clickable(enabled = !scanning && !installing) {
                            if (found.isEmpty()) dismissAnimated() else { found = emptyList(); selected = emptySet(); error = "" }
                        }.padding(horizontal = 12.dp, vertical = 11.dp))
                    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(SourceAccent)
                        .clickable(enabled = !scanning && !installing) {
                            if (found.isEmpty()) {
                                scanning = true
                                scope.launch {
                                    PlaybackSources.discoverAddons(url).fold(
                                        onSuccess = { list ->
                                            found = list
                                            // A direct addon URL resolves to one result; indexes are opt-in per extension.
                                            selected = if (list.size == 1 && list.first().moduleId == null) setOf(list.first().id) else emptySet()
                                            if (list.isEmpty()) error = "No compatible extensions were found at that address."
                                        },
                                        onFailure = { error = it.message ?: "Could not read the extension index." },
                                    )
                                    scanning = false
                                }
                            } else {
                                val chosen = found.filter { it.id in selected }
                                if (chosen.isNotEmpty()) {
                                    installing = true
                                    scope.launch { PlaybackSources.installAddons(chosen); dismissAnimated { onInstall(chosen) } }
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (scanning || installing) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        Text(
                            when { scanning -> "Checking…"; installing -> "Installing…"; found.isEmpty() -> "Find"; else -> "Install ${selected.size}" },
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private data class RemoteForm(
    val webDavUrl: String = "", val webDavUser: String = "", val webDavPassword: String = "",
    val smbHost: String = "", val smbShare: String = "", val smbFolder: String = "",
    val smbUser: String = "", val smbPassword: String = "",
)

@Composable
private fun RemoteLibraryDialog(
    type: String,
    initial: com.darkxvenom.itune.data.remote.RemoteLibrarySettings,
    onDismiss: () -> Unit,
    onSave: (RemoteForm) -> Unit,
    onBrowse: () -> Unit,
) {
    val webDav = type == "webdav"
    var url by remember(initial) { mutableStateOf(initial.webDavUrl) }
    var host by remember(initial) { mutableStateOf(initial.smbHost) }
    var share by remember(initial) { mutableStateOf(initial.smbShare) }
    var folder by remember(initial) { mutableStateOf(initial.smbFolder) }
    var user by remember(initial) { mutableStateOf(if (webDav) initial.webDavUser else initial.smbUser) }
    var password by remember(initial) { mutableStateOf(if (webDav) initial.webDavPassword else initial.smbPassword) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun dismissAnimated() {
        if (closePending) return
        closePending = true; visible = false
        scope.launch { delay(180); onDismiss() }
    }

    fun form() = if (webDav) RemoteForm(webDavUrl = url, webDavUser = user, webDavPassword = password)
        else RemoteForm(smbHost = host, smbShare = share, smbFolder = folder, smbUser = user, smbPassword = password)

    Dialog(onDismissRequest = { if (!busy) dismissAnimated() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = .95f) + slideInVertically(tween(180)) { it / 18 },
            exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = .96f) + slideOutVertically(tween(150)) { it / 22 }) {
        Column(Modifier.fillMaxWidth(.91f).shadow(30.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1C1C1E)).border(.7.dp, Color.White.copy(.14f), RoundedCornerShape(26.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(if (webDav) Icons.Rounded.Cloud else Icons.Rounded.Storage, null, tint = SourceAccent)
                Text(if (webDav) "WebDAV library" else "SMB library", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (webDav) "Connect a WebDAV folder, including Nextcloud." else "Connect a shared folder on your NAS or Windows PC.",
                color = Color.White.copy(.58f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 7.dp, bottom = 13.dp))
            if (webDav) {
                RemoteInput("Server URL", url, { url = it; status = "" }, "https://cloud.example.com/remote.php/dav/files/user/Music", KeyboardType.Uri)
            } else {
                RemoteInput("Server", host, { host = it; status = "" }, "nas.local or 192.168.1.10")
                RemoteInput("Share name", share, { share = it; status = "" }, "Music")
                RemoteInput("Folder (optional)", folder, { folder = it; status = "" }, "Music/Albums")
            }
            RemoteInput("Username (optional)", user, { user = it; status = "" }, "Username")
            RemoteInput("Password (optional)", password, { password = it; status = "" }, "Password", password = true)
            if (status.isNotBlank()) Text(status, color = if (status.startsWith("Connected")) Color(0xFF68D391) else Color(0xFFFF7688),
                fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
            Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = Color.White.copy(.68f), modifier = Modifier.clickable(enabled = !busy, onClick = ::dismissAnimated).padding(10.dp))
                Text("Save", color = SourceAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = !busy) {
                    onSave(form()); status = "Saved securely on this device"
                }.padding(10.dp))
                RemoteAction("Test", busy) {
                    busy = true; onSave(form())
                    scope.launch {
                        val result = if (webDav) {
                            com.darkxvenom.itune.data.remote.webdav.WebDavClient.testConnection(url, user, password)
                        } else {
                            com.darkxvenom.itune.data.remote.smb.SmbClient.testConnection(host, share, folder, user, password)
                        }
                        status = result.fold({ "Connected successfully" }, { "Connection failed: ${it.message ?: "unknown error"}" })
                        busy = false
                    }
                }
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(SourceAccent).clickable(enabled = !busy) {
                    onSave(form()); onBrowse()
                }.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp)
                    Text("Browse", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }
    }
}

@Composable
private fun RemoteInput(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, color = Color.White.copy(.62f), fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.075f)).padding(horizontal = 11.dp, vertical = 10.dp)) {
                    if (value.isBlank()) Text(placeholder, color = Color.White.copy(.32f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun RemoteAction(label: String, disabled: Boolean, onClick: () -> Unit) {
    Text(label, color = Color.White.copy(if (disabled) .35f else .72f), fontSize = 12.sp,
        modifier = Modifier.clickable(enabled = !disabled, onClick = onClick).padding(horizontal = 9.dp, vertical = 10.dp))
}

@Composable
private fun RemoteTracksDialog(
    tracks: List<MusicTrack>,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun dismissAnimated() {
        if (closePending) return
        closePending = true; visible = false
        scope.launch { delay(180); onDismiss() }
    }
    Dialog(onDismissRequest = ::dismissAnimated, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = .95f) + slideInVertically(tween(180)) { it / 18 },
            exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = .96f) + slideOutVertically(tween(150)) { it / 22 }) {
        Column(Modifier.fillMaxWidth(.92f).shadow(30.dp, RoundedCornerShape(25.dp)).clip(RoundedCornerShape(25.dp))
            .background(Color(0xFF1C1C1E)).border(.7.dp, Color.White.copy(.14f), RoundedCornerShape(25.dp)).padding(18.dp)) {
            Text("Remote music", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(when { loading -> "Searching the connected library…"; error.isNotBlank() -> error; tracks.isEmpty() -> "No audio files found"; else -> "${tracks.size} tracks · tap a song to play" },
                color = if (error.isNotBlank()) Color(0xFFFF7688) else Color.White.copy(.56f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
            if (loading) Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SourceAccent, strokeWidth = 2.dp)
            } else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPlay(track) }.padding(vertical = 10.dp, horizontal = 8.dp)) {
                        Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(track.artists, track.album).joinToString(" · "), color = Color.White.copy(.52f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Done", color = SourceAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = ::dismissAnimated).padding(12.dp))
            }
        }
        }
    }
}

@Composable
private fun ExtensionDetailsDialog(addon: MusicAddon, onDismiss: () -> Unit, onRemove: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun dismissAnimated(after: () -> Unit = onDismiss) {
        if (closePending) return
        closePending = true; visible = false
        scope.launch { delay(180); after() }
    }
    Dialog(onDismissRequest = { dismissAnimated() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = .95f) + slideInVertically(tween(180)) { it / 18 },
            exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = .96f) + slideOutVertically(tween(150)) { it / 22 }) {
        Column(Modifier.fillMaxWidth(.88f).shadow(28.dp, RoundedCornerShape(25.dp)).clip(RoundedCornerShape(25.dp))
            .background(Color(0xFF1C1C1E)).border(.7.dp, Color.White.copy(.14f), RoundedCornerShape(25.dp)).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (addon.moduleId != null) Icons.Rounded.Extension else Icons.Rounded.Public, null, tint = SourceAccent)
                Text(addon.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(addon.description.ifBlank { "Installed source extension" }, color = Color.White.copy(.68f), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp))
            Text("${addon.author.ifBlank { "Source provider" }}${addon.version.takeIf { it.isNotBlank() }?.let { " · v$it" }.orEmpty()}",
                color = Color.White.copy(.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Text("${addon.baseUrl}${addon.moduleId?.let { "\nExtension ID: $it" }.orEmpty()}", color = Color.White.copy(.4f), fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Remove", color = Color(0xFFFF6961), modifier = Modifier.clickable { dismissAnimated(onRemove) }.padding(12.dp))
                Text("Done", color = SourceAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { dismissAnimated() }.padding(12.dp))
            }
        }
        }
    }
}
