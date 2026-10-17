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
