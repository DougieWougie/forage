package me.dougals.forage.data.nextcloud

import androidx.room.withTransaction
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.work.ArchiveScheduler

/** Pulls folders, feeds and items from Nextcloud into Room. Local unsent changes always win. */
class NextcloudSyncer(
    private val db: ForageDatabase,
    private val apiProvider: NextcloudApiProvider,
    private val state: SyncStateStore,
    private val archive: ArchiveScheduler,
) {
    /** @return how many unread articles were new to this device */
    suspend fun pull(): Int {
        val api = apiProvider.api()
        val remoteFolders = api.folders().folders
        val remoteFeeds = api.feeds().feeds
        val last = state.lastModified
        val items = if (last == 0L) {
            (api.items(type = 3, getRead = false).items + api.items(type = 2, getRead = true).items).distinctBy { it.id }
        } else {
            api.updatedItems(last).items
        }

        val (newlySaved, newCount) = db.withTransaction {
            val folderIds = reconcileFolders(remoteFolders)
            val feedIds = reconcileFeeds(remoteFeeds, folderIds)
            upsertItems(items, feedIds)
        }

        state.lastModified = maxOf(last, items.maxOfOrNull { it.lastModified } ?: last)
        newlySaved.forEach(archive::schedule)
        state.lastSyncedAt = System.currentTimeMillis()
        return newCount
    }

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

    /** @return remote feed id -> local id */
    private suspend fun reconcileFeeds(remote: List<FeedDto>, folderIds: Map<Long, Long>): Map<Long, Long> {
        val dao = db.feedDao()
        val local = dao.getAll().filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val ids = mutableMapOf<Long, Long>()
        for (r in remote) {
            val folderId = r.folderId?.let { folderIds[it] }
            val existing = local[r.id] ?: dao.getByUrl(r.url)
            ids[r.id] = if (existing == null) {
                dao.insert(FeedEntity(url = r.url, title = r.title, siteUrl = r.link, folderId = folderId, remoteId = r.id))
            } else {
                dao.update(existing.copy(url = r.url, title = r.title, siteUrl = r.link ?: existing.siteUrl, folderId = folderId, remoteId = r.id))
                existing.id
            }
        }
        local.values.filter { it.remoteId !in ids }.forEach { dao.delete(it.id) }
        return ids
    }

    /** @return local ids of articles that became saved and still need an offline copy, and the count of new unread ones */
    private suspend fun upsertItems(items: List<ItemDto>, feedIds: Map<Long, Long>): Pair<List<Long>, Int> {
        val dao = db.articleDao()
        val pending = db.pendingChangeDao().getAll().groupBy({ it.articleId }, { it.field })
        val newlySaved = mutableListOf<Long>()
        var newUnread = 0
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
                existing.id
            }
            if (existing == null && !isRead) newUnread++
            if (isSaved && existing?.isSaved != true) newlySaved += id
        }
        return newlySaved to newUnread
    }

    private fun ItemDto.toEntity(feedId: Long, isRead: Boolean, isSaved: Boolean) = ArticleEntity(
        feedId = feedId, guid = guid, title = title.orEmpty(), url = url.orEmpty(), author = author,
        contentHtml = body, publishedAt = (pubDate ?: 0) * 1000, isRead = isRead, isSaved = isSaved, remoteId = id,
    )
}
