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

import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

/** Each blocked range is checked at both ends, with the public addresses just outside it. */
class PublicAddressTest {
    // Literals are parsed locally, so no lookup happens.
    private fun public(literal: String) = isPublicAddress(InetAddress.getByName(literal))
    private fun blocked(vararg literals: String) = literals.forEach { assertFalse(public(it), it) }
    private fun allowed(vararg literals: String) = literals.forEach { assertTrue(public(it), it) }

    @Test fun ordinaryV4AddressesArePublic() = allowed("1.1.1.1", "8.8.8.8", "93.184.216.34", "223.255.255.255")

    @Test fun thisNetPrivateAndLoopbackAreBlocked() {
        blocked("0.0.0.0", "0.255.255.255", "10.0.0.0", "10.255.255.255", "127.0.0.1", "127.255.255.255")
        allowed("1.0.0.0", "9.255.255.255", "11.0.0.0", "126.255.255.255", "128.0.0.0")
    }

    @Test fun multicastReservedAndBroadcastAreBlocked() = blocked("224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255")

    @Test fun carrierGradeNatIsBlocked() {
        blocked("100.64.0.0", "100.127.255.255")
        allowed("100.63.255.255", "100.128.0.0")
    }

    @Test fun linkLocalIsBlocked() {
        blocked("169.254.0.0", "169.254.169.254", "169.254.255.255")
        allowed("169.253.255.255", "169.255.0.0")
    }

    @Test fun private172IsBlocked() {
        blocked("172.16.0.0", "172.31.255.255")
        allowed("172.15.255.255", "172.32.0.0")
    }

    @Test fun private192168IsBlocked() {
        blocked("192.168.0.0", "192.168.255.255")
        allowed("192.167.255.255", "192.169.0.0")
    }

    @Test fun ietfAndDocumentationRangesAreBlocked() {
        blocked("192.0.0.0", "192.0.0.255", "192.0.2.0", "192.0.2.255", "198.51.100.1", "203.0.113.1")
        allowed("192.0.1.1", "192.0.3.1", "192.1.0.1", "198.51.99.1", "198.51.101.1", "203.0.112.1", "203.0.114.1", "203.1.113.1")
    }

    @Test fun benchmarkingIsBlocked() {
        blocked("198.18.0.0", "198.19.255.255")
        allowed("198.17.255.255", "198.20.0.0")
    }

    @Test fun onlyGlobalUnicastV6IsPublic() {
        allowed("2606:4700::1111", "2a00:1450::1", "3fff:ffff::1")
        blocked("::", "::1", "fe80::1", "fc00::1", "fd12::1", "ff02::1", "4000::1", "1fff:ffff::1")
    }

    @Test fun v4MappedAddressesGetTheV4Rules() {
        blocked("::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254")
        allowed("::ffff:8.8.8.8")
    }

    @Test fun teredoDocumentationAnd6to4AreBlocked() {
        blocked("2001::1", "2001:0:ffff::1", "2001:db8::1", "2001:db8:ffff::1", "2002::1", "2002:c0a8:101::1")
        allowed("2001:1::1", "2001:db7::1", "2001:db9::1", "2001:4860::8888", "2003::1")
    }

    // PublicOnlyDns

    private fun dns(vararg answers: String) = PublicOnlyDns(Dns { answers.map(InetAddress::getByName) })

    @Test fun dnsPassesPublicAnswersThrough() {
        assertEquals(listOf(InetAddress.getByName("8.8.8.8")), dns("8.8.8.8").lookup("example.com"))
    }

    @Test fun dnsRefusesANameWithAnyNonPublicAnswer() {
        assertThrows(UnknownHostException::class.java) { dns("8.8.8.8", "10.0.0.1").lookup("rebind.example") }
    }
}
