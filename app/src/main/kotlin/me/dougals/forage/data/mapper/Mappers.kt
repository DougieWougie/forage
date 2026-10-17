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
package me.dougals.forage.data.mapper

import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder

fun FolderEntity.toDomain() = Folder(id, name)

fun FeedEntity.toDomain(unreadCount: Int = 0) = Feed(id, url, title, siteUrl, folderId, unreadCount)

fun ArticleEntity.toDomain() = Article(
    id, feedId, guid, title, url, author, summary, contentHtml, publishedAt, isRead, isSaved, offlineHtml, imageUrl,
)

fun Article.toEntity() = ArticleEntity(
    id = id, feedId = feedId, guid = guid, title = title, url = url, author = author, summary = summary,
    contentHtml = contentHtml, publishedAt = publishedAt, isRead = isRead, isSaved = isSaved,
    offlineHtml = offlineHtml, imageUrl = imageUrl,
)
