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
package me.dougals.forage.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.data.content.ImageResourceFetcher
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import javax.inject.Inject

data class ReaderState(val article: Article? = null, val settings: AppSettings = AppSettings())

@HiltViewModel
class ReaderViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: FeedRepository,
    private val settingsRepo: SettingsRepository,
    /** Fetches reader images; the WebView itself never touches the network. */
    val images: ImageResourceFetcher,
) : ViewModel() {
    private val articleId: Long = requireNotNull(handle.get<Long>("articleId")) { "articleId required" }
    private val article = repo.observeArticle(articleId)

    val state: StateFlow<ReaderState> = combine(article, settingsRepo.settings) { a, s -> ReaderState(a, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderState())

    init {
        viewModelScope.launch {
            val opened = article.first { it != null }!!
            if (!opened.isRead) repo.setRead(articleId, true)
        }
    }

    fun toggleSaved() {
        viewModelScope.launch {
            val current = article.first() ?: return@launch
            repo.setSaved(articleId, !current.isSaved)
        }
    }

    fun setFont(font: ReaderFont) { viewModelScope.launch { settingsRepo.setReaderFont(font) } }
    fun setFontScale(scale: Float) { viewModelScope.launch { settingsRepo.setFontScale(scale) } }
}
