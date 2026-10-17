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
package me.dougals.forage.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.FeedRepository

/**
 * The repository the UI sees. Both implementations read the same Room tables, so reads always come from
 * [local]; every write and refresh goes to whichever implementation the current [mode] selects.
 */
class ModeAwareFeedRepository(
    private val mode: Flow<SyncMode>,
    private val local: FeedRepository,
    private val nextcloud: FeedRepository,
) : FeedRepository {
    private suspend fun current() = if (mode.first() == SyncMode.NEXTCLOUD) nextcloud else local

    override fun observeUnread(): Flow<List<Article>> = local.observeUnread()
    override fun observeSaved(): Flow<List<Article>> = local.observeSaved()
    override fun observeFeedArticles(feedId: Long): Flow<List<Article>> = local.observeFeedArticles(feedId)
    override fun observeArticle(articleId: Long): Flow<Article?> = local.observeArticle(articleId)
    override fun observeFolders(): Flow<List<Folder>> = local.observeFolders()
    override fun observeFeeds(): Flow<List<Feed>> = local.observeFeeds()

    override suspend fun addFeed(url: String, folderId: Long?) = current().addFeed(url, folderId)
    override suspend fun removeFeed(feedId: Long) = current().removeFeed(feedId)
    override suspend fun moveFeed(feedId: Long, folderId: Long?) = current().moveFeed(feedId, folderId)
    override suspend fun createFolder(name: String) = current().createFolder(name)
    override suspend fun deleteFolder(folderId: Long) = current().deleteFolder(folderId)
    override suspend fun setRead(articleId: Long, read: Boolean) = current().setRead(articleId, read)
    override suspend fun setSaved(articleId: Long, saved: Boolean) = current().setSaved(articleId, saved)
    override suspend fun markFeedRead(feedId: Long) = current().markFeedRead(feedId)
    override suspend fun refresh() = current().refresh()
}
