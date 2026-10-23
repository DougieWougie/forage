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
package me.dougals.forage.presentation.articles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.dougals.forage.R
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.UNCATEGORISED_FOLDER_ID
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.toUiText
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import javax.inject.Inject

sealed interface ListSource {
    data object Unread : ListSource
    data object Saved : ListSource
    data class Feed(val feedId: Long) : ListSource

    companion object {
        fun from(handle: SavedStateHandle): ListSource = when (val s = handle.get<String>("source")) {
            "unread" -> Unread
            "saved" -> Saved
            "feed" -> Feed(requireNotNull(handle.get<Long>("feedId")?.takeIf { it >= 0 }) { "feed list needs a feedId" })
            else -> throw IllegalArgumentException("Unknown article list source: $s")
        }
    }
}

enum class ReadFilter { ALL, UNREAD, READ }

data class ArticleListState(
    val articles: List<Article> = emptyList(),
    val filter: ReadFilter = ReadFilter.ALL,
    val hasArticles: Boolean = false,
    /** Folders with unread articles, offered as chips on the unread list. */
    val folders: List<Folder> = emptyList(),
    val hasUncategorised: Boolean = false,
    /** The chosen folders, which may include [ArticleListViewModel.UNCATEGORISED] for feeds in none. Empty means all. */
    val folderIds: Set<Long> = emptySet(),
    val feedTitles: Map<Long, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val pullToRefresh: Boolean = true,
    val thumbnails: ThumbnailMode = ThumbnailMode.ALWAYS,
    val thumbnailSide: ThumbnailSide = ThumbnailSide.END,
    val message: UiText? = null,
    /** Articles just bulk-marked read ("mark all read", "mark older as read"), kept so the snackbar can undo it. */
    val markedRead: List<Long> = emptyList(),
    val toggled: ToggleUndo? = null,
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repo: FeedRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val source = ListSource.from(handle)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<UiText?>(null)
    private val toggles = ArticleToggles(repo, viewModelScope)
    private val bulkRead = BulkReadUndo(repo, viewModelScope)
    private val filter = MutableStateFlow(ReadFilter.ALL)
    // Kept in the saved state so the chosen chips survive the process being killed in the background.
    private val folderChoice = handle.getStateFlow(FOLDER_CHOICE_KEY, LongArray(0))

    private val articles = when (source) {
        ListSource.Unread -> repo.observeUnread()
        ListSource.Saved -> repo.observeSaved()
        is ListSource.Feed -> repo.observeFeedArticles(source.feedId)
    }

    private class Shown(
        val all: List<Article>,
        val shown: List<Article>,
        val filter: ReadFilter,
        val titles: Map<Long, String>,
        val folders: List<Folder>,
        val hasUncategorised: Boolean,
        val folderIds: Set<Long>,
    )

    private val shownArticles = combine(articles, filter, folderChoice, repo.observeFeeds(), repo.observeFolders()) { list, readFilter, saved, feeds, allFolders ->
        val choice = saved.toSet()
        val folderOf = feeds.associate { it.id to (it.folderId ?: UNCATEGORISED) }
        val present = list.map { folderOf[it.feedId] ?: UNCATEGORISED }.toSet()
        val offered = allFolders.filter { it.id in present }
        // Choices that have run out of articles are dropped, so the list falls back to all rather than stranding the user.
        val folderIds = if (source == ListSource.Unread) choice.filter { it in present }.toSet() else emptySet()
        val byRead = when (readFilter) {
            ReadFilter.ALL -> list
            ReadFilter.UNREAD -> list.filterNot { it.isRead }
            ReadFilter.READ -> list.filter { it.isRead }
        }
        Shown(
            list,
            if (folderIds.isEmpty()) byRead else byRead.filter { (folderOf[it.feedId] ?: UNCATEGORISED) in folderIds },
            readFilter,
            feeds.associate { it.id to it.title },
            offered,
            UNCATEGORISED in present,
            folderIds,
        )
    }

    private val undos = combine(bulkRead.pending, toggles.pending) { marked, toggle -> marked to toggle }

    val state: StateFlow<ArticleListState> = combine(shownArticles, refreshing, message, settingsRepo.settings, undos) { list, isRefreshing, msg, settings, (marked, toggle) ->
        ArticleListState(
            list.shown, list.filter, list.all.isNotEmpty(), list.folders, list.hasUncategorised, list.folderIds, list.titles,
            isRefreshing, settings.pullToRefresh && source == ListSource.Unread, settings.thumbnails, settings.thumbnailSide, msg, marked, toggle,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListState())

    fun setFilter(value: ReadFilter) { filter.value = value }
    fun toggleFolder(id: Long) {
        val now = folderChoice.value.toSet()
        chooseFolders(if (id in now) now - id else now + id)
    }
    /** Narrows the list to exactly [ids], replacing any earlier choice. */
    fun selectFolders(ids: Set<Long>) = chooseFolders(ids)
    fun clearFolders() = chooseFolders(emptySet())

    private fun chooseFolders(ids: Set<Long>) { handle[FOLDER_CHOICE_KEY] = ids.toLongArray() }

    fun toggleRead(article: Article) = toggles.toggleRead(article)
    fun toggleSaved(article: Article) = toggles.toggleSaved(article)
    fun undoToggle() = toggles.undo()
    fun dismissToggle() = toggles.dismiss()

    fun markAllRead() {
        val feeds = state.value.articles.map { it.feedId }.distinct()
        viewModelScope.launch { bulkRead.offer(feeds.flatMap { repo.markFeedRead(it) }) }
    }

    fun markOlderRead(article: Article) {
        viewModelScope.launch { bulkRead.offer(repo.markOlderRead(article.id)) }
    }

    fun undoMarkedRead() = bulkRead.undo()
    fun dismissMarkedRead() = bulkRead.dismiss()

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

    fun dismissMessage() { message.value = null }

    companion object {
        const val UNCATEGORISED = UNCATEGORISED_FOLDER_ID
        private const val FOLDER_CHOICE_KEY = "folderChoice"
    }
}
