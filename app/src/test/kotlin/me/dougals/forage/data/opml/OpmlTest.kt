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

    @Test fun aFileNestedTensOfThousandsDeepDoesNotOverflowTheStack() {
        val depth = 50_000
        val xml = buildString {
            append("<opml version=\"2.0\"><body>")
            repeat(depth) { append("<outline text=\"f$it\">") }
            append("<outline text=\"Deep\" xmlUrl=\"https://deep/feed\"/>")
            repeat(depth) { append("</outline>") }
            append("</body></opml>")
        }
        var out: List<OpmlFeed>? = null
        var error: Throwable? = null
        // A small stack makes the point quickly: a recursive walk would need ~50,000 frames.
        Thread(null, { try { out = Opml.parse(xml.byteInputStream()) } catch (t: Throwable) { error = t } }, "deep", 512 * 1024)
            .apply { start(); join() }
        assertEquals(null, error)
        assertEquals(listOf(OpmlFeed("Deep", "https://deep/feed", folder = "f${depth - 1}")), out)
    }

    @Test fun feedsInsideAndAfterFoldersKeepDocumentOrder() {
        val xml = """<opml><body>
            <outline text="A"><outline text="1" xmlUrl="u1"/><outline text="B"><outline text="2" xmlUrl="u2"/></outline><outline text="3" xmlUrl="u3"/></outline>
            <outline text="4" xmlUrl="u4"/></body></opml>"""
        assertEquals(listOf("u1", "u2", "u3", "u4"), Opml.parse(xml.byteInputStream()).map { it.xmlUrl })
        assertEquals(listOf("A", "B", "A", null), Opml.parse(xml.byteInputStream()).map { it.folder })
    }
}
