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

enum class ReaderFont { SERIF, SANS, DYSLEXIC }

/** When the article list shows thumbnails. [WIFI_ONLY] still shows ones already downloaded on a metered connection. */
enum class ThumbnailMode { ALWAYS, WIFI_ONLY, OFF }

data class AppSettings(
    val syncMode: SyncMode = SyncMode.LOCAL,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val fontScale: Float = 1.0f,
    /** Off by default: feeds and article pages must resolve to public addresses (SSRF guard). */
    val allowLocalNetwork: Boolean = false,
    /** Pulling down on an article list refreshes feeds. */
    val pullToRefresh: Boolean = true,
    val thumbnails: ThumbnailMode = ThumbnailMode.ALWAYS,
    /** Feed URLs of suggestions the user waved away. */
    val dismissedSuggestions: Set<String> = emptySet(),
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.0f
    }
}
