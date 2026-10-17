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
package me.dougals.forage.presentation.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.ui.TabBadges
import javax.inject.Inject

@HiltViewModel
class TabBadgesViewModel @Inject constructor(repo: FeedRepository) : ViewModel() {
    val state: StateFlow<TabBadges> = repo.observeFeeds()
        .map { feeds -> TabBadges(unread = feeds.sumOf { it.unreadCount }, updatedFeeds = feeds.count { it.unreadCount > 0 }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabBadges())
}
