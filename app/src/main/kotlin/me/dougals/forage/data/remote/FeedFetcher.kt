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

fun interface FeedSource {
    suspend fun fetch(url: String): ParsedFeed
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

    override suspend fun fetch(url: String): ParsedFeed = try {
        http.get(url, allowLocalNetwork()) { r ->
            parser.parse(ByteArrayInputStream(r.readBytesBounded(maxBytes)), r.request.url.toString())
        }
    } catch (e: HttpStatusException) {
        throw FeedFetchException("HTTP ${e.code}", e.code, e)
    } catch (e: IOException) {
        throw FeedFetchException(e.message ?: "Network error", cause = e)
    }
}
