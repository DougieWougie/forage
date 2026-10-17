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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.dougals.forage.R
import me.dougals.forage.data.auth.SessionStore
import me.dougals.forage.data.nextcloud.CredentialStore
import me.dougals.forage.data.nextcloud.LoginFlowSession
import me.dougals.forage.data.nextcloud.NextcloudCredentials
import me.dougals.forage.data.nextcloud.NextcloudLoginFlow
import me.dougals.forage.data.nextcloud.SyncStateStore
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.model.ThumbnailMode
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import me.dougals.forage.presentation.UiText
import me.dougals.forage.presentation.toUiText
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Deliberately holds no password: the app password lives only in the encrypted credential store.
 * [loginUrl] is set while waiting for the user to log in to Nextcloud in the browser.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val nextcloudServer: String? = null,
    val nextcloudUser: String? = null,
    val sessionDomains: List<String> = emptyList(),
    val message: UiText? = null,
    val isConnecting: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val loginUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val credentials: CredentialStore,
    private val syncState: SyncStateStore,
    private val sessions: SessionStore,
    private val repo: FeedRepository,
    private val loginFlow: NextcloudLoginFlow,
) : ViewModel() {
    private val creds = MutableStateFlow(credentials.get())
    private val domains = MutableStateFlow(sessions.domains())
    private val message = MutableStateFlow<UiText?>(null)
    private val connecting = MutableStateFlow(false)
    private val syncing = MutableStateFlow(false)
    private val lastSynced = MutableStateFlow(syncState.lastSyncedAt)
    private val loginUrl = MutableStateFlow<String?>(null)
    private var loginJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(settingsRepo.settings, creds, domains, message, connecting, syncing, lastSynced, loginUrl) { values ->
        @Suppress("UNCHECKED_CAST")
        val c = values[1] as NextcloudCredentials?
        SettingsUiState(
            values[0] as AppSettings, c?.serverUrl, c?.username, values[2] as List<String>,
            values[3] as UiText?, values[4] as Boolean, values[5] as Boolean, (values[6] as Long).takeIf { it > 0 },
            values[7] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun connectNextcloud(rawUrl: String, rawUser: String, password: String) {
        val server = rawUrl.trim().trimEnd('/')
        val user = rawUser.trim()
        when {
            server.isEmpty() || user.isEmpty() || password.isEmpty() -> message.value = UiText.Res(R.string.connect_fill_in_all)
            !server.startsWith("https://", ignoreCase = true) -> message.value = UiText.Res(R.string.connect_requires_https)
            else -> viewModelScope.launch {
                connecting.value = true
                connect(NextcloudCredentials(server, user, password))
                connecting.value = false
            }
        }
    }

    /** Starts Login Flow v2: the screen opens [SettingsUiState.loginUrl], and we poll until the user grants access. */
    fun logInToNextcloud(rawUrl: String) {
        val typed = rawUrl.trim()
        val server = typed.trimEnd('/')
        when {
            !typed.startsWith("https://", ignoreCase = true) -> message.value = UiText.Res(R.string.connect_requires_https)
            server.length <= "https://".length -> message.value = UiText.Res(R.string.connect_enter_server)
            else -> {
                loginJob?.cancel()
                loginJob = viewModelScope.launch {
                    connecting.value = true
                    try {
                        val session = loginFlow.start(server)
                        loginUrl.value = session.loginUrl
                        val granted = withTimeoutOrNull(LOGIN_TIMEOUT) { awaitGrant(session) }
                        loginUrl.value = null
                        if (granted == null) message.value = UiText.Res(R.string.login_timed_out) else connect(granted)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message.value = UiText.Res(R.string.login_failed, listOf(e.toUiText(R.string.unknown_error)))
                    } finally {
                        loginUrl.value = null
                        connecting.value = false
                    }
                }
            }
        }
    }

    fun cancelNextcloudLogin() { loginJob?.cancel() }

    private suspend fun awaitGrant(session: LoginFlowSession): NextcloudCredentials {
        while (true) {
            try {
                loginFlow.poll(session)?.let { return it }
            } catch (_: IOException) {
                // the network often drops while the user is switching to and from the browser; keep trying
            }
            delay(POLL_INTERVAL)
        }
    }

    private suspend fun connect(c: NextcloudCredentials) {
        credentials.save(c)
        creds.value = credentials.get()
        syncState.lastModified = 0 // always start from a full pull
        settingsRepo.setSyncMode(SyncMode.NEXTCLOUD)
        val result = repo.refresh()
        if (result.isSuccess) {
            message.value = UiText.Res(R.string.connected_to, listOf(c.serverUrl))
        } else {
            credentials.clear(); creds.value = null
            syncState.lastModified = 0
            settingsRepo.setSyncMode(SyncMode.LOCAL)
            message.value = syncFailed(result.exceptionOrNull())
            revoke(c) // e.g. the News app isn't installed: don't leave an unused app password behind
        }
    }

    /** Best effort, and only for app passwords the server created for us; a pasted one may be in use elsewhere. */
    private suspend fun revoke(c: NextcloudCredentials) {
        if (!c.fromLoginFlow) return
        try { loginFlow.revoke(c) } catch (_: IOException) { }
    }

    /** Flushes queued local changes, then pulls. Failures leave the connection intact. */
    fun syncNow() {
        if (!syncing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val result = try { repo.refresh() } finally { syncing.value = false }
            lastSynced.value = syncState.lastSyncedAt
            message.value = result.fold({ it.toUiText() }, ::syncFailed)
        }
    }

    private fun syncFailed(e: Throwable?): UiText =
        UiText.Res(R.string.sync_failed, listOf(e?.toUiText(R.string.unknown_error) ?: UiText.Res(R.string.unknown_error)))

    fun disconnectNextcloud() {
        viewModelScope.launch {
            val c = credentials.get()
            credentials.clear(); creds.value = null
            settingsRepo.setSyncMode(SyncMode.LOCAL)
            c?.let { revoke(it) }
        }
    }

    fun removeSession(domain: String) { sessions.remove(domain); reloadSessions() }
    fun reloadSessions() { domains.value = sessions.domains(); lastSynced.value = syncState.lastSyncedAt }
    fun setAllowLocalNetwork(allow: Boolean) { viewModelScope.launch { settingsRepo.setAllowLocalNetwork(allow) } }
    fun setPullToRefresh(enabled: Boolean) { viewModelScope.launch { settingsRepo.setPullToRefresh(enabled) } }
    fun setThumbnails(mode: ThumbnailMode) { viewModelScope.launch { settingsRepo.setThumbnails(mode) } }
    fun setThumbnailSide(side: ThumbnailSide) { viewModelScope.launch { settingsRepo.setThumbnailSide(side) } }
    fun setFont(font: ReaderFont) { viewModelScope.launch { settingsRepo.setReaderFont(font) } }
    fun setFontScale(scale: Float) { viewModelScope.launch { settingsRepo.setFontScale(scale) } }
    fun dismissMessage() { message.value = null }

    private companion object {
        val POLL_INTERVAL = 2.seconds
        val LOGIN_TIMEOUT = 20.minutes // the server's poll token lives this long
    }
}
