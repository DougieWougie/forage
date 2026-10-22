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
package me.dougals.forage.ui.categories

import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import me.dougals.forage.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.presentation.categories.CategoriesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(onOpenFeed: (Long) -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showFolder by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Feed?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.resolve(context)); vm.dismissMessage() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.let { vm.importOpml(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val xml = vm.exportOpml()
            withContext(Dispatchers.IO) { context.writeText(uri, xml) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton({ showFolder = true }) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.cd_new_folder)) }
                    IconButton({ menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more)) }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_opml)) }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restore_suggestions)) },
                            onClick = { menuOpen = false; vm.restoreSuggestions() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_opml)) }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = { menuOpen = false; exportLauncher.launch("forage-subscriptions.opml") },
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_feed)) },
            )
        },
    ) { inner ->
        val body: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    if (state.categories.isEmpty()) {
                        item(key = "empty") {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.no_feeds_title), style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    stringResource(R.string.no_feeds_body),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    state.categories.forEach { node ->
                        item(key = "h-${node.folder?.id}") {
                            FolderHeader(node.folder, node.unreadCount, onDelete = { node.folder?.let { vm.deleteFolder(it.id) } })
                        }
                        items(node.feeds, key = { "f-${it.id}" }) { feed ->
                            FeedRow(feed, onOpen = { onOpenFeed(feed.id) }, onMarkRead = { vm.markFeedRead(feed.id) }, onMove = { moving = feed }, onRemove = { vm.removeFeed(feed.id) })
                        }
                        item(key = "d-${node.folder?.id}") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                    }
                    if (state.suggestions.isNotEmpty()) {
                        item(key = "suggested-header") {
                            Text(
                                stringResource(R.string.suggested_feeds).uppercase(), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        items(state.suggestions, key = { "s-${it.feedUrl}" }) { s ->
                            SuggestionRow(s, onAdd = { vm.addSuggestion(s) }, onDismiss = { vm.dismissSuggestion(s) })
                        }
                    }
                }
            }
        }
        if (state.pullToRefresh) {
            PullToRefreshBox(state.isRefreshing, vm::refresh, Modifier.padding(inner).fillMaxSize()) { body() }
        } else {
            Box(Modifier.padding(inner).fillMaxSize()) { body() }
        }
    }

    moving?.let { feed ->
        MoveFeedDialog(
            feed = feed, folders = state.categories.mapNotNull { it.folder },
            onPick = { vm.moveFeed(feed.id, it); moving = null },
            onCreate = { vm.createFolderAndMove(feed.id, it); moving = null }, onDismiss = { moving = null },
        )
    }

    if (showFolder) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolder = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }) },
            confirmButton = { TextButton({ vm.createFolder(name); showFolder = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton({ showFolder = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showAdd) {
        var url by rememberSaveable { mutableStateOf("") }
        var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
        val folders: List<Folder> = state.categories.mapNotNull { it.folder }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.add_feed)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.field_feed_url)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (folders.isNotEmpty()) {
                        Text(stringResource(R.string.field_folder), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(folderId == null, { folderId = null }, label = { Text(stringResource(R.string.folder_none)) })
                            folders.forEach { f -> FilterChip(folderId == f.id, { folderId = f.id }, label = { Text(f.name) }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ vm.addFeed(url, folderId); showAdd = false }, enabled = url.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
            dismissButton = { TextButton({ showAdd = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SuggestionRow(suggestion: FeedSuggestion, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
            Text(
                suggestion.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onAdd) { Text(stringResource(R.string.action_add)) }
        IconButton(onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cd_dismiss_suggestion, suggestion.title)) }
    }
}

@Composable
private fun MoveFeedDialog(feed: Feed, folders: List<Folder>, onPick: (Long?) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (creating) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_folder)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }) },
            confirmButton = { TextButton({ onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton({ creating = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_feed_title, feed.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<Folder?>(null) + folders).forEach { folder ->
                    val selected = folder?.id == feed.folderId
                    Row(
                        Modifier.fillMaxWidth().selectable(selected, onClick = { onPick(folder?.id) }, role = Role.RadioButton).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected, onClick = null)
                        Text(folder?.name ?: stringResource(R.string.uncategorized), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.move_new_folder), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FolderHeader(folder: Folder?, unread: Int, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (folder?.name ?: stringResource(R.string.uncategorized)).uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
        )
        if (unread > 0) Badge { Text(unread.toString()) }
        if (folder != null) IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete_folder, folder.name)) }
    }
    if (confirm && folder != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.delete_folder_title, folder.name)) },
            text = { Text(stringResource(R.string.delete_folder_body)) },
            confirmButton = { TextButton({ confirm = false; onDelete() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FeedRow(feed: Feed, onOpen: () -> Unit, onMarkRead: () -> Unit, onMove: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(feed.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (feed.unreadCount > 0) AssistChip(onClick = onOpen, label = { Text(feed.unreadCount.toString()) })
        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_feed_options)) }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_mark_all_read)) }, onClick = { menu = false; onMarkRead() })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { menu = false; onMove() })
            DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed)) }, onClick = { menu = false; onRemove() })
        }
    }
}

private fun Context.writeText(uri: Uri, text: String) {
    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
}
