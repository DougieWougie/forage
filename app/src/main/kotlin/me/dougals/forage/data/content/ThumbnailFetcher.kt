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

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.Path
import java.io.IOException

/**
 * Coil fetcher for list thumbnails. Registered in place of Coil's own network fetcher, so every http(s) image URL
 * from a feed goes through [ImageResourceFetcher] and its address guard instead of a client of Coil's choosing.
 * Bytes are kept in Coil's disk cache; a disabled network policy serves from that cache only.
 */
class ThumbnailFetcher(
    private val url: String,
    private val options: Options,
    private val images: ImageResourceFetcher,
    private val allowPrivate: suspend () -> Boolean,
    private val diskCache: () -> DiskCache?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val cache = diskCache()
        if (cache != null && options.diskCachePolicy.readEnabled) {
            cache.openSnapshot(url)?.let { return result(cache, it.data, DataSource.DISK, it) }
        }
        if (!options.networkCachePolicy.readEnabled) throw IOException("Network disabled for $url")

        val image = images.fetch(url, allowPrivate()).getOrThrow()
        val editor = cache?.takeIf { options.diskCachePolicy.writeEnabled }?.openEditor(url)
        if (editor != null) {
            try {
                cache.fileSystem.write(editor.data) { write(image.bytes) }
                editor.commit()
                cache.openSnapshot(url)?.let { return result(cache, it.data, DataSource.NETWORK, it) }
            } catch (e: IOException) {
                editor.abort()
            }
        }
        val source = Buffer().write(image.bytes)
        return SourceFetchResult(ImageSource(source, options.fileSystem), image.mimeType, DataSource.NETWORK)
    }

    private fun result(cache: DiskCache, path: Path, from: DataSource, snapshot: DiskCache.Snapshot) =
        SourceFetchResult(ImageSource(path, cache.fileSystem, url, snapshot), null, from)

    class Factory(
        private val images: ImageResourceFetcher,
        private val allowPrivate: suspend () -> Boolean,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "http" || data.scheme == "https") {
                ThumbnailFetcher(data.toString(), options, images, allowPrivate) { imageLoader.diskCache }
            } else null
    }
}
