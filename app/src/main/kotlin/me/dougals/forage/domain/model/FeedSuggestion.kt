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
package me.dougals.forage.domain.model

/** A feed from the bundled catalogue that Forage can recommend. */
data class FeedSuggestion(
    val title: String,
    val feedUrl: String,
    val siteUrl: String,
    val description: String,
    val tags: Set<String>,
    val uk: Boolean,
    /** Shown to people who haven't subscribed to anything the catalogue knows yet. */
    val featured: Boolean,
)
