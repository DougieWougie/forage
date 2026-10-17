package me.dougals.forage.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.SettingsRepository

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            syncMode = p[MODE]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.LOCAL,
            readerFont = p[FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: ReaderFont.SERIF,
            fontScale = p[SCALE] ?: 1.0f,
            allowLocalNetwork = p[LOCAL_NET] ?: false,
        )
    }

    override suspend fun setSyncMode(mode: SyncMode) { store.edit { it[MODE] = mode.name } }
    override suspend fun setAllowLocalNetwork(allow: Boolean) { store.edit { it[LOCAL_NET] = allow } }
    override suspend fun setReaderFont(font: ReaderFont) { store.edit { it[FONT] = font.name } }
    override suspend fun setFontScale(scale: Float) {
        store.edit { it[SCALE] = scale.coerceIn(AppSettings.MIN_SCALE, AppSettings.MAX_SCALE) }
    }

    private companion object {
        val MODE = stringPreferencesKey("sync_mode")
        val FONT = stringPreferencesKey("reader_font")
        val SCALE = floatPreferencesKey("font_scale")
        val LOCAL_NET = booleanPreferencesKey("allow_local_network")
    }
}
