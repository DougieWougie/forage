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
package me.dougals.forage.data.mapper

import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.domain.model.Article
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MappersTest {
    private val entity = ArticleEntity(
        id = 7, feedId = 2, guid = "g", title = "Title", url = "https://x/a", author = "Ann",
        summary = "sum", contentHtml = "<p>full</p>", publishedAt = 1000, isRead = true,
        isSaved = true, offlineHtml = "<p>clean</p>", imageUrl = "https://x/a.jpg",
    )

    @Test fun articleRoundTripsThroughDomain() {
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test fun hasOfflineCopyReflectsOfflineHtml() {
        assertEquals(true, entity.toDomain().hasOfflineCopy)
        assertFalse(entity.copy(offlineHtml = null).toDomain().hasOfflineCopy)
    }

    @Test fun readerHtmlPrefersOfflineThenContentThenSummary() {
        assertEquals("<p>clean</p>", entity.toDomain().readerHtml)
        assertEquals("<p>full</p>", entity.copy(offlineHtml = null).toDomain().readerHtml)
        assertEquals("sum", entity.copy(offlineHtml = null, contentHtml = null).toDomain().readerHtml)
        assertEquals("", entity.copy(offlineHtml = null, contentHtml = null, summary = null).toDomain().readerHtml)
    }

    @Test fun feedMapsWithUnreadCount() {
        val feed = FeedEntity(id = 1, url = "u", title = "t", folderId = 3).toDomain(unreadCount = 5)
        assertEquals(5, feed.unreadCount)
        assertEquals(3L, feed.folderId)
    }
}
