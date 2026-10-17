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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

abstract class DbTestBase {
    protected lateinit var db: ForageDatabase

    @Before fun openDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ForageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun closeDb() = db.close()

    protected suspend fun newFeed(url: String = "https://example.com/feed", folderId: Long? = null): Long =
        db.feedDao().insert(FeedEntity(url = url, title = "Feed", folderId = folderId))

    protected fun article(
        feedId: Long,
        guid: String,
        publishedAt: Long = 0,
        isRead: Boolean = false,
        isSaved: Boolean = false,
    ) = ArticleEntity(
        feedId = feedId, guid = guid, title = "T-$guid", url = "https://example.com/$guid",
        publishedAt = publishedAt, isRead = isRead, isSaved = isSaved,
    )
}
