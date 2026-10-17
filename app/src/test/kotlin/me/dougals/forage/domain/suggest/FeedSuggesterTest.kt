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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeedSuggesterTest {
    private fun s(
        name: String, vararg tags: String, uk: Boolean = false, featured: Boolean = false,
        feed: String = "https://$name.example/feed", site: String = "https://$name.example/",
    ) = FeedSuggestion(name, feed, site, "About $name", tags.toSet(), uk, featured)

    private fun sub(url: String, site: String? = null, title: String = "Sub", folder: Long? = null) = Feed(1, url, title, site, folder, 0)

    private fun names(r: List<FeedSuggestion>) = r.map { it.title }

    private val catalogue = listOf(
        s("lwn", "linux", "opensource", featured = true),
        s("phoronix", "linux", "hardware"),
        s("guardian", "news", uk = true),
        s("hackaday", "hardware", "maker"),
        s("rust", "programming"),
    )

    @Test fun withNoSubscriptionsFeaturedFeedsComeFirstInCatalogueOrder() {
        val r = FeedSuggester(catalogue + s("bbc", "news", uk = true, featured = true)).suggest(emptyList(), emptyList(), emptySet(), limit = 2)
        assertEquals(listOf("lwn", "bbc"), names(r))
    }

    @Test fun neverSuggestsWhatYouAlreadyFollow() {
        val r = FeedSuggester(catalogue).suggest(listOf(sub("https://lwn.example/feed")), emptyList(), emptySet())
        assertEquals(false, "lwn" in names(r))
    }

    @Test fun followedFeedsMatchIgnoringSchemeWwwAndTrailingSlash() {
        val c = listOf(s("a", feed = "https://www.a.example/rss/"), s("b", featured = true))
        assertEquals(listOf("b"), names(FeedSuggester(c).suggest(listOf(sub("http://a.example/rss")), emptyList(), emptySet())))
    }

    @Test fun aSiteYouFollowIsNotSuggestedUnderAnotherFeedUrl() {
        val c = listOf(s("a", feed = "https://a.example/atom.xml", site = "https://a.example/"), s("b", featured = true))
        val r = FeedSuggester(c).suggest(listOf(sub("https://a.example/rss", site = "https://www.a.example/")), emptyList(), emptySet())
        assertEquals(listOf("b"), names(r))
    }

    @Test fun dismissedFeedsAreLeftOut() {
        val r = FeedSuggester(catalogue).suggest(emptyList(), emptyList(), setOf("https://lwn.example/feed/"))
        assertEquals(false, "lwn" in names(r))
    }

    @Test fun feedsSharingTagsWithYourSubscriptionsRankFirst() {
        val r = FeedSuggester(catalogue).suggest(listOf(sub("https://lwn.example/feed")), emptyList(), emptySet())
        assertEquals("phoronix", r.first().title) // shares "linux"
    }

    @Test fun folderNamesCountAsInterests() {
        val r = FeedSuggester(catalogue).suggest(listOf(sub("https://elsewhere.example/rss")), listOf(Folder(1, "Hardware")), emptySet())
        assertEquals(setOf("phoronix", "hackaday"), names(r).take(2).toSet())
    }

    @Test fun moreSharedTagsScoreHigher() {
        val c = listOf(s("one", "linux"), s("two", "linux", "opensource"), s("seed", "linux", "opensource"))
        val r = FeedSuggester(c).suggest(listOf(sub("https://seed.example/feed")), emptyList(), emptySet())
        assertEquals(listOf("two", "one"), names(r))
    }

    @Test fun ukFeedsWinTiesThenAlphabetical() {
        val c = listOf(s("zeta", "linux", uk = true), s("alpha", "linux"), s("beta", "linux", uk = true), s("seed", "linux"))
        val r = FeedSuggester(c).suggest(listOf(sub("https://seed.example/feed")), emptyList(), emptySet())
        assertEquals(listOf("beta", "zeta", "alpha"), names(r))
    }

    @Test fun matchesAreFollowedByFeaturedFeedsToFillTheList() {
        val r = FeedSuggester(catalogue).suggest(listOf(sub("https://phoronix.example/feed")), emptyList(), emptySet())
        assertEquals(listOf("lwn"), names(r).take(1)) // linux match, which is also featured
        assertEquals(false, "rust" in names(r)) // unrelated and not featured
    }

    @Test fun listIsLimited() {
        assertEquals(2, FeedSuggester(catalogue.map { it.copy(featured = true) }).suggest(emptyList(), emptyList(), emptySet(), limit = 2).size)
    }

    @Test fun anEmptyCatalogueSuggestsNothing() {
        assertEquals(emptyList<FeedSuggestion>(), FeedSuggester(emptyList()).suggest(listOf(sub("https://x.example/f")), emptyList(), emptySet()))
    }
}
