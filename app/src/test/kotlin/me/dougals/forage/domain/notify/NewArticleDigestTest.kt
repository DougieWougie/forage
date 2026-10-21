package me.dougals.forage.domain.notify

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.Headline
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.model.UNCATEGORISED_FOLDER_ID
import me.dougals.forage.domain.nextcloud.SyncStateStore
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewArticleDigestTest {
    private val posted = mutableListOf<List<DigestLine>>()
    private val notifier = object : DigestNotifier {
        override fun post(lines: List<DigestLine>) { posted += lines }
        override fun clear() = Unit
    }
    private var syncedAt = 5L

    private fun feed(id: Long, folder: Long?) = Feed(id, "https://f/$id", "F$id", null, folder, 0)
    private val repo = mockk<FeedRepository> {
        every { observeFeeds() } returns flowOf(listOf(feed(1, 10), feed(2, 10), feed(3, 20), feed(4, null)))
        every { observeFolders() } returns flowOf(listOf(Folder(10, "Tech"), Folder(20, "Security")))
    }
    private val state = object : SyncStateStore {
        override var lastModified = 0L
        override var lastSyncedAt: Long
            get() = syncedAt
            set(v) { syncedAt = v }
    }

    private fun digest(settings: AppSettings) = NewArticleDigest(
        repo, mockk<SettingsRepository> { every { this@mockk.settings } returns flowOf(settings) }, state, notifier,
    )

    private val optedIn = AppSettings(notifyFolderIds = setOf(10L, 20L))
    private val withUncategorised = optedIn.copy(notifyFolderIds = optedIn.notifyFolderIds + UNCATEGORISED_FOLDER_ID)

    @Test fun sumsFeedsIntoTheirFoldersAndListsThemAlphabetically() = runTest {
        digest(optedIn).announce(SyncReport(newByFeed = mapOf(1L to 2, 2L to 3, 3L to 1)))
        assertEquals(listOf(DigestLine("Security", 1, 20, listOf("F3")), DigestLine("Tech", 5, 10, listOf("F2", "F1"))), posted.single())
    }

    @Test fun onlyAnnouncesFoldersTheUserOptedIn() = runTest {
        digest(optedIn.copy(notifyFolderIds = setOf(20L))).announce(SyncReport(newByFeed = mapOf(1L to 2, 3L to 1)))
        assertEquals(listOf(DigestLine("Security", 1, 20, listOf("F3"))), posted.single())
    }

    @Test fun feedsInNoFolderAreAnnouncedLastAndOnlyWhenOptedIn() = runTest {
        val report = SyncReport(newByFeed = mapOf(1L to 2, 4L to 9))
        digest(withUncategorised).announce(report)
        assertEquals(listOf(DigestLine("Tech", 2, 10, listOf("F1")), DigestLine(null, 9, UNCATEGORISED_FOLDER_ID, listOf("F4"))), posted.single())
        posted.clear()
        digest(optedIn).announce(report)
        assertEquals(listOf(DigestLine("Tech", 2, 10, listOf("F1"))), posted.single())
        posted.clear()
        digest(optedIn.copy(notifyFolderIds = setOf(UNCATEGORISED_FOLDER_ID))).announce(report)
        assertEquals(listOf(DigestLine(null, 9, UNCATEGORISED_FOLDER_ID, listOf("F4"))), posted.single())
    }

    @Test fun namesTheFeedsBehindACategoryBusiestFirst() = runTest {
        digest(optedIn).announce(SyncReport(newByFeed = mapOf(1L to 2, 2L to 7)))
        assertEquals(listOf("F2", "F1"), posted.single().single().feeds)
        posted.clear()
        digest(optedIn).announce(SyncReport(newByFeed = mapOf(1L to 3, 2L to 3)))
        assertEquals(listOf("F1", "F2"), posted.single().single().feeds)
    }

    @Test fun quotesTheNewestHeadlinesAcrossACategorysFeeds() = runTest {
        val report = SyncReport(
            newByFeed = mapOf(1L to 2, 2L to 2),
            newHeadlines = mapOf(
                1L to listOf(Headline("F1 new", 300), Headline("F1 old", 100)),
                2L to listOf(Headline("F2 new", 400), Headline("F2 old", 200)),
            ),
        )
        digest(optedIn).announce(report)
        assertEquals(listOf("F2 new", "F1 new", "F2 old"), posted.single().single().headlines)
    }

    @Test fun aCategoryWithoutHeadlinesStillAnnounces() = runTest {
        digest(optedIn).announce(SyncReport(newByFeed = mapOf(1L to 2)))
        assertEquals(emptyList<String>(), posted.single().single().headlines)
    }

    @Test fun ignoresFeedsThatNoLongerExist() = runTest {
        digest(optedIn).announce(SyncReport(newByFeed = mapOf(99L to 4)))
        assertTrue(posted.isEmpty())
    }

    @Test fun staysSilentWhenOffOrNothingQualifies() = runTest {
        val report = SyncReport(newByFeed = mapOf(1L to 2, 4L to 9))
        digest(optedIn.copy(notifyFolderIds = emptySet())).announce(report)
        digest(optedIn.copy(notifyFolderIds = setOf(20L))).announce(report)
        digest(optedIn).announce(SyncReport())
        assertTrue(posted.isEmpty())
    }

    @Test fun nextcloudHasNoBaselineUntilItsFirstPull() = runTest {
        val nextcloud = optedIn.copy(syncMode = SyncMode.NEXTCLOUD)
        syncedAt = 0
        assertFalse(digest(nextcloud).hasBaseline())
        assertTrue(digest(optedIn.copy(syncMode = SyncMode.LOCAL)).hasBaseline())
        syncedAt = 99
        assertTrue(digest(nextcloud).hasBaseline())
    }
}
