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
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Private prefs for secrets. Jetpack's EncryptedSharedPreferences is deprecated, so new values live in ordinary
 * app-private prefs (protected by Android's file-based encryption, with backup disabled). Anything an older
 * version saved in the encrypted file [legacyName] is copied across once; [LegacyPrefs] and the security-crypto
 * dependency can go once that migration has had a release or two.
 */
fun securePrefs(context: Context, name: String, legacyName: String): SharedPreferences {
    val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    if (legacyFile(context, legacyName).exists()) {
        migrateLegacyPrefs(prefs, legacy = { LegacyPrefs.open(context, legacyName) }) {
            context.deleteSharedPreferences(legacyName)
            // The key is shared by every legacy file, so it goes only with the last one.
            if (LEGACY_NAMES.none { legacyFile(context, it).exists() }) LegacyPrefs.deleteKey()
        }
    }
    return prefs
}

private val LEGACY_NAMES = listOf("forage_sessions", "forage_nextcloud")

private fun legacyFile(context: Context, name: String) = File(context.dataDir, "shared_prefs/$name.xml")

/**
 * Copies every entry of [legacy] into [target], then calls [discard]. A legacy file that can no longer be read
 * (its Keystore key is gone) is discarded too and the user signs in again, rather than the app failing to start.
 */
internal fun migrateLegacyPrefs(target: SharedPreferences, legacy: () -> SharedPreferences, discard: () -> Unit) {
    try {
        val old = legacy().all
        target.edit {
            old.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
    } catch (_: Exception) {
        // unreadable: start empty
    } finally {
        discard()
    }
}

/** The only remaining use of security-crypto. Needs a real device, so it is not unit-tested. */
private object LegacyPrefs {
    fun open(context: Context, name: String): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, name, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun deleteKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
    }
}
