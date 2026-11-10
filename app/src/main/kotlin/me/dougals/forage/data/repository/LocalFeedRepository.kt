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

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.dougals.forage.data.content.ArticleSnippets
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.remote.CacheValidators
import me.dougals.forage.data.remote.FeedResponse
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
import java.util.concurrent.atomic.AtomicInteger

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
        val feeds = db.feedDao().getAll()
        val outcomes = arrayOfNulls<FeedOutcome>(feeds.size)
        try {
            progress.report(0, feeds.size)
            val gate = Semaphore(MAX_PARALLEL_FETCHES)
            val completed = AtomicInteger()
            coroutineScope {
                feeds.forEachIndexed { i, feed ->
                    launch {
                        outcomes[i] = gate.withPermit { refreshOne(feed) }
                        val done = completed.incrementAndGet()
                        // The last report would be cleared at once, so the bar stops at "n-1 of n" as it always has.
                        if (done < feeds.size) progress.report(done, feeds.size)
                    }
                }
            }
        } finally {
            progress.clear()
        }
        // Folded in feed order, so "the first error" does not depend on which request happened to finish first.
        var failure: Throwable? = null
        val freshByFeed = mutableMapOf<Long, Int>()
        val headlines = mutableMapOf<Long, List<Headline>>()
        feeds.forEachIndexed { i, feed ->
            when (val o = outcomes[i]) {
                is FeedOutcome.Added -> {
                    freshByFeed[feed.id] = o.articles.size
                    headlines[feed.id] = newestHeadlines(o.articles.map { Headline(it.title, it.publishedAt) })
                }
                is FeedOutcome.Failed -> failure = failure ?: o.error
                else -> {}
            }
        }
        val report = SyncReport(newArticles = freshByFeed.values.sum(), newByFeed = freshByFeed, newHeadlines = headlines)
        return failure?.let { Result.failure(if (freshByFeed.isEmpty()) it else PartialRefreshException(report, it)) }
            ?: Result.success(report)
    }

    private sealed interface FeedOutcome {
        data object Unchanged : FeedOutcome
        class Added(val articles: List<ArticleEntity>) : FeedOutcome
        class Failed(val error: Throwable) : FeedOutcome
    }

    /** One failing feed must not stop the others, so everything but cancellation becomes a [FeedOutcome.Failed]. */
    private suspend fun refreshOne(feed: FeedEntity): FeedOutcome = try {
        when (val response = source.fetchConditional(feed.url, CacheValidators(feed.etag, feed.lastModified))) {
            FeedResponse.NotModified -> FeedOutcome.Unchanged
            is FeedResponse.Fresh -> {
                val added = store(feed.id, response.feed.items)
                // Remembered only once the body is safely stored, so a failure here means the full feed is asked for again.
                db.feedDao().setValidators(feed.id, response.validators.etag, response.validators.lastModified)
                if (added.isEmpty()) FeedOutcome.Unchanged else FeedOutcome.Added(added)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FeedOutcome.Failed(e)
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
        val articles = withContext(Dispatchers.Default) { items.map {
            ArticleEntity(
                feedId = feedId, guid = it.guid, title = it.title, url = it.url, author = it.author,
                summary = it.summary, contentHtml = it.contentHtml, imageUrl = it.imageUrl,
                snippet = ArticleSnippets.of(it.summary, it.contentHtml),
                publishedAt = if (it.publishedAt == 0L) clock() else it.publishedAt,
            )
        } }
        val inserted = db.withTransaction {
            items.forEach { item -> item.imageUrl?.let { db.articleDao().fillImageUrl(feedId, item.guid, it) } }
            db.articleDao().insertIgnore(articles)
        }
        return articles.filterIndexed { i, _ -> inserted[i] != -1L }
    }
}

/** Feeds fetched at once. Each is held in memory while it parses, so this stays modest. */
internal const val MAX_PARALLEL_FETCHES = 6
