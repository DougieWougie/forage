package me.dougals.forage.data.content

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class OfflineArchiverTest : DbTestBase() {
    private var html: String? = "<html><head><title>T</title></head><body><nav>menu</nav><article><p>Body text</p></article></body></html>"
    private val fetched = mutableListOf<String>()
    private val fetcher = PageFetcher { url ->
        fetched += url
        html ?: throw IOException("offline")
    }
    private val archiver get() = OfflineArchiver(db, fetcher)

    private suspend fun savedArticle(): Long {
        val feed = newFeed()
        return db.articleDao().insertIgnore(listOf(article(feed, "a", isSaved = true))).single()
    }

    @Test fun fetchesArticleUrlExtractsAndStoresCleanHtml() = runTest {
        val id = savedArticle()
        assertTrue(archiver.archive(id).isSuccess)
        assertEquals(listOf("https://example.com/a"), fetched)
        val stored = db.articleDao().getById(id)!!.offlineHtml!!
        assertTrue(stored.contains("Body text"))
        assertTrue(!stored.contains("menu"))
    }

    @Test fun fetchFailureLeavesArticleUntouchedAndReportsFailure() = runTest {
        val id = savedArticle()
        html = null
        assertTrue(archiver.archive(id).isFailure)
        assertNull(db.articleDao().getById(id)!!.offlineHtml)
    }

    @Test fun unknownArticleFails() = runTest {
        assertTrue(archiver.archive(12345).isFailure)
        assertEquals(emptyList<String>(), fetched)
    }

    @Test fun articleUnsavedBeforeJobRunsIsSkippedWithoutFetching() = runTest {
        val id = savedArticle()
        db.articleDao().setSaved(id, false)
        assertTrue(archiver.archive(id).isSuccess)
        assertEquals(emptyList<String>(), fetched)
        assertNull(db.articleDao().getById(id)!!.offlineHtml)
    }

    @Test fun emptyExtractionIsAFailureSoWeDontCacheBlankPages() = runTest {
        val id = savedArticle()
        html = "<html><body></body></html>"
        assertTrue(archiver.archive(id).isFailure)
        assertNull(db.articleDao().getById(id)!!.offlineHtml)
    }
}
