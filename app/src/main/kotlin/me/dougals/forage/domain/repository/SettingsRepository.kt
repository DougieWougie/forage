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
package me.dougals.forage.domain.repository

import kotlinx.coroutines.flow.Flow
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSyncMode(mode: SyncMode)
    suspend fun setAllowLocalNetwork(allow: Boolean)
    suspend fun setPullToRefresh(enabled: Boolean)
    suspend fun setRefreshInterval(interval: RefreshInterval)
    suspend fun setRefreshWifiOnly(wifiOnly: Boolean)
    /** Replaces every folder opted in to digests, e.g. to select or clear them all at once. */
    suspend fun setNotifyFolders(folderIds: Set<Long>)
    suspend fun setFolderNotifications(folderId: Long, notify: Boolean)
    suspend fun setThumbnails(mode: ThumbnailMode)
    suspend fun setThumbnailSide(side: ThumbnailSide)
    suspend fun dismissSuggestion(feedUrl: String)
    suspend fun clearDismissedSuggestions()
    suspend fun setReaderFont(font: ReaderFont)
    /** Clamped to [AppSettings.MIN_SCALE]..[AppSettings.MAX_SCALE]. */
    suspend fun setFontScale(scale: Float)
}
