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

/** How stale an unread article is, shown on its card as a leaf that wilts and browns with age. */
enum class ArticleFreshness {
    FRESH, WILTING, OLD;

    companion object {
        private val FRESH_UNTIL_MS = TimeUnit.DAYS.toMillis(1)
        private val WILTING_UNTIL_MS = TimeUnit.DAYS.toMillis(7)

        /**
         * Null when [publishedAt] is unknown (feeds without a date store 0 or less), so callers do not claim freshness
         * they cannot know. A date in the future, from a skewed feed clock, counts as fresh.
         */
        fun of(publishedAt: Long, now: Long): ArticleFreshness? {
            if (publishedAt <= 0) return null
            val age = now - publishedAt
            return when {
                age < FRESH_UNTIL_MS -> FRESH
                age <= WILTING_UNTIL_MS -> WILTING
                else -> OLD
            }
        }
    }
}
