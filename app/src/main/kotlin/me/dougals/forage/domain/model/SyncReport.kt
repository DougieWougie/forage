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
data class SyncReport(
    val newArticles: Int = 0,
    val changesSent: Int = 0,
    val feedsAdded: Int = 0,
    val markedRead: Int = 0,
    val saved: Int = 0,
    val feedsReceived: Int = 0,
    val feedsRemoved: Int = 0,
    /** [newArticles] broken down by local feed id; feeds that gained nothing are absent. */
    val newByFeed: Map<Long, Int> = emptyMap(),
)
