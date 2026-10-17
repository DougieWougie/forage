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
    @Test fun nothingChangedSaysUpToDate() = assertEquals("Already up to date", SyncReport().summary())

    @Test fun singularAndPlural() {
        assertEquals("Synced · 1 new article", SyncReport(newArticles = 1).summary())
        assertEquals("Synced · 1 change sent", SyncReport(changesSent = 1).summary())
        assertEquals("Synced · 1 feed added to Nextcloud", SyncReport(feedsAdded = 1).summary())
        assertEquals("Synced · 2 feeds added to Nextcloud", SyncReport(feedsAdded = 2).summary())
    }

    @Test fun everythingInAStableOrder() {
        assertEquals(
            "Synced · 1 feed added to Nextcloud · 18 new articles · 3 changes sent",
            SyncReport(newArticles = 18, changesSent = 3, feedsAdded = 1).summary(),
        )
    }
}
