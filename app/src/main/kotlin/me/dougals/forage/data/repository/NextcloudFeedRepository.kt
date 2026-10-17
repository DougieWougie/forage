package me.dougals.forage.data.repository

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.nextcloud.CreateFeedRequest
import me.dougals.forage.data.nextcloud.CreateFolderRequest
import me.dougals.forage.data.nextcloud.NextcloudApiProvider
import me.dougals.forage.data.nextcloud.NextcloudSyncer
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.data.work.SyncScheduler
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder

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

    override suspend fun refresh(): Result<Unit> {
        flusher.flush().onFailure { return Result.failure(it) }
        return attempt { syncer.pull() }
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
