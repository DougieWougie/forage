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
        assertEquals("Synced · 2 saved", UiText.Joined(listOf(UiText.Res(R.string.synced), UiText.Res(R.string.n_saved, listOf(2))), " · ").resolve(context))

    @Test fun argumentsMayThemselvesBeText() =
        assertEquals("Could not sync: unknown error", UiText.Res(R.string.sync_failed, listOf(UiText.Res(R.string.unknown_error))).resolve(context))
}
