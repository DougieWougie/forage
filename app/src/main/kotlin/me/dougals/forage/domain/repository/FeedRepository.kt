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
package me.dougals.forage.domain.repository

import kotlinx.coroutines.flow.Flow
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.SyncReport

/**
 * Single seam for both operating modes: Local (direct RSS fetching) and Nextcloud Sync.
 * Reads always come from Room; writes update Room first and are queued for remote flush when needed.
 */
interface FeedRepository {
    fun observeUnread(): Flow<List<Article>>
    fun observeSaved(): Flow<List<Article>>
    fun observeFeedArticles(feedId: Long): Flow<List<Article>>
    fun observeArticle(articleId: Long): Flow<Article?>
    fun observeFolders(): Flow<List<Folder>>
    fun observeFeeds(): Flow<List<Feed>>

    suspend fun addFeed(url: String, folderId: Long? = null): Result<Feed>
    suspend fun removeFeed(feedId: Long): Result<Unit>
    /** Files the feed under [folderId], or under no folder when null. */
    suspend fun moveFeed(feedId: Long, folderId: Long?): Result<Unit>
    suspend fun createFolder(name: String): Result<Folder>
    suspend fun deleteFolder(folderId: Long): Result<Unit>

    suspend fun setRead(articleId: Long, read: Boolean)
    suspend fun setSaved(articleId: Long, saved: Boolean)
    suspend fun markFeedRead(feedId: Long)

    /** Pull new items (and, in Nextcloud mode, push queued changes first). */
    suspend fun refresh(): Result<SyncReport>
}
