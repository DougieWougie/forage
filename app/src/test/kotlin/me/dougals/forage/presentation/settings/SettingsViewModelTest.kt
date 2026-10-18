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
package me.dougals.forage.presentation.settings

import me.dougals.forage.presentation.toUiText
import me.dougals.forage.presentation.UiText
import me.dougals.forage.R
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.nextcloud.LoginFlowSession
import me.dougals.forage.domain.nextcloud.NextcloudCredentials
import me.dougals.forage.domain.nextcloud.NextcloudLoginFlow
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.MemCredentialStore
import me.dougals.forage.testutil.MemSessionStore
import me.dougals.forage.testutil.MemSyncState
import me.dougals.forage.testutil.awaitUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

class SettingsViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val settings = mockk<SettingsRepository>(relaxed = true).also { every { it.settings } returns flowOf(AppSettings()) }
    private val creds = MemCredentialStore()
    private val sync = MemSyncState(lastModified = 999)
    private val sessions = MemSessionStore()
    private val repo = mockk<FeedRepository>(relaxed = true)
    private val loginFlow = mockk<NextcloudLoginFlow>(relaxed = true)
    private fun vm() = SettingsViewModel(settings, creds, sync, sessions, repo, loginFlow)

    private val session = LoginFlowSession("https://cloud.example.com", "https://cloud.example.com/login/v2/flow/x", "https://cloud.example.com/login/v2/poll", "t")
    private val granted = NextcloudCredentials("https://cloud.example.com", "dougie", "app-pw", fromLoginFlow = true)

    @Test fun nonHttpsServerIsRefusedAndNothingIsSaved() = runTest {
        val v = vm()
        v.state.test {
            awaitItem(); v.connectNextcloud("http://cloud.example.com", "u", "p")
            assertEquals(UiText.Res(R.string.connect_requires_https), awaitUntil { it.message != null }.message)
        }
        assertNull(creds.value)
        coVerify(exactly = 0) { settings.setSyncMode(any()) }
    }

    @Test fun blankFieldsAreRefused() = runTest {
        val v = vm()
        v.state.test { awaitItem(); v.connectNextcloud("https://c.example.com", " ", "p"); awaitUntil { it.message != null } }
        assertNull(creds.value)
    }

    @Test fun successfulConnectSavesSwitchesModeResetsSyncMarkerThenRefreshes() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport())
        vm().connectNextcloud(" https://cloud.example.com/ ", " dougie ", "app-pw")
        assertEquals(NextcloudCredentials("https://cloud.example.com", "dougie", "app-pw"), creds.value)
        assertEquals(0L, sync.lastModified)
        coVerifyOrder { settings.setSyncMode(SyncMode.NEXTCLOUD); repo.refresh() }
    }

    @Test fun failedFirstSyncRollsBackToLocalMode() = runTest {
        coEvery { repo.refresh() } returns Result.failure(IOException("401 Unauthorized"))
        val v = vm()
        v.state.test {
            awaitItem(); v.connectNextcloud("https://cloud.example.com", "u", "p")
            assertEquals(UiText.Res(R.string.sync_failed, listOf(UiText.Raw("401 Unauthorized"))), awaitUntil { it.message != null }.message)
        }
        assertNull(creds.value)
        coVerify { settings.setSyncMode(SyncMode.LOCAL) }
    }

    @Test fun browserLoginShowsTheLoginPageThenConnectsOnceGranted() = runTest {
        coEvery { loginFlow.start("https://cloud.example.com") } returns session
        coEvery { loginFlow.poll(session) } returnsMany listOf(null, null) andThenThrows IOException("offline") andThen granted
        coEvery { repo.refresh() } returns Result.success(SyncReport())
        val v = vm()
        v.state.test {
            awaitItem(); v.logInToNextcloud(" https://cloud.example.com/ ")
            assertEquals(session.loginUrl, awaitUntil { it.loginUrl != null }.loginUrl)
            assertEquals(UiText.Res(R.string.connected_to, listOf("https://cloud.example.com")), awaitUntil { it.message != null }.message)
            assertNull(v.state.value.loginUrl)
        }
        assertEquals(granted, creds.value)
        coVerify(exactly = 4) { loginFlow.poll(session) }
        coVerifyOrder { settings.setSyncMode(SyncMode.NEXTCLOUD); repo.refresh() }
    }

    @Test fun browserLoginRefusesNonHttpsAndEmptyServers() = runTest {
        val v = vm()
        v.state.test {
            awaitItem(); v.logInToNextcloud("http://cloud.example.com")
            assertEquals(UiText.Res(R.string.connect_requires_https), awaitUntil { it.message != null }.message)
            v.dismissMessage(); v.logInToNextcloud("https://")
            assertEquals(UiText.Res(R.string.connect_enter_server), awaitUntil { it.message != null }.message)
        }
        coVerify(exactly = 0) { loginFlow.start(any()) }
    }

    @Test fun browserLoginThatCannotStartReportsWhy() = runTest {
        coEvery { loginFlow.start(any()) } throws IOException("HTTP 404")
        val v = vm()
        v.state.test {
            awaitItem(); v.logInToNextcloud("https://cloud.example.com")
            assertEquals(UiText.Res(R.string.login_failed, listOf(UiText.Raw("HTTP 404"))), awaitUntil { it.message != null }.message)
        }
        assertNull(creds.value)
    }

    @Test fun browserLoginTimesOutWhenNeverGranted() = runTest {
        coEvery { loginFlow.start(any()) } returns session
        coEvery { loginFlow.poll(session) } returns null
        val v = vm()
        v.state.test {
            awaitItem(); v.logInToNextcloud("https://cloud.example.com")
            assertEquals(UiText.Res(R.string.login_timed_out), awaitUntil { it.message != null }.message)
        }
        assertNull(creds.value)
        assertTrue(!v.state.value.isConnecting)
    }

    @Test fun cancellingBrowserLoginStopsPolling() = runTest {
        coEvery { loginFlow.start(any()) } returns session
        coEvery { loginFlow.poll(session) } returns null
        val v = vm()
        v.state.test {
            awaitItem(); v.logInToNextcloud("https://cloud.example.com")
            awaitUntil { it.loginUrl != null }
            v.cancelNextcloudLogin()
            val s = awaitUntil { it.loginUrl == null && !it.isConnecting }
            assertNull(s.message)
        }
        assertNull(creds.value)
    }

    @Test fun failedFirstSyncAfterBrowserLoginRevokesTheNewAppPassword() = runTest {
        coEvery { loginFlow.start(any()) } returns session
        coEvery { loginFlow.poll(session) } returns granted
        coEvery { repo.refresh() } returns Result.failure(IOException("News app not installed"))
        vm().logInToNextcloud("https://cloud.example.com")
        assertNull(creds.value)
        coVerify { loginFlow.revoke(granted); settings.setSyncMode(SyncMode.LOCAL) }
    }

    @Test fun syncNowRefreshesAndReportsSuccess() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport())
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            assertEquals(UiText.Res(R.string.up_to_date), awaitUntil { it.message != null }.message)
        }
        coVerify(exactly = 1) { repo.refresh() }
        assertTrue(!v.state.value.isSyncing)
    }

    @Test fun syncNowMessageDescribesWhatChanged() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport(newArticles = 12, changesSent = 3))
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            assertEquals(SyncReport(newArticles = 12, changesSent = 3).toUiText(), awaitUntil { it.message != null }.message)
        }
    }

    @Test fun lastSyncedTimeIsShownAndRefreshedAfterSyncNow() = runTest {
        sync.lastSyncedAt = 1_000
        coEvery { repo.refresh() } coAnswers { sync.lastSyncedAt = 2_000; Result.success(SyncReport()) }
        val v = vm()
        v.state.test {
            assertEquals(1_000L, awaitUntil { it.lastSyncedAt == 1_000L }.lastSyncedAt)
            v.syncNow()
            assertEquals(2_000L, awaitUntil { it.lastSyncedAt == 2_000L }.lastSyncedAt)
        }
    }

    @Test fun neverSyncedHasNoLastSyncedTime() = runTest {
        assertNull(vm().state.value.lastSyncedAt)
    }

    @Test fun syncNowReportsFailureAndKeepsCredentials() = runTest {
        creds.value = NextcloudCredentials("https://c", "u", "p")
        coEvery { repo.refresh() } returns Result.failure(IOException("timeout"))
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            assertEquals(UiText.Res(R.string.sync_failed, listOf(UiText.Raw("timeout"))), awaitUntil { it.message != null }.message)
        }
        assertEquals(NextcloudCredentials("https://c", "u", "p"), creds.value)
        coVerify(exactly = 0) { settings.setSyncMode(any()) }
    }

    @Test fun syncNowWhileSyncingIsIgnored() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Result<me.dougals.forage.domain.model.SyncReport>>()
        coEvery { repo.refresh() } coAnswers { gate.await() }
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            awaitUntil { it.isSyncing }
            v.syncNow()
            gate.complete(Result.success(SyncReport()))
            awaitUntil { it.message != null }
        }
        coVerify(exactly = 1) { repo.refresh() }
    }

    @Test fun disconnectClearsCredentialsAndGoesLocal() = runTest {
        creds.value = NextcloudCredentials("https://c", "u", "p")
        vm().disconnectNextcloud()
        assertNull(creds.value)
        coVerify { settings.setSyncMode(SyncMode.LOCAL) }
        coVerify(exactly = 0) { loginFlow.revoke(any()) } // a pasted app password may be in use elsewhere
    }

    @Test fun disconnectRevokesAnAppPasswordFromBrowserLoginEvenIfTheServerIsUnreachable() = runTest {
        creds.value = granted
        coEvery { loginFlow.revoke(any()) } throws IOException("offline")
        vm().disconnectNextcloud()
        assertNull(creds.value)
        coVerify { loginFlow.revoke(granted); settings.setSyncMode(SyncMode.LOCAL) }
    }

    @Test fun stateShowsServerAndUserButNeverThePassword() = runTest {
        creds.value = NextcloudCredentials("https://c", "dougie", "secret")
        vm().state.test {
            val s = awaitItem()
            assertEquals("https://c", s.nextcloudServer)
            assertEquals("dougie", s.nextcloudUser)
            assertTrue(!s.toString().contains("secret"))
        }
    }

    @Test fun sessionsAreListedAndRemovable() = runTest {
        sessions.save("lwn.net", "a=1")
        val v = vm()
        v.state.test {
            assertEquals(listOf("lwn.net"), awaitItem().sessionDomains)
            v.removeSession("lwn.net")
            assertEquals(emptyList<String>(), awaitUntil { it.sessionDomains.isEmpty() }.sessionDomains)
        }
    }

    @Test fun reloadSessionsPicksUpNewLogins() = runTest {
        val v = vm()
        v.state.test {
            awaitItem()
            sessions.save("example.com", "x=1")
            v.reloadSessions()
            assertEquals(listOf("example.com"), awaitUntil { it.sessionDomains.isNotEmpty() }.sessionDomains)
        }
    }

    @Test fun localNetworkTogglePassesThrough() = runTest {
        vm().setAllowLocalNetwork(true)
        coVerify { settings.setAllowLocalNetwork(true) }
    }

    @Test fun refreshChoicesAreWrittenToSettings() = runTest {
        val v = vm()
        v.setRefreshInterval(me.dougals.forage.domain.model.RefreshInterval.OFF)
        v.setRefreshWifiOnly(true)
        coVerify { settings.setRefreshInterval(me.dougals.forage.domain.model.RefreshInterval.OFF); settings.setRefreshWifiOnly(true) }
    }

    @Test fun typographyPassThrough() = runTest {
        val v = vm()
        v.setFont(me.dougals.forage.domain.model.ReaderFont.SANS)
        v.setFontScale(1.3f)
        coVerify { settings.setReaderFont(me.dougals.forage.domain.model.ReaderFont.SANS); settings.setFontScale(1.3f) }
    }
}
