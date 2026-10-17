package me.dougals.forage.data.remote

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
            )
        },
    )

    private fun atomLink(n: XmlNode, base: String): String? =
        n.children("link")
            .firstOrNull { (it.attrs["rel"] ?: "alternate") == "alternate" && it.attrs["href"] != null }
            ?.attrs?.get("href")?.let { resolve(base, it) }

    private fun resolve(base: String, link: String): String {
        val trimmed = link.trim()
        return base.toHttpUrlOrNull()?.resolve(trimmed)?.toString() ?: trimmed
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
