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
package me.dougals.forage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import me.dougals.forage.domain.notify.DigestNotifier
import me.dougals.forage.ui.BrandSplash
import me.dougals.forage.ui.FolderRequest
import me.dougals.forage.ui.ForageRoot
import me.dougals.forage.ui.folderRequest
import me.dougals.forage.ui.theme.ForageTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var digest: DigestNotifier

    private var folderRequest by mutableStateOf<FolderRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a recreation the intent is the one already acted on.
        if (savedInstanceState == null) folderRequest = intent.folderRequest()
        setContent { ForageTheme { BrandSplash { ForageRoot(folderRequest = folderRequest, onFolderRequestHandled = { folderRequest = null }) } } }
    }

    /** A digest tapped while the app is already running (the activity is singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.folderRequest()?.let { folderRequest = it }
    }

    override fun onStop() {
        ForageApp.appVisible = false
        super.onStop()
    }

    /** Opening the app is what the digest asked for, so it shouldn't linger. */
    override fun onStart() {
        super.onStart()
        ForageApp.appVisible = true
        digest.clear()
    }
}
