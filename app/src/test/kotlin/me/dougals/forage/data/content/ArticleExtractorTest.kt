package me.dougals.forage.data.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArticleExtractorTest {
    private val extractor = ArticleExtractor()
    private val base = "https://example.com/posts/1"

    @Test fun prefersArticleElementAndDropsChrome() {
        val html = """
            <html><head><title>Page title</title><script>track()</script></head><body>
              <nav><a href="/">Home</a></nav>
              <article><h1>Headline</h1><p>The real text.</p></article>
              <aside>Related junk</aside><footer>Copyright</footer>
            </body></html>"""
        val r = extractor.extract(html, base)
        assertTrue(r.contentHtml.contains("The real text."))
        assertFalse(r.contentHtml.contains("Related junk"))
        assertFalse(r.contentHtml.contains("Copyright"))
        assertFalse(r.contentHtml.contains("Home"))
        assertFalse(r.contentHtml.contains("track()"))
        assertEquals("Page title", r.title)
    }

    @Test fun withoutArticlePicksTextDensestBlock() {
        val long = "<p>" + "Lorem ipsum dolor sit amet. ".repeat(10) + "</p>"
        val html = """
            <html><body>
              <div id="menu"><p>Short</p></div>
              <div id="content">$long$long</div>
            </body></html>"""
        val r = extractor.extract(html, base)
        assertTrue(r.contentHtml.contains("Lorem ipsum"))
        assertFalse(r.contentHtml.contains("Short"))
    }

    @Test fun relativeUrlsBecomeAbsolute() {
        val html = """<article><p><a href="/other">x</a><img src="img/a.png"></p></article>"""
        val out = extractor.extract(html, base).contentHtml
        assertTrue(out.contains("https://example.com/other"))
        assertTrue(out.contains("https://example.com/posts/img/a.png"))
    }

    @Test fun stripsActiveContent() {
        val html = """<article><p onclick="evil()">hi</p><iframe src="x"></iframe>
            <a href="javascript:evil()">j</a><script>evil()</script></article>"""
        val out = extractor.extract(html, base).contentHtml
        assertFalse(out.contains("onclick"))
        assertFalse(out.contains("iframe"))
        assertFalse(out.contains("javascript:"))
        assertFalse(out.contains("evil()"))
        assertTrue(out.contains("hi"))
    }

    @Test fun emptyPageGivesEmptyContent() {
        assertEquals("", extractor.extract("<html><body></body></html>", base).contentHtml.trim())
    }
}
