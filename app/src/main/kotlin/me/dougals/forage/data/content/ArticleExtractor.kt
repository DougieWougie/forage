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
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class ExtractedArticle(val title: String, val contentHtml: String)

/** Readability-style: drop page chrome, pick the main content block, sanitise, absolutise URLs. */
class ArticleExtractor {
    fun extract(html: String, baseUrl: String): ExtractedArticle {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, noscript, nav, aside, footer, form, iframe, svg").remove()
        val main = doc.selectFirst("article") ?: doc.selectFirst("main, [role=main]") ?: densestBlock(doc.body())
        val clean = Jsoup.clean(main.html(), baseUrl, Safelist.relaxed())
        return ExtractedArticle(doc.title(), clean)
    }

    private fun densestBlock(body: Element): Element {
        val scores = HashMap<Element, Int>()
        for (p in body.select("p")) {
            val len = p.text().length
            if (len < MIN_PARAGRAPH) continue
            p.parent()?.let { scores.merge(it, len, Int::plus) }
            p.parent()?.parent()?.let { scores.merge(it, len / 2, Int::plus) }
        }
        return scores.maxByOrNull { it.value }?.key ?: body
    }

    private companion object { const val MIN_PARAGRAPH = 25 }
}
