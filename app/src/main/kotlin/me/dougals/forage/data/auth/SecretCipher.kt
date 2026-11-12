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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals a secret into a string that is safe to keep in SharedPreferences. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** @throws GeneralSecurityException when the token is corrupt or its key no longer exists. */
    fun decrypt(token: String): String
}

/**
 * AES-256-GCM with a non-exportable Android Keystore key, so the stored value is useless without this device's
 * key. The key needs no user authentication, so background sync keeps working while the phone is locked.
 * Needs a real device, so it is not unit-tested; the stores are tested through a fake [SecretCipher].
 */
class KeystoreSecretCipher(private val alias: String = "forage_secrets") : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun decrypt(token: String): String {
        val raw = Base64.decode(token, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) throw GeneralSecurityException("Token too short")
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        }
        return String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES))
    }

    @Synchronized
    private fun key(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw GeneralSecurityException("Key $alias is missing")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private const val SEALED = "enc1:"

/**
 * Reads a secret written by [putSecret]. A value with no marker is a plaintext one from an older version: it is
 * returned and sealed in place. A value that cannot be opened (its key is gone) is deleted and read as missing,
 * so the user signs in again rather than the app failing.
 */
internal fun SharedPreferences.getSecret(key: String, cipher: SecretCipher): String? {
    val stored = getString(key, null) ?: return null
    if (!stored.startsWith(SEALED)) {
        edit { putString(key, SEALED + cipher.encrypt(stored)) }
        return stored
    }
    return try {
        cipher.decrypt(stored.removePrefix(SEALED))
    } catch (_: GeneralSecurityException) {
        edit { remove(key) }
        null
    } catch (_: IllegalArgumentException) { // bad Base64
        edit { remove(key) }
        null
    }
}

internal fun SharedPreferences.Editor.putSecret(key: String, value: String, cipher: SecretCipher) {
    putString(key, SEALED + cipher.encrypt(value))
}
