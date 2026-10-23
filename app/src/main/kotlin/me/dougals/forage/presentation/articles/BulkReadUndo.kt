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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.dougals.forage.domain.repository.FeedRepository

/** The articles a bulk "mark read" just changed, only the latest batch, kept so the snackbar can mark them unread again. */
class BulkReadUndo(private val repo: FeedRepository, private val scope: CoroutineScope) {
    private val latest = MutableStateFlow<List<Long>>(emptyList())
    val pending: StateFlow<List<Long>> = latest

    /** A batch that changed nothing offers no undo and leaves any earlier one in place. */
    fun offer(changed: List<Long>) { if (changed.isNotEmpty()) latest.value = changed }

    fun undo() {
        val ids = latest.value
        latest.value = emptyList()
        scope.launch { repo.setReadBatch(ids, false) }
    }

    fun dismiss() { latest.value = emptyList() }
}
