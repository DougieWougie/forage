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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Builds an old database from its exported schema, then lets Room open it with the migration and validate. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion(version: Int, name: String) {
        val schema = JSONObject(File("schemas/${ForageDatabase::class.java.name}/$version.json").readText()).getJSONObject("database")
        val sql = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            val indices = e.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                sql.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        sql.execSQL("INSERT INTO feeds (id, url, title) VALUES (1, 'https://e/f', 'F')")
        sql.execSQL(
            "INSERT INTO articles (feedId, guid, title, url, publishedAt, isRead, isSaved) VALUES (1, 'g', 'T', 'https://e/a', 5, 0, 1)",
        )
        sql.version = version
        sql.close()
    }

    @Test fun addingTheThumbnailKeepsExistingArticles() = runBlocking {
        createVersion(1, "migrating.db")
        val db = Room.databaseBuilder(context, ForageDatabase::class.java, "migrating.db")
            .addMigrations(ForageDatabase.MIGRATION_1_2, ForageDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val article = db.articleDao().getAll().single()
            assertEquals("T", article.title)
            assertEquals(true, article.isSaved)
            assertNull(article.imageUrl)
        } finally {
            db.close()
        }
    }

    private fun indexNames(db: ForageDatabase): Set<String> =
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'articles'").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
        }

    @Test fun threeAddsTheArticleIndicesAndDropsTheRedundantOne() = runBlocking {
        createVersion(2, "indexing.db")
        val db = Room.databaseBuilder(context, ForageDatabase::class.java, "indexing.db")
            .addMigrations(ForageDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertEquals("T", db.articleDao().getAll().single().title)
            val names = indexNames(db)
            assertEquals(true, "index_articles_remoteId" in names)
            assertEquals(true, "index_articles_isRead_publishedAt" in names)
            assertEquals(true, "index_articles_feedId_publishedAt" in names)
            assertEquals(false, "index_articles_isRead" in names)
            // Existing rows have no snippet until the background fill reaches them.
            assertNull(db.articleDao().getAll().single().snippet)
            assertEquals(1, db.articleDao().withoutSnippet(10).size)
        } finally {
            db.close()
        }
    }

    @Test fun aVersionOneDatabaseReachesVersionThreeThroughBothMigrations() = runBlocking {
        createVersion(1, "chain.db")
        val db = Room.databaseBuilder(context, ForageDatabase::class.java, "chain.db")
            .addMigrations(ForageDatabase.MIGRATION_1_2, ForageDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertEquals(1, db.articleDao().getAll().size)
            assertEquals(true, "index_articles_remoteId" in indexNames(db))
        } finally {
            db.close()
        }
    }
}
