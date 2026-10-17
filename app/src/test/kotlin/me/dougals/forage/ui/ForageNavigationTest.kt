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
package me.dougals.forage.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForageNavigationTest {
    @get:Rule val rule = createComposeRule()

    private val screens = ForageScreens(
        list = { kind, _ -> Text("list:$kind") },
        categories = { Text("screen:categories") },
        settings = { Text("screen:settings") },
        reader = { Text("screen:reader") },
        login = { _, _ -> Text("screen:login") },
    )

    private fun start() = rule.setContent { ForageTheme { ForageNavScaffold(screens) } }
    private fun tab(label: String) = rule.onNodeWithText(label).performClick().also { rule.waitForIdle() }
    private fun shows(text: String) = rule.onNodeWithText(text).assertExists()
    private fun hides(text: String) = rule.onNodeWithText(text).assertDoesNotExist()

    @Test fun opensOnUnread() {
        start()
        shows("list:unread")
    }

    @Test fun savedTabShowsSavedNotUnread() {
        start()
        tab("Saved")
        shows("list:saved"); hides("list:unread")
    }

    @Test fun unreadAndSavedStayDistinctAcrossRepeatedSwitches() {
        start()
        repeat(3) {
            tab("Saved"); shows("list:saved"); hides("list:unread")
            tab("Unread"); shows("list:unread"); hides("list:saved")
        }
    }

    @Test fun savedSurvivesAVisitToAnotherTab() {
        start()
        tab("Saved"); tab("Settings"); shows("screen:settings")
        tab("Saved")
        shows("list:saved"); hides("list:unread")
    }

    @Test fun selectedTabFollowsTheVisibleList() {
        start()
        tab("Saved")
        rule.onNodeWithText("Saved").assertIsSelected()
        tab("Unread")
        rule.onNodeWithText("Unread").assertIsSelected()
    }

    @Test fun otherTabsStillWork() {
        start()
        tab("Categories"); shows("screen:categories")
        tab("Settings"); shows("screen:settings")
        tab("Unread"); shows("list:unread")
    }
}
