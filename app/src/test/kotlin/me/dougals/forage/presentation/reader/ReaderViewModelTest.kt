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
package me.dougals.forage.presentation.reader

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.article
import me.dougals.forage.testutil.awaitUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ReaderViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repo = mockk<FeedRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true).also {
        every { it.settings } returns flowOf(AppSettings(readerFont = ReaderFont.SANS, fontScale = 1.2f))
    }
    private fun vm(a: me.dougals.forage.domain.model.Article) = run {
        every { repo.observeArticle(a.id) } returns MutableStateFlow(a)
        ReaderViewModel(SavedStateHandle(mapOf("articleId" to a.id)), repo, settings)
    }

    @Test fun exposesArticleAndReaderSettings() = runTest {
        vm(article(5)).state.test {
            val s = awaitUntil { it.article != null }
            assertEquals(5L, s.article!!.id)
            assertEquals(ReaderFont.SANS, s.settings.readerFont)
            assertEquals(1.2f, s.settings.fontScale)
        }
    }

    @Test fun openingAnUnreadArticleMarksItReadOnce() = runTest {
        val v = vm(article(5, read = false))
        v.state.test { awaitUntil { it.article != null } }
        coVerify(exactly = 1) { repo.setRead(5, true) }
    }

    @Test fun openingAReadArticleDoesNotTouchIt() = runTest {
        val v = vm(article(5, read = true))
        v.state.test { awaitUntil { it.article != null } }
        coVerify(exactly = 0) { repo.setRead(any(), any()) }
    }

    @Test fun toggleSavedFlipsCurrentState() = runTest {
        val v = vm(article(5, read = true, saved = false))
        v.state.test { awaitUntil { it.article != null }; v.toggleSaved() }
        coVerify { repo.setSaved(5, true) }
    }

    @Test fun typographyChangesGoToSettings() = runTest {
        val v = vm(article(5, read = true))
        v.setFont(ReaderFont.SERIF)
        v.setFontScale(1.4f)
        coVerify { settings.setReaderFont(ReaderFont.SERIF); settings.setFontScale(1.4f) }
    }
}
