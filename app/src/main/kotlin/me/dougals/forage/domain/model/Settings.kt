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

/** How often feeds refresh in the background. [OFF] leaves refreshing to pull-to-refresh and app start. */
enum class RefreshInterval(val hours: Long) { OFF(0), HOURLY(1), EVERY_3_HOURS(3), EVERY_6_HOURS(6), EVERY_12_HOURS(12) }

/** Which side of the article card the thumbnail sits on. */
enum class ThumbnailSide { START, END }

/** Which articles a feed's list shows, by read state. */
enum class ReadFilter { ALL, UNREAD, READ }

data class AppSettings(
    val syncMode: SyncMode = SyncMode.LOCAL,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val fontScale: Float = 1.0f,
    /** Off by default: feeds and article pages must resolve to public addresses (SSRF guard). */
    val allowLocalNetwork: Boolean = false,
    /** Pulling down on an article list refreshes feeds. */
    val pullToRefresh: Boolean = true,
    /** Opening an article in the reader marks it read. Off leaves it unread until marked by hand. */
    val markReadOnOpen: Boolean = true,
    val thumbnails: ThumbnailMode = ThumbnailMode.ALWAYS,
    val thumbnailSide: ThumbnailSide = ThumbnailSide.END,
    val refreshInterval: RefreshInterval = RefreshInterval.HOURLY,
    /** Background refresh waits for an unmetered connection (Wi-Fi) instead of any connection. */
    val refreshWifiOnly: Boolean = false,
    /** Local ids of the folders (or [UNCATEGORISED_FOLDER_ID]) whose new articles appear in a digest. Empty: no digests. */
    val notifyFolderIds: Set<Long> = emptySet(),
    /** Keeps a silent notification up while articles are unread, which is what puts the count on the app icon. */
    val unreadBadge: Boolean = true,
    /** Feed URLs of suggestions the user waved away. */
    val dismissedSuggestions: Set<String> = emptySet(),
    /** Local ids of the folders folded shut on the Categories screen. New folders start open. */
    val collapsedFolderIds: Set<Long> = emptySet(),
    /** The All / Unread / Read chip chosen on a feed's article list, kept across launches. */
    val readFilter: ReadFilter = ReadFilter.ALL,
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.0f
    }
}
