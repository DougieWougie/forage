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
}
