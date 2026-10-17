package me.dougals.forage.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {
    @TempDir lateinit var dir: File

    private fun TestScope.repo() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "s.preferences_pb") },
    )

    @Test fun defaults() = runTest {
        assertEquals(AppSettings(SyncMode.LOCAL, ReaderFont.SERIF, 1.0f), repo().settings.first())
    }

    @Test fun valuesPersist() = runTest {
        val r = repo()
        r.setSyncMode(SyncMode.NEXTCLOUD)
        r.setReaderFont(ReaderFont.SANS)
        r.setFontScale(1.5f)
        assertEquals(AppSettings(SyncMode.NEXTCLOUD, ReaderFont.SANS, 1.5f), r.settings.first())
    }

    @Test fun fontScaleIsClamped() = runTest {
        val r = repo()
        r.setFontScale(9f); assertEquals(2.0f, r.settings.first().fontScale)
        r.setFontScale(0.1f); assertEquals(0.8f, r.settings.first().fontScale)
    }
}
