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
package me.dougals.forage.data.nextcloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialStoreTest {
    private fun store(): CredentialStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test-nc", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PrefsCredentialStore(prefs)
    }

    @Test fun emptyByDefault() = assertNull(store().get())

    @Test fun roundTripsAndClears() {
        val s = store()
        val c = NextcloudCredentials("https://cloud.example.com", "dougie", "pw")
        s.save(c)
        assertEquals(c, s.get())
        s.clear()
        assertNull(s.get())
    }
}
