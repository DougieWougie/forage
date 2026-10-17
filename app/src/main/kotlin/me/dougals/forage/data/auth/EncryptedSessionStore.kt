package me.dougals.forage.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed store. Needs a real device/emulator, so it is exercised via [PrefsSessionStore] in tests. */
fun encryptedSessionStore(context: Context): SessionStore {
    val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    val prefs = EncryptedSharedPreferences.create(
        context, "forage_sessions", key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    return PrefsSessionStore(prefs)
}
