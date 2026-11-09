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

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.testutil.RecordingSchedulers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A queue write that fails part-way must take the article change with it, or a local change would never sync. */
@RunWith(RobolectricTestRunner::class)
class RoomFeedRepositoryTransactionTest : DbTestBase() {
    private val schedulers = RecordingSchedulers()

    /** Queues like the Nextcloud repository does, but blows up on [failOn]. */
    private inner class Repo(var failOn: Long? = null) : RoomFeedRepository(db, schedulers) {
        var writesDone = 0
        override suspend fun onStateChanged(articleId: Long, field: SyncField, value: Boolean) {
            if (articleId == failOn) error("disk full")
            db.pendingChangeDao().enqueue(PendingChangeEntity(articleId, field, value, 1))
        }
        override fun onWritesDone() { writesDone++ }
        override suspend fun addFeed(url: String, folderId: Long?) = error("unused")
        override suspend fun removeFeed(feedId: Long) = error("unused")
        override suspend fun moveFeed(feedId: Long, folderId: Long?) = error("unused")
        override suspend fun createFolder(name: String) = error("unused")
        override suspend fun deleteFolder(folderId: Long) = error("unused")
        override suspend fun refresh() = error("unused")
    }

    private suspend fun seed(count: Int): List<Long> {
        val feed = newFeed()
        db.articleDao().insertIgnore((1..count).map { articleEntity(feed, "g$it", publishedAt = it.toLong()) })
        return db.articleDao().getAll().map { it.id }
    }

    private suspend fun unreadCount() = db.articleDao().getAll().count { !it.isRead }

    @Test fun setReadAndItsQueuedChangeCommitTogether() = runTest {
        val (id) = seed(1)
        val repo = Repo()
        repo.setRead(id, true)
        assertTrue(db.articleDao().getById(id)!!.isRead)
        assertEquals(1, db.pendingChangeDao().getAll().size)
        assertEquals(1, repo.writesDone)
    }

    @Test fun setReadRollsBackWhenTheChangeCannotBeQueued() = runTest {
        val (id) = seed(1)
        val repo = Repo(failOn = id)
        runCatching { repo.setRead(id, true) }
        assertEquals(false, db.articleDao().getById(id)!!.isRead)
        assertEquals(0, db.pendingChangeDao().getAll().size)
        assertEquals(0, repo.writesDone)
    }

    @Test fun setSavedRollsBackWhenTheChangeCannotBeQueued() = runTest {
        val (id) = seed(1)
        runCatching { Repo(failOn = id).setSaved(id, true) }
        assertEquals(false, db.articleDao().getById(id)!!.isSaved)
        assertTrue(schedulers.archived.isEmpty())
    }

    @Test fun setSavedStillSchedulesTheArchiveAfterTheCommit() = runTest {
        val (id) = seed(1)
        Repo().setSaved(id, true)
        assertTrue(db.articleDao().getById(id)!!.isSaved)
        assertEquals(listOf(id), schedulers.archived)
    }

    @Test fun setReadBatchIsAllOrNothing() = runTest {
        val ids = seed(3)
        runCatching { Repo(failOn = ids[2]).setReadBatch(ids, true) }
        assertEquals(3, unreadCount())
        assertEquals(0, db.pendingChangeDao().getAll().size)
    }

    @Test fun markFeedReadIsAllOrNothing() = runTest {
        val ids = seed(3)
        val feedId = db.articleDao().getById(ids[0])!!.feedId
        runCatching { Repo(failOn = ids[1]).markFeedRead(feedId) }
        assertEquals(3, unreadCount())
        assertEquals(0, db.pendingChangeDao().getAll().size)
    }

    @Test fun markFeedReadReturnsTheIdsItMarkedAndQueuesThemAll() = runTest {
        val ids = seed(3)
        val feedId = db.articleDao().getById(ids[0])!!.feedId
        assertEquals(ids.toSet(), Repo().markFeedRead(feedId).toSet())
        assertEquals(0, unreadCount())
        assertEquals(3, db.pendingChangeDao().getAll().size)
    }

    @Test fun pendingChangesComeBackJoinedWithTheArticlesRemoteId() = runTest {
        val feed = newFeed()
        val ids = db.articleDao().insertIgnore(
            listOf(articleEntity(feed, "a").copy(remoteId = 77, offlineHtml = "<p>big</p>"), articleEntity(feed, "b")),
        )
        db.pendingChangeDao().enqueue(PendingChangeEntity(ids[0], SyncField.READ, true, 1))
        db.pendingChangeDao().enqueue(PendingChangeEntity(ids[1], SyncField.READ, true, 2))
        val joined = db.pendingChangeDao().getAllWithRemoteId()
        assertEquals(listOf(77L, null), joined.map { it.remoteId })
        assertEquals(listOf(ids[0], ids[1]), joined.map { it.change.articleId })
    }

    @Test fun remoteIdLookupsAndListQueriesUseTheirIndices() = runTest {
        fun plan(sql: String): String = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(3) else null }.joinToString("\n")
        }
        assertTrue(plan("SELECT id FROM articles WHERE remoteId = 5").contains("index_articles_remoteId"))
        val unread = plan("SELECT id FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC")
        assertTrue(unread, unread.contains("index_articles_isRead_publishedAt") && !unread.contains("TEMP B-TREE"))
        val byFeed = plan("SELECT id FROM articles WHERE feedId = 1 ORDER BY publishedAt DESC")
        assertTrue(byFeed, byFeed.contains("index_articles_feedId_publishedAt") && !byFeed.contains("TEMP B-TREE"))
    }
}
