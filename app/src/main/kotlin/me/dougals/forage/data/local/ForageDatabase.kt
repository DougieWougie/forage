package me.dougals.forage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, FeedEntity::class, ArticleEntity::class, PendingChangeEntity::class],
    version = 1,
)
abstract class ForageDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun pendingChangeDao(): PendingChangeDao
}
