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
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class GuardedHttpTest {
    private val http = GuardedHttp(OkHttpClient())

    @Test fun aTimeoutAbandonsAServerThatNeverAnswers() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).body("late").build())
            val started = System.nanoTime()
            val result = withTimeoutOrNull(300) { http.get(server.url("/").toString(), allowPrivate = true) { it.body.string() } }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertNull(result)
            assertTrue(elapsedMs < 3_000, "the caller was held for $elapsedMs ms")
        }
    }

    @Test fun aTimeoutAbandonsABodyThatStalls() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("x".repeat(2_000)).throttleBody(100, 1, TimeUnit.SECONDS).build())
            val started = System.nanoTime()
            val result = withTimeoutOrNull(300) { http.get(server.url("/").toString(), allowPrivate = true) { it.body.string() } }
            assertNull(result)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
        }
    }

    @Test fun followsRedirectsAndReturnsTheHandlerResult() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/final").build())
            server.enqueue(MockResponse.Builder().body("done").build())
            assertEquals("done", http.get(server.url("/").toString(), allowPrivate = true) { it.body.string() })
            assertEquals("/final", server.takeRequest().let { server.takeRequest() }.url.encodedPath)
        }
    }

    @Test fun non2xxIsAnHttpStatusException() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(503).build())
            val e = assertThrows<HttpStatusException> { http.get(server.url("/").toString(), allowPrivate = true) { } }
            assertEquals(503, e.code)
        }
    }
}
