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
package me.dougals.forage.ui.categories

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.categories.CategoriesState
import me.dougals.forage.presentation.categories.CategoriesViewModel
import me.dougals.forage.presentation.categories.CategoryNode
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoriesScreenTest {
    @get:Rule val rule = createComposeRule()

    private val tech = Folder(1, "Tech")
    private val news = Folder(2, "News")
    private val lwn = Feed(10, "https://lwn.net/rss", "LWN", null, folderId = 1, unreadCount = 3)
    private val loose = Feed(11, "https://loose.example/feed", "Loose", null, folderId = null, unreadCount = 0)
    private val tree = CategoriesState(
        categories = listOf(CategoryNode(news, emptyList()), CategoryNode(tech, listOf(lwn)), CategoryNode(null, listOf(loose))),
    )
    private val suggestion = FeedSuggestion("The Register", "https://reg.example/f", "https://reg.example/", "IT news", setOf("technology"), uk = true, featured = true)

    private val state = MutableStateFlow(CategoriesState())
    private val vm = mockk<CategoriesViewModel>(relaxed = true).also { every { it.state } returns state }
    private val opened = mutableListOf<Long>()

    private fun show(initial: CategoriesState = tree) {
        state.value = initial
        rule.setContent { ForageTheme { CategoriesScreen(onOpenFeed = { opened += it }, vm = vm) } }
    }

    private fun click(text: String) = rule.onNodeWithText(text).performClick().also { rule.waitForIdle() }
    private fun inDialog(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isDialog()))
    private fun inMenu(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick().also { rule.waitForIdle() }
    private fun field(label: String, value: String) = inDialog(label).performTextReplacement(value)
    /** Rows appear in tree order: LWN under Tech, then Loose under Uncategorized. */
    private fun feedMenu(feed: String) {
        rule.onAllNodes(hasContentDescription("Feed options"))[listOf("LWN", "Loose").indexOf(feed)].performClick()
        rule.waitForIdle()
    }

    @Test fun emptyStateInvitesAddingAFeed() {
        show(CategoriesState())
        rule.onNodeWithText("No feeds yet").assertExists()
    }

    @Test fun foldersShowTheirFeedsAndUnreadCounts() {
        show()
        rule.onNodeWithText("NEWS").assertExists()
        rule.onNodeWithText("TECH").assertExists()
        rule.onNodeWithText("UNCATEGORIZED").assertExists()
        rule.onNodeWithText("LWN").assertExists()
        rule.onAllNodes(hasText("3")).fetchSemanticsNodes().let { assertEquals(2, it.size) } // folder badge and feed chip
        rule.onNodeWithText("No feeds yet").assertDoesNotExist()
    }

    @Test fun collapsedFolderHidesItsFeedsButKeepsItsBadge() {
        show(tree.copy(collapsedFolderIds = setOf(1L)))
        rule.onNodeWithText("TECH").assertExists()
        rule.onNodeWithText("LWN").assertDoesNotExist()
        rule.onNodeWithText("3").assertExists() // folder badge only
        rule.onNodeWithText("Loose").assertExists()
    }

    @Test fun chevronTogglesAFolder() {
        show()
        rule.onNodeWithContentDescription("Collapse folder Tech").performClick()
        verify { vm.toggleFolder(1, true) }
        state.value = tree.copy(collapsedFolderIds = setOf(1L))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Expand folder Tech").performClick()
        verify { vm.toggleFolder(1, false) }
    }

    @Test fun uncategorizedHasNoChevron() {
        show()
        rule.onAllNodes(hasContentDescription("Collapse folder", substring = true)).fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test fun menuExpandsAndCollapsesEverything() {
        show()
        rule.onNodeWithContentDescription("More").performClick()
        inMenu("Collapse all")
        verify { vm.setAllFoldersCollapsed(true) }
        rule.onNodeWithContentDescription("More").performClick()
        inMenu("Expand all")
        verify { vm.setAllFoldersCollapsed(false) }
    }

    @Test fun tappingAFeedOpensIt() {
        show()
        click("LWN")
        assertEquals(listOf(10L), opened)
    }

    @Test fun busyShowsProgress() {
        show(tree.copy(isBusy = true))
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test fun messagesAreShownThenDismissed() {
        show(tree.copy(message = UiText.Raw("Added LWN")))
        rule.onNodeWithText("Added LWN").assertExists()
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("Added LWN").assertDoesNotExist()
        verify { vm.dismissMessage() }
    }

    // Feed menu

    @Test fun feedMenuMarksAllRead() {
        show()
        feedMenu("LWN"); inMenu("Mark all as read")
        verify { vm.markFeedRead(10) }
    }

    @Test fun feedMenuRemovesTheFeed() {
        show()
        feedMenu("Loose"); inMenu("Remove feed")
        verify { vm.removeFeed(11) }
    }

    @Test fun movingAFeedOffersEveryFolderAndMarksTheCurrentOne() {
        show()
        feedMenu("LWN"); inMenu("Move to folder…")
        inDialog("Move “LWN” to").assertExists()
        inDialog("Uncategorized").assertExists()
        inDialog("Tech").assertIsSelected()
        inDialog("News").performClick(); rule.waitForIdle()
        verify { vm.moveFeed(10, 2) }
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun movingAFeedToUncategorizedSendsNoFolder() {
        show()
        feedMenu("LWN"); inMenu("Move to folder…")
        inDialog("Uncategorized").performClick(); rule.waitForIdle()
        verify { vm.moveFeed(10, null) }
    }

    @Test fun cancellingAMoveLeavesTheFeed() {
        show()
        feedMenu("LWN"); inMenu("Move to folder…")
        inDialog("Cancel").performClick(); rule.waitForIdle()
        verify(exactly = 0) { vm.moveFeed(any(), any()) }
    }

    // Folders

    @Test fun deletingAFolderAsksFirst() {
        show()
        rule.onNodeWithContentDescription("Delete folder Tech").performClick(); rule.waitForIdle()
        inDialog("Delete “Tech”?").assertExists()
        inDialog("Cancel").performClick(); rule.waitForIdle()
        verify(exactly = 0) { vm.deleteFolder(any()) }

        rule.onNodeWithContentDescription("Delete folder Tech").performClick(); rule.waitForIdle()
        inDialog("Delete").performClick(); rule.waitForIdle()
        verify { vm.deleteFolder(1) }
    }

    @Test fun uncategorizedCannotBeDeleted() {
        show()
        assertEquals(2, rule.onAllNodes(hasContentDescription("Delete folder", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun newFolderNeedsAName() {
        show()
        rule.onNodeWithContentDescription("New folder").performClick(); rule.waitForIdle()
        inDialog("Create").assertIsNotEnabled()
        field("Name", "Science")
        inDialog("Create").assertIsEnabled().performClick(); rule.waitForIdle()
        verify { vm.createFolder("Science") }
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    // Adding feeds

    @Test fun addingAFeedNeedsAUrlAndCanPickAFolder() {
        show()
        rule.onNodeWithText("Add feed", useUnmergedTree = true).performClick(); rule.waitForIdle()
        inDialog("Add").assertIsNotEnabled()
        field("Feed URL", "example.com/feed")
        inDialog("None").assertIsSelected()
        inDialog("Tech").performClick(); rule.waitForIdle()
        inDialog("Add").assertIsEnabled().performClick(); rule.waitForIdle()
        verify { vm.addFeed("example.com/feed", 1) }
    }

    @Test fun addingAFeedWithoutFoldersHidesTheFolderChoice() {
        show(CategoriesState())
        rule.onNodeWithText("Add feed", useUnmergedTree = true).performClick(); rule.waitForIdle()
        inDialog("Folder").assertDoesNotExist()
        field("Feed URL", "https://example.com/feed")
        inDialog("Add").performClick(); rule.waitForIdle()
        verify { vm.addFeed("https://example.com/feed", null) }
    }

    // Suggestions and the overflow menu

    @Test fun suggestionsCanBeAddedOrDismissed() {
        show(tree.copy(suggestions = listOf(suggestion)))
        rule.onNode(hasScrollToNodeAction()).performScrollToKey("s-https://reg.example/f") // clear of the floating button
        rule.onNodeWithText("SUGGESTED FEEDS").assertExists()
        click("Add")
        rule.onNodeWithContentDescription("Dismiss The Register").performClick(); rule.waitForIdle()
        verify { vm.addSuggestion(suggestion); vm.dismissSuggestion(suggestion) }
    }

    @Test fun overflowMenuRestoresDismissedSuggestions() {
        show()
        rule.onNodeWithContentDescription("More").performClick(); rule.waitForIdle()
        rule.onNode(hasText("Import OPML") and hasAnyAncestor(isPopup())).assertExists()
        rule.onNode(hasText("Export OPML") and hasAnyAncestor(isPopup())).assertExists()
        inMenu("Show dismissed suggestions")
        verify { vm.restoreSuggestions() }
    }
}
