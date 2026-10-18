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
package me.dougals.forage.presentation.search

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import me.dougals.forage.presentation.articles.ToggleUndo
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.article
import me.dougals.forage.testutil.awaitUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SearchViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repo = mockk<FeedRepository>(relaxed = true)
    private val settings = MutableStateFlow(AppSettings(thumbnails = ThumbnailMode.OFF))
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true).also { every { it.settings } returns settings }
    private fun vm() = SearchViewModel(repo, settingsRepo)

    init {
        every { repo.observeFeeds() } returns flowOf(listOf(Feed(1, "u", "LWN", null, null, 0)))
        every { repo.search(any()) } returns flowOf(emptyList())
    }

    @Test fun startsEmptyWithNothingToReport() = runTest {
        vm().state.test {
            val s = awaitItem()
            assertFalse(s.hasQuery)
            assertFalse(s.noResults)
        }
    }

    @Test fun searchesOnceTheUserPausesTyping() = runTest {
        every { repo.search("rust") } returns flowOf(listOf(article(1), article(2)))
        val v = vm()
        v.state.test {
            awaitItem()
            v.setQuery("r"); v.setQuery("ru"); v.setQuery("rust")
            val s = awaitUntil { it.articles.size == 2 }
            assertEquals("rust", s.resultsFor)
            assertEquals(mapOf(1L to "LWN"), s.feedTitles)
            assertEquals(ThumbnailMode.OFF, s.thumbnails)
        }
        verify(exactly = 0) { repo.search("r") }
        verify(exactly = 0) { repo.search("ru") }
    }

    @Test fun noResultsIsReportedForAnEmptyAnswerToTheCurrentQuery() = runTest {
        val v = vm()
        v.state.test {
            awaitItem()
            v.setQuery("zzz")
            assertTrue(awaitUntil { it.resultsFor == "zzz" }.noResults)
        }
    }

    @Test fun noResultsWaitsForTheAnswerToCatchUpWithTheQuery() {
        assertFalse(SearchState(query = "zzz", resultsFor = "").noResults)
        assertFalse(SearchState(query = "zzz", resultsFor = "zz").noResults)
        assertTrue(SearchState(query = " zzz ", resultsFor = "zzz").noResults)
        assertFalse(SearchState(query = "", resultsFor = "").noResults)
    }

    @Test fun surroundingSpaceDoesNotChangeTheSearch() = runTest {
        every { repo.search("rust") } returns flowOf(listOf(article(1)))
        val v = vm()
        v.state.test {
            awaitItem()
            v.setQuery("  rust ")
            assertEquals("rust", awaitUntil { it.articles.isNotEmpty() }.resultsFor)
        }
    }

    @Test fun togglesGoToTheRepositoryAndCanBeUndone() = runTest {
        val v = vm()
        v.state.test {
            awaitItem()
            v.toggleRead(article(1, read = false))
            assertEquals(ToggleUndo(1, ToggleUndo.Field.READ, true), awaitUntil { it.toggled != null }.toggled)
            v.undoToggle()
            awaitUntil { it.toggled == null }
            v.toggleSaved(article(2, saved = false))
            awaitUntil { it.toggled != null }
            v.dismissToggle()
            awaitUntil { it.toggled == null }
        }
        coVerify { repo.setRead(1, true); repo.setRead(1, false); repo.setSaved(2, true) }
        coVerify(exactly = 0) { repo.setSaved(2, false) }
    }
}
