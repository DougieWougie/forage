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
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.repository.FeedRepository
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

data class ArticleListState(
    val articles: List<Article> = emptyList(),
    val feedTitles: Map<Long, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: FeedRepository,
) : ViewModel() {
    val source = ListSource.from(handle)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val articles = when (source) {
        ListSource.Unread -> repo.observeUnread()
        ListSource.Saved -> repo.observeSaved()
        is ListSource.Feed -> repo.observeFeedArticles(source.feedId)
    }

    val state: StateFlow<ArticleListState> = combine(
        articles, repo.observeFeeds().map { feeds -> feeds.associate { it.id to it.title } }, refreshing, error,
    ) { list, titles, isRefreshing, err -> ArticleListState(list, titles, isRefreshing, err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListState())

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
            repo.refresh().onFailure { error.value = it.message ?: "Refresh failed" }
            refreshing.value = false
        }
    }

    fun dismissError() { error.value = null }
}
