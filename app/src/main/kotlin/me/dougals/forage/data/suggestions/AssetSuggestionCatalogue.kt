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
package me.dougals.forage.data.suggestions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.dougals.forage.domain.model.FeedSuggestion
import me.dougals.forage.domain.suggest.SuggestionCatalogue

@Serializable
private data class CatalogueFile(val version: Int = 1, val feeds: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(
        val title: String,
        val feedUrl: String,
        val siteUrl: String,
        val description: String,
        val tags: Set<String> = emptySet(),
        val uk: Boolean = false,
        val featured: Boolean = false,
    )
}

object SuggestionCatalogueParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<FeedSuggestion> = json.decodeFromString<CatalogueFile>(text).feeds.map {
        FeedSuggestion(it.title, it.feedUrl, it.siteUrl, it.description, it.tags, it.uk, it.featured)
    }
}

/** The catalogue bundled in `assets/suggestions.json`. */
class AssetSuggestionCatalogue(private val context: Context) : SuggestionCatalogue {
    override suspend fun load(): List<FeedSuggestion> = withContext(Dispatchers.IO) {
        context.assets.open(ASSET).bufferedReader().use { SuggestionCatalogueParser.parse(it.readText()) }
    }

    private companion object { const val ASSET = "suggestions.json" }
}
