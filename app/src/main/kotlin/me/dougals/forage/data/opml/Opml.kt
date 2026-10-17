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
package me.dougals.forage.data.opml

import me.dougals.forage.data.xml.XmlNode
import me.dougals.forage.data.xml.XmlParseException
import java.io.InputStream

data class OpmlFeed(val title: String, val xmlUrl: String, val folder: String?)

object Opml {
    fun parse(input: InputStream): List<OpmlFeed> {
        val root = XmlNode.parse(input)
        if (root.name != "opml") throw XmlParseException("Not an OPML file")
        val body = root.child("body") ?: return emptyList()
        val out = mutableListOf<OpmlFeed>()
        fun walk(node: XmlNode, folder: String?) {
            for (o in node.children("outline")) {
                val url = o.attrs["xmlUrl"]
                val name = o.attrs["text"] ?: o.attrs["title"]
                if (url != null) out += OpmlFeed(name ?: url, url, folder) else walk(o, name ?: folder)
            }
        }
        walk(body, null)
        return out
    }

    fun export(feeds: List<OpmlFeed>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head><title>Forage subscriptions</title></head>\n  <body>\n")
        feeds.filter { it.folder != null }.groupBy { it.folder!! }.forEach { (folder, items) ->
            append("    <outline text=\"${esc(folder)}\" title=\"${esc(folder)}\">\n")
            items.forEach { append("      ${outline(it)}\n") }
            append("    </outline>\n")
        }
        feeds.filter { it.folder == null }.forEach { append("    ${outline(it)}\n") }
        append("  </body>\n</opml>\n")
    }

    private fun outline(f: OpmlFeed) =
        "<outline type=\"rss\" text=\"${esc(f.title)}\" title=\"${esc(f.title)}\" xmlUrl=\"${esc(f.xmlUrl)}\"/>"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
