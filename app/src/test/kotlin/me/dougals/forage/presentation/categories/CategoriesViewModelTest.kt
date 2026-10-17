package me.dougals.forage.presentation.categories

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.opml.ImportResult
import me.dougals.forage.data.opml.OpmlTransfer
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.awaitUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayInputStream

class CategoriesViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private fun feed(id: Long, title: String, folder: Long? = null) = Feed(id, "https://f/$id", title, null, folder, 0)

    @Test fun treeSortsFoldersAndFeedsKeepsEmptyFoldersAndUncategorizedLast() {
        val tree = buildCategories(
            folders = listOf(Folder(2, "tech"), Folder(1, "Art"), Folder(3, "Empty")),
            feeds = listOf(feed(1, "Zed", 2), feed(2, "alpha", 2), feed(3, "Loose")),
        )
        assertEquals(listOf("Art", "Empty", "tech", null), tree.map { it.folder?.name })
        assertEquals(listOf("alpha", "Zed"), tree[2].feeds.map { it.title })
        assertEquals(listOf("Loose"), tree[3].feeds.map { it.title })
    }

    @Test fun uncategorizedGroupIsOmittedWhenEmpty() {
        assertEquals(listOf("Art"), buildCategories(listOf(Folder(1, "Art")), emptyList()).map { it.folder?.name })
    }

    private val repo = mockk<FeedRepository>(relaxed = true).also {
        every { it.observeFolders() } returns flowOf(listOf(Folder(1, "Tech")))
        every { it.observeFeeds() } returns flowOf(listOf(feed(1, "LWN", 1)))
    }
    private val opml = mockk<OpmlTransfer>()
    private val vm get() = CategoriesViewModel(repo, opml)

    @Test fun stateExposesTree() = runTest {
        vm.state.test { assertEquals("LWN", awaitUntil { it.categories.isNotEmpty() }.categories.single().feeds.single().title) }
    }

    @Test fun blankFeedUrlIsRejectedWithoutCallingRepo() = runTest {
        val v = vm
        v.state.test {
            awaitItem(); v.addFeed("   ", null)
            assertEquals("Enter a feed URL", awaitUntil { it.message != null }.message)
        }
        coVerify(exactly = 0) { repo.addFeed(any(), any()) }
    }

    @Test fun schemeIsAddedWhenMissingAndSuccessIsAnnounced() = runTest {
        coEvery { repo.addFeed("https://example.com/feed", 1) } returns Result.success(feed(9, "Example"))
        val v = vm
        v.state.test { awaitItem(); v.addFeed(" example.com/feed ", 1); assertEquals("Added Example", awaitUntil { it.message != null }.message) }
    }

    @Test fun explicitSchemeIsKept() = runTest {
        coEvery { repo.addFeed("http://example.com/feed", null) } returns Result.success(feed(9, "E"))
        vm.addFeed("http://example.com/feed", null)
        coVerify { repo.addFeed("http://example.com/feed", null) }
    }

    @Test fun addFailureShowsTheReason() = runTest {
        coEvery { repo.addFeed(any(), any()) } returns Result.failure(IllegalStateException("Already subscribed"))
        val v = vm
        v.state.test { awaitItem(); v.addFeed("https://a/b", null); assertEquals("Already subscribed", awaitUntil { it.message != null }.message) }
    }

    @Test fun blankFolderNameIgnoredOtherwiseTrimmed() = runTest {
        coEvery { repo.createFolder(any()) } returns Result.success(Folder(5, "News"))
        val v = vm
        v.createFolder("  ")
        v.createFolder(" News ")
        coVerify(exactly = 1) { repo.createFolder("News") }
    }

    @Test fun importReportsCounts() = runTest {
        coEvery { opml.import(any()) } returns Result.success(ImportResult(added = 2, skipped = 1, failed = 0))
        val v = vm
        v.state.test {
            awaitItem(); v.importOpml(ByteArrayInputStream(ByteArray(0)))
            assertEquals("Imported 2, skipped 1, failed 0", awaitUntil { it.message != null }.message)
        }
    }

    @Test fun invalidOpmlShowsError() = runTest {
        coEvery { opml.import(any()) } returns Result.failure(IllegalArgumentException("Not a valid OPML file"))
        val v = vm
        v.state.test { awaitItem(); v.importOpml(ByteArrayInputStream(ByteArray(0))); assertEquals("Not a valid OPML file", awaitUntil { it.message != null }.message) }
    }

    @Test fun dismissClearsMessage() = runTest {
        val v = vm
        v.state.test { awaitItem(); v.addFeed("", null); awaitUntil { it.message != null }; v.dismissMessage(); assertNull(awaitUntil { it.message == null }.message) }
    }

    @Test fun exportDelegates() = runTest {
        coEvery { opml.export() } returns "<opml/>"
        assertEquals("<opml/>", vm.exportOpml())
    }
}
