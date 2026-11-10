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
package me.dougals.forage.data.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/** Failure messages end up on screen, and a feed URL can carry a secret token. */
class GuardedHttpMessagesTest {
    private val http = GuardedHttp(OkHttpClient())

    @Test fun anInvalidUrlIsReportedWithoutEchoingIt() = runBlocking {
        val e = assertThrows<IOException> { http.get("not a url with token=SECRET", allowPrivate = true) { } }
        assertEquals("Invalid URL", e.message)
    }

    @Test fun anUnsupportedRedirectDoesNotEchoTheLocation() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "ftp://host.example/secret-token").build())
            val e = assertThrows<IOException> { http.get(server.url("/").toString(), allowPrivate = true) { } }
            assertEquals(false, e.message!!.contains("secret-token"))
        }
    }
}
