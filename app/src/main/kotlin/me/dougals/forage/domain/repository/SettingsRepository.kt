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
import me.dougals.forage.domain.model.SyncMode

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSyncMode(mode: SyncMode)
    suspend fun setAllowLocalNetwork(allow: Boolean)
    suspend fun setReaderFont(font: ReaderFont)
    /** Clamped to [AppSettings.MIN_SCALE]..[AppSettings.MAX_SCALE]. */
    suspend fun setFontScale(scale: Float)
}
