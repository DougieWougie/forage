package me.dougals.forage.presentation.reader

import me.dougals.forage.domain.model.ReaderFont
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderHtmlTest {
    private val style = ReaderStyle(ReaderFont.SERIF, 1.0f, background = "#101010", text = "#eeeeee", link = "#88aaff")
    private fun page(title: String = "T", byline: String? = null, body: String = "<p>b</p>", s: ReaderStyle = style) =
        ReaderHtml.wrap(title, byline, body, s)

    @Test fun includesBodyTitleAndByline() {
        val h = page(title = "Hello", byline = "Ann · 2025-06-10", body = "<p>content</p>")
        assertTrue(h.contains("<h1>Hello</h1>"))
        assertTrue(h.contains("Ann · 2025-06-10"))
        assertTrue(h.contains("<p>content</p>"))
    }

    @Test fun titleAndBylineAreEscaped() {
        val h = page(title = "<script>x()</script> & co", byline = "\"<b>\"")
        assertFalse(h.contains("<script>x()"))
        assertTrue(h.contains("&lt;script&gt;x()&lt;/script&gt; &amp; co"))
        assertTrue(h.contains("&quot;&lt;b&gt;&quot;"))
    }

    @Test fun serifAndSansFontStacks() {
        assertTrue(page(s = style.copy(font = ReaderFont.SERIF)).contains("Georgia"))
        val sans = page(s = style.copy(font = ReaderFont.SANS))
        assertTrue(sans.contains("sans-serif"))
        assertFalse(sans.contains("Georgia"))
    }

    @Test fun fontScaleBecomesPercentage() {
        assertTrue(page(s = style.copy(fontScale = 1.25f)).contains("font-size: 125%"))
        assertTrue(page(s = style.copy(fontScale = 0.8f)).contains("font-size: 80%"))
    }

    @Test fun themeColoursAreApplied() {
        val h = page()
        listOf("#101010", "#eeeeee", "#88aaff").forEach { assertTrue(h.contains(it), it) }
    }

    @Test fun cspBlocksScriptsAndHasViewport() {
        val h = page()
        assertTrue(h.contains("Content-Security-Policy"))
        assertTrue(h.contains("script-src 'none'") || h.contains("default-src 'none'"))
        assertTrue(h.contains("name=\"viewport\""))
    }

    @Test fun imagesAreConstrainedToTheColumn() = assertTrue(page().contains("max-width: 100%"))
}
