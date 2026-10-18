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
package me.dougals.forage.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.R
import me.dougals.forage.domain.opml.OpmlExchange
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.toUiText
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.suggest.FeedSuggester
import me.dougals.forage.domain.suggest.SuggestionCatalogue
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.map
import java.io.InputStream
import javax.inject.Inject

/** [folder] is null for the "Uncategorized" group. */
data class CategoryNode(val folder: Folder?, val feeds: List<Feed>) {
    val unreadCount get() = feeds.sumOf { it.unreadCount }
    val updatedFeeds get() = feeds.count { it.unreadCount > 0 }
}

data class CategoriesState(
    val categories: List<CategoryNode> = emptyList(),
    val message: UiText? = null,
    val isBusy: Boolean = false,
    val isRefreshing: Boolean = false,
    val pullToRefresh: Boolean = true,
    val suggestions: List<FeedSuggestion> = emptyList(),
)

fun buildCategories(folders: List<Folder>, feeds: List<Feed>): List<CategoryNode> {
    val byFolder = feeds.groupBy { it.folderId }
    val nodes = folders.sortedBy { it.name.lowercase() }.map { f ->
        CategoryNode(f, byFolder[f.id].orEmpty().sortedBy { it.title.lowercase() })
    }
    val loose = feeds.filter { f -> f.folderId == null || folders.none { it.id == f.folderId } }
    return if (loose.isEmpty()) nodes else nodes + CategoryNode(null, loose.sortedBy { it.title.lowercase() })
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val opml: OpmlExchange,
    private val settingsRepo: SettingsRepository,
    catalogue: SuggestionCatalogue,
) : ViewModel() {
    private val message = MutableStateFlow<UiText?>(null)
    private val busy = MutableStateFlow(false)

    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<CategoriesState> = combine(
        repo.observeFolders(), repo.observeFeeds(), message, busy, refreshing, settingsRepo.settings.map { it.pullToRefresh },
        suggestions(catalogue),
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        CategoriesState(buildCategories(v[0] as List<Folder>, v[1] as List<Feed>), v[2] as UiText?, v[3] as Boolean, v[4] as Boolean, v[5] as Boolean, v[6] as List<FeedSuggestion>)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState())

    private fun suggestions(catalogue: SuggestionCatalogue): Flow<List<FeedSuggestion>> {
        val suggester = flow { emit(runCatching { FeedSuggester(catalogue.load()) }.getOrDefault(FeedSuggester(emptyList()))) }
        return combine(suggester, repo.observeFolders(), repo.observeFeeds(), settingsRepo.settings.map { it.dismissedSuggestions }.distinctUntilChanged()) {
            s, folders, feeds, dismissed -> s.suggest(feeds, folders, dismissed)
        }
    }

    fun addSuggestion(suggestion: FeedSuggestion) = addFeed(suggestion.feedUrl, null)
    fun dismissSuggestion(suggestion: FeedSuggestion) { viewModelScope.launch { settingsRepo.dismissSuggestion(suggestion.feedUrl) } }
    fun restoreSuggestions() { viewModelScope.launch { settingsRepo.clearDismissedSuggestions() } }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            repo.refresh()
                .onSuccess { message.value = it.toUiText() }
                .onFailure { message.value = it.toUiText(R.string.refresh_failed) }
            refreshing.value = false
        }
    }

    fun addFeed(rawUrl: String, folderId: Long?) {
        val url = rawUrl.trim()
        if (url.isEmpty()) { message.value = UiText.Res(R.string.enter_feed_url); return }
        val normalized = if ("://" in url) url else "https://$url"
        runBusy {
            repo.addFeed(normalized, folderId)
                .onSuccess { message.value = UiText.Res(R.string.added_feed, listOf(it.title)) }
                .onFailure { message.value = it.toUiText(R.string.add_feed_failed) }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        runBusy { repo.createFolder(trimmed).onFailure { message.value = it.toUiText(R.string.create_folder_failed) } }
    }

    fun deleteFolder(id: Long) = runBusy { repo.deleteFolder(id).onFailure { message.value = it.toUiText(R.string.delete_folder_failed) } }
    fun removeFeed(id: Long) = runBusy { repo.removeFeed(id).onFailure { message.value = it.toUiText(R.string.remove_feed_failed) } }
    fun moveFeed(feedId: Long, folderId: Long?) = runBusy {
        repo.moveFeed(feedId, folderId).onFailure { message.value = it.toUiText(R.string.move_feed_failed) }
    }

    fun markFeedRead(id: Long) { viewModelScope.launch { repo.markFeedRead(id) } }

    fun importOpml(input: InputStream) = runBusy {
        opml.import(input)
            .onSuccess { message.value = UiText.Res(R.string.import_result, listOf(it.added, it.skipped, it.failed)) }
            .onFailure { message.value = it.toUiText(R.string.import_failed) }
    }

    suspend fun exportOpml(): String = opml.export()

    fun dismissMessage() { message.value = null }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try { block() } finally { busy.value = false }
        }
    }
}
