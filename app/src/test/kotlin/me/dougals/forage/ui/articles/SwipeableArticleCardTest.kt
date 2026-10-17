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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import me.dougals.forage.domain.model.Article
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
class SwipeableArticleCardTest {
    @get:Rule val rule = createComposeRule()

    private var read = 0
    private var saved = 0
    private var clicked = 0

    private fun show(a: Article = article(1)) = rule.setContent {
        ForageTheme {
            SwipeableArticleCard(a, "LWN", { clicked++ }, { read++ }, { saved++ })
        }
    }

    @Test fun swipeRightTogglesReadOnly() {
        show()
        rule.onNodeWithText("Title 1").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(1, read)
        assertEquals(0, saved)
    }

    @Test fun swipeLeftTogglesSavedOnly() {
        show()
        rule.onNodeWithText("Title 1").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, saved)
        assertEquals(0, read)
    }

    @Test fun cardSnapsBackSoItCanBeSwipedAgain() {
        show()
        repeat(2) {
            rule.onNodeWithText("Title 1").performTouchInput { swipeRight() }
            rule.waitForIdle()
        }
        assertEquals(2, read)
    }

    @Test fun tapOpensTheArticleWithoutToggling() {
        show()
        rule.onNodeWithText("Title 1").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, clicked)
        assertEquals(0, read + saved)
    }

    @Test fun showsFeedNameAndTitle() {
        show()
        rule.onNodeWithText("LWN", substring = true, ignoreCase = true).assertExists()
    }
}
