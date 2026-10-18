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
package me.dougals.forage.domain.opml

import java.io.InputStream

data class ImportResult(val added: Int, val skipped: Int, val failed: Int)

/** OPML import and export of the user's subscriptions. */
interface OpmlExchange {
    suspend fun import(input: InputStream): Result<ImportResult>
    suspend fun export(): String
}
