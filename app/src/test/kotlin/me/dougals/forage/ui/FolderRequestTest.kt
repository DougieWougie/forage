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
package me.dougals.forage.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRequestTest {
    @Test fun readsTheCategoriesTheDigestCarried() {
        val intent = Intent().putExtra(EXTRA_DIGEST_FOLDERS, longArrayOf(4, -1))
        assertEquals(setOf(4L, -1L), intent.folderRequest()?.folderIds)
    }

    @Test fun anOrdinaryLaunchIsNotARequest() {
        assertNull(Intent(Intent.ACTION_MAIN).folderRequest())
        assertNull(Intent().putExtra(EXTRA_DIGEST_FOLDERS, longArrayOf()).folderRequest())
    }

    @Test fun eachReadIsADistinctRequest() {
        val intent = Intent().putExtra(EXTRA_DIGEST_FOLDERS, longArrayOf(1))
        assertNotSame(intent.folderRequest(), intent.folderRequest())
    }
}
