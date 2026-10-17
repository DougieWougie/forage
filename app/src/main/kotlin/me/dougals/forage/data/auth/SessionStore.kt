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
package me.dougals.forage.data.auth

import android.content.SharedPreferences

/** Per-domain session cookie headers ("a=1; b=2"), as captured from a WebView login. */
interface SessionStore {
    fun save(domain: String, cookieHeader: String)
    fun get(domain: String): String?
    fun remove(domain: String)
    fun domains(): List<String>
    /**
     * Cookies for a request host. Exact host match only: the WebView hands us a bare cookie string
     * without Domain/Path attributes, so we cannot tell host-only cookies from domain cookies and
     * must not widen them to other subdomains. Log in from the host the feed is served from.
     */
    fun findForHost(host: String): String?

    companion object {
        fun matchHost(sessions: Map<String, String>, host: String): String? {
            return sessions[host.lowercase()]
        }
    }
}

/** Pass EncryptedSharedPreferences in production; plain prefs are fine in tests. */
class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override fun save(domain: String, cookieHeader: String) {
        prefs.edit().putString(domain.lowercase(), cookieHeader).apply()
    }

    override fun get(domain: String): String? = prefs.getString(domain.lowercase(), null)

    override fun remove(domain: String) {
        prefs.edit().remove(domain.lowercase()).apply()
    }

    override fun domains(): List<String> = prefs.all.keys.sorted()

    override fun findForHost(host: String): String? =
        SessionStore.matchHost(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(), host)
}
