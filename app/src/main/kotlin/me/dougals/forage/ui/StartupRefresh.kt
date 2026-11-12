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
package me.dougals.forage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.presentation.startup.StartupRefreshViewModel

/** Shows [content] straight away and a thin progress bar over it while the launch refresh runs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartupRefresh(vm: StartupRefreshViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        if (state.refreshing) {
            val fraction = state.progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total }
            val bar = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            if (fraction != null) LinearWavyProgressIndicator(progress = { fraction }, modifier = bar)
            else LinearWavyProgressIndicator(modifier = bar)
        }
    }
}
