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

/** What a refresh actually changed, for telling the user. */
data class SyncReport(
    val newArticles: Int = 0,
    val changesSent: Int = 0,
    val feedsAdded: Int = 0,
    val markedRead: Int = 0,
    val saved: Int = 0,
    val feedsReceived: Int = 0,
    val feedsRemoved: Int = 0,
    /** [newArticles] broken down by local feed id; feeds that gained nothing are absent. */
    val newByFeed: Map<Long, Int> = emptyMap(),
    /** The newest few titles behind [newByFeed], newest first; see [newestHeadlines]. */
    val newHeadlines: Map<Long, List<Headline>> = emptyMap(),
)

/** A new article's title and when it was published (epoch millis), enough to pick and order what a digest quotes. */
data class Headline(val title: String, val publishedAt: Long)

const val HEADLINES_PER_FEED = 3

/** The newest titles worth quoting: blank ones are dropped and the rest keep their arrival order on ties. */
fun newestHeadlines(found: List<Headline>): List<Headline> =
    found.filter { it.title.isNotBlank() }.sortedByDescending { it.publishedAt }.take(HEADLINES_PER_FEED)

/**
 * A refresh where some feeds failed but others brought in articles. It stays a failure for callers that report or retry,
 * and carries what did arrive so it isn't lost. Its message is the first underlying error's.
 */
class PartialRefreshException(val report: SyncReport, cause: Throwable) : Exception(cause.message, cause)
