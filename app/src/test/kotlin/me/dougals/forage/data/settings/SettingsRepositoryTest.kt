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
package me.dougals.forage.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReadFilter
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {
    @TempDir lateinit var dir: File

    private fun TestScope.repo() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "s.preferences_pb") },
    )

    @Test fun defaults() = runTest {
        assertEquals(AppSettings(SyncMode.LOCAL, ReaderFont.SERIF, 1.0f), repo().settings.first())
    }

    @Test fun valuesPersist() = runTest {
        val r = repo()
        r.setSyncMode(SyncMode.NEXTCLOUD)
        r.setReaderFont(ReaderFont.SANS)
        r.setFontScale(1.5f)
        assertEquals(AppSettings(SyncMode.NEXTCLOUD, ReaderFont.SANS, 1.5f), r.settings.first())
    }

    @Test fun thumbnailsDefaultToAlwaysAndPersist() = runTest {
        val r = repo()
        assertEquals(ThumbnailMode.ALWAYS, r.settings.first().thumbnails)
        r.setThumbnails(ThumbnailMode.WIFI_ONLY)
        assertEquals(ThumbnailMode.WIFI_ONLY, r.settings.first().thumbnails)
    }

    @Test fun backgroundRefreshDefaultsToHourlyAnyNetworkAndPersists() = runTest {
        val r = repo()
        assertEquals(RefreshInterval.HOURLY, r.settings.first().refreshInterval)
        assertEquals(false, r.settings.first().refreshWifiOnly)
        r.setRefreshInterval(RefreshInterval.EVERY_6_HOURS)
        r.setRefreshWifiOnly(true)
        assertEquals(RefreshInterval.EVERY_6_HOURS, r.settings.first().refreshInterval)
        assertEquals(true, r.settings.first().refreshWifiOnly)
    }

    @Test fun thumbnailSideDefaultsToEndAndPersists() = runTest {
        val r = repo()
        assertEquals(ThumbnailSide.END, r.settings.first().thumbnailSide)
        r.setThumbnailSide(ThumbnailSide.START)
        assertEquals(ThumbnailSide.START, r.settings.first().thumbnailSide)
    }

    @Test fun dyslexicFontPersists() = runTest {
        val r = repo()
        r.setReaderFont(ReaderFont.DYSLEXIC)
        assertEquals(ReaderFont.DYSLEXIC, r.settings.first().readerFont)
    }

    @Test fun localNetworkAccessIsOffByDefaultAndPersists() = runTest {
        val r = repo()
        assertEquals(false, r.settings.first().allowLocalNetwork)
        r.setAllowLocalNetwork(true)
        assertEquals(true, r.settings.first().allowLocalNetwork)
    }

    @Test fun pullToRefreshIsOnByDefaultAndPersists() = runTest {
        val r = repo()
        assertEquals(true, r.settings.first().pullToRefresh)
        r.setPullToRefresh(false)
        assertEquals(false, r.settings.first().pullToRefresh)
    }

    @Test fun collapsedFoldersPersistAndCanBeReplaced() = runTest {
        val r = repo()
        assertEquals(emptySet<Long>(), r.settings.first().collapsedFolderIds)
        r.setFolderCollapsed(1, true); r.setFolderCollapsed(2, true); r.setFolderCollapsed(1, false)
        assertEquals(setOf(2L), r.settings.first().collapsedFolderIds)
        r.setCollapsedFolders(setOf(3, 4))
        assertEquals(setOf(3L, 4L), r.settings.first().collapsedFolderIds)
    }

    @Test fun readFilterPersists() = runTest {
        val r = repo()
        assertEquals(ReadFilter.ALL, r.settings.first().readFilter)
        r.setReadFilter(ReadFilter.UNREAD)
        assertEquals(ReadFilter.UNREAD, r.settings.first().readFilter)
    }

    @Test fun dismissedSuggestionsPersistAndCanBeReset() = runTest {
        val r = repo()
        assertEquals(emptySet<String>(), r.settings.first().dismissedSuggestions)
        r.dismissSuggestion("https://a.example/f"); r.dismissSuggestion("https://b.example/f")
        assertEquals(setOf("https://a.example/f", "https://b.example/f"), r.settings.first().dismissedSuggestions)
        r.clearDismissedSuggestions()
        assertEquals(emptySet<String>(), r.settings.first().dismissedSuggestions)
    }

    @Test fun fontScaleIsClamped() = runTest {
        val r = repo()
        r.setFontScale(9f); assertEquals(2.0f, r.settings.first().fontScale)
        r.setFontScale(0.1f); assertEquals(0.8f, r.settings.first().fontScale)
    }

    @Test fun notificationsAreOffByDefaultAndFolderOptInsPersist() = runTest {
        val r = repo()
        assertEquals(emptySet<Long>(), r.settings.first().notifyFolderIds)
        r.setFolderNotifications(3, true)
        r.setFolderNotifications(7, true)
        r.setFolderNotifications(3, false)
        assertEquals(setOf(7L), r.settings.first().notifyFolderIds)
        r.setNotifyFolders(setOf(-1L, 4L, 9L))
        assertEquals(setOf(-1L, 4L, 9L), r.settings.first().notifyFolderIds)
        r.setNotifyFolders(emptySet())
        assertEquals(emptySet<Long>(), r.settings.first().notifyFolderIds)
    }
}
