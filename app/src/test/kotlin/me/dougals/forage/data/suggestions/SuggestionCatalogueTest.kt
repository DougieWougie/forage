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
package me.dougals.forage.data.suggestions

import androidx.test.core.app.ApplicationProvider
import me.dougals.forage.domain.model.FeedSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuggestionCatalogueTest {
    @Test fun parsesEntries() {
        val json = """{"version":1,"feeds":[
            {"title":"LWN","feedUrl":"https://lwn.net/headlines/rss","siteUrl":"https://lwn.net/","description":"Linux news","tags":["linux"],"uk":false,"featured":true},
            {"title":"Min","feedUrl":"https://m.example/f","siteUrl":"https://m.example/","description":"d"}]}"""
        assertEquals(
            listOf(
                FeedSuggestion("LWN", "https://lwn.net/headlines/rss", "https://lwn.net/", "Linux news", setOf("linux"), uk = false, featured = true),
                FeedSuggestion("Min", "https://m.example/f", "https://m.example/", "d", emptySet(), uk = false, featured = false),
            ),
            SuggestionCatalogueParser.parse(json),
        )
    }

    @Test fun unknownFieldsAreIgnoredSoTheCatalogueCanGrow() {
        val json = """{"version":2,"feeds":[{"title":"A","feedUrl":"https://a.example/f","siteUrl":"https://a.example/","description":"d","language":"en"}]}"""
        assertEquals(1, SuggestionCatalogueParser.parse(json).size)
    }

    @Test fun theBundledCatalogueIsSoundAndFavoursUkTechAndLinux() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val feeds = kotlinx.coroutines.runBlocking { AssetSuggestionCatalogue(context).load() }
        assertTrue("expected a decent starter set, got ${feeds.size}", feeds.size >= 50)
        assertTrue("UK feeds: ${feeds.count { it.uk }}", feeds.count { it.uk } >= 25)
        assertEquals("duplicate feed URLs", feeds.size, feeds.map { it.feedUrl }.toSet().size)
        assertTrue("non-https entries: ${feeds.filterNot { it.feedUrl.startsWith("https://") }}", feeds.all { it.feedUrl.startsWith("https://") })
        assertTrue(feeds.all { it.title.isNotBlank() && it.description.isNotBlank() && it.siteUrl.startsWith("https://") })
        assertTrue("every feed needs tags", feeds.all { it.tags.isNotEmpty() })
        assertTrue(feeds.count { "linux" in it.tags } >= 8)
        assertTrue(feeds.count { "technology" in it.tags } >= 8)
        assertTrue("some featured feeds for first run", feeds.count { it.featured } >= 6)
    }
}
