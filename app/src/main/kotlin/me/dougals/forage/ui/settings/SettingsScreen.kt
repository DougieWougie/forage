/*
 * Forage - an RSS reader for Android
 * Copyright (C) 2026 Dougie Richardson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY;
 * see the LICENSE file for details.
 */
package me.dougals.forage.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import android.text.format.Formatter
import kotlinx.coroutines.launch
import me.dougals.forage.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import me.dougals.forage.ui.Links
import me.dougals.forage.ui.openInBrowser
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.UNCATEGORISED_FOLDER_ID
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.presentation.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onLogin: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showConnect by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf<ClearKind?>(null) }
    val uncategorisedLabel = stringResource(R.string.uncategorized)
    val notificationsBlocked = stringResource(R.string.notifications_blocked)
    // Android 13+ asks at runtime; earlier versions grant it at install. Re-read on resume, as it can change in system settings.
    var permitted by remember { mutableStateOf(notificationsPermitted(context)) }
    // The change that prompted the permission request, applied only if it is granted.
    var pendingNotifyChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) pendingNotifyChange?.invoke()
        else scope.launch { snackbar.showSnackbar(notificationsBlocked) }
        pendingNotifyChange = null
    }
    /** Turning a digest on needs the permission first; turning one off never does. */
    fun changeNotifications(enabling: Boolean, apply: () -> Unit) {
        if (enabling && !permitted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotifyChange = apply
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            apply()
        }
    }
    var scale by remember(state.settings.fontScale) { mutableFloatStateOf(state.settings.fontScale) }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it.resolve(context)); vm.dismissMessage() } }

    // Open each Nextcloud login page once, not again when the screen is recreated (e.g. on rotation).
    var openedLoginUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loginUrl) {
        state.loginUrl?.takeIf { it != openedLoginUrl }?.let { context.openInBrowser(it); openedLoginUrl = it }
    }

    // A login finishes on another screen; pick up the new session when we come back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { vm.reloadSessions(); permitted = notificationsPermitted(context) } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) }, scrollBehavior = scroll) },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Image(
                    painterResource(R.drawable.logo_wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(vertical = 8.dp).width(160.dp),
                )
            }
            item { Section(stringResource(R.string.section_reading), divider = false) }
            item {
                Text(stringResource(R.string.typeface), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.settings.readerFont == ReaderFont.SERIF, { vm.setFont(ReaderFont.SERIF) }, label = { Text(stringResource(R.string.font_serif)) })
                    FilterChip(state.settings.readerFont == ReaderFont.SANS, { vm.setFont(ReaderFont.SANS) }, label = { Text(stringResource(R.string.font_sans)) })
                    FilterChip(state.settings.readerFont == ReaderFont.DYSLEXIC, { vm.setFont(ReaderFont.DYSLEXIC) }, label = { Text(stringResource(R.string.font_dyslexic)) })
                }
                Text(stringResource(R.string.text_size, (scale * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                Slider(scale, { scale = it }, Modifier.padding(vertical = 8.dp), onValueChangeFinished = { vm.setFontScale(scale) }, valueRange = AppSettings.MIN_SCALE..AppSettings.MAX_SCALE)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mark_read_on_open), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.mark_read_on_open_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.settings.markReadOnOpen, vm::setMarkReadOnOpen)
                }
            }
            item { Section(stringResource(R.string.section_article_list)) }
            item {
                Text(stringResource(R.string.thumbnails), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.thumbnails_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.settings.thumbnails == ThumbnailMode.ALWAYS, { vm.setThumbnails(ThumbnailMode.ALWAYS) }, label = { Text(stringResource(R.string.thumbnails_always)) })
                    FilterChip(state.settings.thumbnails == ThumbnailMode.WIFI_ONLY, { vm.setThumbnails(ThumbnailMode.WIFI_ONLY) }, label = { Text(stringResource(R.string.thumbnails_wifi)) })
                    FilterChip(state.settings.thumbnails == ThumbnailMode.OFF, { vm.setThumbnails(ThumbnailMode.OFF) }, label = { Text(stringResource(R.string.thumbnails_off)) })
                }
            }
            item {
                Text(stringResource(R.string.thumbnail_side), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.settings.thumbnailSide == ThumbnailSide.START, { vm.setThumbnailSide(ThumbnailSide.START) }, label = { Text(stringResource(R.string.thumbnail_side_left)) })
                    FilterChip(state.settings.thumbnailSide == ThumbnailSide.END, { vm.setThumbnailSide(ThumbnailSide.END) }, label = { Text(stringResource(R.string.thumbnail_side_right)) })
                }
            }
            item { Section(stringResource(R.string.section_refresh)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pull_to_refresh), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.pull_to_refresh_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.settings.pullToRefresh, vm::setPullToRefresh)
                }
            }
            item {
                Text(stringResource(R.string.background_refresh), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.background_refresh_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RefreshInterval.entries.forEach { interval ->
                        FilterChip(
                            state.settings.refreshInterval == interval, { vm.setRefreshInterval(interval) },
                            label = { Text(stringResource(refreshIntervalLabel(interval))) },
                        )
                    }
                }
            }
            if (state.settings.refreshInterval != RefreshInterval.OFF) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.refresh_wifi_only), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.refresh_wifi_only_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.settings.refreshWifiOnly, vm::setRefreshWifiOnly)
                }
            }
            item { Section(stringResource(R.string.section_notifications)) }
            item {
                Text(
                    stringResource(R.string.notifications_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                // Feeds start out in no folder, so "Uncategorized" is always offered, ahead of any the user has made.
                val choices = listOf(UNCATEGORISED_FOLDER_ID to uncategorisedLabel) + state.folders.map { it.id to it.name }
                val chosen = if (permitted) state.settings.notifyFolderIds else emptySet()
                val all = choices.all { it.first in chosen }
                // Chips wrap onto as many lines as the categories need, so a long list stays in view.
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(all, {
                        changeNotifications(enabling = !all) { vm.setNotifyFolders(if (all) emptySet() else choices.map { it.first }.toSet()) }
                    }, label = { Text(stringResource(R.string.filter_all)) })
                    choices.forEach { (id, name) ->
                        val selected = id in chosen
                        FilterChip(selected, {
                            changeNotifications(enabling = !selected) { vm.setFolderNotifications(id, !selected) }
                        }, label = { Text(name) })
                    }
                }
            }
            item { Section(stringResource(R.string.section_sync)) }
            item {
                val nextcloud = state.settings.syncMode == SyncMode.NEXTCLOUD
                // Once connected, progress shows between the buttons instead (the first sync counts as connecting).
                if (state.isConnecting && !nextcloud) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (nextcloud) {
                    Text(stringResource(R.string.nextcloud_news), style = MaterialTheme.typography.titleMedium)
                    Text("${state.nextcloudUser} @ ${state.nextcloudServer}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // No spacedBy: the gap after the indicator animates with it, so Disconnect slides rather than jumps.
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(vm::syncNow, enabled = !state.isSyncing) {
                            Text(stringResource(if (state.isSyncing) R.string.syncing else R.string.sync_now))
                        }
                        Spacer(Modifier.width(8.dp))
                        AnimatedVisibility(
                            state.isSyncing || state.isConnecting,
                            enter = expandHorizontally() + fadeIn(), exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Row {
                                CircularWavyProgressIndicator(Modifier.size(ButtonDefaults.MinHeight))
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        OutlinedButton(vm::disconnectNextcloud) { Text(stringResource(R.string.disconnect)) }
                    }
                    Text(
                        lastSyncedLabel(state.lastSyncedAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (state.loginUrl != null) {
                    Text(stringResource(R.string.connect_nextcloud_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.login_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ context.openInBrowser(state.loginUrl!!) }) { Text(stringResource(R.string.login_open_again)) }
                        OutlinedButton(vm::cancelNextcloudLogin) { Text(stringResource(R.string.action_cancel)) }
                    }
                } else {
                    Text(stringResource(R.string.local_mode), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.local_mode_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ showConnect = true }) { Text(stringResource(R.string.action_connect)) }
                        OutlinedButton({ context.openInBrowser(Links.NEXTCLOUD_NEWS) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.get_news_app))
                        }
                    }
                    Text(
                        stringResource(R.string.nextcloud_requirements),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item { Section(stringResource(R.string.section_signed_in)) }
            item {
                Text(
                    stringResource(R.string.signed_in_body),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.sessionDomains.size) { i ->
                val domain = state.sessionDomains[i]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton({ vm.removeSession(domain) }) { Icon(Icons.Filled.Close, stringResource(R.string.cd_forget_session, domain)) }
                }
            }
            item { OutlinedButton({ showLogin = true }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.log_in_to_site_button)) } }
            item { Section(stringResource(R.string.section_storage)) }
            item {
                Text(
                    stringResource(R.string.storage_offline_usage, state.offlineArticles, Formatter.formatShortFileSize(context, state.offlineBytes)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.storage_thumbnail_usage, Formatter.formatShortFileSize(context, state.thumbnailBytes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ confirmClear = ClearKind.DOWNLOADS }) { Text(stringResource(R.string.storage_clear_downloads)) }
                    OutlinedButton({ confirmClear = ClearKind.ALL }) { Text(stringResource(R.string.storage_clear_all)) }
                }
            }
            item { Section(stringResource(R.string.section_network)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.allow_local_network), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.allow_local_network_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.settings.allowLocalNetwork, vm::setAllowLocalNetwork)
                }
            }
        }
    }

    confirmClear?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(stringResource(if (kind == ClearKind.ALL) R.string.storage_clear_all else R.string.storage_clear_downloads)) },
            text = { Text(stringResource(if (kind == ClearKind.ALL) R.string.storage_clear_all_body else R.string.storage_clear_downloads_body)) },
            confirmButton = {
                TextButton({ confirmClear = null; if (kind == ClearKind.ALL) vm.clearAllOffline() else vm.clearDownloads() }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = { TextButton({ confirmClear = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showConnect) ConnectDialog(
        onDismiss = { showConnect = false },
        onLogIn = { u -> showConnect = false; vm.logInToNextcloud(u) },
        onConnect = { u, n, p -> showConnect = false; vm.connectNextcloud(u, n, p) },
    )
    if (showLogin) {
        var url by rememberSaveable { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showLogin = false },
            title = { Text(stringResource(R.string.log_in_to_site_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.log_in_to_site_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.field_site_url)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            },
            confirmButton = { TextButton({ showLogin = false; onLogin(url.trim()) }, enabled = url.startsWith("https://") && url.length > 8) { Text(stringResource(R.string.action_open)) } },
            dismissButton = { TextButton({ showLogin = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class ClearKind { DOWNLOADS, ALL }

@Composable
private fun Section(title: String, divider: Boolean = true) {
    if (divider) HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(title.uppercase(), Modifier.padding(top = if (divider) 12.dp else 4.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onLogIn: (String) -> Unit, onConnect: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("https://") }
    var useAppPassword by rememberSaveable { mutableStateOf(false) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // deliberately not saved across process death
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_nextcloud_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(server, { server = it }, singleLine = true, label = { Text(stringResource(R.string.field_server)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (useAppPassword) {
                    OutlinedTextField(user, { user = it }, singleLine = true, label = { Text(stringResource(R.string.field_username)) })
                    OutlinedTextField(
                        password, { password = it }, singleLine = true, label = { Text(stringResource(R.string.field_app_password)) },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text(stringResource(R.string.app_password_hint), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.log_in_with_browser_hint), style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ useAppPassword = !useAppPassword }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(if (useAppPassword) R.string.use_browser_login else R.string.use_app_password))
                }
            }
        },
        confirmButton = {
            if (useAppPassword) TextButton({ onConnect(server, user, password) }) { Text(stringResource(R.string.action_connect)) }
            else TextButton({ onLogIn(server) }) { Text(stringResource(R.string.action_log_in)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun lastSyncedLabel(at: Long?): String {
    if (at == null) return stringResource(R.string.last_synced_never)
    val now = System.currentTimeMillis()
    if (now - at < 60_000) return stringResource(R.string.last_synced_now)
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
        at, now, android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString().replaceFirstChar { it.lowercase() }
    return stringResource(R.string.last_synced_relative, ago)
}

private fun refreshIntervalLabel(interval: RefreshInterval) = when (interval) {
    RefreshInterval.OFF -> R.string.refresh_off
    RefreshInterval.HOURLY -> R.string.refresh_1h
    RefreshInterval.EVERY_3_HOURS -> R.string.refresh_3h
    RefreshInterval.EVERY_6_HOURS -> R.string.refresh_6h
    RefreshInterval.EVERY_12_HOURS -> R.string.refresh_12h
}

private fun notificationsPermitted(context: android.content.Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
