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
package me.dougals.forage.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FeedParserTest {
    private val parser = FeedParser()
    private fun parse(xml: String, base: String = "https://example.com/feed.xml") =
        parser.parse(xml.trimIndent().byteInputStream(), base)

    @Test fun rss2() {
        val feed = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"
                 xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Example</title>
                <link>https://example.com/</link>
                <item>
                  <title>First &amp; best</title>
                  <link>https://example.com/1</link>
                  <guid isPermaLink="false">id-1</guid>
                  <dc:creator>Ann</dc:creator>
                  <pubDate>Tue, 10 Jun 2025 12:00:00 GMT</pubDate>
                  <description><![CDATA[<p>short</p>]]></description>
                  <content:encoded><![CDATA[<p>full body</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """)
        assertEquals("Example", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        val item = feed.items.single()
        assertEquals("First & best", item.title)
        assertEquals("id-1", item.guid)
        assertEquals("https://example.com/1", item.url)
        assertEquals("Ann", item.author)
        assertEquals("<p>short</p>", item.summary)
        assertEquals("<p>full body</p>", item.contentHtml)
        assertEquals(Instant.parse("2025-06-10T12:00:00Z").toEpochMilli(), item.publishedAt)
    }

    @Test fun rss2NumericTimezoneAndMissingGuidFallsBackToLink() {
        val item = parse("""
            <rss version="2.0"><channel><title>t</title>
              <item><title>x</title><link>https://example.com/p</link>
              <pubDate>Tue, 10 Jun 2025 14:00:00 +0200</pubDate></item>
            </channel></rss>
        """).items.single()
        assertEquals("https://example.com/p", item.guid)
        assertEquals(Instant.parse("2025-06-10T12:00:00Z").toEpochMilli(), item.publishedAt)
    }

    @Test fun rdfRss1() {
        val feed = parse("""
            <?xml version="1.0"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.com/"><title>RDF Feed</title><link>https://example.com/</link></channel>
              <item rdf:about="https://example.com/r1">
                <title>Item</title><link>https://example.com/r1</link>
                <description>desc</description>
                <dc:creator>Bob</dc:creator>
                <dc:date>2025-06-10T12:00:00Z</dc:date>
              </item>
            </rdf:RDF>
        """)
        assertEquals("RDF Feed", feed.title)
        val item = feed.items.single()
        assertEquals("https://example.com/r1", item.guid)
        assertEquals("Bob", item.author)
        assertEquals("desc", item.summary)
        assertEquals(Instant.parse("2025-06-10T12:00:00Z").toEpochMilli(), item.publishedAt)
    }

    @Test fun atom() {
        val feed = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <link rel="self" href="https://example.com/atom.xml"/>
              <link rel="alternate" href="https://example.com/"/>
              <entry>
                <id>tag:example.com,2025:1</id>
                <title>Entry</title>
                <link rel="replies" href="https://example.com/c"/>
                <link rel="alternate" href="https://example.com/e1"/>
                <author><name>Cy</name></author>
                <published>2025-06-10T12:00:00Z</published>
                <updated>2025-06-11T12:00:00Z</updated>
                <summary>sum</summary>
                <content type="html">&lt;p&gt;body&lt;/p&gt;</content>
              </entry>
            </feed>
        """)
        assertEquals("Atom Feed", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        val e = feed.items.single()
        assertEquals("tag:example.com,2025:1", e.guid)
        assertEquals("https://example.com/e1", e.url)
        assertEquals("Cy", e.author)
        assertEquals("sum", e.summary)
        assertEquals("<p>body</p>", e.contentHtml)
        assertEquals(Instant.parse("2025-06-10T12:00:00Z").toEpochMilli(), e.publishedAt)
    }

    @Test fun atomFallsBackToUpdatedAndLinkWithoutRel() {
        val e = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><title>t</title>
              <entry><id>1</id><title>x</title><link href="https://example.com/e"/>
              <updated>2025-06-11T12:00:00+00:00</updated></entry>
            </feed>
        """).items.single()
        assertEquals("https://example.com/e", e.url)
        assertEquals(Instant.parse("2025-06-11T12:00:00Z").toEpochMilli(), e.publishedAt)
    }

    @Test fun relativeLinksResolveAgainstFeedUrl() {
        val item = parse("""
            <rss version="2.0"><channel><title>t</title>
              <item><title>x</title><link>/posts/1</link></item>
            </channel></rss>
        """, base = "https://example.com/rss/feed.xml").items.single()
        assertEquals("https://example.com/posts/1", item.url)
    }

    @Test fun unparseableDateYieldsZeroNotCrash() {
        val item = parse("""
            <rss version="2.0"><channel><title>t</title>
              <item><title>x</title><link>https://e/1</link><pubDate>yesterday-ish</pubDate></item>
            </channel></rss>
        """).items.single()
        assertEquals(0L, item.publishedAt)
    }

    @Test fun missingOptionalFieldsAreNull() {
        val item = parse("""
            <rss version="2.0"><channel><title>t</title>
              <item><title>x</title><link>https://e/1</link></item>
            </channel></rss>
        """).items.single()
        assertNull(item.author)
        assertNull(item.summary)
        assertNull(item.contentHtml)
    }

    @Test fun nonFeedXmlThrows() {
        assertThrows(FeedParseException::class.java) { parse("<html><body>nope</body></html>") }
    }

    @Test fun malformedXmlThrows() {
        assertThrows(FeedParseException::class.java) { parse("<rss><channel><title>oops") }
    }

    private fun rssImage(inner: String, base: String = "https://example.com/feed.xml") = parse("""
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel><title>t</title><item><title>x</title><link>https://example.com/p</link>$inner</item></channel></rss>
    """, base).items.single().imageUrl

    @Test fun mediaThumbnailIsTheImage() {
        assertEquals("https://example.com/t.jpg", rssImage("""<media:thumbnail url="https://example.com/t.jpg"/>"""))
    }

    @Test fun mediaContentCountsOnlyWhenItIsAnImage() {
        assertEquals("https://example.com/m.jpg", rssImage("""<media:content url="https://example.com/m.jpg" medium="image"/>"""))
        assertEquals("https://example.com/m2.png", rssImage("""<media:content url="https://example.com/m2.png" type="image/png"/>"""))
        assertNull(rssImage("""<media:content url="https://example.com/v.mp4" medium="video" type="video/mp4"/>"""))
    }

    @Test fun mediaGroupIsSearched() {
        assertEquals(
            "https://example.com/g.jpg",
            rssImage("""<media:group><media:thumbnail url="https://example.com/g.jpg"/></media:group>"""),
        )
    }

    @Test fun imageEnclosureIsUsedButAudioIsNot() {
        assertEquals("https://example.com/e.jpg", rssImage("""<enclosure url="https://example.com/e.jpg" type="image/jpeg" length="1"/>"""))
        assertNull(rssImage("""<enclosure url="https://example.com/e.mp3" type="audio/mpeg" length="1"/>"""))
    }

    @Test fun fallsBackToTheFirstImgInTheBodyThenSummary() {
        assertEquals(
            "https://example.com/body.jpg",
            rssImage("""<description>&lt;img src="/sum.jpg"&gt;</description><content:encoded><![CDATA[<img src="/body.jpg">]]></content:encoded>"""),
        )
        assertEquals("https://example.com/sum.jpg", rssImage("""<description>&lt;img src="/sum.jpg"&gt;</description>"""))
    }

    @Test fun relativeMediaUrlsAreResolvedAgainstTheFeed() {
        assertEquals("https://example.com/img/t.jpg", rssImage("""<media:thumbnail url="/img/t.jpg"/>"""))
    }

    @Test fun nonHttpMediaUrlIsIgnored() {
        assertNull(rssImage("""<media:thumbnail url="javascript:alert(1)"/>"""))
    }

    @Test fun atomEnclosureLinkAndContentImage() {
        val atom = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><title>t</title>
              <entry><title>a</title><id>1</id><link href="https://example.com/a"/>
                <link rel="enclosure" type="image/jpeg" href="https://example.com/enc.jpg"/></entry>
              <entry><title>b</title><id>2</id><link href="https://example.com/b"/>
                <content type="html">&lt;img src="/c.jpg"&gt;</content></entry>
              <entry><title>c</title><id>3</id><link href="https://example.com/c"/></entry>
            </feed>
        """)
        assertEquals(listOf("https://example.com/enc.jpg", "https://example.com/c.jpg", null), atom.items.map { it.imageUrl })
    }
}
