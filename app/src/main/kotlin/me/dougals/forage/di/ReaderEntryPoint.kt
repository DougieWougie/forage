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
package me.dougals.forage.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.dougals.forage.data.content.ImageResourceFetcher

/** Lets the reader WebView client (not Hilt-managed) reach the guarded image fetcher. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderEntryPoint {
    fun imageFetcher(): ImageResourceFetcher
}
