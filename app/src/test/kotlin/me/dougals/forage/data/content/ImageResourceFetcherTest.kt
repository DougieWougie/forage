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

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageResourceFetcherTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
    private fun fetcher(maxBytes: Long = 1_000_000) = ImageResourceFetcher(OkHttpClient(), maxBytes)

    private fun image(contentType: String, body: ByteArray = png) =
        MockResponse.Builder().addHeader("Content-Type", contentType).body(Buffer().write(body)).build()

    @Test fun servesImagesWithTheirMimeType() = runTest {
        MockWebServer().use { s ->
            s.start(); s.enqueue(image("image/png; charset=binary"))
            val img = fetcher().fetch(s.url("/a.png").toString(), allowPrivate = true).getOrThrow()
            assertEquals("image/png", img.mimeType)
            assertArrayEquals(png, img.bytes)
        }
    }

    @Test fun nothingButImagesIsServed() = runTest {
        MockWebServer().use { s ->
            s.start()
            listOf("text/html", "application/javascript", "text/css", "").forEach { type ->
                s.enqueue(image(type, "<script>x()</script>".toByteArray()))
                assertTrue(fetcher().fetch(s.url("/x").toString(), allowPrivate = true).isFailure, type)
            }
        }
    }

    @Test fun localNetworkIsRefusedUnlessAllowed() = runTest {
        MockWebServer().use { s ->
            s.start(); s.enqueue(image("image/png"))
            assertTrue(fetcher().fetch(s.url("/a.png").toString(), allowPrivate = false).isFailure)
            assertEquals(0, s.requestCount)
            val literal = s.url("/a.png").newBuilder().host("127.0.0.1").build().toString()
            assertTrue(fetcher().fetch(literal, allowPrivate = false).isFailure)
            assertEquals(0, s.requestCount)
            assertTrue(fetcher().fetch(s.url("/a.png").toString(), allowPrivate = true).isSuccess)
        }
    }

    @Test fun oversizedImagesAreRefused() = runTest {
        MockWebServer().use { s ->
            s.start(); s.enqueue(image("image/png", ByteArray(5_000)))
            assertTrue(fetcher(maxBytes = 1_000).fetch(s.url("/big").toString(), allowPrivate = true).isFailure)
        }
    }

    @Test fun httpErrorsAreFailures() = runTest {
        MockWebServer().use { s ->
            s.start(); s.enqueue(MockResponse.Builder().code(404).build())
            assertTrue(fetcher().fetch(s.url("/missing").toString(), allowPrivate = true).isFailure)
        }
    }
}
