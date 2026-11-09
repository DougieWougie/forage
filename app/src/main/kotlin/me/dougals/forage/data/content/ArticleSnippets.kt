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

import org.jsoup.Jsoup

/** Plain-text preview of an article for list cards, computed once when the article is stored. */
object ArticleSnippets {
    const val MAX_CHARS = 140
    private val whitespace = Regex("\\s+")

    /** The summary if there is one, else the content; tags stripped, whitespace collapsed, cut at a word with an ellipsis. */
    fun of(summary: String?, contentHtml: String?, maxChars: Int = MAX_CHARS): String {
        val html = summary ?: contentHtml ?: return ""
        // Only the head of the document can reach the card, and a feed can ship megabytes of markup.
        val text = Jsoup.parse(html.take(PARSE_LIMIT)).text().replace(whitespace, " ").trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    private const val PARSE_LIMIT = 20_000
}
