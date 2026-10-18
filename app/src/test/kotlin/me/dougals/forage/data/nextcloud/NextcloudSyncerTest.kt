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
package me.dougals.forage.data.nextcloud

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.testutil.FakeNextcloudApi
import me.dougals.forage.testutil.MemSyncState
import me.dougals.forage.testutil.RecordingSchedulers
import me.dougals.forage.testutil.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NextcloudSyncerTest : DbTestBase() {
    private val api = FakeNextcloudApi()
    private val state = MemSyncState()
    private val schedulers = RecordingSchedulers()
    private val syncer get() = NextcloudSyncer(db, NextcloudApiProvider { api }, state, schedulers)

    private fun serverWithOneFeed() {
        api.folderList += FolderDto(1, "Tech")
        api.feedList += FeedDto(10, "https://lwn.net/rss", "LWN", folderId = 1)
    }

    @Test fun pullTakesTheServersThumbnailElseTheFirstImageInTheBody() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, feedId = 10).copy(mediaThumbnail = "https://cdn.example.com/t.jpg")
        api.itemList += item(2, feedId = 10).copy(body = """<p>x</p><img src="/pic.jpg">""")
        api.itemList += item(3, feedId = 10)
        syncer.pull()
        assertEquals("https://cdn.example.com/t.jpg", db.articleDao().getByRemoteId(1)!!.imageUrl)
        assertEquals("https://example.com/pic.jpg", db.articleDao().getByRemoteId(2)!!.imageUrl)
        assertEquals(null, db.articleDao().getByRemoteId(3)!!.imageUrl)
    }

    @Test fun laterPullBackfillsAThumbnailOnAnArticleStoredWithoutOne() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, feedId = 10)
        syncer.pull()
        assertEquals(null, db.articleDao().getByRemoteId(1)!!.imageUrl)

        api.itemList[0] = api.itemList[0].copy(mediaThumbnail = "https://cdn.example.com/t.jpg", lastModified = 999)
        syncer.pull()
        assertEquals("https://cdn.example.com/t.jpg", db.articleDao().getByRemoteId(1)!!.imageUrl)
    }

    @Test fun initialPullMapsFoldersFeedsAndItems() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, feedId = 10)
        syncer.pull()

        val folder = db.folderDao().getAll().single()
        assertEquals("Tech", folder.name)
        assertEquals(1L, folder.remoteId)
        val feed = db.feedDao().getAll().single()
        assertEquals(folder.id, feed.folderId)
        assertEquals(10L, feed.remoteId)
        val a = db.articleDao().getByRemoteId(1)!!
        assertEquals(feed.id, a.feedId)
        assertEquals("Item 1", a.title)
        assertEquals("<p>1</p>", a.contentHtml)
        assertEquals(1_700_000_001_000L, a.publishedAt)
        assertFalse(a.isRead)
    }

    @Test fun initialPullCombinesUnreadAndStarredWithoutDuplicates() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10, unread = true, starred = true)
        api.itemList += item(2, 10, unread = false, starred = true)
        api.itemList += item(3, 10, unread = false, starred = false) // read, not starred: not fetched
        syncer.pull()
        assertEquals(setOf(1L, 2L), db.articleDao().getAll().map { it.remoteId }.toSet())
        assertEquals(listOf("items:3", "items:2"), api.calls.filter { it.startsWith("items") })
        val two = db.articleDao().getByRemoteId(2)!!
        assertTrue(two.isRead)
        assertTrue(two.isSaved)
    }

    @Test fun laterPullsAreIncrementalFromStoredLastModified() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10, lastModified = 500)
        syncer.pull()
        assertEquals(500L, state.lastModified)

        api.calls.clear()
        api.itemList.replaceAll { it.copy(unread = false, lastModified = 900) } // read on another device
        syncer.pull()

        assertTrue(api.calls.contains("updated:500"))
        assertFalse(api.calls.any { it.startsWith("items") })
        assertTrue(db.articleDao().getByRemoteId(1)!!.isRead)
        assertEquals(900L, state.lastModified)
    }

    @Test fun pendingLocalChangesAreNotOverwrittenByRemoteState() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10, lastModified = 500)
        syncer.pull()
        val a = db.articleDao().getByRemoteId(1)!!
        db.articleDao().setRead(a.id, true)
        db.pendingChangeDao().enqueue(PendingChangeEntity(a.id, SyncField.READ, true, 1))

        api.itemList.replaceAll { it.copy(unread = true, starred = true, lastModified = 900) }
        syncer.pull()

        val after = db.articleDao().getByRemoteId(1)!!
        assertTrue("pending READ wins", after.isRead)
        assertTrue("unpended STARRED follows server", after.isSaved)
    }

    @Test fun folderAndFeedRemovedOnServerAreRemovedLocally() = runTest {
        serverWithOneFeed()
        syncer.pull()
        api.folderList.clear()
        api.feedList.clear()
        syncer.pull()
        assertEquals(0, db.folderDao().getAll().size)
        assertEquals(0, db.feedDao().getAll().size)
    }

    @Test fun renamedRemoteFolderAndFeedUpdateInPlace() = runTest {
        serverWithOneFeed()
        syncer.pull()
        val folderId = db.folderDao().getAll().single().id
        api.folderList[0] = FolderDto(1, "Renamed")
        api.feedList[0] = api.feedList[0].copy(title = "LWN 2")
        syncer.pull()
        assertEquals(listOf(folderId to "Renamed"), db.folderDao().getAll().map { it.id to it.name })
        assertEquals("LWN 2", db.feedDao().getAll().single().title)
    }

    @Test fun newlyStarredItemsGetOfflineArchiveScheduled() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10, starred = true)
        api.itemList += item(2, 10, starred = false)
        syncer.pull()
        assertEquals(listOf(db.articleDao().getByRemoteId(1)!!.id), schedulers.archived)
    }

    @Test fun itemsForUnknownFeedsAreSkipped() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, feedId = 999)
        syncer.pull()
        assertNull(db.articleDao().getByRemoteId(1))
    }

    @Test fun existingLocalArticleWithSameGuidIsAdoptedNotDuplicated() = runTest {
        serverWithOneFeed()
        syncer.pull()
        val feed = db.feedDao().getAll().single()
        db.articleDao().insertIgnore(listOf(article(feed.id, "guid-1")))
        api.itemList += item(1, 10)
        syncer.pull()
        val all = db.articleDao().getAll()
        assertEquals(1, all.size)
        assertEquals(1L, all.single().remoteId)
        assertNotNull(all.single())
    }

    @Test fun apiFailurePropagatesAndLeavesStateUntouched() = runTest {
        serverWithOneFeed()
        api.failOn = setOf("feeds")
        runCatching { syncer.pull() }.also { assertTrue(it.isFailure) }
        assertEquals(0L, state.lastModified)
    }

    @Test fun successfulPullRecordsWhenItHappened() = runTest {
        serverWithOneFeed()
        val before = System.currentTimeMillis()
        syncer.pull()
        assertTrue(state.lastSyncedAt >= before)
    }

    @Test fun failedPullDoesNotRecordASync() = runTest {
        serverWithOneFeed()
        api.failOn = setOf("feeds")
        runCatching { syncer.pull() }
        assertEquals(0L, state.lastSyncedAt)
    }

    @Test fun pullReturnsHowManyUnreadArticlesWereNew() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10); api.itemList += item(2, 10, unread = false, starred = true)
        assertEquals(1, syncer.pull().newArticles)
        assertEquals(0, syncer.pull().newArticles)
    }

    @Test fun pullReportsFeedsFromTheServer() = runTest {
        serverWithOneFeed()
        assertEquals(1, syncer.pull().feedsReceived)
        assertEquals(0, syncer.pull().feedsReceived)
        api.feedList.clear()
        assertEquals(1, syncer.pull().feedsRemoved)
    }

    @Test fun feedAdoptedFromLocalIsNotReportedAsReceived() = runTest {
        serverWithOneFeed()
        db.feedDao().insert(FeedEntity(url = "https://lwn.net/rss", title = "LWN"))
        assertEquals(0, syncer.pull().feedsReceived)
    }

    @Test fun pullReportsArticlesMarkedReadOrSavedElsewhere() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10, lastModified = 500); api.itemList += item(2, 10, lastModified = 500)
        syncer.pull()
        api.itemList.replaceAll { it.copy(unread = it.id != 1L, starred = it.id == 2L, lastModified = 900) }
        val r = syncer.pull()
        assertEquals(1, r.markedRead)
        assertEquals(1, r.saved)
        assertEquals(0, r.newArticles)
    }

    @Test fun localOnlyFolderWithADifferentNameIsKept() = runTest {
        val mine = db.folderDao().insert(FolderEntity(name = "Mine"))
        serverWithOneFeed()
        syncer.pull()
        assertNull(db.folderDao().getById(mine)!!.remoteId)
        assertEquals(setOf("Mine", "Tech"), db.folderDao().getAll().map { it.name }.toSet())
    }

    @Test fun feedWithoutALinkKeepsTheSiteItHad() = runTest {
        api.feedList += FeedDto(10, "https://lwn.net/rss", "LWN", link = "https://lwn.net/")
        syncer.pull()
        api.feedList[0] = api.feedList[0].copy(link = null)
        syncer.pull()
        assertEquals("https://lwn.net/", db.feedDao().getAll().single().siteUrl)
    }

    @Test fun localStateThatAlreadyMatchesTheServerIsNotQueued() = runTest {
        serverWithOneFeed()
        syncer.pull()
        val feed = db.feedDao().getAll().single()
        db.articleDao().insertIgnore(
            listOf(ArticleEntity(feedId = feed.id, guid = "guid-1", title = "t", url = "u", publishedAt = 0, isRead = true, isSaved = true)),
        )
        api.itemList += item(1, 10, unread = false, starred = true)
        syncer.pull()
        assertEquals(emptyList<Any>(), db.pendingChangeDao().getAll())
        assertEquals(1L, db.articleDao().getAll().single().remoteId)
    }

    @Test fun itemsWithoutTitleLinkOrDateAreStoredWithBlanks() = runTest {
        serverWithOneFeed()
        api.itemList += item(1, 10).copy(title = null, url = null, pubDate = null)
        syncer.pull()
        val a = db.articleDao().getByRemoteId(1)!!
        assertEquals("", a.title)
        assertEquals("", a.url)
        assertEquals(0L, a.publishedAt)
    }
}
