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

import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.lightColorScheme
import me.dougals.forage.testutil.article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SwipeColorsTest {
    private val scheme = lightColorScheme()

    @Test fun eachSwipeActionHasItsOwnBackground() {
        val colours = listOf(
            swipeColors(scheme, StartToEnd, article(1), armed = true),
            swipeColors(scheme, StartToEnd, article(1, read = true), armed = true),
            swipeColors(scheme, EndToStart, article(1), armed = true),
            swipeColors(scheme, EndToStart, article(1, saved = true), armed = true),
        ).map { it.container }
        assertEquals(4, colours.toSet().size)
    }

    @Test fun passingTheThresholdDeepensTheBackground() {
        listOf(StartToEnd, EndToStart).forEach {
            assertNotEquals(swipeColors(scheme, it, article(1), armed = false), swipeColors(scheme, it, article(1), armed = true))
        }
    }

    @Test fun anIdleCardShowsNoSwipeColour() {
        assertEquals(scheme.surface, swipeColors(scheme, Settled, article(1), armed = false).container)
    }
}
