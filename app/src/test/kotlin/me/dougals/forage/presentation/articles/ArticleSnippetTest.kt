package me.dougals.forage.presentation.articles

import me.dougals.forage.testutil.article
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArticleSnippetTest {
    @Test fun stripsTagsAndCollapsesWhitespace() {
        val a = article(1).copy(summary = "<p>Hello   <b>big</b>\n world</p>")
        assertEquals("Hello big world", ArticleSnippet.of(a))
    }

    @Test fun fallsBackToContentThenEmpty() {
        assertEquals("body", ArticleSnippet.of(article(1).copy(contentHtml = "<p>body</p>")))
        assertEquals("", ArticleSnippet.of(article(1)))
    }

    @Test fun truncatesAtWordBoundaryWithEllipsis() {
        val a = article(1).copy(summary = "word ".repeat(100))
        val s = ArticleSnippet.of(a, maxChars = 50)
        assertTrue(s.endsWith("…"))
        assertTrue(s.length <= 51)
        assertTrue(!s.dropLast(1).endsWith(" "))
    }
}
