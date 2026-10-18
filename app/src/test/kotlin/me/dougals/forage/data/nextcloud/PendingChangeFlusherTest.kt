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
package me.dougals.forage.data.nextcloud

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.local.DbTestBase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.testutil.FakeNextcloudApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingChangeFlusherTest : DbTestBase() {
    private val api = FakeNextcloudApi()
    private val flusher get() = PendingChangeFlusher(db, NextcloudApiProvider { api })

    private suspend fun articles(vararg remoteIds: Long?): List<Long> {
        val feed = newFeed()
        return db.articleDao().insertIgnore(
            remoteIds.mapIndexed { i, r -> articleEntity(feed, "g$i").copy(remoteId = r) },
        )
    }

    private suspend fun enqueue(id: Long, field: SyncField, value: Boolean, at: Long = 1) =
        db.pendingChangeDao().enqueue(PendingChangeEntity(id, field, value, at))

    @Test fun groupsChangesIntoBatchesAndClearsQueue() = runTest {
        val ids = articles(11, 12, 13, 14)
        enqueue(ids[0], SyncField.READ, true)
        enqueue(ids[1], SyncField.READ, true)
        enqueue(ids[2], SyncField.READ, false)
        enqueue(ids[3], SyncField.STARRED, true)
        assertTrue(flusher.flush().isSuccess)
        assertEquals(listOf(11L, 12L), api.batches["markRead"])
        assertEquals(listOf(13L), api.batches["markUnread"])
        assertEquals(listOf(14L), api.batches["star"])
        assertEquals(0, db.pendingChangeDao().getAll().size)
    }

    @Test fun emptyQueueMakesNoApiCallsAndNeverTouchesProvider() = runTest {
        val result = PendingChangeFlusher(db, NextcloudApiProvider { error("must not be called") }).flush()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun failedBatchStaysQueuedWhileSuccessfulOnesAreCleared() = runTest {
        val ids = articles(11, 12)
        enqueue(ids[0], SyncField.READ, true)
        enqueue(ids[1], SyncField.STARRED, true)
        api.failOn = setOf("star")
        assertTrue(flusher.flush().isFailure)
        val left = db.pendingChangeDao().getAll()
        assertEquals(listOf(SyncField.STARRED), left.map { it.field })
    }

    @Test fun changesForArticlesNeverSyncedAreDropped() = runTest {
        val ids = articles(null)
        enqueue(ids[0], SyncField.READ, true)
        assertTrue(flusher.flush().isSuccess)
        assertEquals(emptyList<String>(), api.calls)
        assertEquals(0, db.pendingChangeDao().getAll().size)
    }

    @Test fun changeRequeuedDuringFlushSurvives() = runTest {
        val ids = articles(11)
        enqueue(ids[0], SyncField.READ, true, at = 1)
        val racing = object : FakeNextcloudApiDelegate(api) {
            override suspend fun markRead(body: ItemIdsRequest) {
                super.markRead(body)
                enqueue(ids[0], SyncField.READ, false, at = 2) // user flipped it back mid-flush
            }
        }
        PendingChangeFlusher(db, NextcloudApiProvider { racing }).flush()
        val left = db.pendingChangeDao().getAll().single()
        assertEquals(false, left.value)
    }
}

open class FakeNextcloudApiDelegate(private val d: FakeNextcloudApi) : NextcloudApi by d
