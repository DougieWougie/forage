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
package me.dougals.forage.testutil

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.dougals.forage.domain.auth.SessionStore
import me.dougals.forage.domain.nextcloud.CredentialStore
import me.dougals.forage.domain.nextcloud.NextcloudCredentials
import me.dougals.forage.domain.model.Article
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}

suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) { val item = awaitItem(); if (predicate(item)) return item }
}

fun article(id: Long, read: Boolean = false, saved: Boolean = false, feedId: Long = 1, publishedAt: Long = 0) = Article(
    id = id, feedId = feedId, guid = "g$id", title = "Title $id", url = "https://example.com/$id",
    author = null, summary = null, contentHtml = null, publishedAt = publishedAt, isRead = read, isSaved = saved, offlineHtml = null,
)

class MemCredentialStore(var value: NextcloudCredentials? = null) : CredentialStore {
    override fun get() = value
    override fun save(credentials: NextcloudCredentials) { value = credentials }
    override fun clear() { value = null }
}

class MemSessionStore : SessionStore {
    val map = mutableMapOf<String, String>()
    override fun save(domain: String, cookieHeader: String) { map[domain.lowercase()] = cookieHeader }
    override fun get(domain: String) = map[domain.lowercase()]
    override fun remove(domain: String) { map.remove(domain.lowercase()) }
    override fun domains() = map.keys.sorted()
    override fun findForHost(host: String) = SessionStore.matchHost(map, host)
}
