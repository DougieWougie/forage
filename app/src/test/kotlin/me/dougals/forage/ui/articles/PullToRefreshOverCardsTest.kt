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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import me.dougals.forage.presentation.articles.ListSource
import me.dougals.forage.testutil.article
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Dragging down over the swipeable cards must still reach the pull-to-refresh container. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PullToRefreshOverCardsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun draggingDownOverTheCardsRefreshes() {
        var refreshed = 0
        rule.setContent {
            ForageTheme {
                PullToRefreshBox(isRefreshing = false, onRefresh = { refreshed++ }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items((1L..5L).toList(), key = { it }) { SwipeableArticleCard(article(it), "LWN", {}, {}, {}) }
                    }
                }
            }
        }
        rule.onRoot().performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        assertEquals(1, refreshed)
    }

    @Test fun draggingDownOverTheEmptyStateRefreshes() {
        var refreshed = 0
        rule.setContent {
            ForageTheme {
                PullToRefreshBox(isRefreshing = false, onRefresh = { refreshed++ }, modifier = Modifier.fillMaxSize()) {
                    EmptyState(ListSource.Unread, canPull = true)
                }
            }
        }
        rule.onRoot().performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        assertEquals(1, refreshed)
    }
}
