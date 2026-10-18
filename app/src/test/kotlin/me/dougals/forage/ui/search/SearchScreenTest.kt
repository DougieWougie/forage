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
package me.dougals.forage.ui.search

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import me.dougals.forage.presentation.articles.ToggleUndo
import me.dougals.forage.presentation.search.SearchState
import me.dougals.forage.presentation.search.SearchViewModel
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
class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    private val state = MutableStateFlow(SearchState())
    private val vm = mockk<SearchViewModel>(relaxed = true).also { every { it.state } returns state }
    private val opened = mutableListOf<Long>()
    private var backs = 0

    private fun show(initial: SearchState = SearchState()) {
        state.value = initial
        rule.setContent { ForageTheme { SearchScreen(onOpenArticle = { opened += it }, onBack = { backs++ }, vm = vm) } }
    }

    @Test fun anEmptySearchInvitesTyping() {
        show()
        rule.onNodeWithText("Search your articles").assertExists()
    }

    @Test fun typingSendsTheQueryToTheViewModel() {
        show()
        rule.onNodeWithText("Search articles").performTextInput("rust")
        rule.waitForIdle()
        verify { vm.setQuery("rust") }
    }

    @Test fun resultsShowAsCardsAndOpenTheArticle() {
        show(SearchState(query = "title", resultsFor = "title", articles = listOf(article(1, feedId = 10), article(2, feedId = 10)), feedTitles = mapOf(10L to "LWN")))
        rule.onNodeWithText("Title 1").assertExists()
        rule.onNodeWithText("Title 2").performClick(); rule.waitForIdle()
        assertEquals(listOf(2L), opened)
    }

    @Test fun noMatchesSaysWhatWasSearchedFor() {
        show(SearchState(query = "zzz ", resultsFor = "zzz"))
        rule.onNodeWithText("No matches").assertExists()
        rule.onNodeWithText("Nothing found for \u201czzz\u201d.").assertExists()
    }

    @Test fun nothingIsClaimedWhileTheAnswerIsStillComing() {
        show(SearchState(query = "zzz", resultsFor = ""))
        rule.onNodeWithText("No matches").assertDoesNotExist()
        rule.onNodeWithText("Search your articles").assertDoesNotExist()
    }

    @Test fun clearingEmptiesTheQuery() {
        show()
        rule.onNodeWithText("Search articles").performTextInput("x")
        rule.onNodeWithContentDescription("Clear search").performClick(); rule.waitForIdle()
        verify { vm.setQuery("") }
    }

    @Test fun backGoesBack() {
        show()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test fun aToggleOffersUndo() {
        show(SearchState(query = "t", resultsFor = "t", articles = listOf(article(1)), toggled = ToggleUndo(1, ToggleUndo.Field.SAVED, true)))
        rule.onNodeWithText("Saved").assertExists()
        rule.onNodeWithText("Undo").performClick(); rule.waitForIdle()
        verify { vm.undoToggle() }
    }
}
