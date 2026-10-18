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
package me.dougals.forage.presentation

import me.dougals.forage.R
import me.dougals.forage.domain.model.SyncReport

/** "Synced · 2 feeds from Nextcloud · 18 new articles · 3 changes sent", or "Already up to date". */
fun SyncReport.toUiText(): UiText {
    val parts = buildList {
        if (feedsAdded > 0) add(UiText.Plural(R.plurals.feeds_added_to_nextcloud, feedsAdded))
        if (feedsReceived > 0) add(UiText.Plural(R.plurals.feeds_from_nextcloud, feedsReceived))
        if (feedsRemoved > 0) add(UiText.Plural(R.plurals.feeds_removed, feedsRemoved))
        if (newArticles > 0) add(UiText.Plural(R.plurals.new_articles, newArticles))
        if (markedRead > 0) add(UiText.Plural(R.plurals.n_marked_read, markedRead))
        if (saved > 0) add(UiText.Plural(R.plurals.n_saved, saved))
        if (changesSent > 0) add(UiText.Plural(R.plurals.changes_sent, changesSent))
    }
    return if (parts.isEmpty()) UiText.Res(R.string.up_to_date) else UiText.Joined(listOf(UiText.Res(R.string.synced)) + parts, " · ")
}
