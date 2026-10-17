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
package me.dougals.forage.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import me.dougals.forage.data.auth.SessionCapture
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val capture: SessionCapture) : ViewModel() {
    /** Stores whatever cookies the WebView now holds for [url]'s host. */
    fun capture(url: String): Boolean = capture.capture(url)
}

/** Removes every WebView cookie and web-storage entry, so a login never sees or leaves a stale session. */
private suspend fun clearWebSession() {
    suspendCancellableCoroutine { cont ->
        CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
    }
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}

/**
 * In-app login. The WebView cookie jar is wiped before the page loads and again when the screen closes: the only
 * lasting copy of the session is the one [SessionCapture] writes to encrypted storage, scoped to a single host.
 * In-app login: the user signs in normally, then taps “Save session” to keep that host's cookies (encrypted). */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(startUrl: String, onDone: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(web) {
        val view = web ?: return@LaunchedEffect
        clearWebSession()
        if (startUrl.startsWith("https://")) view.loadUrl(startUrl)
    }
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData() }
    }

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
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
