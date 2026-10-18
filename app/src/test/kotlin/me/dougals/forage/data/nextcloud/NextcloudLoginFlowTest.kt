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
package me.dougals.forage.data.nextcloud

import me.dougals.forage.domain.nextcloud.LoginFlowSession
import me.dougals.forage.domain.nextcloud.NextcloudCredentials
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class NextcloudLoginFlowTest {
    private lateinit var server: MockWebServer
    @BeforeEach fun up() { server = MockWebServer().apply { start() } }
    @AfterEach fun down() { server.close() }

    private fun flow(allowInsecure: Boolean = true) = OkHttpNextcloudLoginFlow(OkHttpClient(), allowInsecure = allowInsecure)
    private fun base(path: String = "/") = server.url(path).toString().trimEnd('/')

    private fun startJson(login: String = server.url("/login/v2/flow/abc").toString(), endpoint: String = server.url("/login/v2/poll").toString()) =
        """{"poll":{"token":"poll-secret","endpoint":"$endpoint"},"login":"$login"}"""

    @Test fun startPostsToLoginV2AndReturnsTheBrowserAndPollAddresses() = runTest {
        server.enqueue(MockResponse.Builder().body(startJson()).build())
        val session = flow().start(base("/nextcloud") + "/")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/nextcloud/index.php/login/v2", req.url.encodedPath)
        assertEquals("Forage (Android)", req.headers["User-Agent"])
        assertEquals(base("/nextcloud"), session.serverUrl)
        assertEquals(server.url("/login/v2/flow/abc").toString(), session.loginUrl)
        assertEquals(server.url("/login/v2/poll").toString(), session.pollEndpoint)
        assertEquals("poll-secret", session.pollToken)
        assertFalse(session.toString().contains("poll-secret"))
    }

    @Test fun pollIsNullUntilGrantedThenReturnsAnAppPasswordForTheTypedServer() = runTest {
        server.enqueue(MockResponse.Builder().body(startJson()).build())
        server.enqueue(MockResponse.Builder().code(404).body("[]").build())
        server.enqueue(MockResponse.Builder().body("""{"server":"http://internal:8080","loginName":"dougie","appPassword":"app-pw"}""").build())
        val f = flow()
        val session = f.start(base())
        server.takeRequest()
        assertNull(f.poll(session))
        val req = server.takeRequest()
        assertEquals("/login/v2/poll", req.url.encodedPath)
        assertEquals("token=poll-secret", req.body?.utf8())
        assertEquals(NextcloudCredentials(base(), "dougie", "app-pw", fromLoginFlow = true), f.poll(session))
    }

    @Test fun serverErrorsWhilePollingAreFailures() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        assertThrows<IOException> { flow().poll(LoginFlowSession(base(), "x", server.url("/poll").toString(), "t")) }
    }

    @Test fun revokeDeletesTheAppPasswordWithItsOwnCredentials() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"ocs":{}}""").build())
        flow().revoke(NextcloudCredentials(base("/nextcloud"), "dougie", "app-pass", fromLoginFlow = true))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/nextcloud/ocs/v2.php/core/apppassword", req.url.encodedPath)
        assertEquals("Basic ZG91Z2llOmFwcC1wYXNz", req.headers["Authorization"])
        assertEquals("true", req.headers["OCS-APIRequest"])
    }

    @Test fun plainHttpServerIsRefusedByDefault() = runTest {
        assertThrows<IOException> { flow(allowInsecure = false).start("http://cloud.example.com") }
        assertEquals(0, server.requestCount)
    }

    @Test fun httpLinksToTheSameHostAreUpgradedAndOtherHostsRefused() {
        assertEquals("https://cloud.example.com/login/v2/flow/abc", secureUrl("http://cloud.example.com/login/v2/flow/abc", "cloud.example.com").toString())
        assertEquals("https://cloud.example.com/x", secureUrl("https://cloud.example.com/x", null).toString())
        assertThrows<IOException> { secureUrl("http://elsewhere.example/login", "cloud.example.com") }
        assertThrows<IOException> { secureUrl("http://cloud.example.com/", null) }
        assertThrows<IOException> { secureUrl("not a url", "cloud.example.com") }
    }
}
