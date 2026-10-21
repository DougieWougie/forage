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

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.remote.FeedSource
import me.dougals.forage.data.remote.ParsedItem
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.PartialRefreshException
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.model.Headline
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.model.newestHeadlines

/** Local Mode: feeds are fetched straight from their publishers; nothing is synced anywhere. */
class LocalFeedRepository(
    db: ForageDatabase,
    private val source: FeedSource,
    archive: ArchiveScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val progress: RefreshProgress = RefreshProgress(),
) : RoomFeedRepository(db, archive) {

    override suspend fun addFeed(url: String, folderId: Long?): Result<Feed> {
        val trimmed = url.trim()
        if (db.feedDao().getByUrl(trimmed) != null) return Result.failure(IllegalStateException("Already subscribed"))
        return attempt {
            val parsed = source.fetch(trimmed)
            val id = db.feedDao().insert(
                FeedEntity(url = trimmed, title = parsed.title.ifBlank { trimmed }, siteUrl = parsed.siteUrl, folderId = folderId),
            )
            store(id, parsed.items)
            feedWithCount(id)!!
        }
    }

    override suspend fun refresh(): Result<SyncReport> {
        var failure: Throwable? = null
        val freshByFeed = mutableMapOf<Long, Int>()
        val headlines = mutableMapOf<Long, List<Headline>>()
        val feeds = db.feedDao().getAll()
        try {
            feeds.forEachIndexed { i, feed ->
                progress.report(i, feeds.size)
                try {
                    val added = store(feed.id, source.fetch(feed.url).items)
                    if (added.isNotEmpty()) {
                        freshByFeed[feed.id] = added.size
                        headlines[feed.id] = newestHeadlines(added.map { Headline(it.title, it.publishedAt) })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = failure ?: e
                }
            }
        } finally {
            progress.clear()
        }
        val report = SyncReport(newArticles = freshByFeed.values.sum(), newByFeed = freshByFeed, newHeadlines = headlines)
        return failure?.let { Result.failure(if (freshByFeed.isEmpty()) it else PartialRefreshException(report, it)) }
            ?: Result.success(report)
    }

    override suspend fun removeFeed(feedId: Long): Result<Unit> {
        db.feedDao().delete(feedId)
        return Result.success(Unit)
    }

    override suspend fun moveFeed(feedId: Long, folderId: Long?): Result<Unit> {
        val feed = db.feedDao().getById(feedId) ?: return Result.failure(NoSuchElementException("Feed not found"))
        db.feedDao().update(feed.copy(folderId = folderId))
        return Result.success(Unit)
    }

    override suspend fun createFolder(name: String): Result<Folder> =
        Result.success(FolderEntity(db.folderDao().insert(FolderEntity(name = name)), name).toDomain())

    override suspend fun deleteFolder(folderId: Long): Result<Unit> {
        db.folderDao().delete(folderId)
        return Result.success(Unit)
    }

    /** @return the items that were new */
    private suspend fun store(feedId: Long, items: List<ParsedItem>): List<ArticleEntity> {
        items.forEach { item -> item.imageUrl?.let { db.articleDao().fillImageUrl(feedId, item.guid, it) } }
        val articles = items.map {
            ArticleEntity(
                feedId = feedId, guid = it.guid, title = it.title, url = it.url, author = it.author,
                summary = it.summary, contentHtml = it.contentHtml, imageUrl = it.imageUrl,
                publishedAt = if (it.publishedAt == 0L) clock() else it.publishedAt,
            )
        }
        val inserted = db.articleDao().insertIgnore(articles)
        return articles.filterIndexed { i, _ -> inserted[i] != -1L }
    }
}
