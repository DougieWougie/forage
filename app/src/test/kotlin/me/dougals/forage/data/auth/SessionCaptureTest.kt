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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCaptureTest {
    private class MemStore : SessionStore {
        val map = mutableMapOf<String, String>()
        override fun save(domain: String, cookieHeader: String) { map[domain.lowercase()] = cookieHeader }
        override fun get(domain: String) = map[domain.lowercase()]
        override fun remove(domain: String) { map.remove(domain.lowercase()) }
        override fun domains() = map.keys.sorted()
        override fun findForHost(host: String) = SessionStore.matchHost(map, host)
    }

    @Test fun storesCookiesUnderTheHostOfTheLoginUrl() {
        val store = MemStore()
        val capture = SessionCapture({ "sid=abc; theme=dark" }, store)
        assertTrue(capture.capture("https://lwn.net/Login/"))
        assertEquals("sid=abc; theme=dark", store.get("lwn.net"))
    }

    @Test fun noCookiesMeansNothingStored() {
        val store = MemStore()
        assertFalse(SessionCapture({ null }, store).capture("https://lwn.net/"))
        assertFalse(SessionCapture({ "" }, store).capture("https://lwn.net/"))
        assertNull(store.get("lwn.net"))
    }

    @Test fun invalidUrlIsRejected() {
        assertFalse(SessionCapture({ "a=b" }, MemStore()).capture("not a url"))
    }
}
