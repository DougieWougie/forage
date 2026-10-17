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

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.local.PendingChangeEntity
import me.dougals.forage.data.local.SyncField
import me.dougals.forage.data.work.FlushAction

/** Pushes queued read/starred changes to Nextcloud in batches; stops at the first failing batch. */
class PendingChangeFlusher(
    private val db: ForageDatabase,
    private val apiProvider: NextcloudApiProvider,
) : FlushAction {
    private class Batch(
        val field: SyncField,
        val value: Boolean,
        val send: suspend (NextcloudApi, ItemIdsRequest) -> Unit,
    ) { val changes = mutableListOf<Pair<PendingChangeEntity, Long>>() }

    override suspend fun flush(): Result<Unit> = flushCounting().map { }

    /** @return how many changes were delivered to the server */
    suspend fun flushCounting(): Result<Int> {
        val dao = db.pendingChangeDao()
        val pending = dao.getAll()
        if (pending.isEmpty()) return Result.success(0)

        val batches = listOf(
            Batch(SyncField.READ, true) { api, b -> api.markRead(b) },
            Batch(SyncField.READ, false) { api, b -> api.markUnread(b) },
            Batch(SyncField.STARRED, true) { api, b -> api.star(b) },
            Batch(SyncField.STARRED, false) { api, b -> api.unstar(b) },
        )
        for (change in pending) {
            val remoteId = db.articleDao().getById(change.articleId)?.remoteId
            if (remoteId == null) {
                discard(change) // never reached the server, nothing to tell it
            } else {
                batches.first { it.field == change.field && it.value == change.value }.changes += change to remoteId
            }
        }

        val toSend = batches.filter { it.changes.isNotEmpty() }
        if (toSend.isEmpty()) return Result.success(0)
        return try {
            val api = apiProvider.api()
            for (batch in toSend) {
                batch.send(api, ItemIdsRequest(batch.changes.map { it.second }))
                batch.changes.forEach { discard(it.first) }
            }
            Result.success(toSend.sumOf { it.changes.size })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun discard(c: PendingChangeEntity) =
        db.pendingChangeDao().deleteIfUnchanged(c.articleId, c.field, c.createdAt, c.value)
}
