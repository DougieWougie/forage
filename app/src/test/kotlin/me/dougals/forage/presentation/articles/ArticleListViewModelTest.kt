package me.dougals.forage.presentation.articles

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.article
import me.dougals.forage.testutil.awaitUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

class ArticleListViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repo = mockk<FeedRepository>(relaxed = true)
    private fun vm(vararg args: Pair<String, Any>) = ArticleListViewModel(SavedStateHandle(mapOf(*args)), repo)

    init { every { repo.observeFeeds() } returns flowOf(listOf(Feed(1, "u", "LWN", null, null, 0))) }

    @Test fun unreadSourceShowsUnreadArticlesWithFeedTitles() = runTest {
        every { repo.observeUnread() } returns MutableStateFlow(listOf(article(1)))
        vm("source" to "unread").state.test {
            val s = awaitUntil { it.articles.isNotEmpty() && it.feedTitles.isNotEmpty() }
            assertEquals(listOf(1L), s.articles.map { it.id })
            assertEquals("LWN", s.feedTitles[1L])
        }
    }

    @Test fun savedSourceShowsSavedArticles() = runTest {
        every { repo.observeSaved() } returns flowOf(listOf(article(2, saved = true)))
        vm("source" to "saved").state.test { assertEquals(listOf(2L), awaitUntil { it.articles.isNotEmpty() }.articles.map { it.id }) }
    }

    @Test fun feedSourceShowsThatFeedsArticles() = runTest {
        every { repo.observeFeedArticles(3) } returns flowOf(listOf(article(4)))
        vm("source" to "feed", "feedId" to 3L).state.test { assertEquals(listOf(4L), awaitUntil { it.articles.isNotEmpty() }.articles.map { it.id }) }
    }

    @Test fun unknownSourceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { vm("source" to "bogus") }
        assertThrows(IllegalArgumentException::class.java) { vm("source" to "feed") } // missing feedId
    }

    @Test fun swipeRightTogglesReadBothWays() = runTest {
        val v = vm("source" to "unread")
        v.toggleRead(article(1, read = false))
        v.toggleRead(article(2, read = true))
        coVerify { repo.setRead(1, true); repo.setRead(2, false) }
    }

    @Test fun swipeLeftTogglesSavedBothWays() = runTest {
        val v = vm("source" to "unread")
        v.toggleSaved(article(1, saved = false))
        v.toggleSaved(article(2, saved = true))
        coVerify { repo.setSaved(1, true); repo.setSaved(2, false) }
    }

    @Test fun refreshShowsSpinnerThenSurfacesFailure() = runTest {
        val gate = CompletableDeferred<Result<me.dougals.forage.domain.model.SyncReport>>()
        every { repo.observeUnread() } returns flowOf(emptyList())
        coEvery { repo.refresh() } coAnswers { gate.await() }
        val v = vm("source" to "unread")
        v.state.test {
            awaitItem()
            v.refresh()
            awaitUntil { it.isRefreshing }
            gate.complete(Result.failure(IOException("offline")))
            val s = awaitUntil { !it.isRefreshing && it.error != null }
            assertEquals("offline", s.error)
            v.dismissError()
            awaitUntil { it.error == null }
        }
    }

    @Test fun markAllReadMarksEveryFeedShown() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 2)))
        val v = vm("source" to "unread")
        v.state.test { awaitUntil { it.articles.size == 3 }; v.markAllRead() }
        coVerify(exactly = 1) { repo.markFeedRead(1) }
        coVerify(exactly = 1) { repo.markFeedRead(2) }
    }
}
