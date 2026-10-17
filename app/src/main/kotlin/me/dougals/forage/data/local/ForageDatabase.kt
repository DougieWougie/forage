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
