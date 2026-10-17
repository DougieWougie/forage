package me.dougals.forage.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.dougals.forage.data.auth.SessionCapture
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val capture: SessionCapture) : ViewModel() {
    /** Stores whatever cookies the WebView now holds for [url]'s host. */
    fun capture(url: String): Boolean = capture.capture(url)
}

/** In-app login: the user signs in normally, then taps “Save session” to keep that host's cookies (encrypted). */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(startUrl: String, onDone: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Sign in, then save", maxLines = 1) },
                navigationIcon = { IconButton(onDone) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    TextButton({
                        val url = web?.url
                        if (url != null && vm.capture(url)) onDone()
                        else scope.launch { snackbar.showSnackbar("No session cookies found yet — finish signing in first.") }
                    }) { Text("Save session") }
                },
            )
        },
    ) { inner ->
        AndroidView(
            modifier = Modifier.padding(inner).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Scripts are required by most login forms, so this screen (and only this one) enables them.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    if (startUrl.startsWith("https://")) loadUrl(startUrl)
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
