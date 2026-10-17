package me.dougals.forage.testutil

import me.dougals.forage.data.nextcloud.CreateFeedRequest
import me.dougals.forage.data.nextcloud.CreateFolderRequest
import me.dougals.forage.data.nextcloud.FeedDto
import me.dougals.forage.data.nextcloud.FeedsResponse
import me.dougals.forage.data.nextcloud.FolderDto
import me.dougals.forage.data.nextcloud.FoldersResponse
import me.dougals.forage.data.nextcloud.ItemDto
import me.dougals.forage.data.nextcloud.ItemIdsRequest
import me.dougals.forage.data.nextcloud.ItemsResponse
import me.dougals.forage.data.nextcloud.NextcloudApi
import me.dougals.forage.data.nextcloud.SyncStateStore
import me.dougals.forage.data.remote.FeedSource
import me.dougals.forage.data.remote.ParsedFeed
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.data.work.SyncScheduler
import java.io.IOException

fun item(
    id: Long, feedId: Long, unread: Boolean = true, starred: Boolean = false,
    lastModified: Long = id * 10, guid: String = "guid-$id",
) = ItemDto(
    id = id, guid = guid, url = "https://example.com/$id", title = "Item $id", author = "A",
    pubDate = 1_700_000_000L + id, body = "<p>$id</p>", feedId = feedId,
    unread = unread, starred = starred, lastModified = lastModified,
)

/** Server state lives in the public lists; [calls] records every endpoint hit in order. */
class FakeNextcloudApi : NextcloudApi {
    val folderList = mutableListOf<FolderDto>()
    val feedList = mutableListOf<FeedDto>()
    val itemList = mutableListOf<ItemDto>()
    val calls = mutableListOf<String>()
    val batches = mutableMapOf<String, List<Long>>()
    var failOn: Set<String> = emptySet()
    var nextId = 100L

    private fun <T> rec(name: String, block: () -> T): T {
        calls += name
        if (name in failOn) throw IOException("boom: $name")
        return block()
    }

    override suspend fun folders() = rec("folders") { FoldersResponse(folderList.toList()) }
    override suspend fun createFolder(body: CreateFolderRequest) = rec("createFolder") {
        FolderDto(nextId++, body.name).also { folderList += it }.let { FoldersResponse(listOf(it)) }
    }
    override suspend fun deleteFolder(id: Long) = rec("deleteFolder:$id") { folderList.removeAll { it.id == id }; Unit }
    override suspend fun feeds() = rec("feeds") { FeedsResponse(feedList.toList()) }
    override suspend fun createFeed(body: CreateFeedRequest) = rec("createFeed:${body.url}:${body.folderId}") {
        FeedDto(nextId++, body.url, "Created", body.folderId).also { feedList += it }.let { FeedsResponse(listOf(it)) }
    }
    override suspend fun deleteFeed(id: Long) = rec("deleteFeed:$id") { feedList.removeAll { it.id == id }; Unit }
    override suspend fun items(type: Int, getRead: Boolean, batchSize: Int, offset: Long) = rec("items:$type") {
        ItemsResponse(
            when (type) {
                2 -> itemList.filter { it.starred }
                else -> itemList.filter { getRead || it.unread }
            },
        )
    }
    override suspend fun updatedItems(lastModified: Long, type: Int, id: Long) = rec("updated:$lastModified") {
        ItemsResponse(itemList.filter { it.lastModified > lastModified })
    }
    override suspend fun markRead(body: ItemIdsRequest) = rec("markRead") { batches["markRead"] = body.items }
    override suspend fun markUnread(body: ItemIdsRequest) = rec("markUnread") { batches["markUnread"] = body.items }
    override suspend fun star(body: ItemIdsRequest) = rec("star") { batches["star"] = body.items }
    override suspend fun unstar(body: ItemIdsRequest) = rec("unstar") { batches["unstar"] = body.items }
}

class MemSyncState(override var lastModified: Long = 0) : SyncStateStore

class RecordingSchedulers : SyncScheduler, ArchiveScheduler {
    var flushes = 0
    val archived = mutableListOf<Long>()
    override fun scheduleFlush() { flushes++ }
    override fun schedule(articleId: Long) { archived += articleId }
}

class FakeFeedSource : FeedSource {
    val feeds = mutableMapOf<String, ParsedFeed>()
    val failing = mutableSetOf<String>()
    override suspend fun fetch(url: String): ParsedFeed {
        if (url in failing) throw IOException("down: $url")
        return feeds[url] ?: throw IOException("unknown $url")
    }
}
