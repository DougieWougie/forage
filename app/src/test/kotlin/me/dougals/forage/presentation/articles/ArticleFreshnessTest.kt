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
package me.dougals.forage.presentation.articles

import java.util.concurrent.TimeUnit
import me.dougals.forage.presentation.articles.ArticleFreshness.FRESH
import me.dougals.forage.presentation.articles.ArticleFreshness.OLD
import me.dougals.forage.presentation.articles.ArticleFreshness.WILTING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArticleFreshnessTest {
    private val now = 1_800_000_000_000L
    private fun ago(days: Long = 0, hours: Long = 0, millis: Long = 0) =
        now - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hours) - millis

    @Test fun justPublishedIsFresh() = assertEquals(FRESH, ArticleFreshness.of(now, now))

    @Test fun lastMillisecondOfFirstDayIsFresh() =
        assertEquals(FRESH, ArticleFreshness.of(ago(hours = 24, millis = -1), now))

    @Test fun exactlyOneDayIsWilting() = assertEquals(WILTING, ArticleFreshness.of(ago(days = 1), now))

    @Test fun fewDaysIsWilting() = assertEquals(WILTING, ArticleFreshness.of(ago(days = 4), now))

    @Test fun exactlyOneWeekIsStillWilting() = assertEquals(WILTING, ArticleFreshness.of(ago(days = 7), now))

    @Test fun overOneWeekIsOld() = assertEquals(OLD, ArticleFreshness.of(ago(days = 7, millis = 1), now))

    @Test fun monthsOldIsOld() = assertEquals(OLD, ArticleFreshness.of(ago(days = 200), now))

    @Test fun futureDateFromSkewedFeedIsFresh() = assertEquals(FRESH, ArticleFreshness.of(now + TimeUnit.DAYS.toMillis(2), now))

    @Test fun unknownDateIsNull() {
        assertNull(ArticleFreshness.of(0, now))
        assertNull(ArticleFreshness.of(-1, now))
    }
}
