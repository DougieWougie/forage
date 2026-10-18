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

import me.dougals.forage.domain.auth.SessionCapture
import me.dougals.forage.testutil.MemSessionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCaptureTest {
    @Test fun storesCookiesUnderTheHostOfTheLoginUrl() {
        val store = MemSessionStore()
        val capture = SessionCapture({ "sid=abc; theme=dark" }, store)
        assertTrue(capture.capture("https://lwn.net/Login/"))
        assertEquals("sid=abc; theme=dark", store.get("lwn.net"))
    }

    @Test fun noCookiesMeansNothingStored() {
        val store = MemSessionStore()
        assertFalse(SessionCapture({ null }, store).capture("https://lwn.net/"))
        assertFalse(SessionCapture({ "" }, store).capture("https://lwn.net/"))
        assertNull(store.get("lwn.net"))
    }

    @Test fun invalidUrlIsRejected() {
        assertFalse(SessionCapture({ "a=b" }, MemSessionStore()).capture("not a url"))
    }
}
