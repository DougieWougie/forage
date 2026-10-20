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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.dougals.forage.domain.notify.DigestNotifier
import me.dougals.forage.ui.BrandSplash
import me.dougals.forage.ui.ForageRoot
import me.dougals.forage.ui.theme.ForageTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var digest: DigestNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ForageTheme { BrandSplash { ForageRoot() } } }
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
