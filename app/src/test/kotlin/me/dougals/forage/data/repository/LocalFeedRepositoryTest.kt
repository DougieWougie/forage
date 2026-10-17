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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.remote.ParsedFeed
import me.dougals.forage.data.remote.ParsedItem
import me.dougals.forage.testutil.FakeFeedSource
import me.dougals.forage.testutil.RecordingSchedulers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalFeedRepositoryTest : DbTestBase() {
    private val source = FakeFeedSource()
    private val schedulers = RecordingSchedulers()
    private val repo by lazy { LocalFeedRepository(db, source, schedulers) }

    private fun parsed(title: String, vararg guids: String) = ParsedFeed(
        title, "https://site/",
        guids.mapIndexed { i, g ->
            ParsedItem(g, "T-$g", "https://site/$g", "Ann", "sum", "<p>$g</p>", publishedAt = 1000L + i)
        },
    )

    @Test fun addFeedFetchesStoresFeedAndArticles() = runTest {
        source.feeds["https://a/feed"] = parsed("Feed A", "1", "2")
        val feed = repo.addFeed("https://a/feed").getOrThrow()
        assertEquals("Feed A", feed.title)
        assertEquals("https://site/", feed.siteUrl)
        assertEquals(2, feed.unreadCount)
        assertEquals(2, repo.observeUnread().first().size)
    }

    @Test fun addFeedFileUnderFolder() = runTest {
        source.feeds["https://a/feed"] = parsed("A")
        val folder = repo.createFolder("Tech").getOrThrow()
        val feed = repo.addFeed("https://a/feed", folder.id).getOrThrow()
        assertEquals(folder.id, feed.folderId)
    }

    @Test fun addFeedFetchFailureStoresNothing() = runTest {
        source.failing += "https://a/feed"
        assertTrue(repo.addFeed("https://a/feed").isFailure)
        assertEquals(0, db.feedDao().getAll().size)
    }

    @Test fun addingSameUrlTwiceFails() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        repo.addFeed("https://a/feed").getOrThrow()
        assertTrue(repo.addFeed("https://a/feed").isFailure)
        assertEquals(1, db.feedDao().getAll().size)
    }

    @Test fun refreshAddsOnlyNewItemsAndKeepsReadState() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        repo.addFeed("https://a/feed").getOrThrow()
        repo.setRead(repo.observeUnread().first().single().id, true)

        source.feeds["https://a/feed"] = parsed("A", "1", "2")
        assertEquals(me.dougals.forage.domain.model.SyncReport(newArticles = 1), repo.refresh().getOrThrow())

        assertEquals(listOf("2"), repo.observeUnread().first().map { it.guid })
        assertEquals(2, db.articleDao().getAll().size)
    }

    @Test fun oneFailingFeedDoesNotBlockOthersButIsReported() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        source.feeds["https://b/feed"] = parsed("B", "1")
        repo.addFeed("https://a/feed").getOrThrow()
        repo.addFeed("https://b/feed").getOrThrow()
        source.feeds["https://b/feed"] = parsed("B", "1", "2")
        source.failing += "https://a/feed"
        assertTrue(repo.refresh().isFailure)
        assertEquals(3, db.articleDao().getAll().size)
    }

    @Test fun savingSchedulesArchiveAndUnsavingDropsIt() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        repo.addFeed("https://a/feed").getOrThrow()
        val id = repo.observeUnread().first().single().id
        repo.setSaved(id, true)
        assertEquals(listOf(id), schedulers.archived)
        assertEquals(listOf(id), repo.observeSaved().first().map { it.id })
        db.articleDao().saveOfflineHtml(id, "<p/>")
        repo.setSaved(id, false)
        assertNull(db.articleDao().getById(id)!!.offlineHtml)
        assertEquals(0, repo.observeSaved().first().size)
    }

    @Test fun localModeNeverQueuesSyncChangesOrSchedulesFlush() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        repo.addFeed("https://a/feed").getOrThrow()
        val id = repo.observeUnread().first().single().id
        repo.setRead(id, true)
        repo.setSaved(id, true)
        assertEquals(0, db.pendingChangeDao().getAll().size)
        assertEquals(0, schedulers.flushes)
    }

    @Test fun deletingFolderKeepsItsFeeds() = runTest {
        source.feeds["https://a/feed"] = parsed("A")
        val folder = repo.createFolder("Tech").getOrThrow()
        val feed = repo.addFeed("https://a/feed", folder.id).getOrThrow()
        repo.deleteFolder(folder.id)
        assertNull(db.feedDao().getById(feed.id)!!.folderId)
        assertNotNull(db.feedDao().getById(feed.id))
    }

    @Test fun removeFeedDeletesItsArticles() = runTest {
        source.feeds["https://a/feed"] = parsed("A", "1")
        val feed = repo.addFeed("https://a/feed").getOrThrow()
        repo.removeFeed(feed.id)
        assertEquals(0, db.articleDao().getAll().size)
    }
}
