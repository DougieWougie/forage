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
package me.dougals.forage.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import me.dougals.forage.presentation.articles.ArticleToggles
import me.dougals.forage.presentation.articles.ToggleUndo
import javax.inject.Inject

data class SearchState(
    /** What the user has typed so far. */
    val query: String = "",
    /** The term [articles] answers, which trails [query] while the user is still typing. */
    val resultsFor: String = "",
    val articles: List<Article> = emptyList(),
    val feedTitles: Map<Long, String> = emptyMap(),
    val thumbnails: ThumbnailMode = ThumbnailMode.ALWAYS,
    val thumbnailSide: ThumbnailSide = ThumbnailSide.END,
    val toggled: ToggleUndo? = null,
) {
    val hasQuery get() = query.isNotBlank()
    /** True once the results on screen are for the current query and there are none. */
    val noResults get() = hasQuery && articles.isEmpty() && resultsFor == query.trim()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: FeedRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val toggles = ArticleToggles(repo, viewModelScope)

    private class Found(val term: String, val articles: List<Article>)

    private val found = query.map { it.trim() }.debounce(DEBOUNCE_MS).distinctUntilChanged()
        .flatMapLatest { term -> repo.search(term).map { Found(term, it) } }

    val state: StateFlow<SearchState> = combine(query, found, repo.observeFeeds(), settingsRepo.settings, toggles.pending) { typed, hits, feeds, settings, toggle ->
        SearchState(
            typed, hits.term, hits.articles, feeds.associate { it.id to it.title },
            settings.thumbnails, settings.thumbnailSide, toggle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun setQuery(value: String) { query.value = value }

    fun toggleRead(article: Article) = toggles.toggleRead(article)
    fun toggleSaved(article: Article) = toggles.toggleSaved(article)
    fun undoToggle() = toggles.undo()
    fun dismissToggle() = toggles.dismiss()

    private companion object { const val DEBOUNCE_MS = 250L }
}
