package me.dougals.forage.domain.repository

import kotlinx.coroutines.flow.Flow
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder

/**
 * Single seam for both operating modes: Local (direct RSS fetching) and Nextcloud Sync.
 * Reads always come from Room; writes update Room first and are queued for remote flush when needed.
 */
interface FeedRepository {
    fun observeUnread(): Flow<List<Article>>
    fun observeSaved(): Flow<List<Article>>
    fun observeFolders(): Flow<List<Folder>>
    fun observeFeeds(): Flow<List<Feed>>

    suspend fun addFeed(url: String, folderId: Long? = null): Result<Feed>
    suspend fun removeFeed(feedId: Long): Result<Unit>
    suspend fun createFolder(name: String): Result<Folder>
    suspend fun deleteFolder(folderId: Long): Result<Unit>

    suspend fun setRead(articleId: Long, read: Boolean)
    suspend fun setSaved(articleId: Long, saved: Boolean)
    suspend fun markFeedRead(feedId: Long)

    /** Pull new items (and, in Nextcloud mode, push queued changes first). */
    suspend fun refresh(): Result<Unit>
}
