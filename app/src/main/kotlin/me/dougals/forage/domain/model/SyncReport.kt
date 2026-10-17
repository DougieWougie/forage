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

/** What a refresh actually changed, for telling the user. */
data class SyncReport(val newArticles: Int = 0, val changesSent: Int = 0, val feedsAdded: Int = 0) {
    fun summary(): String {
        val parts = buildList {
            if (feedsAdded > 0) add("$feedsAdded ${if (feedsAdded == 1) "feed" else "feeds"} added to Nextcloud")
            if (newArticles > 0) add("$newArticles new ${if (newArticles == 1) "article" else "articles"}")
            if (changesSent > 0) add("$changesSent ${if (changesSent == 1) "change" else "changes"} sent")
        }
        return if (parts.isEmpty()) "Already up to date" else "Synced · " + parts.joinToString(" · ")
    }
}
