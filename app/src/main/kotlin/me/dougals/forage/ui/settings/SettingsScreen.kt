package me.dougals.forage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.presentation.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogin: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var showConnect by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var scale by remember(state.settings.fontScale) { mutableFloatStateOf(state.settings.fontScale) }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }

    // A login finishes on another screen; pick up the new session when we come back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.reloadSessions() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Settings", style = MaterialTheme.typography.titleLarge) }, scrollBehavior = scroll) },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { Section("Sync") }
            item {
                if (state.isConnecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.settings.syncMode == SyncMode.NEXTCLOUD) {
                    Text("Nextcloud News", style = MaterialTheme.typography.titleMedium)
                    Text("${state.nextcloudUser} @ ${state.nextcloudServer}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(vm::disconnectNextcloud, Modifier.padding(top = 8.dp)) { Text("Disconnect (use local mode)") }
                } else {
                    Text("Local mode", style = MaterialTheme.typography.titleMedium)
                    Text("Feeds are fetched directly. Connect Nextcloud News to sync read and saved state across devices.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button({ showConnect = true }, Modifier.padding(top = 8.dp)) { Text("Connect Nextcloud") }
                }
            }
            item { Section("Reading") }
            item {
                Text("Typeface", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.settings.readerFont == ReaderFont.SERIF, { vm.setFont(ReaderFont.SERIF) }, label = { Text("Serif") })
                    FilterChip(state.settings.readerFont == ReaderFont.SANS, { vm.setFont(ReaderFont.SANS) }, label = { Text("Sans-serif") })
                }
                Text("Text size  ${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(scale, { scale = it }, onValueChangeFinished = { vm.setFontScale(scale) }, valueRange = AppSettings.MIN_SCALE..AppSettings.MAX_SCALE)
            }
            item { Section("Signed-in sites") }
            item {
                Text(
                    "Log in to sites that need an account (for example lwn.net) and Forage will send that session with feed and article requests to the same host only.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.sessionDomains.size) { i ->
                val domain = state.sessionDomains[i]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton({ vm.removeSession(domain) }) { Icon(Icons.Filled.Close, "Forget session for $domain") }
                }
            }
            item { OutlinedButton({ showLogin = true }, Modifier.padding(top = 8.dp)) { Text("Log in to a site…") } }
            item { Section("Network") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow feeds on local network", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Off by default. When off, feeds and articles that resolve to private or loopback addresses are refused.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.settings.allowLocalNetwork, vm::setAllowLocalNetwork)
                }
            }
        }
    }

    if (showConnect) ConnectDialog(onDismiss = { showConnect = false }, onConnect = { u, n, p -> showConnect = false; vm.connectNextcloud(u, n, p) })
    if (showLogin) {
        var url by rememberSaveable { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showLogin = false },
            title = { Text("Log in to a site") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use the same host the feed is served from, e.g. https://lwn.net/", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Site URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            },
            confirmButton = { TextButton({ showLogin = false; onLogin(url.trim()) }, enabled = url.startsWith("https://") && url.length > 8) { Text("Open") } },
            dismissButton = { TextButton({ showLogin = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(title.uppercase(), Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onConnect: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("https://") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // deliberately not saved across process death
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Nextcloud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(server, { server = it }, singleLine = true, label = { Text("Server (https)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, singleLine = true, label = { Text("Username") })
                OutlinedTextField(
                    password, { password = it }, singleLine = true, label = { Text("App password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text("Create an app password in Nextcloud under Settings → Security.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onConnect(server, user, password) }) { Text("Connect") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
