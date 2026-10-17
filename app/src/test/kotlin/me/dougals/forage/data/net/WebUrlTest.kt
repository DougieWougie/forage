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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebUrlTest {
    @Test fun onlyHttpAndHttpsWithAHostAreWebUrls() {
        listOf("https://example.com/a?b=1", "http://example.com", "HTTPS://Example.com/x", "http://192.168.1.1:8080/")
            .forEach { assertTrue(isWebUrl(it), it) }
        listOf(
            "", "   ", "file:///etc/passwd", "javascript:alert(1)", "intent://x#Intent;scheme=http;end", "content://a/b",
            "ftp://example.com/f", "market://details?id=x", "tel:123", "http://", "example.com/feed", "data:text/html,hi",
        ).forEach { assertFalse(isWebUrl(it), it) }
    }
}
