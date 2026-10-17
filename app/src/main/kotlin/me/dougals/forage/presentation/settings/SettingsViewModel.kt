package me.dougals.forage.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.data.auth.SessionStore
import me.dougals.forage.data.nextcloud.CredentialStore
import me.dougals.forage.data.nextcloud.NextcloudCredentials
import me.dougals.forage.data.nextcloud.SyncStateStore
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import javax.inject.Inject

/** Deliberately holds no password: the app password lives only in the encrypted credential store. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val nextcloudServer: String? = null,
    val nextcloudUser: String? = null,
    val sessionDomains: List<String> = emptyList(),
    val message: String? = null,
    val isConnecting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val credentials: CredentialStore,
    private val syncState: SyncStateStore,
    private val sessions: SessionStore,
    private val repo: FeedRepository,
) : ViewModel() {
    private val creds = MutableStateFlow(credentials.get())
    private val domains = MutableStateFlow(sessions.domains())
    private val message = MutableStateFlow<String?>(null)
    private val connecting = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settingsRepo.settings, creds, domains, message, connecting) { s, c, d, m, busy ->
        SettingsUiState(s, c?.serverUrl, c?.username, d, m, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun connectNextcloud(rawUrl: String, rawUser: String, password: String) {
        val server = rawUrl.trim().trimEnd('/')
        val user = rawUser.trim()
        when {
            server.isEmpty() || user.isEmpty() || password.isEmpty() -> message.value = "Fill in server, username and app password"
            !server.startsWith("https://", ignoreCase = true) -> message.value = "Nextcloud server must use https://"
            else -> viewModelScope.launch {
                connecting.value = true
                credentials.save(NextcloudCredentials(server, user, password))
                creds.value = credentials.get()
                syncState.lastModified = 0 // always start from a full pull
                settingsRepo.setSyncMode(SyncMode.NEXTCLOUD)
                val result = repo.refresh()
                if (result.isSuccess) {
                    message.value = "Connected to $server"
                } else {
                    credentials.clear(); creds.value = null
                    syncState.lastModified = 0
                    settingsRepo.setSyncMode(SyncMode.LOCAL)
                    message.value = "Could not sync: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                }
                connecting.value = false
            }
        }
    }

    fun disconnectNextcloud() {
        viewModelScope.launch {
            credentials.clear(); creds.value = null
            settingsRepo.setSyncMode(SyncMode.LOCAL)
        }
    }

    fun removeSession(domain: String) { sessions.remove(domain); reloadSessions() }
    fun reloadSessions() { domains.value = sessions.domains() }
    fun setFont(font: ReaderFont) { viewModelScope.launch { settingsRepo.setReaderFont(font) } }
    fun setFontScale(scale: Float) { viewModelScope.launch { settingsRepo.setFontScale(scale) } }
    fun dismissMessage() { message.value = null }
}
