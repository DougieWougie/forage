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
package me.dougals.forage.data.notify

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.dougals.forage.ForageApp
import me.dougals.forage.MainActivity
import me.dougals.forage.domain.model.UNCATEGORISED_FOLDER_ID
import me.dougals.forage.domain.notify.DigestLine
import me.dougals.forage.ui.EXTRA_DIGEST_FOLDERS
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidDigestNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifier = AndroidDigestNotifier(context)

    private val tech = DigestLine("Tech", 5, 10, listOf("Ars", "LWN"))
    private val blogs = DigestLine(null, 2, UNCATEGORISED_FOLDER_ID, listOf("Blog"))

    @Before fun background() { ForageApp.appVisible = false }
    @After fun reset() { ForageApp.appVisible = false }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single()
    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    @Test fun tappingOpensTheActivityWithTheDigestsCategories() {
        notifier.post(listOf(tech, blogs))
        val intent = shadowOf(posted().contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertArrayEquals(longArrayOf(10, UNCATEGORISED_FOLDER_ID), intent.getLongArrayExtra(EXTRA_DIGEST_FOLDERS))
    }

    @Test fun aSecondDigestReplacesTheFirstAndItsCategories() {
        notifier.post(listOf(tech))
        notifier.post(listOf(blogs))
        assertArrayEquals(longArrayOf(UNCATEGORISED_FOLDER_ID), shadowOf(posted().contentIntent).savedIntent.getLongArrayExtra(EXTRA_DIGEST_FOLDERS))
    }

    @Test fun aSingleCategoryNamesItsFeeds() {
        notifier.post(listOf(tech))
        assertEquals("5 new articles", posted().text(Notification.EXTRA_TITLE))
        assertEquals("Tech · Ars, LWN", posted().text(Notification.EXTRA_TEXT))
    }

    @Test fun manyFeedsAreTrimmedToTheBusiestTwo() {
        notifier.post(listOf(tech.copy(feeds = listOf("A", "B", "C", "D"))))
        assertEquals("Tech · A, B and 2 more", posted().text(Notification.EXTRA_TEXT))
    }

    @Test fun severalCategoriesAreSummarisedWithCountsAndExpandToTheirFeeds() {
        notifier.post(listOf(tech, blogs))
        assertEquals("7 new articles", posted().text(Notification.EXTRA_TITLE))
        assertEquals("Tech 5 · Uncategorized 2", posted().text(Notification.EXTRA_TEXT))
        assertEquals("Tech · 5 — Ars, LWN\nUncategorized · 2 — Blog", posted().text(Notification.EXTRA_BIG_TEXT))
    }

    @Test fun aLoneCategoryQuotesUpToThreeHeadlinesBelowItsFeeds() {
        notifier.post(listOf(tech.copy(headlines = listOf("One", "Two", "Three", "Four"))))
        assertEquals("Tech · 5 — Ars, LWN\n• One\n• Two\n• Three", posted().text(Notification.EXTRA_BIG_TEXT))
    }

    @Test fun categoriesSharingTheNotificationQuoteTwoHeadlinesEach() {
        notifier.post(listOf(tech.copy(headlines = listOf("One", "Two", "Three")), blogs.copy(headlines = listOf("Solo"))))
        assertEquals(
            "Tech · 5 — Ars, LWN\n• One\n• Two\nUncategorized · 2 — Blog\n• Solo",
            posted().text(Notification.EXTRA_BIG_TEXT),
        )
    }

    @Test fun headlinesAreTidiedToOneLine() {
        notifier.post(listOf(tech.copy(headlines = listOf("  Spread \n over\t lines  "))))
        assertEquals("Tech · 5 — Ars, LWN\n• Spread over lines", posted().text(Notification.EXTRA_BIG_TEXT))
    }

    @Test fun aCategoryWithNoFeedNamesJustShowsItself() {
        notifier.post(listOf(tech.copy(feeds = emptyList())))
        assertEquals("Tech", posted().text(Notification.EXTRA_TEXT))
    }

    @Test fun staysQuietWhileTheAppIsOnScreen() {
        ForageApp.appVisible = true
        notifier.post(listOf(tech))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun clearRemovesTheDigest() {
        notifier.post(listOf(tech))
        notifier.clear()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertNull(shadowOf(manager).getNotification(1))
    }
}
