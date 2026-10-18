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
package me.dougals.forage.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.R
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.presentation.search.SearchViewModel
import me.dougals.forage.ui.articles.SwipeableArticleCard
import me.dougals.forage.ui.articles.ToggleUndoSnackbar
import me.dougals.forage.ui.rememberMeteredConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenArticle: (Long) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Only ask for the keyboard on a fresh search, not when coming back to one.
    LaunchedEffect(Unit) { if (text.isEmpty()) focus.requestFocus() }
    LaunchedEffect(text) { vm.setQuery(text) }

    ToggleUndoSnackbar(state.toggled, snackbar, onUndo = vm::undoToggle, onDismiss = vm::dismissToggle)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.focusRequester(focus),
                    )
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    if (text.isNotEmpty()) {
                        IconButton({ text = ""; focus.requestFocus() }) { Icon(Icons.Filled.Close, stringResource(R.string.search_clear)) }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                !state.hasQuery -> Message(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
                state.noResults -> Message(stringResource(R.string.search_no_results_title), stringResource(R.string.search_no_results_body, state.query.trim()))
                else -> {
                    val metered = rememberMeteredConnection()
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.articles, key = { it.id }) { article ->
                            SwipeableArticleCard(
                                article = article,
                                feedTitle = state.feedTitles[article.feedId],
                                onClick = { onOpenArticle(article.id) },
                                onToggleRead = { vm.toggleRead(article) },
                                onToggleSaved = { vm.toggleSaved(article) },
                                thumbnailSide = state.thumbnailSide,
                                showThumbnail = state.thumbnails != ThumbnailMode.OFF,
                                thumbnailFromCacheOnly = state.thumbnails == ThumbnailMode.WIFI_ONLY && metered,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
