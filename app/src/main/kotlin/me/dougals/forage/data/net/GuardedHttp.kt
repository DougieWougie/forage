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
package me.dougals.forage.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException

class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * GET helper for URLs that come from untrusted places (feeds, OPML files, article links).
 *
 * Unless [get] is told private networks are allowed, every hop must be a globally routable address: names are
 * checked in [PublicOnlyDns] (which also covers odd forms like `2130706433`), IP literals (which OkHttp never
 * sends through Dns) are checked before connecting, and redirects are followed manually so each hop gets both
 * checks. Proxies are disabled so the checked address is the one actually dialled.
 */
class GuardedHttp(
    client: OkHttpClient,
    private val addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) {
    private val open = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val guarded = open.newBuilder().proxy(Proxy.NO_PROXY).dns(PublicOnlyDns(client.dns, addressPolicy)).build()

    /** @throws HttpStatusException for non-2xx answers; other [IOException]s for blocked or failed requests. */
    suspend fun <T> get(url: String, allowPrivate: Boolean, handle: (Response) -> T): T = withContext(Dispatchers.IO) {
        var current = url.toHttpUrlOrNull() ?: throw IOException("Invalid URL: $url")
        val http = if (allowPrivate) open else guarded
        repeat(MAX_REDIRECTS + 1) {
            if (!allowPrivate) requirePublicLiteral(current.host)
            http.newCall(Request.Builder().url(current).build()).execute().use { r ->
                if (r.isRedirect) {
                    val location = r.header("Location") ?: throw IOException("Redirect without Location")
                    current = r.request.url.resolve(location) ?: throw IOException("Unsupported redirect: $location")
                    return@repeat
                }
                if (!r.isSuccessful) throw HttpStatusException(r.code)
                return@withContext handle(r)
            }
        }
        throw IOException("Too many redirects")
    }

    private fun requirePublicLiteral(host: String) {
        val isLiteral = host.contains(':') || host.all { it.isDigit() || it == '.' }
        // Literals are parsed locally by getByName (no lookup happens).
        if (isLiteral && !addressPolicy(InetAddress.getByName(host))) throw IOException("$host is a non-public address")
    }

    private companion object { const val MAX_REDIRECTS = 10 }
}

class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val policy: (InetAddress) -> Boolean = ::isPublicAddress,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { !policy(it) }) throw UnknownHostException("$hostname resolves to a non-public address")
        return addresses
    }
}

/** Strict allowlist of globally routable unicast space; everything special-purpose is rejected. */
fun isPublicAddress(address: InetAddress): Boolean {
    val b = address.address.map { it.toInt() and 0xFF }
    return if (b.size == 4) isPublicV4(b) else isPublicV6(b)
}

private fun isPublicV4(b: List<Int>): Boolean {
    val (a, o2, o3) = Triple(b[0], b[1], b[2])
    return when {
        a == 0 || a == 10 || a == 127 || a >= 224 -> false             // this-net, private, loopback, multicast/reserved/broadcast
        a == 100 && o2 in 64..127 -> false                             // CGNAT
        a == 169 && o2 == 254 -> false                                 // link-local
        a == 172 && o2 in 16..31 -> false
        a == 192 && o2 == 168 -> false
        a == 192 && o2 == 0 && (o3 == 0 || o3 == 2) -> false           // IETF protocol assignments, TEST-NET-1
        a == 198 && o2 in 18..19 -> false                              // benchmarking
        a == 198 && o2 == 51 && o3 == 100 -> false                     // TEST-NET-2
        a == 203 && o2 == 0 && o3 == 113 -> false                      // TEST-NET-3
        else -> true
    }
}

private fun isPublicV6(b: List<Int>): Boolean {
    if ((b[0] and 0xE0) != 0x20) return false                          // only 2000::/3 is global unicast
    if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) return false          // 2001::/32 Teredo
    if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false    // 2001:db8::/32 documentation
    if (b[0] == 0x20 && b[1] == 0x02) return false                                    // 2002::/16 6to4 (embeds IPv4)
    return true
}
