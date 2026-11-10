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
package me.dougals.forage.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Cleartext must be refused by an explicit config, not by whatever the platform defaults to for this targetSdk. */
class NetworkSecurityConfigTest {
    private val main = File("src/main")

    @Test fun theManifestPointsAtAConfigThatForbidsCleartext() {
        assertTrue(File(main, "AndroidManifest.xml").readText().contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        val config = File(main, "res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertTrue(!config.contains("cleartextTrafficPermitted=\"true\""), "no domain may opt back in")
    }
}
