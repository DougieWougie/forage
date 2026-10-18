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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingChangeDaoTest : DbTestBase() {
    private val dao get() = db.pendingChangeDao()

    private suspend fun articleId(): Long {
        val feed = newFeed()
        return db.articleDao().insertIgnore(listOf(articleEntity(feed, "a"))).single()
    }

    @Test fun laterToggleOfSameFieldReplacesEarlier() = runTest {
        val id = articleId()
        dao.enqueue(PendingChangeEntity(id, SyncField.READ, true, createdAt = 1))
        dao.enqueue(PendingChangeEntity(id, SyncField.READ, false, createdAt = 2))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(false, all.single().value)
    }

    @Test fun differentFieldsAreKeptSeparately() = runTest {
        val id = articleId()
        dao.enqueue(PendingChangeEntity(id, SyncField.READ, true, createdAt = 1))
        dao.enqueue(PendingChangeEntity(id, SyncField.STARRED, true, createdAt = 2))
        assertEquals(2, dao.getAll().size)
    }

    @Test fun getAllIsOldestFirst() = runTest {
        val feed = newFeed()
        val ids = db.articleDao().insertIgnore(listOf(articleEntity(feed, "a"), articleEntity(feed, "b")))
        dao.enqueue(PendingChangeEntity(ids[1], SyncField.READ, true, createdAt = 5))
        dao.enqueue(PendingChangeEntity(ids[0], SyncField.READ, true, createdAt = 2))
        assertEquals(listOf(ids[0], ids[1]), dao.getAll().map { it.articleId })
    }

    @Test fun deleteIfUnchangedRemovesFlushedChange() = runTest {
        val id = articleId()
        val change = PendingChangeEntity(id, SyncField.READ, true, createdAt = 1)
        dao.enqueue(change)
        dao.deleteIfUnchanged(change.articleId, change.field, change.createdAt, change.value)
        assertEquals(0, dao.getAll().size)
    }

    @Test fun deleteIfUnchangedKeepsAChangeThatWasReplacedMeanwhile() = runTest {
        val id = articleId()
        val flushed = PendingChangeEntity(id, SyncField.READ, true, createdAt = 1)
        dao.enqueue(flushed)
        dao.enqueue(PendingChangeEntity(id, SyncField.READ, false, createdAt = 2))
        dao.deleteIfUnchanged(flushed.articleId, flushed.field, flushed.createdAt, flushed.value)
        assertEquals(false, dao.getAll().single().value)
    }
}
