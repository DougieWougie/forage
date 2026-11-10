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

/** Fetches an article's full page and keeps a cleaned copy for reading offline. */
interface ArticleDownloader {
    /** Works for any article, saved or not; the reader's explicit "download full article". */
    suspend fun download(articleId: Long): Result<Unit>
}
