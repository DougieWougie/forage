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
package me.dougals.forage.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.dougals.forage.domain.model.FeedProgress
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.repository.FeedRepository
import javax.inject.Inject

data class SplashState(val progress: FeedProgress? = null, val finished: Boolean = false)

/** Refreshes feeds once at launch; [SplashState.finished] flips [HOLD_MS] after the refresh ends, or gives up after [TIMEOUT_MS]. */
@HiltViewModel
class SplashViewModel @Inject constructor(repo: FeedRepository, progress: RefreshProgress) : ViewModel() {
    private val finished = MutableStateFlow(false)

    val state: StateFlow<SplashState> = combine(progress.state, finished, ::SplashState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SplashState())

    init {
        viewModelScope.launch {
            withTimeoutOrNull(TIMEOUT_MS) { repo.refresh() }
            delay(HOLD_MS)
            finished.value = true
        }
    }

    companion object {
        const val HOLD_MS = 500L
        const val TIMEOUT_MS = 10_000L
    }
}
