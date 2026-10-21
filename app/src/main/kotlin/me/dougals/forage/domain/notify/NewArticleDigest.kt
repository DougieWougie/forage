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
package me.dougals.forage.domain.notify

import kotlinx.coroutines.flow.first
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.UNCATEGORISED_FOLDER_ID
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.nextcloud.SyncStateStore
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository

/**
 * One category's share of a digest. [folder] is null for feeds in no folder, whose [folderId] is
 * [UNCATEGORISED_FOLDER_ID]. [feeds] are the titles of the feeds that gained articles, busiest first.
 */
data class DigestLine(val folder: String?, val count: Int, val folderId: Long, val feeds: List<String> = emptyList())

interface DigestNotifier {
    /** Shows the digest, replacing any earlier one. May do nothing when the system disallows notifications. */
    fun post(lines: List<DigestLine>)
    fun clear()
}

/**
 * Turns what a background refresh found into at most one quiet notification, limited to the folders the user opted in.
 * Refresh intervals are an hour or more, which is also the most often a digest can appear.
 */
class NewArticleDigest(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    private val syncState: SyncStateStore,
    private val notifier: DigestNotifier,
) {
    /**
     * False before the first Nextcloud pull, which brings down the whole unread backlog as "new".
     * Read before refreshing, and announce only when it was true.
     */
    suspend fun hasBaseline(): Boolean =
        settings.settings.first().syncMode != SyncMode.NEXTCLOUD || syncState.lastSyncedAt != 0L

    suspend fun announce(report: SyncReport) {
        if (report.newByFeed.isEmpty()) return
        val current = settings.settings.first()
        if (current.notifyFolderIds.isEmpty()) return

        val feeds = repo.observeFeeds().first().associateBy { it.id }
        val perFolder = report.newByFeed.entries
            .filter { it.key in feeds }
            .groupBy { feeds.getValue(it.key).folderId ?: UNCATEGORISED_FOLDER_ID }
        fun line(name: String?, id: Long) = perFolder[id]?.let { entries ->
            val busiest = entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { feeds.getValue(it.key).title.lowercase() })
            DigestLine(name, entries.sumOf { it.value }, id, busiest.map { feeds.getValue(it.key).title })
        }
        val named = repo.observeFolders().first()
            .filter { it.id in current.notifyFolderIds }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { line(it.name, it.id) }
        val uncategorised = line(null, UNCATEGORISED_FOLDER_ID)?.takeIf { UNCATEGORISED_FOLDER_ID in current.notifyFolderIds }
        val lines = named + listOfNotNull(uncategorised)
        if (lines.isNotEmpty()) notifier.post(lines)
    }
}
