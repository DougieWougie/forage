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
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
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

    @Test fun fontScaleIsClamped() = runTest {
        val r = repo()
        r.setFontScale(9f); assertEquals(2.0f, r.settings.first().fontScale)
        r.setFontScale(0.1f); assertEquals(0.8f, r.settings.first().fontScale)
    }
}
