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
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * Keystore-backed prefs. Needs a real device/emulator, so tests use plain prefs instead.
 *
 * If the Keystore key and the file no longer match (a restored device, a wiped key) `create` throws on every launch
 * and the app could never start. The unreadable file is then deleted along with the key and rebuilt empty, which
 * signs the user out rather than locking them out.
 */
fun encryptedPrefs(context: Context, name: String): SharedPreferences = try {
    open(context, name)
} catch (e: Exception) {
    context.deleteSharedPreferences(name)
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    open(context, name)
}

private fun open(context: Context, name: String): SharedPreferences {
    val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(
        context, name, key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
