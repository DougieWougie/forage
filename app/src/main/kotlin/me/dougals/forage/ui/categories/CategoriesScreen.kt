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

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
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
                title = { Text("Categories", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton({ showFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "New folder") }
                    IconButton({ menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import OPML") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export OPML") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
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
                text = { Text("Add feed") },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.categories.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No feeds yet", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Add a feed URL or import an OPML file from another reader.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    state.categories.forEach { node ->
                        item(key = "h-${node.folder?.id}") {
                            FolderHeader(node.folder, onDelete = { node.folder?.let { vm.deleteFolder(it.id) } })
                        }
                        items(node.feeds, key = { "f-${it.id}" }) { feed ->
                            FeedRow(feed, onOpen = { onOpenFeed(feed.id) }, onMarkRead = { vm.markFeedRead(feed.id) }, onRemove = { vm.removeFeed(feed.id) })
                        }
                        item(key = "d-${node.folder?.id}") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                    }
                }
            }
        }
    }

    if (showFolder) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolder = false },
            title = { Text("New folder") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton({ vm.createFolder(name); showFolder = false }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton({ showFolder = false }) { Text("Cancel") } },
        )
    }

    if (showAdd) {
        var url by rememberSaveable { mutableStateOf("") }
        var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
        val folders: List<Folder> = state.categories.mapNotNull { it.folder }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add feed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        url, { url = it }, singleLine = true, label = { Text("Feed URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (folders.isNotEmpty()) {
                        Text("Folder", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(folderId == null, { folderId = null }, label = { Text("None") })
                            folders.forEach { f -> FilterChip(folderId == f.id, { folderId = f.id }, label = { Text(f.name) }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ vm.addFeed(url, folderId); showAdd = false }, enabled = url.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton({ showAdd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FolderHeader(folder: Folder?, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (folder?.name ?: "Uncategorized").uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
        )
        if (folder != null) IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete folder ${folder.name}") }
    }
    if (confirm && folder != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete “${folder.name}”?") },
            text = { Text("Feeds in this folder are kept (local mode) or removed with it (Nextcloud mode).") },
            confirmButton = { TextButton({ confirm = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FeedRow(feed: Feed, onOpen: () -> Unit, onMarkRead: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(feed.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (feed.unreadCount > 0) AssistChip(onClick = onOpen, label = { Text(feed.unreadCount.toString()) })
        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Feed options") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Mark all as read") }, onClick = { menu = false; onMarkRead() })
            DropdownMenuItem(text = { Text("Remove feed") }, onClick = { menu = false; onRemove() })
        }
    }
}

private fun Context.writeText(uri: Uri, text: String) {
    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
}
