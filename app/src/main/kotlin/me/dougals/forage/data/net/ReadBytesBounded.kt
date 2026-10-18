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
package me.dougals.forage.data.net

import okhttp3.Response
import java.io.IOException

fun Response.readBytesBounded(maxBytes: Long): ByteArray {
    val source = body.source()
    source.request(maxBytes + 1)
    if (source.buffer.size > maxBytes) throw IOException("Response larger than $maxBytes bytes")
    return source.readByteArray()
}
