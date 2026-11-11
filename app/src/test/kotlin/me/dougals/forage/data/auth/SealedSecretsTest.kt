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
import androidx.test.core.app.ApplicationProvider
import me.dougals.forage.data.nextcloud.PrefsCredentialStore
import me.dougals.forage.domain.nextcloud.NextcloudCredentials
import me.dougals.forage.testutil.FakeSecretCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SealedSecretsTest {
    private val cipher = FakeSecretCipher()
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("test-sealed", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun passwordIsNotStoredInPlaintext() {
        PrefsCredentialStore(prefs, cipher).save(NextcloudCredentials("https://c.example", "ann", "hunter2"))
        assertFalse(prefs.all.values.any { it.toString().contains("hunter2") })
    }

    @Test fun cookiesAreNotStoredInPlaintext() {
        PrefsSessionStore(prefs, cipher).save("lwn.net", "session=abc")
        assertFalse(prefs.all.values.any { it.toString().contains("session=abc") })
        assertEquals("session=abc", PrefsSessionStore(prefs, cipher).get("lwn.net"))
    }

    @Test fun plaintextFromAnOlderVersionIsReadThenSealed() {
        prefs.edit().putString("url", "https://c.example").putString("user", "ann").putString("pass", "hunter2").commit()
        val store = PrefsCredentialStore(prefs, cipher)
        assertEquals("hunter2", store.get()?.appPassword)
        assertFalse(prefs.getString("pass", "")!!.contains("hunter2"))
        assertEquals("hunter2", store.get()?.appPassword)
    }

    @Test fun plaintextCookieFromAnOlderVersionIsSealed() {
        prefs.edit().putString("lwn.net", "session=abc").commit()
        assertEquals("session=abc", PrefsSessionStore(prefs, cipher).get("lwn.net"))
        assertFalse(prefs.getString("lwn.net", "")!!.contains("session=abc"))
    }

    @Test fun aLostKeyReadsAsSignedOutAndDropsTheValue() {
        val store = PrefsCredentialStore(prefs, cipher)
        store.save(NextcloudCredentials("https://c.example", "ann", "hunter2"))
        cipher.lockedOut = true
        assertNull(store.get())
        assertFalse(prefs.contains("pass"))
        val sessions = PrefsSessionStore(prefs, cipher)
        cipher.lockedOut = false
        sessions.save("lwn.net", "a=1")
        cipher.lockedOut = true
        assertNull(sessions.get("lwn.net"))
    }
}
