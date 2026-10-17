package me.dougals.forage.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.mapper.toDomain
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.repository.FeedRepository

/** Reads and local state changes shared by both modes; room is always the source of truth for the UI. */
abstract class RoomFeedRepository(
    protected val db: ForageDatabase,
    private val archive: ArchiveScheduler,
) : FeedRepository {
    override fun observeUnread(): Flow<List<Article>> = db.articleDao().observeUnread().map { it.map { a -> a.toDomain() } }
    override fun observeSaved(): Flow<List<Article>> = db.articleDao().observeSaved().map { it.map { a -> a.toDomain() } }
    override fun observeFeedArticles(feedId: Long): Flow<List<Article>> =
        db.articleDao().observeByFeed(feedId).map { it.map { a -> a.toDomain() } }
    override fun observeArticle(articleId: Long): Flow<Article?> = db.articleDao().observeById(articleId).map { it?.toDomain() }
    override fun observeFolders(): Flow<List<Folder>> = db.folderDao().observeAll().map { it.map { f -> f.toDomain() } }
    override fun observeFeeds(): Flow<List<Feed>> =
        combine(db.feedDao().observeAll(), db.articleDao().observeUnreadCounts()) { feeds, counts ->
            val byFeed = counts.associate { it.feedId to it.count }
            feeds.map { it.toDomain(byFeed[it.id] ?: 0) }
        }

    override suspend fun setRead(articleId: Long, read: Boolean) {
        db.articleDao().setRead(articleId, read)
        onStateChanged(articleId, SyncField.READ, read)
        onWritesDone()
    }

    override suspend fun setSaved(articleId: Long, saved: Boolean) {
        db.articleDao().setSaved(articleId, saved)
        if (saved) archive.schedule(articleId) else db.articleDao().clearOfflineHtml(articleId)
        onStateChanged(articleId, SyncField.STARRED, saved)
        onWritesDone()
    }

    override suspend fun markFeedRead(feedId: Long) {
        val ids = db.articleDao().unreadIds(feedId)
        db.articleDao().markFeedRead(feedId)
        ids.forEach { onStateChanged(it, SyncField.READ, true) }
        if (ids.isNotEmpty()) onWritesDone()
    }

    protected suspend fun feedWithCount(feedId: Long): Feed? =
        db.feedDao().getById(feedId)?.toDomain(db.articleDao().unreadCount(feedId))

    protected open suspend fun onStateChanged(articleId: Long, field: SyncField, value: Boolean) {}
    protected open fun onWritesDone() {}
}
