package me.dougals.forage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class UnreadCount(val feedId: Long, val count: Int)

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long
    @Query("DELETE FROM folders WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM folders ORDER BY name") fun observeAll(): Flow<List<FolderEntity>>
}

@Dao
interface FeedDao {
    @Insert suspend fun insert(feed: FeedEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(feed: FeedEntity): Long
    @Query("DELETE FROM feeds WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM feeds WHERE id = :id") suspend fun getById(id: Long): FeedEntity?
    @Query("SELECT * FROM feeds ORDER BY title") fun observeAll(): Flow<List<FeedEntity>>
    @Query("SELECT * FROM feeds WHERE folderId = :folderId ORDER BY title")
    fun observeByFolder(folderId: Long): Flow<List<FeedEntity>>
}

@Dao
interface ArticleDao {
    /** Returns -1 for rows skipped because (feedId, guid) already exists, preserving user state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(articles: List<ArticleEntity>): List<Long>
    @Query("SELECT * FROM articles WHERE id = :id") suspend fun getById(id: Long): ArticleEntity?
    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC")
    fun observeUnread(): Flow<List<ArticleEntity>>
    @Query("SELECT * FROM articles WHERE isSaved = 1 ORDER BY publishedAt DESC")
    fun observeSaved(): Flow<List<ArticleEntity>>
    @Query("SELECT feedId, COUNT(*) AS count FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnreadCounts(): Flow<List<UnreadCount>>
    @Query("UPDATE articles SET isRead = :read WHERE id = :id") suspend fun setRead(id: Long, read: Boolean)
    @Query("UPDATE articles SET isSaved = :saved WHERE id = :id") suspend fun setSaved(id: Long, saved: Boolean)
    @Query("UPDATE articles SET offlineHtml = :html WHERE id = :id") suspend fun saveOfflineHtml(id: Long, html: String)
    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId") suspend fun markFeedRead(feedId: Long)
}

@Dao
interface PendingChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(change: PendingChangeEntity)
    @Query("SELECT * FROM pending_changes ORDER BY createdAt") suspend fun getAll(): List<PendingChangeEntity>
    @Delete suspend fun delete(changes: List<PendingChangeEntity>)
}
