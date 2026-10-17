package me.dougals.forage.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.nextcloud.FeedDto
import me.dougals.forage.data.nextcloud.NextcloudApiProvider
import me.dougals.forage.data.nextcloud.NextcloudSyncer
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
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
        api.feedList += FeedDto(10, "u", "t")
        api.itemList += item(1, 10)
        api.itemList += item(2, 10)
        repo.refresh()
        val feedId = db.feedDao().getAll().single().id
        repo.markFeedRead(feedId)
        assertEquals(0, repo.observeUnread().first().size)
        assertEquals(2, db.pendingChangeDao().getAll().size)
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

    @Test fun observeFeedsCarriesUnreadCounts() = runTest {
        api.feedList += FeedDto(10, "u", "t")
        api.itemList += item(1, 10)
        api.itemList += item(2, 10, unread = false, starred = true)
        repo.refresh()
        assertEquals(1, repo.observeFeeds().first().single().unreadCount)
    }

}
