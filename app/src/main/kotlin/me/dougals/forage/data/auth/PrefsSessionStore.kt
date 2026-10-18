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
import me.dougals.forage.domain.auth.SessionStore

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
}
