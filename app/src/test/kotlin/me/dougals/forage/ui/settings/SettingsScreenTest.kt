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
package me.dougals.forage.ui.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.settings.SettingsUiState
import me.dougals.forage.presentation.settings.SettingsViewModel
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = MutableStateFlow(SettingsUiState())
    private val vm = mockk<SettingsViewModel>(relaxed = true).also { every { it.state } returns state }
    private val siteLogins = mutableListOf<String>()

    private val connected = SettingsUiState(
        settings = AppSettings(syncMode = SyncMode.NEXTCLOUD),
        nextcloudServer = "https://cloud.example.com", nextcloudUser = "dougie",
    )

    private fun show(initial: SettingsUiState = SettingsUiState()) {
        state.value = initial
        rule.setContent { ForageTheme { SettingsScreen(onLogin = { siteLogins += it }, vm = vm) } }
    }

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    private fun click(text: String) = rule.onNodeWithText(text).performClick().also { rule.waitForIdle() }
    private fun inDialog(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isDialog()))
    private fun field(label: String, value: String) = rule.onNode(hasText(label) and hasAnyAncestor(isDialog())).performTextReplacement(value)
    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
    private val switch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private fun nextBrowserUrl(): String? = shadowOf(rule.activity).nextStartedActivity
        ?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

    @Test fun readingAndListChoicesGoToTheViewModel() {
        show()
        click("Sans-serif")
        scrollTo("Off"); rule.onAllNodesWithText("Off")[0].performClick().also { rule.waitForIdle() }
        scrollTo("Left"); click("Left")
        verify { vm.setFont(ReaderFont.SANS); vm.setThumbnails(ThumbnailMode.OFF); vm.setThumbnailSide(ThumbnailSide.START) }
    }

    @Test fun backgroundRefreshChoicesGoToTheViewModel() {
        show()
        scrollTo("Background refresh")
        click("6 h")
        scrollTo("Wi-Fi only")
        rule.onAllNodes(switch)[1].performClick()
        rule.waitForIdle()
        verify { vm.setRefreshInterval(RefreshInterval.EVERY_6_HOURS); vm.setRefreshWifiOnly(true) }
    }

    @Test fun wifiOnlyIsHiddenWhenBackgroundRefreshIsOff() {
        show(SettingsUiState(settings = AppSettings(refreshInterval = RefreshInterval.OFF)))
        scrollTo("Background refresh")
        rule.onNodeWithText("Skip background refreshes on mobile data and other metered connections.").assertDoesNotExist()
    }

    @Test fun switchesGoToTheViewModel() {
        show()
        scrollTo("Pull down to refresh")
        rule.onAllNodes(switch)[0].performClick()
        scrollTo("Allow feeds on local network")
        rule.onAllNodes(switch).onLast().performClick()
        rule.waitForIdle()
        verify { vm.setPullToRefresh(false); vm.setAllowLocalNetwork(true) }
    }

    @Test fun messagesAreShownThenDismissed() {
        show(SettingsUiState(message = UiText.Raw("Connected to cloud")))
        rule.onNodeWithText("Connected to cloud").assertExists()
        verify(exactly = 0) { vm.dismissMessage() }
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("Connected to cloud").assertDoesNotExist()
        verify { vm.dismissMessage() }
    }

    @Test fun sessionsAreReloadedWhenTheScreenResumes() {
        show()
        verify { vm.reloadSessions() }
    }

    // Local mode and the connect dialog

    @Test fun localModeOffersToConnect() {
        show()
        scrollTo("Local mode")
        rule.onNodeWithText("Local mode").assertExists()
        rule.onNodeWithText("Disconnect").assertDoesNotExist()
    }

    @Test fun connectDefaultsToBrowserLogin() {
        show()
        scrollTo("Connect"); click("Connect")
        inDialog("Connect Nextcloud").assertExists()
        inDialog("You'll log in on your server's own page in the browser, and Forage gets an app password of its own.").assertExists()
        rule.onNodeWithText("Username").assertDoesNotExist()
        field("Server (https)", "https://cloud.example.com")
        click("Log in")
        verify { vm.logInToNextcloud("https://cloud.example.com") }
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun appPasswordCanBeUsedInstead() {
        show()
        scrollTo("Connect"); click("Connect")
        click("Use an app password instead")
        field("Server (https)", "https://cloud.example.com")
        field("Username", "dougie")
        field("App password", "secret")
        inDialog("Connect").performClick(); rule.waitForIdle()
        verify { vm.connectNextcloud("https://cloud.example.com", "dougie", "secret") }
        verify(exactly = 0) { vm.logInToNextcloud(any()) }
    }

    @Test fun appPasswordModeCanSwitchBackToTheBrowser() {
        show()
        scrollTo("Connect"); click("Connect")
        click("Use an app password instead")
        click("Log in through the browser instead")
        rule.onNodeWithText("Username").assertDoesNotExist()
        inDialog("Log in").assertExists()
    }

    @Test fun cancellingTheConnectDialogDoesNothing() {
        show()
        scrollTo("Connect"); click("Connect")
        inDialog("Cancel").performClick(); rule.waitForIdle()
        rule.onNode(isDialog()).assertDoesNotExist()
        verify(exactly = 0) { vm.logInToNextcloud(any()); vm.connectNextcloud(any(), any(), any()) }
    }

    @Test fun connectingWithAnAppPasswordShowsABarBeforeTheFirstSync() {
        show(SettingsUiState(isConnecting = true))
        scrollTo("Local mode")
        rule.onNode(spinner).assertExists()
    }

    // Waiting for the browser login

    @Test fun theLoginPageOpensOnceInTheBrowser() {
        show()
        assertNull(nextBrowserUrl())
        state.value = SettingsUiState(isConnecting = true, loginUrl = "https://cloud.example.com/login/v2/flow/abc")
        rule.waitForIdle()
        assertEquals("https://cloud.example.com/login/v2/flow/abc", nextBrowserUrl())
        state.value = state.value.copy(message = UiText.Raw("still waiting")) // recomposes with the same URL
        rule.waitForIdle()
        assertNull(nextBrowserUrl())
    }

    @Test fun waitingForLoginCanReopenTheBrowserOrCancel() {
        show(SettingsUiState(isConnecting = true, loginUrl = "https://cloud.example.com/login/v2/flow/abc"))
        nextBrowserUrl()
        scrollTo("Finish logging in to Nextcloud in your browser.")
        scrollTo("Open again")
        rule.onNodeWithText("Connect").assertDoesNotExist()
        click("Open again")
        assertEquals("https://cloud.example.com/login/v2/flow/abc", nextBrowserUrl())
        click("Cancel")
        verify { vm.cancelNextcloudLogin() }
    }

    // Connected to Nextcloud

    @Test fun connectedShowsTheAccountAndSyncState() {
        show(connected)
        scrollTo("dougie @ https://cloud.example.com")
        rule.onNodeWithText("Not synced yet").assertExists()
        rule.onNode(spinner).assertDoesNotExist()
        state.value = connected.copy(lastSyncedAt = System.currentTimeMillis())
        rule.onNodeWithText("Last synced just now").assertExists()
    }

    @Test fun syncNowAndDisconnectGoToTheViewModel() {
        show(connected)
        scrollTo("Sync now"); click("Sync now")
        click("Disconnect")
        verify { vm.syncNow(); vm.disconnectNextcloud() }
    }

    @Test fun whileSyncingTheSpinnerSitsBetweenTheButtons() {
        show(connected.copy(isSyncing = true))
        scrollTo("Disconnect")
        rule.onNodeWithText("Syncing…").assertIsNotEnabled()
        val sync = rule.onNodeWithText("Syncing…").getUnclippedBoundsInRoot()
        val spin = rule.onNode(spinner).getUnclippedBoundsInRoot()
        val disconnect = rule.onNodeWithText("Disconnect").getUnclippedBoundsInRoot()
        assertTrue("spinner after sync", spin.left >= sync.right)
        assertTrue("disconnect after spinner", disconnect.left >= spin.right)
        assertEquals("spinner as tall as a button", 40f, (spin.bottom - spin.top).value, 0.5f)
    }

    @Test fun disconnectMovesBackWhenSyncingStops() {
        show(connected.copy(isSyncing = true))
        scrollTo("Disconnect")
        val busy = rule.onNodeWithText("Disconnect").getUnclippedBoundsInRoot().left
        state.value = connected
        rule.waitForIdle()
        rule.onNode(spinner).assertDoesNotExist()
        assertTrue(rule.onNodeWithText("Disconnect").getUnclippedBoundsInRoot().left < busy)
        rule.onNodeWithText("Sync now").assertIsEnabled()
    }

    @Test fun theFirstSyncAfterConnectingShowsTheSpinnerNotTheBar() {
        show(connected.copy(isConnecting = true))
        scrollTo("Disconnect")
        assertEquals(1, rule.onAllNodes(spinner).fetchSemanticsNodes().size)
    }

    // Site sessions

    @Test fun signedInSitesCanBeForgotten() {
        show(SettingsUiState(sessionDomains = listOf("lwn.net")))
        scrollTo("lwn.net")
        rule.onNodeWithContentDescription("Forget session for lwn.net").performClick()
        verify { vm.removeSession("lwn.net") }
    }

    @Test fun loggingInToASiteNeedsAnHttpsAddress() {
        show()
        scrollTo("Log in to a site…"); click("Log in to a site…")
        inDialog("Open").assertIsNotEnabled()
        field("Site URL", "http://lwn.net")
        inDialog("Open").assertIsNotEnabled()
        field("Site URL", "https://lwn.net ")
        inDialog("Open").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf("https://lwn.net"), siteLogins)
    }
}
