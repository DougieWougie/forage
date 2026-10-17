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
package me.dougals.forage.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Feeds fetched so far by the refresh now running. */
data class FeedProgress(val done: Int, val total: Int)

/** Live progress of a feed refresh; null when none is running or the mode can't say (Nextcloud syncs in one step). */
class RefreshProgress {
    private val current = MutableStateFlow<FeedProgress?>(null)
    val state: StateFlow<FeedProgress?> = current

    fun report(done: Int, total: Int) { current.value = FeedProgress(done, total) }
    fun clear() { current.value = null }
}
