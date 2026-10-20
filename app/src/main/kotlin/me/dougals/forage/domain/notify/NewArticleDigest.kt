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

/** One category's share of a digest; [folder] is null for feeds in no folder. */
data class DigestLine(val folder: String?, val count: Int)

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

        val folderOfFeed = repo.observeFeeds().first().associate { it.id to it.folderId }
        val perFolder = report.newByFeed.entries
            .groupingBy { folderOfFeed[it.key] ?: UNCATEGORISED_FOLDER_ID }
            .fold(0) { total, entry -> total + entry.value }
        val named = repo.observeFolders().first()
            .filter { it.id in current.notifyFolderIds }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { folder -> perFolder[folder.id]?.let { DigestLine(folder.name, it) } }
        val uncategorised = perFolder[UNCATEGORISED_FOLDER_ID]
            ?.takeIf { UNCATEGORISED_FOLDER_ID in current.notifyFolderIds }
            ?.let { DigestLine(null, it) }
        val lines = named + listOfNotNull(uncategorised)
        if (lines.isNotEmpty()) notifier.post(lines)
    }
}
