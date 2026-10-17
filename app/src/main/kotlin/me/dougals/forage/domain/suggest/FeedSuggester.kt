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
package me.dougals.forage.domain.suggest

import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.model.Folder
import java.net.URI

/**
 * Recommends catalogue feeds from what you already follow, entirely on the device.
 *
 * Your interests are the tags of subscribed feeds the catalogue knows, plus folder names that mention a tag.
 * Candidates are ranked by how many interest points their tags share, then UK feeds, featured feeds, and title.
 * Featured feeds follow the matches (and are all there is before you have any matches).
 */
class FeedSuggester(private val catalogue: List<FeedSuggestion>) {
    fun suggest(feeds: List<Feed>, folders: List<Folder>, dismissed: Set<String>, limit: Int = 8): List<FeedSuggestion> {
        val followedUrls = feeds.map { normalizeUrl(it.url) }.toSet()
        val followedHosts = feeds.mapNotNull { f -> f.siteUrl?.let(::hostOf) }.toSet()
        val skipped = dismissed.map(::normalizeUrl).toSet()

        val interests = interests(feeds, folders)
        val candidates = catalogue.filter { c ->
            normalizeUrl(c.feedUrl) !in followedUrls && normalizeUrl(c.feedUrl) !in skipped && hostOf(c.siteUrl) !in followedHosts
        }
        val scored = candidates
            .map { it to it.tags.sumOf { tag -> interests[tag] ?: 0 } }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<FeedSuggestion, Int>> { it.second }
                    .thenByDescending { it.first.uk }.thenByDescending { it.first.featured }.thenBy { it.first.title.lowercase() },
            )
            .map { it.first }
        val featured = candidates.filter { it.featured && it !in scored }
        return (scored + featured).take(limit)
    }

    private fun interests(feeds: List<Feed>, folders: List<Folder>): Map<String, Int> {
        val points = mutableMapOf<String, Int>()
        val byUrl = catalogue.associateBy { normalizeUrl(it.feedUrl) }
        val byHost = catalogue.groupBy { hostOf(it.siteUrl) }
        for (f in feeds) {
            val known = byUrl[normalizeUrl(f.url)]?.let(::listOf) ?: f.siteUrl?.let(::hostOf)?.let { byHost[it] }.orEmpty()
            known.flatMap { it.tags }.distinct().forEach { points.merge(it, 1, Int::plus) }
        }
        val vocabulary = catalogue.flatMap { it.tags }.toSet()
        for (folder in folders) {
            val name = folder.name.lowercase()
            vocabulary.filter { it == name || (it.length >= 4 && it in name) }.forEach { points.merge(it, 2, Int::plus) }
        }
        return points
    }

    private companion object {
        fun normalizeUrl(url: String) = url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")
    }
}
