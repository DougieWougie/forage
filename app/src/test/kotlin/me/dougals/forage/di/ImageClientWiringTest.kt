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
package me.dougals.forage.di

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.auth.SessionCookieInterceptor
import me.dougals.forage.data.content.ImageResourceFetcher
import me.dougals.forage.testutil.MemSessionStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Article HTML is untrusted, so image requests must never carry the user's session cookies. */
class ImageClientWiringTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private suspend fun cookieSeenBy(client: OkHttpClient): String? = MockWebServer().use { s ->
        s.start()
        s.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(png)).build())
        ImageResourceFetcher(client).fetch(s.url("/a.png").toString(), allowPrivate = true).getOrThrow()
        s.takeRequest().headers["Cookie"]
    }

    @Test fun controlSessionClientDoesAttachCookies() = runTest {
        val store = MemSessionStore().apply { save("localhost", "sid=secret") }
        val sessionClient = OkHttpClient.Builder()
            .addNetworkInterceptor(SessionCookieInterceptor(store, allowInsecure = true)).build()
        assertEquals("sid=secret", cookieSeenBy(sessionClient))
    }

    @Test fun apiClientUsedForImagesSendsNoCookies() = runTest {
        assertNull(cookieSeenBy(AppModule.apiClient()))
    }

    @Test fun apiClientHasNoInterceptorsOrCookieJar() {
        val c = AppModule.apiClient()
        assertTrue(c.interceptors.isEmpty())
        assertTrue(c.networkInterceptors.isEmpty())
        assertEquals(CookieJar.NO_COOKIES, c.cookieJar)
    }

    @Test fun imageFetcherProviderRequestsTheApiClientQualifier() {
        val m = AppModule::class.java.declaredMethods.single { it.name == "imageFetcher" }
        val quals = m.parameterAnnotations.single().map { it.annotationClass }
        assertTrue(ApiClient::class in quals, "image fetcher must take @ApiClient, got $quals")
        assertTrue(WebClient::class !in quals)
    }
}
