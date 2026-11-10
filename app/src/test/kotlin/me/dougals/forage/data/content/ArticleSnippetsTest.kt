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
package me.dougals.forage.data.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArticleSnippetsTest {
    @Test fun stripsTagsAndCollapsesWhitespace() =
        assertEquals("Hello big world", ArticleSnippets.of("<p>Hello   <b>big</b>\n world</p>", null))

    @Test fun fallsBackToContentThenEmpty() {
        assertEquals("body", ArticleSnippets.of(null, "<p>body</p>"))
        assertEquals("", ArticleSnippets.of(null, null))
    }

    @Test fun truncatesAtWordBoundaryWithEllipsis() {
        val s = ArticleSnippets.of("word ".repeat(100), null, maxChars = 50)
        assertTrue(s.endsWith("…"))
        assertTrue(s.length <= 51)
        assertTrue(!s.dropLast(1).endsWith(" "))
    }

    @Test fun aHugeBodyIsStillSummarisedFromItsStart() {
        val s = ArticleSnippets.of("<p>Opening words</p>" + "<i>x</i>".repeat(500_000), null)
        assertTrue(s.startsWith("Opening words"))
    }
}
