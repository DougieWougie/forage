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
package me.dougals.forage.domain.auth

import java.net.URI
import java.net.URISyntaxException

/**
 * Persists the cookies a WebView holds for a login URL.
 * [cookieSource] is `CookieManager.getInstance()::getCookie` in production.
 */
class SessionCapture(private val cookieSource: (String) -> String?, private val store: SessionStore) {
    fun capture(url: String): Boolean {
        val host = httpsHostOf(url) ?: return false // session cookies only ever travel over HTTPS, so nothing else is worth keeping
        val cookies = cookieSource(url)?.takeIf { it.isNotBlank() } ?: return false
        store.save(host, cookies)
        return true
    }

    /** Lower-cased host (no brackets on an IPv6 literal, matching how the HTTP client names it) of an `https` URL, else null. */
    private fun httpsHostOf(url: String): String? {
        val uri = try { URI(url) } catch (e: URISyntaxException) { return null }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        return uri.host?.removeSurrounding("[", "]")?.lowercase()?.takeIf { it.isNotEmpty() }
    }
}
