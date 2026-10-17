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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.SettingsRepository

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            syncMode = p[MODE]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.LOCAL,
            readerFont = p[FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: ReaderFont.SERIF,
            fontScale = p[SCALE] ?: 1.0f,
            allowLocalNetwork = p[LOCAL_NET] ?: false,
            pullToRefresh = p[PULL] ?: true,
            dismissedSuggestions = p[DISMISSED].orEmpty(),
        )
    }

    override suspend fun setSyncMode(mode: SyncMode) { store.edit { it[MODE] = mode.name } }
    override suspend fun setAllowLocalNetwork(allow: Boolean) { store.edit { it[LOCAL_NET] = allow } }
    override suspend fun setPullToRefresh(enabled: Boolean) { store.edit { it[PULL] = enabled } }
    override suspend fun dismissSuggestion(feedUrl: String) { store.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + feedUrl } }
    override suspend fun clearDismissedSuggestions() { store.edit { it.remove(DISMISSED) } }
    override suspend fun setReaderFont(font: ReaderFont) { store.edit { it[FONT] = font.name } }
    override suspend fun setFontScale(scale: Float) {
        store.edit { it[SCALE] = scale.coerceIn(AppSettings.MIN_SCALE, AppSettings.MAX_SCALE) }
    }

    private companion object {
        val MODE = stringPreferencesKey("sync_mode")
        val FONT = stringPreferencesKey("reader_font")
        val SCALE = floatPreferencesKey("font_scale")
        val LOCAL_NET = booleanPreferencesKey("allow_local_network")
        val PULL = booleanPreferencesKey("pull_to_refresh")
        val DISMISSED = stringSetPreferencesKey("dismissed_suggestions")
    }
}
