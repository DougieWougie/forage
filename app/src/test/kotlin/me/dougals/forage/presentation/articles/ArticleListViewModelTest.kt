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
package me.dougals.forage.presentation.articles

import me.dougals.forage.presentation.toUiText
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.presentation.UiText
import me.dougals.forage.R
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
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReadFilter
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.repository.SettingsRepository
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
    private val settings = MutableStateFlow(AppSettings())
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true).also {
        every { it.settings } returns settings
        coEvery { it.setReadFilter(any()) } answers { settings.value = settings.value.copy(readFilter = firstArg()) }
    }
    private fun vm(vararg args: Pair<String, Any>) = ArticleListViewModel(SavedStateHandle(mapOf(*args)), repo, settingsRepo)

    init {
        every { repo.observeFeeds() } returns flowOf(listOf(Feed(1, "u", "LWN", null, null, 0)))
        every { repo.observeFolders() } returns flowOf(emptyList())
    }

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

    @Test fun savedSourceIgnoresTheReadFilter() = runTest {
        every { repo.observeSaved() } returns flowOf(listOf(article(1, saved = true), article(2, saved = true, read = true)))
        settings.value = AppSettings(readFilter = ReadFilter.UNREAD)
        vm("source" to "saved").state.test {
            assertEquals(listOf(1L, 2L), awaitUntil { it.articles.isNotEmpty() }.articles.map { it.id })
        }
    }

    @Test fun feedSourceShowsThatFeedsArticles() = runTest {
        every { repo.observeFeedArticles(3) } returns flowOf(listOf(article(4)))
        vm("source" to "feed", "feedId" to 3L).state.test { assertEquals(listOf(4L), awaitUntil { it.articles.isNotEmpty() }.articles.map { it.id }) }
    }

    @Test fun feedSourceFiltersByReadState() = runTest {
        every { repo.observeFeedArticles(3) } returns flowOf(listOf(article(1), article(2, read = true)))
        val vm = vm("source" to "feed", "feedId" to 3L)
        vm.state.test {
            assertEquals(listOf(1L, 2L), awaitUntil { it.articles.size == 2 }.articles.map { it.id })
            vm.setFilter(ReadFilter.UNREAD)
            assertEquals(listOf(1L), awaitUntil { it.filter == ReadFilter.UNREAD }.articles.map { it.id })
            vm.setFilter(ReadFilter.READ)
            awaitUntil { it.filter == ReadFilter.READ }.let { assertEquals(listOf(2L), it.articles.map { a -> a.id }); assertEquals(true, it.hasArticles) }
        }
    }

    @Test fun readFilterComesFromTheSavedSetting() = runTest {
        every { repo.observeFeedArticles(3) } returns flowOf(listOf(article(1), article(2, read = true)))
        settings.value = AppSettings(readFilter = ReadFilter.READ)
        vm("source" to "feed", "feedId" to 3L).state.test {
            awaitUntil { it.filter == ReadFilter.READ }.let { assertEquals(listOf(2L), it.articles.map { a -> a.id }) }
        }
    }

    @Test fun thumbnailModeFollowsTheSetting() = runTest {
        every { repo.observeUnread() } returns MutableStateFlow(listOf(article(1)))
        vm("source" to "unread").state.test {
            assertEquals(ThumbnailMode.ALWAYS, awaitUntil { it.feedTitles.isNotEmpty() }.thumbnails)
            settings.value = AppSettings(thumbnails = ThumbnailMode.OFF)
            assertEquals(ThumbnailMode.OFF, awaitUntil { it.thumbnails == ThumbnailMode.OFF }.thumbnails)
        }
    }

    @Test fun thumbnailSideFollowsTheSetting() = runTest {
        every { repo.observeUnread() } returns MutableStateFlow(listOf(article(1)))
        vm("source" to "unread").state.test {
            assertEquals(ThumbnailSide.END, awaitUntil { it.feedTitles.isNotEmpty() }.thumbnailSide)
            settings.value = AppSettings(thumbnailSide = ThumbnailSide.START)
            assertEquals(ThumbnailSide.START, awaitUntil { it.thumbnailSide == ThumbnailSide.START }.thumbnailSide)
        }
    }

    @Test fun pullToRefreshFollowsTheSetting() = runTest {
        every { repo.observeUnread() } returns flowOf(emptyList())
        vm("source" to "unread").state.test {
            assertEquals(true, awaitUntil { it.feedTitles.isNotEmpty() }.pullToRefresh)
            settings.value = AppSettings(pullToRefresh = false)
            assertEquals(false, awaitUntil { !it.pullToRefresh }.pullToRefresh)
        }
    }

    @Test fun onlyTheUnreadListPullsToRefresh() = runTest {
        every { repo.observeSaved() } returns flowOf(emptyList())
        every { repo.observeFeedArticles(3) } returns flowOf(emptyList())
        vm("source" to "saved").state.test { assertEquals(false, awaitUntil { it.feedTitles.isNotEmpty() }.pullToRefresh) }
        vm("source" to "feed", "feedId" to 3L).state.test { assertEquals(false, awaitUntil { it.feedTitles.isNotEmpty() }.pullToRefresh) }
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

    @Test fun undoingAReadSwipeFlipsItBack() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1)))
        val v = vm("source" to "unread")
        v.state.test {
            awaitItem()
            v.toggleRead(article(1, read = false))
            assertEquals(ToggleUndo(1, ToggleUndo.Field.READ, true), awaitUntil { it.toggled != null }.toggled)
            v.undoToggle()
            awaitUntil { it.toggled == null }
        }
        coVerify(exactly = 1) { repo.setRead(1, true); repo.setRead(1, false) }
    }

    @Test fun undoingASavedSwipeFlipsItBack() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1)))
        val v = vm("source" to "unread")
        v.state.test {
            awaitItem()
            v.toggleSaved(article(1, saved = true))
            assertEquals(ToggleUndo(1, ToggleUndo.Field.SAVED, false), awaitUntil { it.toggled != null }.toggled)
            v.undoToggle()
            awaitUntil { it.toggled == null }
        }
        coVerify(exactly = 1) { repo.setSaved(1, false); repo.setSaved(1, true) }
    }

    @Test fun dismissingAToggleForgetsItWithoutTouchingTheRepository() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1)))
        val v = vm("source" to "unread")
        v.state.test {
            awaitItem()
            v.toggleRead(article(1))
            awaitUntil { it.toggled != null }
            v.dismissToggle()
            awaitUntil { it.toggled == null }
        }
        coVerify(exactly = 1) { repo.setRead(1, true) }
        coVerify(exactly = 0) { repo.setRead(1, false) }
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
            val s = awaitUntil { !it.isRefreshing && it.message != null }
            assertEquals(UiText.Raw("offline"), s.message)
            v.dismissMessage()
            awaitUntil { it.message == null }
        }
    }

    @Test fun successfulRefreshTellsTheUserWhatChanged() = runTest {
        every { repo.observeUnread() } returns flowOf(emptyList())
        coEvery { repo.refresh() } returns Result.success(me.dougals.forage.domain.model.SyncReport(newArticles = 4, markedRead = 2))
        val v = vm("source" to "unread")
        v.state.test {
            awaitItem(); v.refresh()
            assertEquals(SyncReport(newArticles = 4, markedRead = 2).toUiText(), awaitUntil { it.message != null }.message)
        }
    }

    @Test fun markAllReadMarksEveryFeedShown() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 2)))
        val v = vm("source" to "unread")
        v.state.test { awaitUntil { it.articles.size == 3 }; v.markAllRead() }
        coVerify(exactly = 1) { repo.markFeedRead(1) }
        coVerify(exactly = 1) { repo.markFeedRead(2) }
    }

    @Test fun markAllReadKeepsEveryChangedIdForUndo() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 2)))
        coEvery { repo.markFeedRead(1) } returns listOf(1L)
        coEvery { repo.markFeedRead(2) } returns listOf(2L, 3L)
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 3 }
            v.markAllRead()
            assertEquals(listOf(1L, 2L, 3L), awaitUntil { it.markedRead.isNotEmpty() }.markedRead)
            v.undoMarkedRead()
            awaitUntil { it.markedRead.isEmpty() }
        }
        coVerify { repo.setReadBatch(listOf(1L, 2L, 3L), false) }
    }

    @Test fun markOlderReadKeepsTheMarkedIdsForUndo() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1), article(2)))
        coEvery { repo.markOlderRead(2) } returns listOf(1L)
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 2 }
            v.markOlderRead(article(2))
            assertEquals(listOf(1L), awaitUntil { it.markedRead.isNotEmpty() }.markedRead)
            v.undoMarkedRead()
            awaitUntil { it.markedRead.isEmpty() }
        }
        coVerify(exactly = 1) { repo.setReadBatch(listOf(1L), false) }
    }

    @Test fun markOlderReadWithNothingOlderOffersNoUndo() = runTest {
        every { repo.observeUnread() } returns flowOf(listOf(article(1)))
        coEvery { repo.markOlderRead(1) } returns emptyList()
        val v = vm("source" to "unread")
        v.state.test { awaitUntil { it.articles.size == 1 }; v.markOlderRead(article(1)); expectNoEvents() }
    }

    // Category filter

    private val tech = Folder(1, "Tech")
    private val news = Folder(2, "News")
    private fun categorised(unread: MutableStateFlow<List<me.dougals.forage.domain.model.Article>>) {
        every { repo.observeUnread() } returns unread
        every { repo.observeFolders() } returns flowOf(listOf(tech, news))
        every { repo.observeFeeds() } returns flowOf(
            listOf(Feed(1, "u", "LWN", null, 1, 0), Feed(2, "u", "BBC", null, 2, 0), Feed(3, "u", "Blog", null, null, 0)),
        )
    }

    @Test fun unreadListFiltersByCategory() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 3))))
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 3 }.let {
                assertEquals(listOf(tech, news), it.folders)
                assertEquals(true, it.hasUncategorised)
            }
            v.toggleFolder(2)
            assertEquals(listOf(2L), awaitUntil { it.folderIds == setOf(2L) }.articles.map { it.id })
            v.clearFolders()
            assertEquals(3, awaitUntil { it.folderIds.isEmpty() }.articles.size)
            v.toggleFolder(ArticleListViewModel.UNCATEGORISED)
            assertEquals(listOf(3L), awaitUntil { it.folderIds.isNotEmpty() }.articles.map { it.id })
        }
    }

    @Test fun severalCategoriesCanBeChosenAtOnce() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 3))))
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 3 }
            v.toggleFolder(1)
            awaitUntil { it.folderIds == setOf(1L) }
            v.toggleFolder(ArticleListViewModel.UNCATEGORISED)
            awaitUntil { it.folderIds.size == 2 }.let { assertEquals(listOf(1L, 3L), it.articles.map { a -> a.id }) }
            v.toggleFolder(1)
            assertEquals(listOf(3L), awaitUntil { it.folderIds == setOf(ArticleListViewModel.UNCATEGORISED) }.articles.map { it.id })
        }
    }

    @Test fun selectFoldersReplacesTheEarlierChoice() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 3))))
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 3 }
            v.toggleFolder(1)
            awaitUntil { it.folderIds == setOf(1L) }
            v.selectFolders(setOf(2L, ArticleListViewModel.UNCATEGORISED))
            awaitUntil { it.folderIds.size == 2 }.let {
                assertEquals(setOf(2L, ArticleListViewModel.UNCATEGORISED), it.folderIds)
                assertEquals(listOf(2L, 3L), it.articles.map { a -> a.id })
            }
        }
    }

    @Test fun chosenCategoriesSurviveARecreatedViewModel() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2), article(3, feedId = 3))))
        val handle = SavedStateHandle(mapOf("source" to "unread"))
        val first = ArticleListViewModel(handle, repo, settingsRepo)
        first.state.test {
            awaitUntil { it.articles.size == 3 }
            first.toggleFolder(1)
            first.toggleFolder(2)
            awaitUntil { it.folderIds == setOf(1L, 2L) }
        }
        ArticleListViewModel(handle, repo, settingsRepo).state.test {
            awaitUntil { it.folderIds == setOf(1L, 2L) }.let { assertEquals(listOf(1L, 2L), it.articles.map { a -> a.id }) }
        }
    }

    @Test fun onlyCategoriesWithUnreadArticlesAreOffered() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1))))
        vm("source" to "unread").state.test {
            awaitUntil { it.articles.isNotEmpty() }.let {
                assertEquals(listOf(tech), it.folders)
                assertEquals(false, it.hasUncategorised)
            }
        }
    }

    @Test fun aCategoryThatRunsOutFallsBackToAll() = runTest {
        val unread = MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2)))
        categorised(unread)
        val v = vm("source" to "unread")
        v.state.test {
            awaitUntil { it.articles.size == 2 }
            v.toggleFolder(2)
            awaitUntil { it.folderIds == setOf(2L) }
            unread.value = listOf(article(1, feedId = 1))
            awaitUntil { it.folderIds.isEmpty() }.let { assertEquals(listOf(1L), it.articles.map { a -> a.id }) }
        }
    }

    @Test fun markAllReadOnlyMarksTheChosenCategory() = runTest {
        categorised(MutableStateFlow(listOf(article(1, feedId = 1), article(2, feedId = 2))))
        val v = vm("source" to "unread")
        v.state.test { awaitUntil { it.articles.size == 2 }; v.toggleFolder(2); awaitUntil { it.folderIds == setOf(2L) }; v.markAllRead() }
        coVerify(exactly = 1) { repo.markFeedRead(2) }
        coVerify(exactly = 0) { repo.markFeedRead(1) }
    }

    @Test fun otherListsIgnoreTheCategoryFilter() = runTest {
        categorised(MutableStateFlow(emptyList()))
        every { repo.observeSaved() } returns flowOf(listOf(article(1, feedId = 1), article(2, feedId = 2)))
        val v = vm("source" to "saved")
        v.toggleFolder(2)
        v.state.test { assertEquals(2, awaitUntil { it.articles.isNotEmpty() }.articles.size) }
    }
}
