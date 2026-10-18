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
            articleEntity(feed, "old", publishedAt = 1),
            articleEntity(feed, "new", publishedAt = 3),
            articleEntity(feed, "read", publishedAt = 2, isRead = true),
        ))
        assertEquals(listOf("new", "old"), dao.observeUnread().first().map { it.guid })
    }

    @Test fun searchMatchesTitleAuthorSummaryAndTextIgnoringCase() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(
            articleEntity(feed, "title", publishedAt = 1).copy(title = "Rust in the kernel"),
            articleEntity(feed, "author", publishedAt = 2).copy(author = "Ada RUSTAM"),
            articleEntity(feed, "summary", publishedAt = 3).copy(summary = "all about rusty nails"),
            articleEntity(feed, "text", publishedAt = 4).copy(contentHtml = "<p>trust the process</p>"),
            articleEntity(feed, "other", publishedAt = 5).copy(title = "Gardening"),
        ))
        assertEquals(listOf("text", "summary", "author", "title"), dao.search("%rust%").first().map { it.guid })
    }

    @Test fun searchFindsTextBeyondTheTruncatedListColumns() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(articleEntity(feed, "long").copy(contentHtml = "x".repeat(5_000) + " needle")))
        val hit = dao.search("%needle%").first().single()
        assertEquals(4_000, hit.contentHtml!!.length)
    }

    @Test fun searchTreatsBackslashAsTheEscapeCharacter() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(articleEntity(feed, "pct").copy(title = "100% sure"), articleEntity(feed, "plain").copy(title = "1000 sure")))
        assertEquals(listOf("pct"), dao.search("%100\\% s%").first().map { it.guid })
    }

    @Test fun searchIsCapped() = runTest {
        val feed = newFeed()
        dao.insertIgnore((1..SEARCH_LIMIT + 5).map { articleEntity(feed, "g$it", publishedAt = it.toLong()) })
        assertEquals(SEARCH_LIMIT, dao.search("%T-g%").first().size)
    }

    @Test fun savedReturnsOnlySaved() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(articleEntity(feed, "a", isSaved = true), articleEntity(feed, "b")))
        assertEquals(listOf("a"), dao.observeSaved().first().map { it.guid })
    }

    @Test fun reinsertingSameGuidKeepsUserState() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(articleEntity(feed, "a")))
        val id = dao.observeUnread().first().single().id
        dao.setRead(id, true)
        dao.setSaved(id, true)

        val inserted = dao.insertIgnore(listOf(articleEntity(feed, "a")))

        assertEquals(listOf(-1L), inserted)
        val row = dao.getById(id)!!
        assertTrue(row.isRead)
        assertTrue(row.isSaved)
    }

    @Test fun sameGuidInDifferentFeedsIsAllowed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        val ids = dao.insertIgnore(listOf(articleEntity(f1, "x"), articleEntity(f2, "x")))
        assertTrue(ids.none { it == -1L })
    }

    @Test fun saveOfflineHtmlStoresCleanHtml() = runTest {
        val feed = newFeed()
        dao.insertIgnore(listOf(articleEntity(feed, "a")))
        val id = dao.observeUnread().first().single().id
        assertNull(dao.getById(id)!!.offlineHtml)
        dao.saveOfflineHtml(id, "<p>clean</p>")
        assertEquals("<p>clean</p>", dao.getById(id)!!.offlineHtml)
    }

    @Test fun unreadCountsPerFeed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(
            articleEntity(f1, "1"), articleEntity(f1, "2"), articleEntity(f1, "3", isRead = true), articleEntity(f2, "4"),
        ))
        val counts = dao.observeUnreadCounts().first().associate { it.feedId to it.count }
        assertEquals(mapOf(f1 to 2, f2 to 1), counts)
    }

    @Test fun markFeedReadOnlyAffectsThatFeed() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(articleEntity(f1, "1"), articleEntity(f2, "2")))
        dao.markFeedRead(f1)
        assertEquals(listOf("2"), dao.observeUnread().first().map { it.guid })
    }

    @Test fun unreadIdsOlderThanStaysInTheFeedAndSkipsReadAndNewer() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(
            articleEntity(f1, "oldest", publishedAt = 1), articleEntity(f1, "oldRead", publishedAt = 2, isRead = true),
            articleEntity(f1, "tied", publishedAt = 5), articleEntity(f1, "selected", publishedAt = 5), articleEntity(f1, "newer", publishedAt = 9),
            articleEntity(f2, "otherFeed", publishedAt = 1),
        ))
        val selected = dao.getByFeedAndGuid(f1, "selected")!!.id
        val ids = dao.unreadIdsOlderThan(selected)
        assertEquals(listOf("oldest"), ids.map { dao.getById(it)!!.guid })
    }

    @Test fun setReadInUpdatesOnlyTheGivenIds() = runTest {
        val f1 = newFeed("https://a/feed")
        dao.insertIgnore(listOf(articleEntity(f1, "1"), articleEntity(f1, "2")))
        val ids = dao.getAll().map { it.id }
        dao.setReadIn(listOf(ids[0]), true)
        assertEquals(listOf("2"), dao.observeUnread().first().map { it.guid })
        dao.setReadIn(listOf(ids[0]), false)
        assertEquals(2, dao.observeUnread().first().size)
    }

    @Test fun feedArticlesIncludeReadOnesNewestFirst() = runTest {
        val f1 = newFeed("https://a/feed")
        val f2 = newFeed("https://b/feed")
        dao.insertIgnore(listOf(
            articleEntity(f1, "old", publishedAt = 1), articleEntity(f1, "new", publishedAt = 5, isRead = true), articleEntity(f2, "other", publishedAt = 9),
        ))
        assertEquals(listOf("new", "old"), dao.observeByFeed(f1).first().map { it.guid })
    }

    @Test fun observeByIdEmitsUpdatesAndNullWhenMissing() = runTest {
        val feed = newFeed()
        val id = dao.insertIgnore(listOf(articleEntity(feed, "a"))).single()
        assertEquals(false, dao.observeById(id).first()!!.isRead)
        dao.setRead(id, true)
        assertEquals(true, dao.observeById(id).first()!!.isRead)
        assertNull(dao.observeById(999).first())
    }

    @Test fun listQueriesTruncateBodiesAndKeepOfflinePresence() = runTest {
        val feed = newFeed()
        val id = dao.insertIgnore(listOf(articleEntity(feed, "big").copy(summary = "s".repeat(10_000), contentHtml = "c".repeat(10_000)))).single()
        dao.saveOfflineHtml(id, "o".repeat(10_000))

        val row = dao.observeUnread().first().single()

        assertEquals(4000, row.summary!!.length)
        assertEquals(4000, row.contentHtml!!.length)
        assertEquals("", row.offlineHtml)
        assertEquals(10_000, dao.getById(id)!!.offlineHtml!!.length)
    }
}
