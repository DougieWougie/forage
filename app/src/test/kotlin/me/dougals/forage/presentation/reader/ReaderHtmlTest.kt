package me.dougals.forage.presentation.reader

import me.dougals.forage.domain.model.ReaderFont
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test fun dyslexicFontDeclaresBundledFaceAndFallsBack() {
        val h = page(s = style.copy(font = ReaderFont.DYSLEXIC))
        assertTrue(h.contains("@font-face"))
        assertTrue(h.contains("'OpenDyslexic'"))
        assertTrue(h.contains(ReaderFonts.REGULAR_URL))
        assertTrue(h.contains(ReaderFonts.BOLD_URL))
        assertTrue(h.contains("sans-serif"))
    }

    @Test fun otherFontsDoNotLoadTheBundledFace() {
        listOf(ReaderFont.SERIF, ReaderFont.SANS).forEach {
            val h = page(s = style.copy(font = it))
            assertFalse(h.contains("@font-face"), it.name)
            assertFalse(h.contains("OpenDyslexic"), it.name)
        }
    }

    @Test fun cspAllowsFontsFromTheReservedOriginOnly() {
        val csp = Regex("Content-Security-Policy\" content=\"([^\"]*)\"").find(page())!!.groupValues[1]
        val fontSrc = csp.split(";").map { it.trim() }.single { it.startsWith("font-src") }
        assertEquals("font-src ${ReaderFonts.ORIGIN}", fontSrc)
        assertTrue(csp.contains("script-src 'none'"))
    }

    @Test fun onlyTheTwoBundledFontUrlsMapToAssets() {
        assertEquals("fonts/opendyslexic-400.woff2", ReaderFonts.assetFor(ReaderFonts.REGULAR_URL))
        assertEquals("fonts/opendyslexic-700.woff2", ReaderFonts.assetFor(ReaderFonts.BOLD_URL))
        listOf(
            "${ReaderFonts.ORIGIN}/fonts/../../secret", "${ReaderFonts.ORIGIN}/fonts/other.woff2",
            "https://evil.example/fonts/opendyslexic-400.woff2", "http://forage.invalid/fonts/opendyslexic-400.woff2",
            "${ReaderFonts.REGULAR_URL}?x=1", "",
        ).forEach { assertNull(ReaderFonts.assetFor(it), it) }
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
