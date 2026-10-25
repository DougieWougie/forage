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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.dougals.forage.domain.model.FeedProgress
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.repository.FeedRepository
import javax.inject.Inject

/** [ready] once the refresh has ended; [newArticles] is null when it failed or timed out. */
data class SplashState(
    val progress: FeedProgress? = null,
    val newArticles: Int? = null,
    /** How many categories (folders) those articles landed in; uncategorised feeds don't count. */
    val categories: Int = 0,
    val ready: Boolean = false,
    val finished: Boolean = false,
)

/**
 * Refreshes feeds once at launch. When the refresh ends the splash reports what it found and waits for
 * [continueNow]; [SplashState.finished] flips on a tap or after [AUTO_MS], so it can never get stuck.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(private val repo: FeedRepository, progress: RefreshProgress) : ViewModel() {
    private val outcome = MutableStateFlow(SplashState())
    private val tapped = CompletableDeferred<Unit>()

    val state: StateFlow<SplashState> = combine(progress.state, outcome) { p, o -> o.copy(progress = p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SplashState())

    init {
        viewModelScope.launch {
            val report = withTimeoutOrNull(TIMEOUT_MS) { repo.refresh() }?.getOrNull()
            outcome.value = SplashState(newArticles = report?.newArticles, categories = report?.let { categoriesWithNew(it) } ?: 0, ready = true)
            withTimeoutOrNull(AUTO_MS) { tapped.await() }
            outcome.value = outcome.value.copy(finished = true)
        }
    }

    private suspend fun categoriesWithNew(report: SyncReport): Int {
        if (report.newByFeed.isEmpty()) return 0
        return repo.observeFeeds().first().filter { it.id in report.newByFeed }.mapNotNull { it.folderId }.toSet().size
    }

    /** Ignored until the refresh has ended, so an early tap can't skip the result. */
    fun continueNow() {
        if (outcome.value.ready) tapped.complete(Unit)
    }

    companion object {
        const val AUTO_MS = 5_000L
        const val TIMEOUT_MS = 10_000L
    }
}
