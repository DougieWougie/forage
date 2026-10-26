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

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress

fun interface PageFetcher { suspend fun fetchHtml(url: String): String }

/**
 * Fetches article pages through [GuardedHttp]; see there for the SSRF rules. Pass a client that carries only the
 * session-cookie interceptor, never Nextcloud credentials.
 */
class OkHttpPageFetcher(
    client: OkHttpClient,
    private val allowPrivateNetworks: suspend () -> Boolean = { false },
    private val maxBytes: Long = 5_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : PageFetcher {
    private val http = GuardedHttp(client, addressPolicy)

    override suspend fun fetchHtml(url: String): String =
        http.get(url, allowPrivateNetworks()) { it.readBytesBounded(maxBytes).decodeToString() }
}

/** Tier 1 of saving: cache a cleaned copy of the full page so it can be read offline. */
class OfflineArchiver(
    private val db: ForageDatabase,
    private val fetcher: PageFetcher,
    private val extractor: ArticleExtractor = ArticleExtractor(),
) {
    suspend fun archive(articleId: Long): Result<Unit> = fetchAndStore(articleId, onlyIfSaved = true)

    /** Same as [archive] but for the reader's explicit download, which doesn't require the article to be saved. */
    suspend fun download(articleId: Long): Result<Unit> = fetchAndStore(articleId, onlyIfSaved = false)

    private suspend fun fetchAndStore(articleId: Long, onlyIfSaved: Boolean): Result<Unit> {
        val article = db.articleDao().getById(articleId)
            ?: return Result.failure(IllegalArgumentException("No article $articleId"))
        if (onlyIfSaved && !article.isSaved) return Result.success(Unit)
        return try {
            val html = fetcher.fetchHtml(article.url)
            val clean = extractor.extract(html, article.url).contentHtml
            if (clean.isBlank()) return Result.failure(IOException("No readable content at ${article.url}"))
            db.articleDao().saveOfflineHtml(articleId, clean)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
