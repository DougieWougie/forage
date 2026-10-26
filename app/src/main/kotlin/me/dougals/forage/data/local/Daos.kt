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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class UnreadCount(val feedId: Long, val count: Int)

data class OfflineStats(val count: Int, val bytes: Long)

/**
 * Columns for article lists. Bodies are truncated and the offline copy is reduced to a presence marker, so
 * a few large articles can't overflow the 2MB CursorWindow; the reader loads the full row via observeById.
 */
private const val LIST_COLUMNS =
    "id, feedId, guid, title, url, author, substr(summary, 1, 4000) AS summary, " +
        "substr(contentHtml, 1, 4000) AS contentHtml, publishedAt, isRead, isSaved, " +
        "CASE WHEN offlineHtml IS NULL THEN NULL ELSE '' END AS offlineHtml, remoteId, imageUrl"

const val SEARCH_LIMIT = 200

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long
    @Query("DELETE FROM folders WHERE id = :id") suspend fun delete(id: Long)
    @Update suspend fun update(folder: FolderEntity)
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun getById(id: Long): FolderEntity?
    @Query("SELECT * FROM folders") suspend fun getAll(): List<FolderEntity>
    @Query("SELECT * FROM folders ORDER BY name") fun observeAll(): Flow<List<FolderEntity>>
}

@Dao
interface FeedDao {
    @Insert suspend fun insert(feed: FeedEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(feed: FeedEntity): Long
    @Query("DELETE FROM feeds WHERE id = :id") suspend fun delete(id: Long)
    @Update suspend fun update(feed: FeedEntity)
    @Query("SELECT * FROM feeds WHERE id = :id") suspend fun getById(id: Long): FeedEntity?
    @Query("SELECT * FROM feeds WHERE url = :url") suspend fun getByUrl(url: String): FeedEntity?
    @Query("SELECT * FROM feeds") suspend fun getAll(): List<FeedEntity>
    @Query("SELECT * FROM feeds ORDER BY title") fun observeAll(): Flow<List<FeedEntity>>
}

@Dao
interface ArticleDao {
    /** Returns -1 for rows skipped because (feedId, guid) already exists, preserving user state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(articles: List<ArticleEntity>): List<Long>
    @Query("SELECT * FROM articles WHERE id = :id") suspend fun getById(id: Long): ArticleEntity?
    @Query("SELECT * FROM articles") suspend fun getAll(): List<ArticleEntity>
    @Query("SELECT * FROM articles WHERE remoteId = :remoteId") suspend fun getByRemoteId(remoteId: Long): ArticleEntity?
    @Query("SELECT * FROM articles WHERE feedId = :feedId AND guid = :guid")
    suspend fun getByFeedAndGuid(feedId: Long, guid: String): ArticleEntity?
    @Query("SELECT id FROM articles WHERE feedId = :feedId AND isRead = 0") suspend fun unreadIds(feedId: Long): List<Long>
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND isRead = 0") suspend fun unreadCount(feedId: Long): Int
    @Query("UPDATE articles SET remoteId = :remoteId, isRead = :read, isSaved = :saved WHERE id = :id")
    suspend fun updateRemoteState(id: Long, remoteId: Long, read: Boolean, saved: Boolean)
    /** Backfills articles stored before thumbnails existed; never replaces one already set. */
    @Query("UPDATE articles SET imageUrl = :url WHERE feedId = :feedId AND guid = :guid AND imageUrl IS NULL")
    suspend fun fillImageUrl(feedId: Long, guid: String, url: String)
    @Query("UPDATE articles SET offlineHtml = NULL WHERE id = :id") suspend fun clearOfflineHtml(id: Long)
    @Query("UPDATE articles SET offlineHtml = NULL WHERE isSaved = 0 AND offlineHtml IS NOT NULL") suspend fun clearUnsavedOfflineHtml()
    @Query("UPDATE articles SET offlineHtml = NULL WHERE offlineHtml IS NOT NULL") suspend fun clearAllOfflineHtml()
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(length(CAST(offlineHtml AS BLOB))), 0) AS bytes FROM articles WHERE offlineHtml IS NOT NULL")
    fun observeOfflineStats(): Flow<OfflineStats>
    @Query("SELECT " + LIST_COLUMNS + " FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC")
    fun observeUnread(): Flow<List<ArticleEntity>>
    @Query("SELECT " + LIST_COLUMNS + " FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun observeByFeed(feedId: Long): Flow<List<ArticleEntity>>
    @Query("SELECT * FROM articles WHERE id = :id") fun observeById(id: Long): Flow<ArticleEntity?>
    @Query("SELECT " + LIST_COLUMNS + " FROM articles WHERE isSaved = 1 ORDER BY publishedAt DESC")
    fun observeSaved(): Flow<List<ArticleEntity>>
    /** [pattern] is a LIKE pattern in which `\` escapes `%` and `_`; capped so a broad term can't flood the list. */
    @Query(
        "SELECT " + LIST_COLUMNS + " FROM articles WHERE articles.title LIKE :pattern ESCAPE '\\' " +
            "OR articles.author LIKE :pattern ESCAPE '\\' OR articles.summary LIKE :pattern ESCAPE '\\' " +
            "OR articles.contentHtml LIKE :pattern ESCAPE '\\' ORDER BY publishedAt DESC LIMIT " + SEARCH_LIMIT,
    )
    fun search(pattern: String): Flow<List<ArticleEntity>>
    @Query("SELECT feedId, COUNT(*) AS count FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnreadCounts(): Flow<List<UnreadCount>>
    @Query("UPDATE articles SET isRead = :read WHERE id = :id") suspend fun setRead(id: Long, read: Boolean)
    @Query("UPDATE articles SET isSaved = :saved WHERE id = :id") suspend fun setSaved(id: Long, saved: Boolean)
    @Query("UPDATE articles SET offlineHtml = :html WHERE id = :id") suspend fun saveOfflineHtml(id: Long, html: String)
    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId") suspend fun markFeedRead(feedId: Long)
    /** Unread articles in the same feed as [articleId] that were published before it. */
    @Query(
        "SELECT id FROM articles WHERE isRead = 0 AND feedId = (SELECT feedId FROM articles WHERE id = :articleId) " +
            "AND publishedAt < (SELECT publishedAt FROM articles WHERE id = :articleId)",
    )
    suspend fun unreadIdsOlderThan(articleId: Long): List<Long>
    @Query("UPDATE articles SET isRead = :read WHERE id IN (:ids)") suspend fun setReadIn(ids: List<Long>, read: Boolean)
}

@Dao
interface PendingChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(change: PendingChangeEntity)
    @Query("SELECT * FROM pending_changes ORDER BY createdAt") suspend fun getAll(): List<PendingChangeEntity>
    /** Only removes the row if it is still the exact change that was flushed, not a newer replacement. */
    @Query("DELETE FROM pending_changes WHERE articleId = :articleId AND field = :field AND createdAt = :createdAt AND value = :value")
    suspend fun deleteIfUnchanged(articleId: Long, field: SyncField, createdAt: Long, value: Boolean)
}
