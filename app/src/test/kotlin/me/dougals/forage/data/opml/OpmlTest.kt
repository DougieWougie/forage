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
