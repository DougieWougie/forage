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
import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import me.dougals.forage.domain.content.FetchedImage
import me.dougals.forage.domain.content.ImageFetcher
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress

/**
 * Loads reader images on behalf of the reader WebView, so the WebView itself never opens a socket: article HTML is
 * untrusted and could point `<img>` at the LAN or a metadata address. Goes through [GuardedHttp] and serves
 * responses whose content type is an image only.
 */
class ImageResourceFetcher(
    client: OkHttpClient,
    private val maxBytes: Long = 15_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : ImageFetcher {
    private val http = GuardedHttp(client, addressPolicy)

    override suspend fun fetch(url: String, allowPrivate: Boolean, maxBytes: Long?): Result<FetchedImage> = try {
        Result.success(
            http.get(url, allowPrivate) { r ->
                val type = r.body.contentType()
                if (type?.type != "image") throw IOException("Not an image: ${type ?: "no content type"}")
                FetchedImage("${type.type}/${type.subtype}", r.readBytesBounded(minOf(maxBytes ?: this.maxBytes, this.maxBytes)))
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
