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
package me.dougals.forage.data.remote

import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.HttpStatusException
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress

class FeedFetchException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

/** The `ETag` / `Last-Modified` a server gave with a feed, replayed as `If-None-Match` / `If-Modified-Since`. */
data class CacheValidators(val etag: String? = null, val lastModified: String? = null) {
    val isEmpty: Boolean get() = etag == null && lastModified == null
}

sealed interface FeedResponse {
    /** The server sent the feed; [validators] are what to send next time. */
    data class Fresh(val feed: ParsedFeed, val validators: CacheValidators) : FeedResponse
    /** 304: nothing has changed since the validators were issued. */
    data object NotModified : FeedResponse
}

interface FeedSource {
    suspend fun fetch(url: String): ParsedFeed

    /** Like [fetch], but lets the server answer [FeedResponse.NotModified] when [validators] still match. */
    suspend fun fetchConditional(url: String, validators: CacheValidators): FeedResponse =
        FeedResponse.Fresh(fetch(url), CacheValidators())
}

/**
 * Feed URLs arrive via OPML files, user input and redirects, so they get the same SSRF guard as article pages
 * unless the user has opted in to local-network feeds ([allowLocalNetwork]).
 */
class FeedFetcher(
    client: OkHttpClient,
    private val parser: FeedParser,
    private val allowLocalNetwork: suspend () -> Boolean = { false },
    private val maxBytes: Long = 10_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : FeedSource {
    private val http = GuardedHttp(client, addressPolicy)

    override suspend fun fetch(url: String): ParsedFeed = (fetchConditional(url, CacheValidators()) as FeedResponse.Fresh).feed

    override suspend fun fetchConditional(url: String, validators: CacheValidators): FeedResponse = try {
        val headers = buildMap {
            validators.etag?.let { put("If-None-Match", it) }
            validators.lastModified?.let { put("If-Modified-Since", it) }
        }
        // 304 is only expected (and only let through) when a validator was actually sent.
        val pass = if (headers.isEmpty()) emptySet() else setOf(304)
        http.get(url, allowLocalNetwork(), headers, pass) { r ->
            if (r.code == 304) {
                FeedResponse.NotModified
            } else {
                val feed = parser.parse(ByteArrayInputStream(r.readBytesBounded(maxBytes)), r.request.url.toString())
                FeedResponse.Fresh(feed, CacheValidators(r.header("ETag"), r.header("Last-Modified")))
            }
        }
    } catch (e: HttpStatusException) {
        throw FeedFetchException("HTTP ${e.code}", e.code, e)
    } catch (e: IOException) {
        throw FeedFetchException(e.message ?: "Network error", cause = e)
    }
}
