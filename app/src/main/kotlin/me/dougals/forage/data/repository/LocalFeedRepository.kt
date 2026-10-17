package me.dougals.forage.data.repository

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.remote.FeedSource
import me.dougals.forage.data.remote.ParsedItem
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder

/** Local Mode: feeds are fetched straight from their publishers; nothing is synced anywhere. */
class LocalFeedRepository(
    db: ForageDatabase,
    private val source: FeedSource,
    archive: ArchiveScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : RoomFeedRepository(db, archive) {

    override suspend fun addFeed(url: String, folderId: Long?): Result<Feed> {
        val trimmed = url.trim()
        if (db.feedDao().getByUrl(trimmed) != null) return Result.failure(IllegalStateException("Already subscribed"))
        return try {
            val parsed = source.fetch(trimmed)
            val id = db.feedDao().insert(
                FeedEntity(url = trimmed, title = parsed.title.ifBlank { trimmed }, siteUrl = parsed.siteUrl, folderId = folderId),
            )
            store(id, parsed.items)
            Result.success(feedWithCount(id)!!)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refresh(): Result<Unit> {
        var failure: Throwable? = null
        for (feed in db.feedDao().getAll()) {
            try {
                store(feed.id, source.fetch(feed.url).items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        }
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun removeFeed(feedId: Long): Result<Unit> {
        db.feedDao().delete(feedId)
        return Result.success(Unit)
    }

    override suspend fun createFolder(name: String): Result<Folder> =
        Result.success(FolderEntity(db.folderDao().insert(FolderEntity(name = name)), name).toDomain())

    override suspend fun deleteFolder(folderId: Long): Result<Unit> {
        db.folderDao().delete(folderId)
        return Result.success(Unit)
    }

    private suspend fun store(feedId: Long, items: List<ParsedItem>) {
        db.articleDao().insertIgnore(
            items.map {
                ArticleEntity(
                    feedId = feedId, guid = it.guid, title = it.title, url = it.url, author = it.author,
                    summary = it.summary, contentHtml = it.contentHtml,
                    publishedAt = if (it.publishedAt == 0L) clock() else it.publishedAt,
                )
            },
        )
    }
}
