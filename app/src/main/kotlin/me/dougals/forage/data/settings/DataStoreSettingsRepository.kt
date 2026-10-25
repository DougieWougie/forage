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
import me.dougals.forage.domain.model.ReadFilter
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.domain.repository.SettingsRepository

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            syncMode = p[MODE]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.LOCAL,
            readerFont = p[FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: ReaderFont.SERIF,
            fontScale = p[SCALE] ?: 1.0f,
            allowLocalNetwork = p[LOCAL_NET] ?: false,
            pullToRefresh = p[PULL] ?: true,
            markReadOnOpen = p[MARK_READ_ON_OPEN] ?: true,
            refreshInterval = p[REFRESH]?.let { runCatching { RefreshInterval.valueOf(it) }.getOrNull() } ?: RefreshInterval.HOURLY,
            refreshWifiOnly = p[REFRESH_WIFI] ?: false,
            thumbnails = p[THUMBS]?.let { runCatching { ThumbnailMode.valueOf(it) }.getOrNull() } ?: ThumbnailMode.ALWAYS,
            thumbnailSide = p[THUMB_SIDE]?.let { runCatching { ThumbnailSide.valueOf(it) }.getOrNull() } ?: ThumbnailSide.END,
            notifyFolderIds = p[NOTIFY_FOLDERS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            dismissedSuggestions = p[DISMISSED].orEmpty(),
            collapsedFolderIds = p[COLLAPSED_FOLDERS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            readFilter = p[READ_FILTER]?.let { runCatching { ReadFilter.valueOf(it) }.getOrNull() } ?: ReadFilter.ALL,
        )
    }

    override suspend fun setSyncMode(mode: SyncMode) { store.edit { it[MODE] = mode.name } }
    override suspend fun setAllowLocalNetwork(allow: Boolean) { store.edit { it[LOCAL_NET] = allow } }
    override suspend fun setPullToRefresh(enabled: Boolean) { store.edit { it[PULL] = enabled } }
    override suspend fun setMarkReadOnOpen(enabled: Boolean) { store.edit { it[MARK_READ_ON_OPEN] = enabled } }
    override suspend fun setRefreshInterval(interval: RefreshInterval) { store.edit { it[REFRESH] = interval.name } }
    override suspend fun setRefreshWifiOnly(wifiOnly: Boolean) { store.edit { it[REFRESH_WIFI] = wifiOnly } }
    override suspend fun setNotifyFolders(folderIds: Set<Long>) { store.edit { it[NOTIFY_FOLDERS] = folderIds.map(Long::toString).toSet() } }
    override suspend fun setFolderNotifications(folderId: Long, notify: Boolean) {
        store.edit {
            val current = it[NOTIFY_FOLDERS].orEmpty()
            it[NOTIFY_FOLDERS] = if (notify) current + folderId.toString() else current - folderId.toString()
        }
    }
    override suspend fun setCollapsedFolders(folderIds: Set<Long>) { store.edit { it[COLLAPSED_FOLDERS] = folderIds.map(Long::toString).toSet() } }
    override suspend fun setFolderCollapsed(folderId: Long, collapsed: Boolean) {
        store.edit {
            val current = it[COLLAPSED_FOLDERS].orEmpty()
            it[COLLAPSED_FOLDERS] = if (collapsed) current + folderId.toString() else current - folderId.toString()
        }
    }
    override suspend fun setReadFilter(filter: ReadFilter) { store.edit { it[READ_FILTER] = filter.name } }
    override suspend fun setThumbnails(mode: ThumbnailMode) { store.edit { it[THUMBS] = mode.name } }
    override suspend fun setThumbnailSide(side: ThumbnailSide) { store.edit { it[THUMB_SIDE] = side.name } }
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
        val MARK_READ_ON_OPEN = booleanPreferencesKey("mark_read_on_open")
        val REFRESH = stringPreferencesKey("refresh_interval")
        val REFRESH_WIFI = booleanPreferencesKey("refresh_wifi_only")
        val THUMBS = stringPreferencesKey("thumbnails")
        val THUMB_SIDE = stringPreferencesKey("thumbnail_side")
        val NOTIFY_FOLDERS = stringSetPreferencesKey("notify_folder_ids")
        val COLLAPSED_FOLDERS = stringSetPreferencesKey("collapsed_folder_ids")
        val READ_FILTER = stringPreferencesKey("read_filter")
        val DISMISSED = stringSetPreferencesKey("dismissed_suggestions")
    }
}
