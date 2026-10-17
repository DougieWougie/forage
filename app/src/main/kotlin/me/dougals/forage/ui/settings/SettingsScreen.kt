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

import me.dougals.forage.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import me.dougals.forage.data.net.Links
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
import me.dougals.forage.presentation.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogin: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showConnect by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var scale by remember(state.settings.fontScale) { mutableFloatStateOf(state.settings.fontScale) }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it.resolve(context)); vm.dismissMessage() } }

    // A login finishes on another screen; pick up the new session when we come back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.reloadSessions() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) }, scrollBehavior = scroll) },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { Section(stringResource(R.string.section_reading)) }
            item {
                Text(stringResource(R.string.typeface), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.settings.readerFont == ReaderFont.SERIF, { vm.setFont(ReaderFont.SERIF) }, label = { Text(stringResource(R.string.font_serif)) })
                    FilterChip(state.settings.readerFont == ReaderFont.SANS, { vm.setFont(ReaderFont.SANS) }, label = { Text(stringResource(R.string.font_sans)) })
                    FilterChip(state.settings.readerFont == ReaderFont.DYSLEXIC, { vm.setFont(ReaderFont.DYSLEXIC) }, label = { Text(stringResource(R.string.font_dyslexic)) })
                }
                Text(stringResource(R.string.text_size, (scale * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                Slider(scale, { scale = it }, onValueChangeFinished = { vm.setFontScale(scale) }, valueRange = AppSettings.MIN_SCALE..AppSettings.MAX_SCALE)
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
            item { Section(stringResource(R.string.section_sync)) }
            item {
                if (state.isConnecting || state.isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.settings.syncMode == SyncMode.NEXTCLOUD) {
                    Text(stringResource(R.string.nextcloud_news), style = MaterialTheme.typography.titleMedium)
                    Text("${state.nextcloudUser} @ ${state.nextcloudServer}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(vm::syncNow, enabled = !state.isSyncing) {
                            Text(stringResource(if (state.isSyncing) R.string.syncing else R.string.sync_now))
                        }
                        OutlinedButton(vm::disconnectNextcloud) { Text(stringResource(R.string.disconnect)) }
                    }
                    Text(
                        lastSyncedLabel(state.lastSyncedAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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

    if (showConnect) ConnectDialog(onDismiss = { showConnect = false }, onConnect = { u, n, p -> showConnect = false; vm.connectNextcloud(u, n, p) })
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

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(title.uppercase(), Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onConnect: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("https://") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // deliberately not saved across process death
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_nextcloud_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(server, { server = it }, singleLine = true, label = { Text(stringResource(R.string.field_server)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, singleLine = true, label = { Text(stringResource(R.string.field_username)) })
                OutlinedTextField(
                    password, { password = it }, singleLine = true, label = { Text(stringResource(R.string.field_app_password)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.app_password_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onConnect(server, user, password) }) { Text(stringResource(R.string.action_connect)) } },
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
