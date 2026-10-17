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
package me.dougals.forage.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ThumbnailFetcherTest {
    @get:Rule val tmp = TemporaryFolder()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun fetcher(url: String, cache: DiskCache, networkPolicy: CachePolicy = CachePolicy.ENABLED) =
        ThumbnailFetcher(
            url,
            Options(ApplicationProvider.getApplicationContext<Context>(), networkCachePolicy = networkPolicy),
            ImageResourceFetcher(OkHttpClient()),
            allowPrivate = { true },
            diskCache = { cache },
        )

    @Test fun secondFetchComesFromDiskAndDisabledNetworkStillServesIt() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(png)).build())
            val cache = DiskCache.Builder().directory(tmp.newFolder().toOkioPath()).maxSizeBytes(1_000_000).build()
            val url = server.url("/a.png").toString()

            fetcher(url, cache).fetch()
            fetcher(url, cache, CachePolicy.DISABLED).fetch()
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun disabledNetworkWithColdCacheFails() = runTest {
        val cache = DiskCache.Builder().directory(tmp.newFolder().toOkioPath()).maxSizeBytes(1_000_000).build()
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { fetcher("https://example.com/a.png", cache, CachePolicy.DISABLED).fetch() }
        }
    }

    @Test fun nonImageResponsesAreRejected() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html>").build())
            val cache = DiskCache.Builder().directory(tmp.newFolder().toOkioPath()).maxSizeBytes(1_000_000).build()
            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking { fetcher(server.url("/x").toString(), cache).fetch() }
            }
        }
    }
}
