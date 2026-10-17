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
package me.dougals.forage.data.nextcloud

import androidx.room.withTransaction
import me.dougals.forage.data.content.ArticleImages
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.domain.model.SyncReport

/** Pulls folders, feeds and items from Nextcloud into Room. Local unsent changes always win. */
class NextcloudSyncer(
    private val db: ForageDatabase,
    private val apiProvider: NextcloudApiProvider,
    private val state: SyncStateStore,
    private val archive: ArchiveScheduler,
) {
    /** @return what came down: new unread articles, state changed elsewhere, and feeds added or removed */
    suspend fun pull(): SyncReport {
        val api = apiProvider.api()
        val remoteFolders = api.folders().folders
        val remoteFeeds = api.feeds().feeds
        val last = state.lastModified
        val items = if (last == 0L) {
            (api.items(type = 3, getRead = false).items + api.items(type = 2, getRead = true).items).distinctBy { it.id }
        } else {
            api.updatedItems(last).items
        }

        lateinit var feeds: FeedsReconciled
        val pulled = db.withTransaction {
            val folderIds = reconcileFolders(remoteFolders)
            feeds = reconcileFeeds(remoteFeeds, folderIds)
            upsertItems(items, feeds.ids)
        }

        state.lastModified = maxOf(last, items.maxOfOrNull { it.lastModified } ?: last)
        pulled.newlySaved.forEach(archive::schedule)
        state.lastSyncedAt = System.currentTimeMillis()
        return SyncReport(
            newArticles = pulled.newUnread, markedRead = pulled.markedRead, saved = pulled.newlySaved.size,
            feedsReceived = feeds.received, feedsRemoved = feeds.removed,
        )
    }

    private class FeedsReconciled(val ids: Map<Long, Long>, val received: Int, val removed: Int)
    private class ItemsUpserted(val newlySaved: List<Long>, val newUnread: Int, val markedRead: Int)

    /** @return remote folder id -> local id */
    private suspend fun reconcileFolders(remote: List<FolderDto>): Map<Long, Long> {
        val dao = db.folderDao()
        val all = dao.getAll()
        val local = all.filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val unsynced = all.filter { it.remoteId == null }.toMutableList()
        val ids = mutableMapOf<Long, Long>()
        for (r in remote) {
            val existing = local[r.id] ?: unsynced.firstOrNull { it.name == r.name }?.also { unsynced -= it }
            ids[r.id] = if (existing == null) {
                dao.insert(FolderEntity(name = r.name, remoteId = r.id))
            } else {
                if (existing.name != r.name || existing.remoteId != r.id) dao.update(existing.copy(name = r.name, remoteId = r.id))
                existing.id
            }
        }
        local.values.filter { it.remoteId !in ids }.forEach { dao.delete(it.id) }
        return ids
    }

    private suspend fun reconcileFeeds(remote: List<FeedDto>, folderIds: Map<Long, Long>): FeedsReconciled {
        val dao = db.feedDao()
        val local = dao.getAll().filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val ids = mutableMapOf<Long, Long>()
        var received = 0
        for (r in remote) {
            val folderId = r.folderId?.let { folderIds[it] }
            val existing = local[r.id] ?: dao.getByUrl(r.url)
            ids[r.id] = if (existing == null) {
                received++
                dao.insert(FeedEntity(url = r.url, title = r.title, siteUrl = r.link, folderId = folderId, remoteId = r.id))
            } else {
                dao.update(existing.copy(url = r.url, title = r.title, siteUrl = r.link ?: existing.siteUrl, folderId = folderId, remoteId = r.id))
                existing.id
            }
        }
        val gone = local.values.filter { it.remoteId !in ids }
        gone.forEach { dao.delete(it.id) }
        return FeedsReconciled(ids, received, gone.size)
    }

    private suspend fun upsertItems(items: List<ItemDto>, feedIds: Map<Long, Long>): ItemsUpserted {
        val dao = db.articleDao()
        val pending = db.pendingChangeDao().getAll().groupBy({ it.articleId }, { it.field })
        val newlySaved = mutableListOf<Long>()
        var newUnread = 0
        var markedRead = 0
        for (item in items) {
            val feedId = feedIds[item.feedId] ?: continue
            val existing = dao.getByRemoteId(item.id) ?: dao.getByFeedAndGuid(feedId, item.guid)
            val held = pending[existing?.id].orEmpty().toMutableSet()
            if (existing != null && existing.remoteId == null) {
                // Never synced (read or saved before connecting): our state is the intent, so queue it to be pushed.
                val now = System.currentTimeMillis()
                if (existing.isRead != !item.unread) {
                    db.pendingChangeDao().enqueue(PendingChangeEntity(existing.id, SyncField.READ, existing.isRead, now)); held += SyncField.READ
                }
                if (existing.isSaved != item.starred) {
                    db.pendingChangeDao().enqueue(PendingChangeEntity(existing.id, SyncField.STARRED, existing.isSaved, now)); held += SyncField.STARRED
                }
            }
            val isRead = if (existing != null && SyncField.READ in held) existing.isRead else !item.unread
            val isSaved = if (existing != null && SyncField.STARRED in held) existing.isSaved else item.starred

            val id = if (existing == null) {
                dao.insertIgnore(listOf(item.toEntity(feedId, isRead, isSaved))).single().takeIf { it != -1L } ?: continue
            } else {
                dao.updateRemoteState(existing.id, item.id, isRead, isSaved)
                item.toEntity(feedId, isRead, isSaved).imageUrl?.let { dao.fillImageUrl(feedId, existing.guid, it) }
                existing.id
            }
            if (existing == null && !isRead) newUnread++
            if (existing != null && !existing.isRead && isRead) markedRead++
            if (isSaved && existing?.isSaved != true) newlySaved += id
        }
        return ItemsUpserted(newlySaved, newUnread, markedRead)
    }

    private fun ItemDto.toEntity(feedId: Long, isRead: Boolean, isSaved: Boolean) = ArticleEntity(
        feedId = feedId, guid = guid, title = title.orEmpty(), url = url.orEmpty(), author = author,
        contentHtml = body, publishedAt = (pubDate ?: 0) * 1000, isRead = isRead, isSaved = isSaved, remoteId = id,
        imageUrl = ArticleImages.httpUrl(mediaThumbnail, url) ?: ArticleImages.firstInHtml(body, url),
    )
}
