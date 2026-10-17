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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** Picks the image that best represents an article in a list. */
object ArticleImages {
    /** First usable `<img>` in [html]; resolved against [baseUrl]. Skips tracking pixels, icons and `data:` URIs. */
    fun firstInHtml(html: String?, baseUrl: String?): String? {
        if (html.isNullOrBlank()) return null
        val doc = Jsoup.parseBodyFragment(html, baseUrl.orEmpty())
        return doc.select("img").firstNotNullOfOrNull { img ->
            if (img.tinyDeclared()) return@firstNotNullOfOrNull null
            listOf("src", "data-src").firstNotNullOfOrNull { httpUrl(img.attr(it), baseUrl) }
        }
    }

    /** [raw] resolved against [baseUrl] if it is an absolute or relative http(s) URL, else null. */
    fun httpUrl(raw: String?, baseUrl: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val resolved = baseUrl?.toHttpUrlOrNull()?.resolve(s) ?: s.toHttpUrlOrNull()
        return resolved?.toString()
    }

    private fun org.jsoup.nodes.Element.tinyDeclared(): Boolean =
        listOf("width", "height").any { attr(it).trim().removeSuffix("px").toIntOrNull()?.let { n -> n < MIN_SIDE } == true }

    private const val MIN_SIDE = 32
}
