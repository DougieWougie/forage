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
package me.dougals.forage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, FeedEntity::class, ArticleEntity::class, PendingChangeEntity::class],
    version = 3,
)
abstract class ForageDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun pendingChangeDao(): PendingChangeDao

    companion object {
        /** Adds the list thumbnail; articles stored before this have none until they are fetched again. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN imageUrl TEXT")
            }
        }

        /**
         * Indices for the Nextcloud pull (`remoteId` lookups) and the article lists (filter plus newest-first sort).
         * The single-column `isRead` index is dropped: `(isRead, publishedAt)` serves every query it did.
         * Also adds `snippet`, the list-card preview. Existing rows start with NULL and are filled in the background by
         * [me.dougals.forage.data.content.SnippetBackfill]: parsing every stored body here would hold up opening the database.
         * Also adds `feeds.etag` and `feeds.lastModified`, the validators for conditional feed requests (NULL means send none).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN snippet TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastModified TEXT")
                db.execSQL("DROP INDEX IF EXISTS `index_articles_isRead`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_remoteId` ON `articles` (`remoteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_isRead_publishedAt` ON `articles` (`isRead`, `publishedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId_publishedAt` ON `articles` (`feedId`, `publishedAt`)")
            }
        }
    }
}
