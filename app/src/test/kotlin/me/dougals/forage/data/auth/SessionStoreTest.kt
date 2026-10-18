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

import me.dougals.forage.domain.auth.SessionStore
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionStoreTest {
    private fun store(): SessionStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test-sessions", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PrefsSessionStore(prefs)
    }

    @Test fun savesAndReadsPerDomain() {
        val s = store()
        s.save("lwn.net", "a=1; b=2")
        s.save("example.com", "c=3")
        assertEquals("a=1; b=2", s.get("lwn.net"))
        assertEquals("c=3", s.get("example.com"))
    }

    @Test fun domainKeysAreCaseInsensitive() {
        val s = store()
        s.save("LWN.net", "a=1")
        assertEquals("a=1", s.get("lwn.NET"))
    }

    @Test fun removeForgetsDomain() {
        val s = store()
        s.save("lwn.net", "a=1")
        s.remove("lwn.net")
        assertNull(s.get("lwn.net"))
    }

    @Test fun listsDomainsWithSessions() {
        val s = store()
        s.save("b.com", "x=1")
        s.save("a.com", "x=1")
        assertEquals(listOf("a.com", "b.com"), s.domains())
    }

    @Test fun findForHostIsExactAndNeverWidensToSubdomainsOrParents() {
        val s = store()
        s.save("lwn.net", "a=1")
        assertEquals("a=1", s.findForHost("lwn.net"))
        assertNull(s.findForHost("www.lwn.net"))
        assertNull(s.findForHost("user-content.lwn.net"))
        assertEquals("a=1", s.findForHost("LWN.net"))
        assertNull(s.findForHost("evil-lwn.net"))
        assertNull(s.findForHost("net"))
    }
}
