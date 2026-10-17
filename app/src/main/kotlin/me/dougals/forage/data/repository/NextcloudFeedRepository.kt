package me.dougals.forage.data.repository

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.nextcloud.CreateFeedRequest
import me.dougals.forage.data.nextcloud.CreateFolderRequest
import me.dougals.forage.data.nextcloud.NextcloudApi
import me.dougals.forage.data.nextcloud.NextcloudApiProvider
import me.dougals.forage.data.nextcloud.NextcloudSyncer
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.data.work.SyncScheduler
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.model.SyncReport

/**
 * Nextcloud Sync Mode. Read/starred changes are local-first and queued for [SyncScheduler];
 * structural changes (add/remove feed or folder) go to the server first.
 */
class NextcloudFeedRepository(
    db: ForageDatabase,
    private val apiProvider: NextcloudApiProvider,
    private val flusher: PendingChangeFlusher,
    private val syncer: NextcloudSyncer,
    private val sync: SyncScheduler,
    archive: ArchiveScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : RoomFeedRepository(db, archive) {

    override suspend fun refresh(): Result<SyncReport> {
        val sentFirst = flusher.flushCounting().getOrElse { return Result.failure(it) }
        var added = 0
        var fresh = 0
        attempt { added = pushLocalOnly(); fresh = syncer.pull() }.onFailure { return Result.failure(it) }
        // The pull may have queued local state for articles adopted from before connecting.
        val sentAfter = flusher.flushCounting().getOrElse { return Result.failure(it) }
        return Result.success(SyncReport(newArticles = fresh, changesSent = sentFirst + sentAfter, feedsAdded = added))
    }

    /**
     * Feeds (and their folders) added while in local mode don't exist on the server yet; the pull adopts them by URL.
     * @return how many feeds were created on the server
     */
    private suspend fun pushLocalOnly(): Int {
        val unsynced = db.feedDao().getAll().filter { it.remoteId == null }
        if (unsynced.isEmpty()) return 0
        val api = apiProvider.api()
        val onServer = api.feeds().feeds.map { it.url }.toSet()
        var created = 0
        for (feed in unsynced) {
            if (feed.url in onServer) continue
            val folder = feed.folderId?.let { remoteFolderId(api, it) }
            api.createFeed(CreateFeedRequest(feed.url, folder))
            created++
        }
        return created
    }

    private suspend fun remoteFolderId(api: NextcloudApi, localId: Long): Long? {
        val folder = db.folderDao().getById(localId) ?: return null
        folder.remoteId?.let { return it }
        val remote = api.folders().folders.firstOrNull { it.name == folder.name }
            ?: api.createFolder(CreateFolderRequest(folder.name)).folders.first()
        db.folderDao().update(folder.copy(remoteId = remote.id))
        return remote.id
    }

    override suspend fun addFeed(url: String, folderId: Long?): Result<Feed> = attempt {
        val remoteFolder = folderId?.let { db.folderDao().getById(it)?.remoteId }
        val created = apiProvider.api().createFeed(CreateFeedRequest(url.trim(), remoteFolder)).feeds.first()
        refresh().getOrThrow()
        val local = db.feedDao().getAll().first { it.remoteId == created.id }
        feedWithCount(local.id)!!
    }

    override suspend fun removeFeed(feedId: Long): Result<Unit> = attempt {
        val remoteId = db.feedDao().getById(feedId)?.remoteId
        if (remoteId != null) apiProvider.api().deleteFeed(remoteId)
        db.feedDao().delete(feedId)
    }

    override suspend fun createFolder(name: String): Result<Folder> = attempt {
        val created = apiProvider.api().createFolder(CreateFolderRequest(name)).folders.first()
        val id = db.folderDao().insert(FolderEntity(name = created.name, remoteId = created.id))
        FolderEntity(id, created.name, created.id).toDomain()
    }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> = attempt {
        val remoteId = db.folderDao().getById(folderId)?.remoteId
        if (remoteId != null) apiProvider.api().deleteFolder(remoteId)
        db.folderDao().delete(folderId)
    }

    override suspend fun onStateChanged(articleId: Long, field: SyncField, value: Boolean) {
        db.pendingChangeDao().enqueue(PendingChangeEntity(articleId, field, value, clock()))
    }

    override fun onWritesDone() = sync.scheduleFlush()

    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
