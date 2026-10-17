package me.dougals.forage.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedDaoTest : DbTestBase() {
    @Test fun deletingFolderKeepsFeedsButUnfilesThem() = runTest {
        val folder = db.folderDao().insert(FolderEntity(name = "Tech"))
        val feed = newFeed(folderId = folder)
        db.folderDao().delete(folder)
        assertNull(db.feedDao().getById(feed)!!.folderId)
    }

    @Test fun deletingFeedCascadesToArticles() = runTest {
        val feed = newFeed()
        db.articleDao().insertIgnore(listOf(article(feed, "a")))
        db.feedDao().delete(feed)
        assertEquals(emptyList<ArticleEntity>(), db.articleDao().observeUnread().first())
    }

    @Test fun feedUrlIsUnique() = runTest {
        newFeed("https://a/feed")
        assertEquals(-1L, db.feedDao().insertIgnore(FeedEntity(url = "https://a/feed", title = "Dup")))
    }

    @Test fun observeFeedsInFolder() = runTest {
        val folder = db.folderDao().insert(FolderEntity(name = "Tech"))
        newFeed("https://a/feed", folderId = folder)
        newFeed("https://b/feed")
        assertEquals(listOf("https://a/feed"), db.feedDao().observeByFolder(folder).first().map { it.url })
    }
}
