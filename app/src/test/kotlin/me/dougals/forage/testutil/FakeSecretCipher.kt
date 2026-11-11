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

import me.dougals.forage.data.auth.SecretCipher
import java.security.GeneralSecurityException

/** Reversible stand-in for the Keystore cipher; [lockOut] simulates the key being lost. */
class FakeSecretCipher : SecretCipher {
    var lockedOut = false

    override fun encrypt(plain: String) = "x" + plain.reversed()

    override fun decrypt(token: String): String {
        if (lockedOut || !token.startsWith("x")) throw GeneralSecurityException("no key")
        return token.drop(1).reversed()
    }
}
