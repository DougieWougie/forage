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

import me.dougals.forage.domain.model.Article
import org.jsoup.Jsoup

object ArticleSnippet {
    fun of(article: Article, maxChars: Int = 140): String {
        val html = article.summary ?: article.contentHtml ?: return ""
        val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }
}
