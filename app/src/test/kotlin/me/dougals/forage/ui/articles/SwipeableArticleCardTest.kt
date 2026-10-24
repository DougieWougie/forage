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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.util.concurrent.TimeUnit
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeableArticleCardTest {
    @get:Rule val rule = createComposeRule()

    private var read = 0
    private var saved = 0
    private var clicked = 0

    private var older = 0

    private fun show(a: Article = article(1), canMarkOlder: Boolean = true) = rule.setContent {
        ForageTheme {
            SwipeableArticleCard(a, "LWN", { clicked++ }, { read++ }, { saved++ }, onMarkOlderRead = if (canMarkOlder) ({ older++ }) else null)
        }
    }

    @Test fun longPressPopsUpAConfirmationToMarkOlderAsRead() {
        show()
        rule.onNodeWithText("Title 1").performTouchInput { longClick() }
        rule.onNodeWithText("Mark read").performClick()
        assertEquals(1, older)
        assertEquals(0, clicked)
    }

    @Test fun cancellingTheLongPressPopUpMarksNothing() {
        show()
        rule.onNodeWithText("Title 1").performTouchInput { longClick() }
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, older)
        rule.onNodeWithText("Mark older as read").assertDoesNotExist()
    }

    @Test fun longPressDoesNothingWhenNothingIsOlder() {
        show(canMarkOlder = false)
        rule.onNodeWithText("Title 1").performTouchInput { longClick() }
        rule.onNodeWithText("Mark older as read").assertDoesNotExist()
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

    private fun agedDays(days: Long) = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    private val markers = listOf("Fresh", "Wilting", "Old")

    private fun assertOnlyMarker(expected: String?) = markers.forEach {
        if (it == expected) rule.onNodeWithContentDescription(it).assertExists() else rule.onNodeWithContentDescription(it).assertDoesNotExist()
    }

    @Test fun anUnreadArticleFromTodayShowsTheFreshLeaf() {
        show(article(1, publishedAt = agedDays(0)))
        assertOnlyMarker("Fresh")
    }

    @Test fun anUnreadArticleFromAFewDaysAgoShowsTheWiltingLeaf() {
        show(article(1, publishedAt = agedDays(4)))
        assertOnlyMarker("Wilting")
    }

    @Test fun anUnreadArticleOverAWeekOldShowsTheBrownLeaf() {
        show(article(1, publishedAt = agedDays(10)))
        assertOnlyMarker("Old")
    }

    @Test fun anUnreadArticleWithAnUnknownDateShowsNoLeaf() {
        show(article(1, publishedAt = 0))
        assertOnlyMarker(null)
    }

    @Test fun aReadArticleShowsNoLeaf() {
        show(article(1, read = true, publishedAt = agedDays(0)))
        assertOnlyMarker(null)
    }
}
