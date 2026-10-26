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

import me.dougals.forage.data.content.ImageResourceFetcher
import okhttp3.OkHttpClient
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import me.dougals.forage.data.content.OfflineArchiver
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val archiver = mockk<OfflineArchiver>(relaxed = true)
    private fun vm(a: me.dougals.forage.domain.model.Article, markReadOnOpen: Boolean = true) = run {
        every { settings.settings } returns flowOf(AppSettings(readerFont = ReaderFont.SANS, fontScale = 1.2f, markReadOnOpen = markReadOnOpen))
        every { repo.observeArticle(a.id) } returns MutableStateFlow(a)
        ReaderViewModel(SavedStateHandle(mapOf("articleId" to a.id)), repo, settings, archiver, ImageResourceFetcher(OkHttpClient()))
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

    @Test fun openingAnUnreadArticleLeavesItUnreadWhenMarkReadOnOpenIsOff() = runTest {
        val v = vm(article(5, read = false), markReadOnOpen = false)
        v.state.test { awaitUntil { it.article != null } }
        coVerify(exactly = 0) { repo.setRead(any(), any()) }
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

    @Test fun downloadFetchesTheFullArticleWithoutSavingIt() = runTest {
        coEvery { archiver.download(5) } returns Result.success(Unit)
        val v = vm(article(5, read = true, saved = false))
        v.state.test { awaitUntil { it.article != null }; v.downloadFullArticle(); awaitUntil { !it.downloading } }
        coVerify(exactly = 1) { archiver.download(5) }
        coVerify(exactly = 0) { repo.setSaved(any(), any()) }
        assertFalse(v.state.value.downloadFailed)
    }

    @Test fun failedDownloadIsReportedUntilDismissed() = runTest {
        coEvery { archiver.download(5) } returns Result.failure(java.io.IOException("offline"))
        val v = vm(article(5, read = true))
        v.state.test {
            awaitUntil { it.article != null }
            v.downloadFullArticle()
            assertTrue(awaitUntil { it.downloadFailed }.downloadFailed)
            v.dismissDownloadError()
            awaitUntil { !it.downloadFailed }
        }
    }

    @Test fun downloadingTwiceAtOnceFetchesOnce() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
        coEvery { archiver.download(5) } coAnswers { gate.await() }
        val v = vm(article(5, read = true))
        v.state.test {
            awaitUntil { it.article != null }
            v.downloadFullArticle()
            awaitUntil { it.downloading }
            v.downloadFullArticle()
            gate.complete(Result.success(Unit))
            awaitUntil { !it.downloading }
        }
        coVerify(exactly = 1) { archiver.download(5) }
    }
}
