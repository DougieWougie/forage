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
package me.dougals.forage.data.opml

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpmlTest {
    @Test fun parsesFoldersAndTopLevelFeeds() {
        val opml = """
            <?xml version="1.0"?>
            <opml version="2.0"><head><title>x</title></head><body>
              <outline text="Tech" title="Tech">
                <outline type="rss" text="LWN" xmlUrl="https://lwn.net/headlines/rss"/>
                <outline type="rss" title="Ars" xmlUrl="https://ars/feed"/>
              </outline>
              <outline type="rss" text="Loose" xmlUrl="https://loose/feed"/>
              <outline text="Empty folder"/>
            </body></opml>
        """.trimIndent()
        val result = Opml.parse(opml.byteInputStream())
        assertEquals(
            listOf(
                OpmlFeed("LWN", "https://lwn.net/headlines/rss", folder = "Tech"),
                OpmlFeed("Ars", "https://ars/feed", folder = "Tech"),
                OpmlFeed("Loose", "https://loose/feed", folder = null),
            ),
            result,
        )
    }

    @Test fun exportThenParseRoundTripsIncludingEscaping() {
        val feeds = listOf(
            OpmlFeed("A & B <c>", "https://a/feed?x=1&y=2", folder = "Fo\"lder"),
            OpmlFeed("Loose", "https://loose/feed", folder = null),
        )
        val xml = Opml.export(feeds)
        assertEquals(feeds, Opml.parse(xml.byteInputStream()))
    }
}
