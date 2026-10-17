package me.dougals.forage.domain.repository

import kotlinx.coroutines.flow.Flow
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSyncMode(mode: SyncMode)
    suspend fun setAllowLocalNetwork(allow: Boolean)
    suspend fun setReaderFont(font: ReaderFont)
    /** Clamped to [AppSettings.MIN_SCALE]..[AppSettings.MAX_SCALE]. */
    suspend fun setFontScale(scale: Float)
}
