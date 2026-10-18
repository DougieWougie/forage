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
package me.dougals.forage.ui.articles

import androidx.compose.ui.platform.LocalContext
import me.dougals.forage.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.articles.ArticleListViewModel
import me.dougals.forage.ui.rememberMeteredConnection
import me.dougals.forage.presentation.articles.ListSource
import me.dougals.forage.presentation.articles.ReadFilter
import me.dougals.forage.presentation.articles.ToggleUndo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onOpenArticle: (Long) -> Unit,
    onBack: (() -> Unit)?,
    vm: ArticleListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.resolve(context)); vm.dismissMessage() }
    }

    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(state.markedOlder) {
        if (state.markedOlder.isEmpty()) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            UiText.Plural(R.plurals.articles_marked_read, state.markedOlder.size).resolve(context),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoMarkOlderRead() else vm.dismissMarkedOlder()
    }

    LaunchedEffect(state.toggled) {
        val toggle = state.toggled ?: return@LaunchedEffect
        val message = when (toggle.field) {
            ToggleUndo.Field.READ -> if (toggle.value) R.string.toggled_read else R.string.toggled_unread
            ToggleUndo.Field.SAVED -> if (toggle.value) R.string.toggled_saved else R.string.toggled_unsaved
        }
        val result = snackbar.showSnackbar(context.getString(message), actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undoToggle() else vm.dismissToggle()
    }

    // Oldest unread per feed: an article has something older to mark only when that is earlier than its own date.
    val oldestUnread = remember(state.articles) {
        state.articles.filterNot { it.isRead }.groupBy { it.feedId }.mapValues { (_, list) -> list.minOf { it.publishedAt } }
    }

    val title = when (val s = vm.source) {
        ListSource.Unread -> stringResource(R.string.title_unread)
        ListSource.Saved -> stringResource(R.string.title_saved)
        is ListSource.Feed -> state.feedTitles[s.feedId] ?: stringResource(R.string.title_feed_fallback)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (vm.source != ListSource.Saved && state.articles.isNotEmpty()) {
                        IconButton(vm::markAllRead) { Icon(Icons.Filled.DoneAll, stringResource(R.string.action_mark_all_read)) }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
            if (vm.source is ListSource.Feed && state.hasArticles) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f, onClick = { vm.setFilter(f) },
                            label = {
                                Text(
                                    stringResource(
                                        when (f) {
                                            ReadFilter.ALL -> R.string.filter_all
                                            ReadFilter.UNREAD -> R.string.filter_unread
                                            ReadFilter.READ -> R.string.filter_read
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            if (vm.source == ListSource.Unread && state.folders.size + (if (state.hasUncategorised) 1 else 0) >= 2) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = state.folderIds.isEmpty(), onClick = vm::clearFolders, label = { Text(stringResource(R.string.filter_all)) }) }
                    items(state.folders, key = { it.id }) { f ->
                        FilterChip(selected = f.id in state.folderIds, onClick = { vm.toggleFolder(f.id) }, label = { Text(f.name) })
                    }
                    if (state.hasUncategorised) {
                        item {
                            FilterChip(
                                selected = ArticleListViewModel.UNCATEGORISED in state.folderIds,
                                onClick = { vm.toggleFolder(ArticleListViewModel.UNCATEGORISED) },
                                label = { Text(stringResource(R.string.filter_uncategorised)) },
                            )
                        }
                    }
                }
            }
            if (state.articles.isEmpty()) {
                if (state.hasArticles) FilterEmptyState() else EmptyState(vm.source, state.pullToRefresh)
            } else {
                val metered = rememberMeteredConnection()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.articles, key = { it.id }) { article ->
                        SwipeableArticleCard(
                            article = article,
                            feedTitle = state.feedTitles[article.feedId],
                            onClick = { onOpenArticle(article.id) },
                            onToggleRead = { vm.toggleRead(article) },
                            onToggleSaved = { vm.toggleSaved(article) },
                            onMarkOlderRead = if (vm.source != ListSource.Saved && (oldestUnread[article.feedId] ?: Long.MAX_VALUE) < article.publishedAt) {
                                { vm.markOlderRead(article) }
                            } else null,
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
        if (state.pullToRefresh) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(inner).fillMaxSize(),
            ) { content() }
        } else {
            Box(Modifier.padding(inner).fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun FilterEmptyState() {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.empty_filter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyState(source: ListSource, canPull: Boolean) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (source) {
                    ListSource.Unread -> stringResource(R.string.empty_unread_title)
                    ListSource.Saved -> stringResource(R.string.empty_saved_title)
                    is ListSource.Feed -> stringResource(R.string.empty_feed_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                when (source) {
                    ListSource.Unread -> if (canPull) stringResource(R.string.empty_unread_pull) else stringResource(R.string.empty_unread_no_pull)
                    ListSource.Saved -> stringResource(R.string.empty_saved_body)
                    is ListSource.Feed -> if (canPull) stringResource(R.string.empty_feed_pull) else stringResource(R.string.empty_feed_no_pull)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
