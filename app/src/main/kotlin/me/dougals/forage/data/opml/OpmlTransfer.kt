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
package me.dougals.forage.data.opml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.dougals.forage.data.xml.XmlParseException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import me.dougals.forage.domain.repository.FeedRepository
import java.io.InputStream

data class ImportResult(val added: Int, val skipped: Int, val failed: Int)

/** OPML import/export on top of [FeedRepository], so it works in both sync modes. */
class OpmlTransfer(private val repo: FeedRepository) {
    suspend fun import(input: InputStream): Result<ImportResult> {
        val feeds = try {
            withContext(Dispatchers.IO) { Opml.parse(input) }
        } catch (e: XmlParseException) {
            return Result.failure(IllegalArgumentException("Not a valid OPML file", e))
        }

        val folderIds = repo.observeFolders().first().associate { it.name.lowercase() to it.id }.toMutableMap()
        val unusableFolders = mutableSetOf<String>()
        val known = repo.observeFeeds().first().map { it.url }.toMutableSet()
        var added = 0; var skipped = 0; var failed = 0

        for (f in feeds) {
            // Feed files are untrusted: only real http(s) URLs ever reach the repository.
            if (f.xmlUrl.toHttpUrlOrNull() == null) { failed++; continue }
            if (!known.add(f.xmlUrl)) { skipped++; continue }
            val key = f.folder?.lowercase()
            val folderId = when {
                key == null || key in unusableFolders -> null
                else -> folderIds[key] ?: repo.createFolder(f.folder!!).getOrNull()?.id?.also { folderIds[key] = it }
                    ?: run { unusableFolders += key; null }
            }
            if (repo.addFeed(f.xmlUrl, folderId).isSuccess) added++ else failed++
        }
        return Result.success(ImportResult(added, skipped, failed))
    }

    suspend fun export(): String {
        val folders = repo.observeFolders().first().associate { it.id to it.name }
        return Opml.export(repo.observeFeeds().first().map { OpmlFeed(it.title, it.url, it.folderId?.let(folders::get)) })
    }
}
