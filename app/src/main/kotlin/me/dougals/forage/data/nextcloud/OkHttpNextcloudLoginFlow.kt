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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import me.dougals.forage.domain.nextcloud.LoginFlowSession
import me.dougals.forage.domain.nextcloud.NextcloudCredentials
import me.dougals.forage.domain.nextcloud.NextcloudLoginFlow
import java.io.IOException

class OkHttpNextcloudLoginFlow(
    private val client: OkHttpClient,
    private val userAgent: String = "Forage (Android)", // the device name shown in Devices & sessions
    private val allowInsecure: Boolean = false,
) : NextcloudLoginFlow {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun start(serverUrl: String): LoginFlowSession {
        val base = serverUrl.trim().trimEnd('/')
        val server = secure("$base/", host = null)
        val body = call(Request.Builder().url(server.resolve("index.php/login/v2")!!).post(ByteArray(0).toRequestBody()))
            ?: throw IOException("Nextcloud did not start a login")
        val start = json.decodeFromString<StartResponse>(body)
        return LoginFlowSession(
            serverUrl = base,
            loginUrl = secure(start.login, server.host).toString(),
            pollEndpoint = secure(start.poll.endpoint, server.host).toString(),
            pollToken = start.poll.token,
        )
    }

    override suspend fun poll(session: LoginFlowSession): NextcloudCredentials? {
        val form = FormBody.Builder().add("token", session.pollToken).build()
        val body = call(Request.Builder().url(session.pollEndpoint).post(form), notFoundIsNull = true) ?: return null
        val result = json.decodeFromString<PollResponse>(body)
        // Keep the URL the user typed: the server's idea of itself can be wrong behind a proxy.
        return NextcloudCredentials(session.serverUrl, result.loginName, result.appPassword, fromLoginFlow = true)
    }

    override suspend fun revoke(credentials: NextcloudCredentials) {
        val server = secure(credentials.serverUrl.trimEnd('/') + "/", host = null)
        call(
            Request.Builder().url(server.resolve("ocs/v2.php/core/apppassword")!!).delete()
                .header("Authorization", Credentials.basic(credentials.username, credentials.appPassword))
                .header("OCS-APIRequest", "true"),
        )
    }

    private suspend fun call(request: Request.Builder, notFoundIsNull: Boolean = false): String? = withContext(Dispatchers.IO) {
        client.newCall(request.header("User-Agent", userAgent).build()).execute().use { r ->
            when {
                r.isSuccessful -> r.body.string()
                notFoundIsNull && r.code == 404 -> null
                else -> throw IOException("HTTP ${r.code}")
            }
        }
    }

    private fun secure(url: String, host: String?) = secureUrl(url, host, allowInsecure)

    @Serializable private data class StartResponse(val login: String, val poll: Poll)
    @Serializable private data class Poll(val token: String, val endpoint: String)
    @Serializable private data class PollResponse(val server: String, val loginName: String, val appPassword: String)
}

/**
 * Only ever talk HTTPS. A Nextcloud behind a TLS-terminating proxy without `overwriteprotocol` hands
 * out http:// links to itself; those are upgraded when they point back at [host], the server already in use.
 */
internal fun secureUrl(url: String, host: String?, allowInsecure: Boolean = false): HttpUrl {
    val parsed = try { url.toHttpUrl() } catch (e: IllegalArgumentException) { throw IOException("Invalid address: $url", e) }
    return when {
        parsed.isHttps || allowInsecure -> parsed
        host != null && parsed.host == host -> parsed.newBuilder().scheme("https").port(443).build()
        else -> throw IOException("Nextcloud must use https://")
    }
}
