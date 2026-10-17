package me.dougals.forage.presentation.settings

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.nextcloud.NextcloudCredentials
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
    private fun vm() = SettingsViewModel(settings, creds, sync, sessions, repo)

    @Test fun nonHttpsServerIsRefusedAndNothingIsSaved() = runTest {
        val v = vm()
        v.state.test {
            awaitItem(); v.connectNextcloud("http://cloud.example.com", "u", "p")
            assertTrue(awaitUntil { it.message != null }.message!!.contains("https"))
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
            assertTrue(awaitUntil { it.message != null }.message!!.contains("401 Unauthorized"))
        }
        assertNull(creds.value)
        coVerify { settings.setSyncMode(SyncMode.LOCAL) }
    }

    @Test fun syncNowRefreshesAndReportsSuccess() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport())
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            assertEquals("Already up to date", awaitUntil { it.message != null }.message)
        }
        coVerify(exactly = 1) { repo.refresh() }
        assertTrue(!v.state.value.isSyncing)
    }

    @Test fun syncNowMessageDescribesWhatChanged() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport(newArticles = 12, changesSent = 3))
        val v = vm()
        v.state.test {
            awaitItem(); v.syncNow()
            assertEquals("Synced · 12 new articles · 3 changes sent", awaitUntil { it.message != null }.message)
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
            assertTrue(awaitUntil { it.message != null }.message!!.contains("timeout"))
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

    @Test fun typographyPassThrough() = runTest {
        val v = vm()
        v.setFont(me.dougals.forage.domain.model.ReaderFont.SANS)
        v.setFontScale(1.3f)
        coVerify { settings.setReaderFont(me.dougals.forage.domain.model.ReaderFont.SANS); settings.setFontScale(1.3f) }
    }
}
