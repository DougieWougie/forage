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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.R
import me.dougals.forage.domain.model.Article
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
    val feedTitles: Map<Long, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val pullToRefresh: Boolean = true,
    val thumbnails: ThumbnailMode = ThumbnailMode.ALWAYS,
    val thumbnailSide: ThumbnailSide = ThumbnailSide.END,
    val message: UiText? = null,
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: FeedRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val source = ListSource.from(handle)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<UiText?>(null)
    private val filter = MutableStateFlow(ReadFilter.ALL)

    private val articles = when (source) {
        ListSource.Unread -> repo.observeUnread()
        ListSource.Saved -> repo.observeSaved()
        is ListSource.Feed -> repo.observeFeedArticles(source.feedId)
    }

    private class Shown(val all: List<Article>, val shown: List<Article>, val filter: ReadFilter)

    private val shownArticles = combine(articles, filter) { list, readFilter ->
        Shown(
            list,
            when (readFilter) {
                ReadFilter.ALL -> list
                ReadFilter.UNREAD -> list.filterNot { it.isRead }
                ReadFilter.READ -> list.filter { it.isRead }
            },
            readFilter,
        )
    }

    val state: StateFlow<ArticleListState> = combine(
        shownArticles, repo.observeFeeds().map { feeds -> feeds.associate { it.id to it.title } }, refreshing, message,
        settingsRepo.settings,
    ) { list, titles, isRefreshing, msg, settings ->
        ArticleListState(
            list.shown, list.filter, list.all.isNotEmpty(), titles, isRefreshing, settings.pullToRefresh && source == ListSource.Unread, settings.thumbnails, settings.thumbnailSide, msg,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListState())

    fun setFilter(value: ReadFilter) { filter.value = value }

    fun toggleRead(article: Article) { viewModelScope.launch { repo.setRead(article.id, !article.isRead) } }
    fun toggleSaved(article: Article) { viewModelScope.launch { repo.setSaved(article.id, !article.isSaved) } }

    fun markAllRead() {
        val feeds = state.value.articles.map { it.feedId }.distinct()
        viewModelScope.launch { feeds.forEach { repo.markFeedRead(it) } }
    }

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
}
