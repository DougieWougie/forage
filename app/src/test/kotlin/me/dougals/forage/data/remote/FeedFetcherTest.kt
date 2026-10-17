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
package me.dougals.forage.data.remote

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.auth.SessionCookieInterceptor
import me.dougals.forage.data.auth.SessionStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedFetcherTest {
    private val rss = """<rss version="2.0"><channel><title>Locked</title>
        <item><title>Paid</title><link>https://lwn.net/a</link></item></channel></rss>"""

    private fun withServer(block: suspend (MockWebServer, FeedFetcher) -> Unit) = runTest {
        val store = object : SessionStore {
            val map = mutableMapOf("localhost" to "sid=abc")
            override fun save(domain: String, cookieHeader: String) { map[domain] = cookieHeader }
            override fun get(domain: String) = map[domain]
            override fun remove(domain: String) { map.remove(domain) }
            override fun domains() = map.keys.sorted()
            override fun findForHost(host: String) = SessionStore.matchHost(map, host)
        }
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(SessionCookieInterceptor(store, allowInsecure = true)).build()
        MockWebServer().use { server ->
            server.start()
            block(server, FeedFetcher(client, FeedParser(), allowLocalNetwork = { true }))
        }
    }

    @Test fun fetchesParsesAndSendsSessionCookie() = withServer { server, fetcher ->
        server.enqueue(MockResponse.Builder().body(rss).build())
        val feed = fetcher.fetch(server.url("/rss").toString())
        assertEquals("Locked", feed.title)
        assertEquals("sid=abc", server.takeRequest().headers["Cookie"])
    }

    @Test fun httpErrorBecomesFetchException() = withServer { server, fetcher ->
        server.enqueue(MockResponse.Builder().code(403).build())
        val e = assertThrows(FeedFetchException::class.java) {
            kotlinx.coroutines.runBlocking { fetcher.fetch(server.url("/rss").toString()) }
        }
        assertEquals(403, e.httpCode)
    }

    @Test fun loopbackIsRefusedByDefaultAndNothingIsSent() = runTest {
        MockWebServer().use { server ->
            server.start()
            val fetcher = FeedFetcher(OkHttpClient(), FeedParser())
            val e = assertThrows(FeedFetchException::class.java) {
                kotlinx.coroutines.runBlocking { fetcher.fetch(server.url("/rss").toString()) }
            }
            assertEquals(0, server.requestCount)
            assertEquals(null, e.httpCode)
        }
    }

    @Test fun ipLiteralAndRedirectToInternalHostsAreRefused() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://192.168.1.1/admin").build())
            val fetcher = FeedFetcher(
                OkHttpClient(), FeedParser(),
                addressPolicy = { it.isLoopbackAddress || me.dougals.forage.data.net.isPublicAddress(it) },
            )
            val e = assertThrows(FeedFetchException::class.java) {
                kotlinx.coroutines.runBlocking { fetcher.fetch(server.url("/rss").toString()) }
            }
            assertEquals(true, e.message!!.contains("non-public"))
            assertEquals(1, server.requestCount)
            assertThrows(FeedFetchException::class.java) {
                kotlinx.coroutines.runBlocking { fetcher.fetch("http://169.254.169.254/latest/meta-data/") }
            }
        }
    }

    @Test fun redirectsToTheSameServerAreFollowed() = withServer { server, fetcher ->
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/real").build())
        server.enqueue(MockResponse.Builder().body(rss).build())
        assertEquals("Locked", fetcher.fetch(server.url("/old").toString()).title)
    }

    @Test fun oversizedFeedsAreRejected() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("x".repeat(5_000)).build())
            val fetcher = FeedFetcher(OkHttpClient(), FeedParser(), allowLocalNetwork = { true }, maxBytes = 1_000)
            assertThrows(FeedFetchException::class.java) {
                kotlinx.coroutines.runBlocking { fetcher.fetch(server.url("/rss").toString()) }
            }
        }
    }

    @Test fun nonHttpSchemesAreRejected() = runTest {
        val fetcher = FeedFetcher(OkHttpClient(), FeedParser(), allowLocalNetwork = { true })
        listOf("file:///etc/passwd", "ftp://example.com/feed", "javascript:alert(1)").forEach { u ->
            assertThrows(FeedFetchException::class.java) { kotlinx.coroutines.runBlocking { fetcher.fetch(u) } }
        }
    }
}
