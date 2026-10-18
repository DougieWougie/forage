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
package me.dougals.forage.ui.articles

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.articles.ArticleListState
import me.dougals.forage.presentation.articles.ArticleListViewModel
import me.dougals.forage.presentation.articles.ListSource
import me.dougals.forage.presentation.articles.ReadFilter
import me.dougals.forage.testutil.article
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArticleListScreenTest {
    @get:Rule val rule = createComposeRule()

    private val one = article(1, feedId = 10)
    private val two = article(2, feedId = 10)
    private val list = ArticleListState(articles = listOf(one, two), feedTitles = mapOf(10L to "LWN"))

    private val state = MutableStateFlow(ArticleListState())
    private val vm = mockk<ArticleListViewModel>(relaxed = true).also { every { it.state } returns state }
    private val opened = mutableListOf<Long>()
    private var backs = 0

    private fun show(initial: ArticleListState = list, source: ListSource = ListSource.Unread, canGoBack: Boolean = false) {
        state.value = initial
        every { vm.source } returns source
        rule.setContent {
            ForageTheme { ArticleListScreen(onOpenArticle = { opened += it }, onBack = if (canGoBack) ({ backs++ }) else null, vm = vm) }
        }
    }

    /** Starts below the app bar, which would otherwise take the drag. */
    private fun pull() = rule.onRoot().performTouchInput { swipeDown(startY = centerY, durationMillis = 600) }.also { rule.waitForIdle() }

    // Title and navigation

    @Test fun unreadAndSavedHaveFixedTitles() {
        show(source = ListSource.Saved)
        rule.onNodeWithText("Saved").assertExists()
    }

    @Test fun feedTitleComesFromTheFeed() {
        show(ArticleListState(feedTitles = list.feedTitles), source = ListSource.Feed(10)) // no cards also naming the feed
        rule.onNodeWithText("LWN").assertExists()
    }

    @Test fun unknownFeedFallsBackToAGenericTitle() {
        show(ArticleListState(), source = ListSource.Feed(99))
        rule.onNodeWithText("Feed").assertExists()
    }

    @Test fun backOnlyShowsWhenThereIsSomewhereToGo() {
        show()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test fun backGoesBack() {
        show(canGoBack = true)
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        assertEquals(1, backs)
    }

    // Mark all read

    @Test fun markAllReadMarksTheList() {
        show()
        rule.onNodeWithText("Unread").assertExists()
        rule.onNodeWithContentDescription("Mark all as read").performClick(); rule.waitForIdle()
        verify { vm.markAllRead() }
    }

    @Test fun markAllReadIsHiddenWhenThereIsNothingToMark() {
        show(ArticleListState())
        rule.onNodeWithContentDescription("Mark all as read").assertDoesNotExist()
    }

    @Test fun savedArticlesCannotBeMarkedAllRead() {
        show(source = ListSource.Saved)
        rule.onNodeWithContentDescription("Mark all as read").assertDoesNotExist()
    }

    // Articles

    @Test fun articlesShowWithTheirFeed() {
        show(list.copy(thumbnails = ThumbnailMode.WIFI_ONLY))
        rule.onNodeWithText("Title 1").assertExists()
        rule.onNodeWithText("Title 2").assertExists()
        rule.onAllNodes(hasText("LWN", substring = true, ignoreCase = true))
            .fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test fun tappingAnArticleOpensIt() {
        show()
        rule.onNodeWithText("Title 2").performClick(); rule.waitForIdle()
        assertEquals(listOf(2L), opened)
    }

    @Test fun swipingTogglesReadAndSaved() {
        show(list.copy(thumbnails = ThumbnailMode.OFF))
        rule.onNodeWithText("Title 1").performTouchInput { swipeRight() }; rule.waitForIdle()
        rule.onNodeWithText("Title 2").performTouchInput { swipeLeft() }; rule.waitForIdle()
        verify { vm.toggleRead(one); vm.toggleSaved(two) }
        verify(exactly = 0) { vm.toggleRead(two); vm.toggleSaved(one) }
    }

    @Test fun messagesAreShownThenDismissed() {
        show(list.copy(message = UiText.Raw("Refreshed")))
        rule.onNodeWithText("Refreshed").assertExists()
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("Refreshed").assertDoesNotExist()
        verify { vm.dismissMessage() }
    }

    // Mark older as read

    private val older = article(1, feedId = 10, publishedAt = 100)
    private val newer = article(2, feedId = 10, publishedAt = 200)
    private val dated = ArticleListState(articles = listOf(newer, older), feedTitles = mapOf(10L to "LWN"))

    @Test fun longPressingAnArticleWithOlderUnreadOnesOffersToMarkThem() {
        show(dated)
        rule.onNodeWithText("Title 2").performTouchInput { longClick() }
        rule.onNodeWithText("Mark every older unread article in this feed as read?").assertExists()
        rule.onNodeWithText("Mark read").performClick(); rule.waitForIdle()
        verify { vm.markOlderRead(newer) }
    }

    @Test fun theOldestUnreadArticleHasNothingOlderToMark() {
        show(dated)
        rule.onNodeWithText("Title 1").performTouchInput { longClick() }
        rule.onNodeWithText("Mark every older unread article in this feed as read?").assertDoesNotExist()
    }

    @Test fun olderArticlesThatAreAlreadyReadDoNotCount() {
        show(dated.copy(articles = listOf(newer, older.copy(isRead = true))))
        rule.onNodeWithText("Title 2").performTouchInput { longClick() }
        rule.onNodeWithText("Mark every older unread article in this feed as read?").assertDoesNotExist()
    }

    @Test fun olderUnreadArticlesInAnotherFeedDoNotCount() {
        show(dated.copy(articles = listOf(newer, older.copy(feedId = 11))))
        rule.onNodeWithText("Title 2").performTouchInput { longClick() }
        rule.onNodeWithText("Mark every older unread article in this feed as read?").assertDoesNotExist()
    }

    @Test fun savedArticlesCannotMarkOlderAsRead() {
        show(dated, source = ListSource.Saved)
        rule.onNodeWithText("Title 2").performTouchInput { longClick() }
        rule.onNodeWithText("Mark every older unread article in this feed as read?").assertDoesNotExist()
    }

    @Test fun markingOlderAsReadReportsTheCountAndCanBeUndone() {
        show(dated.copy(markedOlder = listOf(1, 3, 4)))
        rule.onNodeWithText("3 articles marked read").assertExists()
        rule.onNodeWithText("Undo").performClick(); rule.waitForIdle()
        verify { vm.undoMarkOlderRead() }
        verify(exactly = 0) { vm.dismissMarkedOlder() }
    }

    @Test fun markedArticlesAreForgottenOnceTheSnackbarTimesOut() {
        show(dated.copy(markedOlder = listOf(1)))
        rule.onNodeWithText("1 article marked read").assertExists()
        rule.mainClock.advanceTimeBy(15_000)
        rule.onNodeWithText("1 article marked read").assertDoesNotExist()
        verify { vm.dismissMarkedOlder() }
        verify(exactly = 0) { vm.undoMarkOlderRead() }
    }

    // Read filter

    private val feedList = list.copy(hasArticles = true)

    @Test fun feedListsOfferAReadFilter() {
        show(feedList, source = ListSource.Feed(10))
        listOf("All", "Unread", "Read").forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test fun otherListsHaveNoReadFilter() {
        show(feedList, source = ListSource.Saved)
        rule.onNodeWithText("All").assertDoesNotExist()
        rule.onNodeWithText("Read").assertDoesNotExist()
    }

    @Test fun anEmptyFeedHasNoReadFilter() {
        show(ArticleListState(), source = ListSource.Feed(10))
        rule.onNodeWithText("All").assertDoesNotExist()
    }

    @Test fun tappingAChipChoosesThatFilter() {
        show(feedList, source = ListSource.Feed(10))
        rule.onNodeWithText("Unread").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Read").performClick(); rule.waitForIdle()
        verify { vm.setFilter(ReadFilter.UNREAD); vm.setFilter(ReadFilter.READ) }
    }

    @Test fun theChosenFilterIsSelected() {
        show(feedList.copy(filter = ReadFilter.UNREAD), source = ListSource.Feed(10))
        rule.onNodeWithText("Unread").assertIsSelected()
        rule.onNodeWithText("All").assertIsNotSelected()
    }

    @Test fun aFilterMatchingNothingKeepsTheChipsAndSaysSo() {
        show(ArticleListState(filter = ReadFilter.READ, hasArticles = true), source = ListSource.Feed(10))
        rule.onNodeWithText("No articles match this filter.").assertExists()
        rule.onNodeWithText("No articles").assertDoesNotExist()
        rule.onNodeWithText("All").assertExists()
    }

    // Category filter

    private val categorised = list.copy(folders = listOf(Folder(1, "Tech"), Folder(2, "News")), hasArticles = true)

    @Test fun theUnreadListOffersCategoryChips() {
        show(categorised.copy(hasUncategorised = true))
        listOf("All", "Tech", "News", "Uncategorised").forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test fun oneCategoryIsNotWorthAChip() {
        show(categorised.copy(folders = listOf(Folder(1, "Tech"))))
        rule.onNodeWithText("Tech").assertDoesNotExist()
    }

    @Test fun savedListsHaveNoCategoryChips() {
        show(categorised, source = ListSource.Saved)
        rule.onNodeWithText("Tech").assertDoesNotExist()
    }

    @Test fun feedListsHaveNoCategoryChips() {
        show(categorised, source = ListSource.Feed(10))
        rule.onNodeWithText("Tech").assertDoesNotExist()
    }

    @Test fun tappingACategoryTogglesIt() {
        show(categorised.copy(hasUncategorised = true))
        rule.onNodeWithText("News").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Uncategorised").performClick(); rule.waitForIdle()
        rule.onNodeWithText("All").performClick(); rule.waitForIdle()
        verify { vm.toggleFolder(2); vm.toggleFolder(ArticleListViewModel.UNCATEGORISED); vm.clearFolders() }
    }

    @Test fun everyChosenCategoryIsSelected() {
        show(categorised.copy(hasUncategorised = true, folderIds = setOf(1, 2)))
        rule.onNodeWithText("News").assertIsSelected()
        rule.onNodeWithText("Tech").assertIsSelected()
        rule.onNodeWithText("Uncategorised").assertIsNotSelected()
        rule.onNodeWithText("All").assertIsNotSelected()
    }

    @Test fun allIsSelectedWhenNoCategoryIs() {
        show(categorised)
        rule.onNodeWithText("All").assertIsSelected()
        rule.onNodeWithText("Tech").assertIsNotSelected()
    }

    // Pull to refresh

    @Test fun pullingDownRefreshes() {
        show()
        pull()
        verify { vm.refresh() }
    }

    @Test fun pullingDownDoesNothingWhenPullToRefreshIsOff() {
        show(list.copy(pullToRefresh = false))
        pull()
        verify(exactly = 0) { vm.refresh() }
    }

    // Empty states

    @Test fun emptyUnreadInvitesAPull() {
        show(ArticleListState())
        rule.onNodeWithText("All caught up").assertExists()
        rule.onNodeWithText("Pull down to refresh, or add feeds under Categories.").assertExists()
    }

    @Test fun emptyUnreadWithoutPullPointsAtCategories() {
        show(ArticleListState(pullToRefresh = false))
        rule.onNodeWithText("Add feeds under Categories.").assertExists()
    }

    @Test fun emptySavedExplainsHowToSave() {
        show(ArticleListState(), source = ListSource.Saved)
        rule.onNodeWithText("Nothing saved yet").assertExists()
        rule.onNodeWithText("Swipe an article left to save it for offline reading.").assertExists()
    }

    @Test fun emptyFeedInvitesAPull() {
        show(ArticleListState(), source = ListSource.Feed(10))
        rule.onNodeWithText("No articles").assertExists()
        rule.onNodeWithText("Pull down to refresh.").assertExists()
    }

    @Test fun emptyFeedWithoutPullSaysSo() {
        show(ArticleListState(pullToRefresh = false), source = ListSource.Feed(10))
        rule.onNodeWithText("Nothing here yet.").assertExists()
    }
}
