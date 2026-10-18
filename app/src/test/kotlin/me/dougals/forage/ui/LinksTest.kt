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
package me.dougals.forage.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinksTest {
    @Test fun nextcloudNewsLinkIsAnHttpsWebUrlOnTheOfficialAppStore() {
        assertTrue(isWebUrl(Links.NEXTCLOUD_NEWS))
        assertTrue(Links.NEXTCLOUD_NEWS.startsWith("https://"))
        assertEquals("https://apps.nextcloud.com/apps/news", Links.NEXTCLOUD_NEWS)
    }
}
