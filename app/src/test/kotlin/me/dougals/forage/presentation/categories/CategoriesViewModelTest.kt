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
package me.dougals.forage.presentation.categories

import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.presentation.UiText
import me.dougals.forage.R
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.repository.SettingsRepository
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.opml.ImportResult
import me.dougals.forage.domain.opml.OpmlExchange
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.suggest.SuggestionCatalogue
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

    @Test fun categoryCountsUnreadArticlesAndFeedsWithNewOnes() {
        val node = CategoryNode(Folder(1, "Tech"), listOf(feed(1, "A").copy(unreadCount = 4), feed(2, "B"), feed(3, "C").copy(unreadCount = 1)))
        assertEquals(5, node.unreadCount)
        assertEquals(2, node.updatedFeeds)
    }

    @Test fun uncategorizedGroupIsOmittedWhenEmpty() {
        assertEquals(listOf("Art"), buildCategories(listOf(Folder(1, "Art")), emptyList()).map { it.folder?.name })
    }

    private val repo = mockk<FeedRepository>(relaxed = true).also {
        every { it.observeFolders() } returns flowOf(listOf(Folder(1, "Tech")))
        every { it.observeFeeds() } returns flowOf(listOf(feed(1, "LWN", 1)))
    }
    private val opml = mockk<OpmlExchange>()
    private val settings = MutableStateFlow(AppSettings())
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true).also { every { it.settings } returns settings }
    private var catalogue = listOf(
        FeedSuggestion("LWN", "https://lwn.net/rss", "https://lwn.net/", "Linux", setOf("linux"), uk = false, featured = true),
        FeedSuggestion("Reg", "https://reg.example/f", "https://reg.example/", "IT", setOf("technology"), uk = true, featured = true),
    )
    private val vm get() = CategoriesViewModel(repo, opml, settingsRepo, SuggestionCatalogue { catalogue })

    @Test fun movingAFeedAsksTheRepository() = runTest {
        vm.moveFeed(5, 2)
        vm.moveFeed(6, null)
        coVerify { repo.moveFeed(5, 2); repo.moveFeed(6, null) }
    }

    @Test fun deletingRemovingAndMarkingReadAskTheRepository() = runTest {
        val v = vm
        v.deleteFolder(3)
        v.removeFeed(4)
        v.markFeedRead(5)
        coVerify { repo.deleteFolder(3); repo.removeFeed(4); repo.markFeedRead(5) }
    }

    @Test fun failedDeleteAndRemoveAreReported() = runTest {
        coEvery { repo.deleteFolder(any()) } returns Result.failure(java.io.IOException("folder busy"))
        coEvery { repo.removeFeed(any()) } returns Result.failure(java.io.IOException("feed busy"))
        val v = vm
        v.state.test {
            awaitItem(); v.deleteFolder(1)
            assertEquals(UiText.Raw("folder busy"), awaitUntil { it.message != null }.message)
            v.dismissMessage(); v.removeFeed(1)
            assertEquals(UiText.Raw("feed busy"), awaitUntil { it.message == UiText.Raw("feed busy") }.message)
        }
    }

    @Test fun aFailedMoveIsReported() = runTest {
        coEvery { repo.moveFeed(any(), any()) } returns Result.failure(java.io.IOException("offline"))
        val v = vm
        v.state.test {
            awaitItem(); v.moveFeed(1, 2)
            assertEquals(UiText.Raw("offline"), awaitUntil { it.message != null }.message)
        }
    }

    @Test fun suggestionsAreOfferedAndSubscribedOnesLeftOut() = runTest {
        every { repo.observeFeeds() } returns flowOf(listOf(feed(1, "Reg", 1).copy(url = "https://reg.example/f")))
        vm.state.test {
            assertEquals(listOf("LWN"), awaitUntil { it.suggestions.isNotEmpty() }.suggestions.map { it.title })
        }
    }

    @Test fun dismissedSuggestionsDisappearAndAreRemembered() = runTest {
        val v = vm
        v.state.test {
            awaitUntil { it.suggestions.size == 2 }
            v.dismissSuggestion(catalogue[0])
            settings.value = AppSettings(dismissedSuggestions = setOf("https://lwn.net/rss"))
            assertEquals(listOf("Reg"), awaitUntil { it.suggestions.size == 1 }.suggestions.map { it.title })
        }
        coVerify { settingsRepo.dismissSuggestion("https://lwn.net/rss") }
    }

    @Test fun addingASuggestionSubscribesToItsFeed() = runTest {
        coEvery { repo.addFeed(any(), any()) } returns Result.success(feed(7, "LWN"))
        vm.addSuggestion(catalogue[0])
        coVerify { repo.addFeed("https://lwn.net/rss", null) }
    }

    @Test fun dismissedSuggestionsCanBeBroughtBack() = runTest {
        vm.restoreSuggestions()
        coVerify { settingsRepo.clearDismissedSuggestions() }
    }

    @Test fun pullToRefreshFollowsTheSetting() = runTest {
        vm.state.test {
            assertEquals(true, awaitUntil { it.categories.isNotEmpty() }.pullToRefresh)
            settings.value = AppSettings(pullToRefresh = false)
            assertEquals(false, awaitUntil { !it.pullToRefresh }.pullToRefresh)
        }
    }

    @Test fun successfulRefreshTellsTheUserWhatChanged() = runTest {
        coEvery { repo.refresh() } returns Result.success(me.dougals.forage.domain.model.SyncReport())
        val v = vm
        v.state.test {
            awaitUntil { it.categories.isNotEmpty() }; v.refresh()
            assertEquals(UiText.Res(R.string.up_to_date), awaitUntil { it.message != null }.message)
        }
    }

    @Test fun refreshSyncsAndReportsFailure() = runTest {
        coEvery { repo.refresh() } returns Result.failure(java.io.IOException("offline"))
        val v = vm
        v.state.test {
            awaitUntil { it.categories.isNotEmpty() }
            v.refresh()
            assertEquals(UiText.Raw("offline"), awaitUntil { it.message != null }.message)
        }
        coVerify { repo.refresh() }
    }

    @Test fun stateExposesTree() = runTest {
        vm.state.test { assertEquals("LWN", awaitUntil { it.categories.isNotEmpty() }.categories.single().feeds.single().title) }
    }

    @Test fun blankFeedUrlIsRejectedWithoutCallingRepo() = runTest {
        val v = vm
        v.state.test {
            awaitItem(); v.addFeed("   ", null)
            assertEquals(UiText.Res(R.string.enter_feed_url), awaitUntil { it.message != null }.message)
        }
        coVerify(exactly = 0) { repo.addFeed(any(), any()) }
    }

    @Test fun schemeIsAddedWhenMissingAndSuccessIsAnnounced() = runTest {
        coEvery { repo.addFeed("https://example.com/feed", 1) } returns Result.success(feed(9, "Example"))
        val v = vm
        v.state.test { awaitItem(); v.addFeed(" example.com/feed ", 1); assertEquals(UiText.Res(R.string.added_feed, listOf("Example")), awaitUntil { it.message != null }.message) }
    }

    @Test fun explicitSchemeIsKept() = runTest {
        coEvery { repo.addFeed("http://example.com/feed", null) } returns Result.success(feed(9, "E"))
        vm.addFeed("http://example.com/feed", null)
        coVerify { repo.addFeed("http://example.com/feed", null) }
    }

    @Test fun addFailureShowsTheReason() = runTest {
        coEvery { repo.addFeed(any(), any()) } returns Result.failure(IllegalStateException("Already subscribed"))
        val v = vm
        v.state.test { awaitItem(); v.addFeed("https://a/b", null); assertEquals(UiText.Raw("Already subscribed"), awaitUntil { it.message != null }.message) }
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
            assertEquals(UiText.Res(R.string.import_result, listOf(2, 1, 0)), awaitUntil { it.message != null }.message)
        }
    }

    @Test fun invalidOpmlShowsError() = runTest {
        coEvery { opml.import(any()) } returns Result.failure(IllegalArgumentException("Not a valid OPML file"))
        val v = vm
        v.state.test { awaitItem(); v.importOpml(ByteArrayInputStream(ByteArray(0))); assertEquals(UiText.Raw("Not a valid OPML file"), awaitUntil { it.message != null }.message) }
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
