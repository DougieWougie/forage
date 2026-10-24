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

import android.content.Context
import me.dougals.forage.domain.auth.SessionStore

/** Keystore-backed store. Needs a real device/emulator, so it is exercised via [PrefsSessionStore] in tests. */
fun encryptedSessionStore(context: Context): SessionStore = PrefsSessionStore(encryptedPrefs(context, "forage_sessions"))
