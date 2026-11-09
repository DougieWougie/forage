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

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dougals.forage.data.local.ForageDatabase

/**
 * Fills in [me.dougals.forage.data.local.ArticleEntity.snippet] for rows stored before it existed. Works in small
 * transactions so the article lists, which re-query on every write, are not rebuilt once per row.
 */
class SnippetBackfill(private val db: ForageDatabase, private val batchSize: Int = 100) {
    /** @return how many articles were filled in */
    suspend fun run(): Int {
        var done = 0
        while (true) {
            val batch = db.articleDao().withoutSnippet(batchSize)
            if (batch.isEmpty()) return done
            val snippets = withContext(Dispatchers.Default) { batch.map { it.id to ArticleSnippets.of(it.summary, it.contentHtml) } }
            // An empty snippet is still a snippet: it is what keeps a text-free article from being picked again.
            db.withTransaction { snippets.forEach { (id, snippet) -> db.articleDao().setSnippet(id, snippet) } }
            done += batch.size
        }
    }
}
