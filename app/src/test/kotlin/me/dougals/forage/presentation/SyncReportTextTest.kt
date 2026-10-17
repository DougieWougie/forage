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
package me.dougals.forage.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.dougals.forage.domain.model.SyncReport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncReportTextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun SyncReport.summary() = toUiText().resolve(context)

    @Test fun nothingChangedSaysUpToDate() = assertEquals("Already up to date", SyncReport().summary())

    @Test fun singularAndPlural() {
        assertEquals("Synced · 1 new article", SyncReport(newArticles = 1).summary())
        assertEquals("Synced · 1 change sent", SyncReport(changesSent = 1).summary())
        assertEquals("Synced · 1 feed added to Nextcloud", SyncReport(feedsAdded = 1).summary())
        assertEquals("Synced · 2 feeds added to Nextcloud", SyncReport(feedsAdded = 2).summary())
    }

    @Test fun describesWhatCameDownFromNextcloud() {
        assertEquals("Synced · 3 marked read", SyncReport(markedRead = 3).summary())
        assertEquals("Synced · 1 saved", SyncReport(saved = 1).summary())
        assertEquals("Synced · 2 feeds from Nextcloud", SyncReport(feedsReceived = 2).summary())
        assertEquals("Synced · 1 feed from Nextcloud", SyncReport(feedsReceived = 1).summary())
        assertEquals("Synced · 1 feed removed", SyncReport(feedsRemoved = 1).summary())
    }

    @Test fun everythingInAStableOrder() {
        assertEquals(
            "Synced · 1 feed added to Nextcloud · 18 new articles · 3 changes sent",
            SyncReport(newArticles = 18, changesSent = 3, feedsAdded = 1).summary(),
        )
        assertEquals(
            "Synced · 1 feed added to Nextcloud · 2 feeds from Nextcloud · 1 feed removed · 18 new articles · 4 marked read · 2 saved · 3 changes sent",
            SyncReport(18, 3, 1, markedRead = 4, saved = 2, feedsReceived = 2, feedsRemoved = 1).summary(),
        )
    }
}
