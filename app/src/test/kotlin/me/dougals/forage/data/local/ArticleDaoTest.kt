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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
class ArticleDaoTest : DbTestBase() {
    private val dao get() = db.articleDao()

    @Test fun unreadIsNewestFirstAndExcludesRead() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(
            article(feed, "old", publishedAt = 1),
            article(feed, "new", publishedAt = 3),
            article(feed, "read", publishedAt = 2, isRead = true),
        ))
        assertEquals(listOf("new", "old"), dao.observeUnread().first().map { it.guid })
    }

    @Test fun savedReturnsOnlySaved() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(article(feed, "a", isSaved = true), article(feed, "b")))
        assertEquals(listOf("a"), dao.observeSaved().first().map { it.guid })
    }

    @Test fun reinsertingSameGuidKeepsUserState() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(article(feed, "a")))
        val id = dao.observeUnread().first().single().id
        dao.setRead(id, true)
        dao.setSaved(id, true)

        val inserted = dao.insertIgnore(listOf(article(feed, "a")))

        assertEquals(listOf(-1L), inserted)
        val row = dao.getById(id)!!
        assertTrue(row.isRead)
        assertTrue(row.isSaved)
    }

    @Test fun sameGuidInDifferentFeedsIsAllowed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        val ids = dao.insertIgnore(listOf(article(f1, "x"), article(f2, "x")))
        assertTrue(ids.none { it == -1L })
    }

    @Test fun saveOfflineHtmlStoresCleanHtml() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(article(feed, "a")))
        val id = dao.observeUnread().first().single().id
        assertNull(dao.getById(id)!!.offlineHtml)
        dao.saveOfflineHtml(id, "<p>clean</p>")
        assertEquals("<p>clean</p>", dao.getById(id)!!.offlineHtml)
    }

    @Test fun unreadCountsPerFeed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(
            article(f1, "1"), article(f1, "2"), article(f1, "3", isRead = true), article(f2, "4"),
        ))
        val counts = dao.observeUnreadCounts().first().associate { it.feedId to it.count }
        assertEquals(mapOf(f1 to 2, f2 to 1), counts)
    }

    @Test fun markFeedReadOnlyAffectsThatFeed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(article(f1, "1"), article(f2, "2")))
        dao.markFeedRead(f1)
        assertEquals(listOf("2"), dao.observeUnread().first().map { it.guid })
    }

    @Test fun feedArticlesIncludeReadOnesNewestFirst() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(
            article(f1, "old", publishedAt = 1), article(f1, "new", publishedAt = 5, isRead = true), article(f2, "other", publishedAt = 9),
        ))
        assertEquals(listOf("new", "old"), dao.observeByFeed(f1).first().map { it.guid })
    }

    @Test fun observeByIdEmitsUpdatesAndNullWhenMissing() = runTest {
        val feed = newFeed()
        val id = dao.insertIgnore(listOf(article(feed, "a"))).single()
        assertEquals(false, dao.observeById(id).first()!!.isRead)
        dao.setRead(id, true)
        assertEquals(true, dao.observeById(id).first()!!.isRead)
        assertNull(dao.observeById(999).first())
    }
}
