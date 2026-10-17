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
package me.dougals.forage.presentation.reader

import android.content.Intent
import me.dougals.forage.testutil.article
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    @Test fun buildsActionSendWithTitleAndUrl() {
        val a = article(1).copy(title = "Great read", url = "https://example.com/p")
        val i = ShareIntents.send(a)
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("text/plain", i.type)
        assertEquals("Great read", i.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Great read\nhttps://example.com/p", i.getStringExtra(Intent.EXTRA_TEXT))
    }
}
