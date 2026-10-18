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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedDaoTest : DbTestBase() {
    @Test fun deletingFolderKeepsFeedsButUnfilesThem() = runTest {
        val folder = db.folderDao().insert(FolderEntity(name = "Tech"))
        val feed = newFeed(folderId = folder)
        db.folderDao().delete(folder)
        assertNull(db.feedDao().getById(feed)!!.folderId)
    }

    @Test fun deletingFeedCascadesToArticles() = runTest {
        val feed = newFeed()
        db.articleDao().insertIgnore(listOf(articleEntity(feed, "a")))
        db.feedDao().delete(feed)
        assertEquals(emptyList<ArticleEntity>(), db.articleDao().observeUnread().first())
    }

    @Test fun feedUrlIsUnique() = runTest {
        newFeed("https://a/feed")
        assertEquals(-1L, db.feedDao().insertIgnore(FeedEntity(url = "https://a/feed", title = "Dup")))
    }

    @Test fun observeFeedsInFolder() = runTest {
        val folder = db.folderDao().insert(FolderEntity(name = "Tech"))
        newFeed("https://a/feed", folderId = folder)
        newFeed("https://b/feed")
        assertEquals(listOf("https://a/feed"), db.feedDao().observeByFolder(folder).first().map { it.url })
    }
}
