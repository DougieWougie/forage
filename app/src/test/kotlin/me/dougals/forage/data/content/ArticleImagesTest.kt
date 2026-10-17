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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArticleImagesTest {
    private val base = "https://example.com/post/1"

    @Test fun firstImageIsResolvedAgainstTheBase() {
        assertEquals("https://example.com/img/a.jpg", ArticleImages.firstInHtml("""<p>hi</p><img src="/img/a.jpg">""", base))
    }

    @Test fun skipsTrackingPixelsAndSmallIcons() {
        val html = """<img src="https://t.example/p.gif" width="1" height="1"><img src="/e.png" width="16px"><img src="/real.jpg">"""
        assertEquals("https://example.com/real.jpg", ArticleImages.firstInHtml(html, base))
    }

    @Test fun skipsDataUrisAndFallsBackToLazyLoadAttribute() {
        val html = """<img src="data:image/gif;base64,R0lGOD" data-src="/lazy.jpg">"""
        assertEquals("https://example.com/lazy.jpg", ArticleImages.firstInHtml(html, base))
        assertNull(ArticleImages.firstInHtml("""<img src="data:image/gif;base64,R0lGOD">""", base))
    }

    @Test fun nullWhenThereIsNoImage() {
        assertNull(ArticleImages.firstInHtml("<p>text</p>", base))
        assertNull(ArticleImages.firstInHtml(null, base))
        assertNull(ArticleImages.firstInHtml("   ", base))
    }

    @Test fun httpUrlRejectsOtherSchemes() {
        assertNull(ArticleImages.httpUrl("javascript:alert(1)", base))
        assertNull(ArticleImages.httpUrl("file:///etc/passwd", base))
        assertNull(ArticleImages.httpUrl("  ", base))
        assertEquals("https://example.com/x.png", ArticleImages.httpUrl("/x.png", base))
    }
}
