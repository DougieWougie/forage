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
package me.dougals.forage.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncField { READ, STARRED }

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val remoteId: Long? = null,
)

@Entity(
    tableName = "feeds",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("url", unique = true), Index("folderId")],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    val folderId: Long? = null,
    val remoteId: Long? = null,
)

@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(FeedEntity::class, ["id"], ["feedId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("feedId", "guid", unique = true), Index("isRead"), Index("isSaved")],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val url: String,
    val author: String? = null,
    val summary: String? = null,
    val contentHtml: String? = null,
    val publishedAt: Long,
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val offlineHtml: String? = null,
    val remoteId: Long? = null,
)

@Entity(
    tableName = "pending_changes",
    primaryKeys = ["articleId", "field"],
    foreignKeys = [ForeignKey(ArticleEntity::class, ["id"], ["articleId"], onDelete = ForeignKey.CASCADE)],
)
data class PendingChangeEntity(
    val articleId: Long,
    val field: SyncField,
    val value: Boolean,
    val createdAt: Long,
)
