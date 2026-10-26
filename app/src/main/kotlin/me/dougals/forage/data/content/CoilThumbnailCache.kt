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
package me.dougals.forage.data.content

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dougals.forage.domain.repository.ThumbnailCache

class CoilThumbnailCache(private val context: Context) : ThumbnailCache {
    private val cache get() = SingletonImageLoader.get(context).diskCache

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { cache?.size ?: 0L }
    override suspend fun clear() = withContext(Dispatchers.IO) { cache?.clear(); Unit }
}
