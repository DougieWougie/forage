package me.dougals.forage.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.FeedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModeAwareFeedRepositoryTest {
    private val local = mockk<FeedRepository>(relaxed = true)
    private val nextcloud = mockk<FeedRepository>(relaxed = true)
    private val mode = MutableStateFlow(SyncMode.LOCAL)
    private val repo = ModeAwareFeedRepository(mode, local, nextcloud)

    @Test fun writesFollowTheCurrentMode() = runTest {
        repo.setRead(1, true)
        coVerify { local.setRead(1, true) }
        mode.value = SyncMode.NEXTCLOUD
        repo.setRead(2, true); repo.setSaved(2, true); repo.markFeedRead(3); repo.refresh()
        coVerify { nextcloud.setRead(2, true); nextcloud.setSaved(2, true); nextcloud.markFeedRead(3); nextcloud.refresh() }
        coVerify(exactly = 0) { local.refresh() }
    }

    @Test fun structuralChangesFollowTheModeToo() = runTest {
        repo.addFeed("u", 1); repo.createFolder("f"); repo.removeFeed(1); repo.deleteFolder(1)
        coVerify { local.addFeed("u", 1); local.createFolder("f"); local.removeFeed(1); local.deleteFolder(1) }
        coVerify(exactly = 0) { nextcloud.addFeed(any(), any()) }
    }

    @Test fun readsComeFromSharedRoomRegardlessOfMode() = runTest {
        val articles = flowOf(emptyList<me.dougals.forage.domain.model.Article>())
        every { local.observeUnread() } returns articles
        mode.value = SyncMode.NEXTCLOUD
        assertEquals(articles, repo.observeUnread())
    }

    @Test fun resultsAreReturnedUnchanged() = runTest {
        coEvery { local.refresh() } returns Result.failure(RuntimeException("x"))
        assertEquals("x", repo.refresh().exceptionOrNull()!!.message)
    }
}
