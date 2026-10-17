package me.dougals.forage.data.nextcloud

import kotlinx.coroutines.test.runTest
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
}
