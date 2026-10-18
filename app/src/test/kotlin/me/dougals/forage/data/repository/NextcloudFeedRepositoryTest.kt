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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.nextcloud.FeedDto
import me.dougals.forage.data.nextcloud.NextcloudApiProvider
import me.dougals.forage.data.nextcloud.NextcloudSyncer
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.testutil.FakeNextcloudApi
import me.dougals.forage.testutil.MemSyncState
import me.dougals.forage.testutil.RecordingSchedulers
import me.dougals.forage.testutil.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NextcloudFeedRepositoryTest : DbTestBase() {
    private val api = FakeNextcloudApi()
    private val schedulers = RecordingSchedulers()
    private val provider = NextcloudApiProvider { api }
    private val repo by lazy {
        NextcloudFeedRepository(
            db, provider, PendingChangeFlusher(db, provider),
            NextcloudSyncer(db, provider, MemSyncState(), schedulers), schedulers, schedulers,
        )
    }

    private suspend fun syncedArticle(): Long {
        api.feedList += FeedDto(10, "https://lwn.net/rss", "LWN")
        api.itemList += item(1, 10)
        repo.refresh()
        api.calls.clear()
        return db.articleDao().getByRemoteId(1)!!.id
    }

    private suspend fun syncUnread(count: Int) {
        api.feedList += FeedDto(10, "u", "t")
        (1..count).forEach { api.itemList += item(it.toLong(), 10) }
        repo.refresh()
    }

    @Test fun setReadUpdatesRoomQueuesChangeAndSchedulesFlush() = runTest {
        val id = syncedArticle()
        repo.setRead(id, true)
        assertTrue(db.articleDao().getById(id)!!.isRead)
        val pending = db.pendingChangeDao().getAll().single()
        assertEquals(SyncField.READ, pending.field)
        assertEquals(true, pending.value)
        assertEquals(1, schedulers.flushes)
        assertEquals(emptyList<String>(), api.calls) // no network on the UI path
    }

    @Test fun togglingBackCollapsesToTheLatestIntent() = runTest {
        val id = syncedArticle()
        repo.setRead(id, true)
        repo.setRead(id, false)
        assertEquals(false, db.pendingChangeDao().getAll().single().value)
    }

    @Test fun savingArchivesOfflineAndQueuesStar() = runTest {
        val id = syncedArticle()
        repo.setSaved(id, true)
        assertEquals(listOf(id), schedulers.archived)
        assertEquals(SyncField.STARRED, db.pendingChangeDao().getAll().single().field)
        assertEquals(listOf(id), repo.observeSaved().first().map { it.id })
    }

    @Test fun unsavingDropsOfflineCopy() = runTest {
        val id = syncedArticle()
        repo.setSaved(id, true)
        db.articleDao().saveOfflineHtml(id, "<p>x</p>")
        repo.setSaved(id, false)
        assertNull(db.articleDao().getById(id)!!.offlineHtml)
        assertEquals(false, db.pendingChangeDao().getAll().single { it.field == SyncField.STARRED }.value)
    }

    @Test fun markFeedReadQueuesEveryPreviouslyUnreadArticle() = runTest {
        syncUnread(2)
        val feedId = db.feedDao().getAll().single().id
        repo.markFeedRead(feedId)
        assertEquals(0, repo.observeUnread().first().size)
        assertEquals(2, db.pendingChangeDao().getAll().size)
    }

    @Test fun markOlderReadQueuesOnlyTheOlderUnreadArticlesAndUndoRequeues() = runTest {
        syncUnread(3)
        val selected = db.articleDao().getByRemoteId(2)!!.id
        val older = db.articleDao().getByRemoteId(1)!!.id

        val marked = repo.markOlderRead(selected)

        assertEquals(listOf(older), marked)
        assertEquals(setOf(selected, db.articleDao().getByRemoteId(3)!!.id), repo.observeUnread().first().map { it.id }.toSet())
        assertEquals(listOf(older), db.pendingChangeDao().getAll().map { it.articleId })

        repo.setReadBatch(marked, false)
        assertEquals(3, repo.observeUnread().first().size)
        assertEquals(false, db.pendingChangeDao().getAll().single().value)
    }

    @Test fun refreshFlushesBeforePulling() = runTest {
        val id = syncedArticle()
        repo.setRead(id, true)
        api.calls.clear()
        assertTrue(repo.refresh().isSuccess)
        assertTrue(api.calls.indexOf("markRead") < api.calls.indexOf("folders"))
    }

    @Test fun refreshAbortsWithoutPullingWhenFlushFails() = runTest {
        val id = syncedArticle()
        repo.setRead(id, true)
        api.calls.clear()
        api.failOn = setOf("markRead")
        assertTrue(repo.refresh().isFailure)
        assertTrue("folders" !in api.calls)
        assertEquals(1, db.pendingChangeDao().getAll().size)
    }

    @Test fun addFeedCreatesRemotelyUnderMappedFolderThenSyncs() = runTest {
        api.folderList += me.dougals.forage.data.nextcloud.FolderDto(5, "Tech")
        repo.refresh()
        val folder = db.folderDao().getAll().single()
        api.nextId = 77
        api.itemList += item(1, feedId = 77)

        val feed = repo.addFeed("https://new/feed", folder.id).getOrThrow()

        assertTrue("createFeed:https://new/feed:5" in api.calls)
        assertEquals("https://new/feed", feed.url)
        assertEquals(folder.id, feed.folderId)
        assertNotNull(db.articleDao().getByRemoteId(1))
    }

    @Test fun addFeedToALocalOnlyFolderCreatesTheFolderOnTheServerFirst() = runTest {
        val mine = db.folderDao().insert(FolderEntity(name = "Mine"))

        val feed = repo.addFeed("https://new/feed", mine).getOrThrow()

        assertEquals(listOf("folders", "createFolder", "createFeed:https://new/feed:100"), api.calls.take(3))
        assertEquals(100L, db.folderDao().getById(mine)!!.remoteId)
        assertEquals(mine, feed.folderId)
    }

    @Test fun addFeedFailureLeavesNothingLocal() = runTest {
        api.failOn = setOf("createFeed:https://new/feed:null")
        assertTrue(repo.addFeed("https://new/feed").isFailure)
        assertEquals(0, db.feedDao().getAll().size)
    }

    @Test fun removeFeedDeletesRemotelyThenLocally() = runTest {
        syncedArticle()
        val feed = db.feedDao().getAll().single()
        assertTrue(repo.removeFeed(feed.id).isSuccess)
        assertTrue("deleteFeed:10" in api.calls)
        assertNull(db.feedDao().getById(feed.id))
    }

    @Test fun removeFeedKeepsLocalCopyWhenServerFails() = runTest {
        syncedArticle()
        val feed = db.feedDao().getAll().single()
        api.failOn = setOf("deleteFeed:10")
        assertTrue(repo.removeFeed(feed.id).isFailure)
        assertNotNull(db.feedDao().getById(feed.id))
    }

    @Test fun createAndDeleteFolderGoRemoteFirst() = runTest {
        val folder = repo.createFolder("Tech").getOrThrow()
        assertEquals("Tech", folder.name)
        assertEquals(100L, db.folderDao().getAll().single().remoteId)
        assertTrue(repo.deleteFolder(folder.id).isSuccess)
        assertTrue("deleteFolder:100" in api.calls)
        assertEquals(0, db.folderDao().getAll().size)
    }

    @Test fun moveFeedGoesRemoteFirstThenLocal() = runTest {
        syncedArticle()
        val tech = repo.createFolder("Tech").getOrThrow()
        api.calls.clear()
        val feed = db.feedDao().getAll().single()
        assertTrue(repo.moveFeed(feed.id, tech.id).isSuccess)
        assertEquals(listOf("moveFeed:10:100"), api.calls)
        assertEquals(tech.id, db.feedDao().getById(feed.id)!!.folderId)
    }

    @Test fun moveFeedToNoFolderMovesItToTheServerRoot() = runTest {
        syncedArticle()
        val tech = repo.createFolder("Tech").getOrThrow()
        val feed = db.feedDao().getAll().single()
        repo.moveFeed(feed.id, tech.id)
        api.calls.clear()
        assertTrue(repo.moveFeed(feed.id, null).isSuccess)
        assertEquals(listOf("moveFeed:10:null"), api.calls)
        assertNull(db.feedDao().getById(feed.id)!!.folderId)
    }

    @Test fun moveFeedKeepsItsFolderWhenTheServerFails() = runTest {
        syncedArticle()
        val tech = repo.createFolder("Tech").getOrThrow()
        val feed = db.feedDao().getAll().single()
        api.failOn = setOf("moveFeed:10:100")
        assertTrue(repo.moveFeed(feed.id, tech.id).isFailure)
        assertNull(db.feedDao().getById(feed.id)!!.folderId)
    }

    @Test fun moveFeedCreatesALocalOnlyFolderOnTheServerFirst() = runTest {
        syncedArticle()
        val folderId = db.folderDao().insert(FolderEntity(name = "Mine"))
        val feed = db.feedDao().getAll().single()
        assertTrue(repo.moveFeed(feed.id, folderId).isSuccess)
        assertEquals(listOf("folders", "createFolder", "moveFeed:10:100"), api.calls)
        assertEquals(100L, db.folderDao().getById(folderId)!!.remoteId)
    }

    @Test fun aFeedNotYetOnTheServerIsMovedLocallyAndPushedLater() = runTest {
        val folderId = db.folderDao().insert(FolderEntity(name = "Mine"))
        val feedId = db.feedDao().insert(FeedEntity(url = "https://local/feed", title = "Local"))
        assertTrue(repo.moveFeed(feedId, folderId).isSuccess)
        assertEquals(folderId, db.feedDao().getById(feedId)!!.folderId)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun deletingAFolderMovesItsFeedsToTheRootFirstSoTheServerKeepsThem() = runTest {
        syncedArticle()
        val tech = repo.createFolder("Tech").getOrThrow()
        val feed = db.feedDao().getAll().single()
        repo.moveFeed(feed.id, tech.id)
        api.calls.clear()
        assertTrue(repo.deleteFolder(tech.id).isSuccess)
        assertEquals(listOf("moveFeed:10:null", "deleteFolder:100"), api.calls)
        assertEquals(1, api.feedList.size)
        assertNull(db.feedDao().getById(feed.id)!!.folderId)
    }

    @Test fun observeFeedsCarriesUnreadCounts() = runTest {
        api.feedList += FeedDto(10, "u", "t")
        api.itemList += item(1, 10)
        api.itemList += item(2, 10, unread = false, starred = true)
        repo.refresh()
        assertEquals(1, repo.observeFeeds().first().single().unreadCount)
    }


    // --- feeds that only exist locally (added or read before connecting) ---

    private suspend fun localFeed(url: String = "https://omgubuntu.co.uk/feed", folderId: Long? = null) =
        db.feedDao().insert(me.dougals.forage.data.local.FeedEntity(url = url, title = "Local", folderId = folderId))

    @Test fun localOnlyFeedIsCreatedOnTheServerAndAdoptedNotDuplicated() = runTest {
        localFeed()
        assertTrue(repo.refresh().isSuccess)
        assertEquals(listOf("createFeed:https://omgubuntu.co.uk/feed:null"), api.calls.filter { it.startsWith("createFeed") })
        val feed = db.feedDao().getAll().single()
        assertEquals(api.feedList.single().id, feed.remoteId)
    }

    @Test fun feedAlreadyOnTheServerIsNotCreatedAgain() = runTest {
        localFeed()
        api.feedList += FeedDto(10, "https://omgubuntu.co.uk/feed", "OMG")
        repo.refresh()
        assertTrue(api.calls.none { it.startsWith("createFeed") })
        assertEquals(10L, db.feedDao().getAll().single().remoteId)
    }

    @Test fun localFolderIsCreatedFirstAndTheFeedIsPutInIt() = runTest {
        val folder = db.folderDao().insert(me.dougals.forage.data.local.FolderEntity(name = "Tech"))
        localFeed(folderId = folder)
        repo.refresh()
        val remoteFolder = api.folderList.single()
        assertEquals("Tech", remoteFolder.name)
        assertTrue(api.calls.contains("createFeed:https://omgubuntu.co.uk/feed:${remoteFolder.id}"))
        assertEquals(1, db.folderDao().getAll().size)
        assertEquals(remoteFolder.id, db.folderDao().getAll().single().remoteId)
    }

    @Test fun localFolderMatchingAServerFolderByNameIsAdopted() = runTest {
        val folder = db.folderDao().insert(me.dougals.forage.data.local.FolderEntity(name = "Tech"))
        localFeed(folderId = folder)
        api.folderList += me.dougals.forage.data.nextcloud.FolderDto(3, "Tech")
        repo.refresh()
        assertTrue(api.calls.none { it == "createFolder" })
        assertEquals(1, db.folderDao().getAll().size)
        assertEquals(3L, db.folderDao().getAll().single().remoteId)
    }

    @Test fun readAndSavedStateFromBeforeConnectingIsPushedNotOverwritten() = runTest {
        val feedId = localFeed()
        db.articleDao().insertIgnore(
            listOf(
                me.dougals.forage.data.local.ArticleEntity(feedId = feedId, guid = "guid-1", title = "t", url = "u", publishedAt = 0, isRead = true),
                me.dougals.forage.data.local.ArticleEntity(feedId = feedId, guid = "guid-2", title = "t", url = "u", publishedAt = 0, isSaved = true),
            ),
        )
        // The server will know the feed (as id 100) with both items unread and unstarred.
        api.itemList += item(1, 100)
        api.itemList += item(2, 100)
        repo.refresh()
        val byGuid = db.articleDao().getAll().associateBy { it.guid }
        assertTrue(byGuid.getValue("guid-1").isRead)
        assertTrue(byGuid.getValue("guid-2").isSaved)
        assertEquals(listOf(1L), api.batches["markRead"])
        assertEquals(listOf(2L), api.batches["star"])
        assertEquals(emptyList<Any>(), db.pendingChangeDao().getAll())
    }

    @Test fun createFeedFailureFailsTheRefresh() = runTest {
        localFeed()
        api.failOn = setOf("createFeed:https://omgubuntu.co.uk/feed:null")
        assertTrue(repo.refresh().isFailure)
    }

    // --- the report behind the "Sync now" message ---

    @Test fun reportCountsNewArticlesPulled() = runTest {
        api.feedList += FeedDto(10, "https://lwn.net/rss", "LWN")
        api.itemList += item(1, 10); api.itemList += item(2, 10)
        assertEquals(SyncReport(newArticles = 2, feedsReceived = 1), repo.refresh().getOrThrow())
    }

    @Test fun alreadyReadItemsArePulledButNotCountedAsNew() = runTest {
        api.feedList += FeedDto(10, "https://lwn.net/rss", "LWN")
        api.itemList += item(1, 10, unread = false, starred = true)
        assertEquals(SyncReport(saved = 1, feedsReceived = 1), repo.refresh().getOrThrow())
    }

    @Test fun reportCountsQueuedChangesSent() = runTest {
        val id = syncedArticle()
        repo.setRead(id, true)
        repo.setSaved(id, true)
        assertEquals(SyncReport(changesSent = 2), repo.refresh().getOrThrow())
    }

    @Test fun reportCountsFeedsCreatedOnTheServerAndStatePushedAfterAdoption() = runTest {
        val feedId = localFeed()
        db.articleDao().insertIgnore(
            listOf(me.dougals.forage.data.local.ArticleEntity(feedId = feedId, guid = "guid-1", title = "t", url = "u", publishedAt = 0, isRead = true)),
        )
        api.itemList += item(1, 100)
        assertEquals(SyncReport(feedsAdded = 1, changesSent = 1), repo.refresh().getOrThrow())
    }

    @Test fun nothingToDoGivesAnEmptyReport() = runTest {
        syncedArticle()
        assertEquals(SyncReport(), repo.refresh().getOrThrow())
    }

    @Test fun refreshFailsWhenStateQueuedByThePullCannotBeSent() = runTest {
        val feedId = localFeed()
        db.articleDao().insertIgnore(
            listOf(me.dougals.forage.data.local.ArticleEntity(feedId = feedId, guid = "guid-1", title = "t", url = "u", publishedAt = 0, isRead = true)),
        )
        api.itemList += item(1, 100)
        api.failOn = setOf("markRead") // nothing is queued before the pull, so only the flush after it sends
        assertTrue(repo.refresh().isFailure)
        assertTrue("folders" in api.calls)
        assertEquals(1, db.pendingChangeDao().getAll().size)
    }

    // --- things that never reached the server, and cancellation ---

    @Test fun cancellationIsRethrownNotReportedAsAFailure() = runTest {
        val cancelling = NextcloudFeedRepository(
            db, { throw CancellationException("left the screen") }, PendingChangeFlusher(db, provider),
            NextcloudSyncer(db, provider, MemSyncState(), schedulers), schedulers, schedulers,
        )
        val thrown = runCatching { cancelling.createFolder("Tech") }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test fun removingALocalOnlyFeedDoesNotCallTheServer() = runTest {
        val feedId = localFeed()
        assertTrue(repo.removeFeed(feedId).isSuccess)
        assertEquals(emptyList<String>(), api.calls)
        assertNull(db.feedDao().getById(feedId))
    }

    @Test fun deletingALocalOnlyFolderDoesNotCallTheServer() = runTest {
        val folderId = db.folderDao().insert(FolderEntity(name = "Mine"))
        assertTrue(repo.deleteFolder(folderId).isSuccess)
        assertEquals(emptyList<String>(), api.calls)
        assertEquals(0, db.folderDao().getAll().size)
    }

    @Test fun deletingAFolderOnlyMovesFeedsTheServerKnows() = runTest {
        val tech = repo.createFolder("Tech").getOrThrow()
        localFeed(folderId = tech.id)
        api.calls.clear()
        assertTrue(repo.deleteFolder(tech.id).isSuccess)
        assertEquals(listOf("deleteFolder:100"), api.calls)
    }

    @Test fun movingAMissingFeedFails() = runTest {
        assertTrue(repo.moveFeed(42, null).isFailure)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun addFeedFindsTheNewFeedAmongExistingOnes() = runTest {
        syncedArticle()
        val feed = repo.addFeed("  https://new/feed  ").getOrThrow()
        assertTrue("createFeed:https://new/feed:null" in api.calls)
        assertEquals("https://new/feed", feed.url)
        assertEquals(2, db.feedDao().getAll().size)
    }
}
