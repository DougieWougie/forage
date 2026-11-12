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
package me.dougals.forage.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.dougals.forage.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiTextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun resourceWithArguments() =
        assertEquals("Connected to https://c.example.com", UiText.Res(R.string.connected_to, listOf("https://c.example.com")).resolve(context))

    @Test fun pluralFollowsTheCount() {
        assertEquals("1 new article", UiText.Plural(R.plurals.new_articles, 1).resolve(context))
        assertEquals("18 new articles", UiText.Plural(R.plurals.new_articles, 18).resolve(context))
    }

    @Test fun rawTextPassesThrough() = assertEquals("offline", UiText.Raw("offline").resolve(context))

    @Test fun partsAreJoinedWithTheSeparator() =
        assertEquals("Synced · 2 saved", UiText.Joined(listOf(UiText.Res(R.string.synced), UiText.Plural(R.plurals.n_saved, 2)), " · ").resolve(context))

    @Test fun argumentsMayThemselvesBeText() =
        assertEquals("Could not sync: unknown error", UiText.Res(R.string.sync_failed, listOf(UiText.Res(R.string.unknown_error))).resolve(context))

    @Test fun anErrorMessageNeverShowsAWebAddress() {
        val e = java.io.IOException("Failed to load https://feeds.example.com/rss?token=SECRET123 (timeout)")
        val text = e.toUiText(R.string.unknown_error) as UiText.Raw
        assertEquals("Failed to load <link> (timeout)", text.value)
    }

    @Test fun statusAndShortReasonSurvive() =
        assertEquals(UiText.Raw("HTTP 403"), java.io.IOException("HTTP 403").toUiText(R.string.unknown_error))

    @Test fun aMessageThatWasOnlyAnAddressStillSaysSomething() =
        assertEquals(UiText.Raw("<link>"), java.io.IOException("http://a.example/x").toUiText(R.string.unknown_error))

    @Test fun noMessageFallsBack() = assertEquals(UiText.Res(R.string.unknown_error), RuntimeException().toUiText(R.string.unknown_error))

    @Test fun offlineAndMistypedAddressHaveTheirOwnWording() {
        val cause = java.net.UnknownHostException("a.example")
        assertEquals(UiText.Res(R.string.error_offline), me.dougals.forage.domain.model.OfflineException(cause).toUiText(R.string.refresh_failed))
        assertEquals(UiText.Res(R.string.error_host_not_found), me.dougals.forage.domain.model.HostNotFoundException(cause).toUiText(R.string.add_feed_failed))
    }
}
