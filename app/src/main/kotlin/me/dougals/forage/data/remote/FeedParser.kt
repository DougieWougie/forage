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
package me.dougals.forage.data.remote

import me.dougals.forage.data.content.ArticleImages
import me.dougals.forage.data.xml.XmlNode
import me.dougals.forage.data.xml.XmlParseException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ParsedItem(
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val summary: String?,
    val contentHtml: String?,
    val publishedAt: Long,
    val imageUrl: String? = null,
)

data class ParsedFeed(val title: String, val siteUrl: String?, val items: List<ParsedItem>)

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom into one shape. */
class FeedParser {
    fun parse(input: InputStream, baseUrl: String): ParsedFeed {
        val root = try {
            XmlNode.parse(input)
        } catch (e: XmlParseException) {
            throw FeedParseException(e.message ?: "Malformed feed", e)
        }
        return when (root.name) {
            "rss" -> rss(root.child("channel") ?: throw FeedParseException("RSS without channel"), baseUrl)
            "RDF" -> rdf(root, baseUrl)
            "feed" -> atom(root, baseUrl)
            else -> throw FeedParseException("Not a feed: <${root.name}>")
        }
    }

    private fun rss(channel: XmlNode, base: String) = ParsedFeed(
        title = channel.childText("title").orEmpty(),
        siteUrl = channel.childText("link")?.let { resolve(base, it) },
        items = channel.children("item").map { rssItem(it, base) },
    )

    private fun rdf(root: XmlNode, base: String): ParsedFeed {
        val channel = root.child("channel") ?: throw FeedParseException("RDF without channel")
        return ParsedFeed(
            title = channel.childText("title").orEmpty(),
            siteUrl = channel.childText("link")?.let { resolve(base, it) },
            items = root.children("item").map { rssItem(it, base) },
        )
    }

    private fun rssItem(n: XmlNode, base: String): ParsedItem {
        val title = n.childText("title").orEmpty()
        val url = n.childText("link")?.let { resolve(base, it) }.orEmpty()
        return ParsedItem(
            guid = n.childText("guid") ?: url.ifEmpty { null } ?: n.attrs["about"] ?: title,
            title = title,
            url = url,
            author = n.childText("creator") ?: n.childText("author"),
            summary = n.childText("description"),
            contentHtml = n.childText("encoded"),
            publishedAt = parseDate(n.childText("pubDate") ?: n.childText("date")),
            imageUrl = imageOf(n, base, n.childText("encoded"), n.childText("description")),
        )
    }

    private fun atom(feed: XmlNode, base: String) = ParsedFeed(
        title = feed.childText("title").orEmpty(),
        siteUrl = atomLink(feed, base),
        items = feed.children("entry").map { e ->
            val title = e.childText("title").orEmpty()
            val url = atomLink(e, base).orEmpty()
            ParsedItem(
                guid = e.childText("id") ?: url.ifEmpty { title },
                title = title,
                url = url,
                author = e.child("author")?.childText("name"),
                summary = e.childText("summary"),
                contentHtml = e.childText("content"),
                publishedAt = parseDate(e.childText("published") ?: e.childText("updated")),
                imageUrl = imageOf(e, base, e.childText("content"), e.childText("summary")),
            )
        },
    )

    private fun atomLink(n: XmlNode, base: String): String? =
        n.children("link")
            .firstOrNull { (it.attrs["rel"] ?: "alternate") == "alternate" && it.attrs["href"] != null }
            ?.attrs?.get("href")?.let { resolve(base, it) }

    /** Media RSS thumbnail or image, then an image enclosure, then the first `<img>` in the item's HTML. */
    private fun imageOf(item: XmlNode, base: String, vararg html: String?): String? {
        val media = item.children + item.children("group").flatMap { it.children }
        val declared = media.firstNotNullOfOrNull { n ->
            val url = n.attrs["url"] ?: n.attrs["href"].takeIf { n.name == "link" && n.attrs["rel"] == "enclosure" }
            when {
                url == null -> null
                n.name == "thumbnail" -> url
                n.name in MEDIA_OR_ENCLOSURE && isImage(n) -> url
                else -> null
            }
        }
        return ArticleImages.httpUrl(declared, base)
            ?: html.firstNotNullOfOrNull { ArticleImages.firstInHtml(it, base) }
    }

    private fun isImage(n: XmlNode): Boolean =
        n.attrs["medium"] == "image" || (n.attrs["type"] ?: "").startsWith("image/")

    private fun resolve(base: String, link: String): String {
        val trimmed = link.trim()
        return base.toHttpUrlOrNull()?.resolve(trimmed)?.toString() ?: trimmed
    }

    private companion object {
        val MEDIA_OR_ENCLOSURE = setOf("content", "enclosure", "link")
    }

    private fun parseDate(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching {
                ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z", Locale.US))
                    .toInstant().toEpochMilli()
            }
            .getOrDefault(0)
    }
}
