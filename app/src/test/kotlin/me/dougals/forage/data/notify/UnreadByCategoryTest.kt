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
package me.dougals.forage.data.notify

import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadByCategoryTest {
    private fun feed(id: Long, folder: Long?, unread: Int) = Feed(id, "u$id", "f$id", null, folder, unread)

    @Test fun `sums per folder, biggest first, uncategorised named null`() {
        val feeds = listOf(feed(1, 10, 2), feed(2, 10, 3), feed(3, 11, 9), feed(4, null, 1), feed(5, 11, 0))
        val folders = listOf(Folder(10, "Tech"), Folder(11, "News"))
        assertEquals(listOf("News" to 9, "Tech" to 5, null to 1), unreadByCategory(feeds, folders))
    }

    @Test fun `nothing unread gives no categories`() {
        assertEquals(emptyList<Pair<String?, Int>>(), unreadByCategory(listOf(feed(1, 10, 0)), listOf(Folder(10, "Tech"))))
    }
}
