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
package me.dougals.forage.data.content

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnippetBackfillTest : DbTestBase() {
    @Test fun fillsEveryRowThatHasNoSnippetAndLeavesTheRestAlone() = runTest {
        val feed = newFeed()
        db.articleDao().insertIgnore(
            listOf(
                articleEntity(feed, "a").copy(summary = "<p>Alpha <b>text</b></p>"),
                articleEntity(feed, "b").copy(contentHtml = "<div>Beta</div>"),
                articleEntity(feed, "empty"),
                articleEntity(feed, "done").copy(summary = "<p>ignored</p>", snippet = "kept"),
            ),
        )
        val filled = SnippetBackfill(db, batchSize = 2).run()
        assertEquals(3, filled)
        val bySuffix = db.articleDao().getAll().associate { it.guid to it.snippet }
        assertEquals("Alpha text", bySuffix["a"])
        assertEquals("Beta", bySuffix["b"])
        assertEquals("", bySuffix["empty"])
        assertEquals("kept", bySuffix["done"])
    }

    @Test fun runningAgainDoesNothing() = runTest {
        val feed = newFeed()
        db.articleDao().insertIgnore(listOf(articleEntity(feed, "a").copy(summary = "x")))
        SnippetBackfill(db).run()
        assertEquals(0, SnippetBackfill(db).run())
        assertTrue(db.articleDao().withoutSnippet(10).isEmpty())
    }
}
