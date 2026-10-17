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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NextcloudNotConfiguredException(cause: Throwable? = null) : Exception("Nextcloud is not configured", cause)

/**
 * [appPassword] is a Nextcloud app password, never the account password. [fromLoginFlow] marks one the
 * server created for Forage, which is safe to revoke on disconnect; one the user pasted in may be shared.
 */
data class NextcloudCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val fromLoginFlow: Boolean = false,
)

interface CredentialStore {
    fun get(): NextcloudCredentials?
    fun save(credentials: NextcloudCredentials)
    fun clear()
}

class PrefsCredentialStore(private val prefs: SharedPreferences) : CredentialStore {
    override fun get(): NextcloudCredentials? {
        val url = prefs.getString(URL, null) ?: return null
        val user = prefs.getString(USER, null) ?: return null
        val pass = prefs.getString(PASS, null) ?: return null
        return NextcloudCredentials(url, user, pass, prefs.getBoolean(FLOW, false))
    }

    override fun save(credentials: NextcloudCredentials) = prefs.edit {
        putString(URL, credentials.serverUrl)
        putString(USER, credentials.username)
        putString(PASS, credentials.appPassword)
        putBoolean(FLOW, credentials.fromLoginFlow)
    }

    override fun clear() = prefs.edit { clear() }

    private companion object { const val URL = "url"; const val USER = "user"; const val PASS = "pass"; const val FLOW = "flow" }
}

/** Keystore-backed; needs a device/emulator, so tests use [PrefsCredentialStore] with plain prefs. */
fun encryptedCredentialStore(context: Context): CredentialStore {
    val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return PrefsCredentialStore(
        EncryptedSharedPreferences.create(
            context, "forage_nextcloud", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )
}

fun interface NextcloudApiProvider {
    /** @throws NextcloudNotConfiguredException when no (valid) credentials are stored. */
    fun api(): NextcloudApi
}

/** Basic auth, attached per hop and only to the configured host, and only over HTTPS unless allowed. */
internal class BasicAuthInterceptor(
    private val host: String,
    private val header: String,
    private val allowInsecure: Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.host != host || !(req.url.isHttps || allowInsecure)) return chain.proceed(req)
        return chain.proceed(req.newBuilder().header("Authorization", header).build())
    }
}

class RetrofitNextcloudApiProvider(
    private val credentials: CredentialStore,
    private val baseClient: OkHttpClient,
    private val allowInsecure: Boolean = false,
) : NextcloudApiProvider {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private var cached: Pair<NextcloudCredentials, NextcloudApi>? = null

    @Synchronized
    override fun api(): NextcloudApi {
        val c = credentials.get() ?: throw NextcloudNotConfiguredException()
        cached?.takeIf { it.first == c }?.let { return it.second }
        val base = try {
            (c.serverUrl.trim().trimEnd('/') + "/index.php/apps/news/api/v1-3/").toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw NextcloudNotConfiguredException(e)
        }
        val client = baseClient.newBuilder()
            .addNetworkInterceptor(BasicAuthInterceptor(base.host, Credentials.basic(c.username, c.appPassword), allowInsecure))
            .build()
        val api = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NextcloudApi::class.java)
        cached = c to api
        return api
    }
}

interface SyncStateStore {
    var lastModified: Long
    /** Wall-clock time of the last successful pull, 0 if never. */
    var lastSyncedAt: Long
}

class PrefsSyncStateStore(private val prefs: SharedPreferences) : SyncStateStore {
    override var lastModified: Long
        get() = prefs.getLong("lastModified", 0)
        set(v) = prefs.edit { putLong("lastModified", v) }
    override var lastSyncedAt: Long
        get() = prefs.getLong("lastSyncedAt", 0)
        set(v) = prefs.edit { putLong("lastSyncedAt", v) }
}
