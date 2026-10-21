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
package me.dougals.forage.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncReportTest {
    @Test fun newestHeadlinesKeepsTheNewestThreeNewestFirst() {
        val found = listOf(Headline("a", 10), Headline("d", 40), Headline("b", 20), Headline("c", 30))
        assertEquals(listOf("d", "c", "b"), newestHeadlines(found).map { it.title })
    }

    @Test fun newestHeadlinesDropsBlankTitlesBeforeCounting() {
        val found = listOf(Headline("", 50), Headline("  ", 40), Headline("a", 10), Headline("b", 5))
        assertEquals(listOf("a", "b"), newestHeadlines(found).map { it.title })
    }

    @Test fun tiesKeepTheirArrivalOrder() {
        assertEquals(listOf("x", "y"), newestHeadlines(listOf(Headline("x", 7), Headline("y", 7))).map { it.title })
    }
}
