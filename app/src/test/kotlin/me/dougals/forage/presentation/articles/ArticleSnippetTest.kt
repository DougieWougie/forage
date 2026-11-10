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
import org.junit.jupiter.api.Test

class ArticleSnippetTest {
    @Test fun showsTheStoredSnippet() = assertEquals("Hello big world", ArticleSnippet.of(article(1).copy(snippet = "Hello big world")))

    @Test fun anArticleWithoutOneShowsNothingYetAndNeverParsesTheBody() {
        assertEquals("", ArticleSnippet.of(article(1)))
        assertEquals("", ArticleSnippet.of(article(1).copy(summary = "<p>raw html</p>", contentHtml = "<p>more</p>")))
    }
}
