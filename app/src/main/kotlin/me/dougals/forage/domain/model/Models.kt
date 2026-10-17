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

data class Folder(val id: Long, val name: String)

data class Feed(
    val id: Long,
    val url: String,
    val title: String,
    val siteUrl: String?,
    val folderId: Long?,
    val unreadCount: Int,
)

data class Article(
    val id: Long,
    val feedId: Long,
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val summary: String?,
    val contentHtml: String?,
    val publishedAt: Long,
    val isRead: Boolean,
    val isSaved: Boolean,
    val offlineHtml: String?,
    /** Thumbnail for the article list; null when the article has no usable image. */
    val imageUrl: String? = null,
) {
    val hasOfflineCopy: Boolean get() = offlineHtml != null

    /** Best available body for the reader: cleaned offline copy, then feed content, then summary. */
    val readerHtml: String get() = offlineHtml ?: contentHtml ?: summary ?: ""
}

enum class SyncMode { LOCAL, NEXTCLOUD }
