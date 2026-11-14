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
package me.dougals.forage.domain.content

class FetchedImage(val mimeType: String, val bytes: ByteArray)

/**
 * Loads an image the app has decided to show. Implementations apply the app's network rules (address guard, size
 * limit, image-only content), so callers never need their own HTTP client.
 */
interface ImageFetcher {
    /** [maxBytes] lowers the size limit for this call (list thumbnails); null keeps the fetcher's own limit. */
    suspend fun fetch(url: String, allowPrivate: Boolean, maxBytes: Long? = null): Result<FetchedImage>
}
