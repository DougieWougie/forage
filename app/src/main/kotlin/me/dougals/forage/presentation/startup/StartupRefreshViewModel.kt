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
package me.dougals.forage.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.domain.model.FeedProgress
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.repository.FeedRepository
import javax.inject.Inject

/** [refreshing] is true while the launch refresh runs; [progress] is its position, when known. */
data class StartupState(val refreshing: Boolean = true, val progress: FeedProgress? = null)

/**
 * Refreshes feeds once at launch while the app already shows the cached article list. A failed refresh is
 * silent: the list is simply as fresh as it was. Created once per activity, so rotation does not repeat it.
 */
@HiltViewModel
class StartupRefreshViewModel @Inject constructor(private val repo: FeedRepository, progress: RefreshProgress) : ViewModel() {
    private val running = MutableStateFlow(true)

    val state: StateFlow<StartupState> = combine(running, progress.state) { r, p -> StartupState(r, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StartupState())

    init {
        viewModelScope.launch {
            try { repo.refresh() } finally { running.value = false }
        }
    }
}
