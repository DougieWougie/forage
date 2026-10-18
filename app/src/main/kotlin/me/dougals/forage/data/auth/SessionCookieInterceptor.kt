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

import me.dougals.forage.domain.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Register with `addNetworkInterceptor` so it runs per hop: a redirect to another host is
 * re-evaluated and never receives the original host's session.
 */
class SessionCookieInterceptor(
    private val store: SessionStore,
    private val allowInsecure: Boolean = false,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Cookie") != null || !(request.url.isHttps || allowInsecure)) return chain.proceed(request)
        val cookies = store.findForHost(request.url.host) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Cookie", cookies).build())
    }
}
