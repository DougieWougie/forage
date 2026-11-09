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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Persists the cookies a WebView holds for a login URL.
 * [cookieSource] is `CookieManager.getInstance()::getCookie` in production.
 */
class SessionCapture(private val cookieSource: (String) -> String?, private val store: SessionStore) {
    fun capture(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        // Session cookies are only ever sent over HTTPS, so a page that isn't HTTPS has nothing worth keeping.
        if (!parsed.isHttps) return false
        val host = parsed.host
        val cookies = cookieSource(url)?.takeIf { it.isNotBlank() } ?: return false
        store.save(host, cookies)
        return true
    }
}
