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
package me.dougals.forage.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPrefsTest {
    private fun prefs(name: String): SharedPreferences {
        val p = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        p.edit().clear().commit()
        return p
    }

    @Test fun everyLegacyEntryIsCopiedWhateverItsTypeThenTheLegacyFileIsDiscarded() {
        val legacy = prefs("legacy").also {
            it.edit().putString("url", "https://c").putBoolean("flow", true).putLong("n", 7).putInt("i", 3)
                .putFloat("f", 1.5f).putStringSet("s", setOf("a", "b")).commit()
        }
        val target = prefs("target")
        var discarded = 0
        migrateLegacyPrefs(target, { legacy }) { discarded++ }
        assertEquals(legacy.all, target.all)
        assertEquals(1, discarded)
    }

    @Test fun anUnreadableLegacyFileLeavesTheTargetEmptyAndIsStillDiscarded() {
        val target = prefs("target2")
        var discarded = 0
        migrateLegacyPrefs(target, { throw java.security.GeneralSecurityException("key gone") }) { discarded++ }
        assertTrue(target.all.isEmpty())
        assertEquals(1, discarded)
    }
}
