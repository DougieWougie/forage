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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.dougals.forage.R
import me.dougals.forage.presentation.splash.SplashViewModel

private const val FADE_MS = 300
private val STATUS_HEIGHT = 136.dp

/** Covers [content] with the wordmark and refresh progress on a cold start; not repeated on rotation. */
@Composable
fun BrandSplash(content: @Composable () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (!dismissed) SplashOverlay(onFinished = { dismissed = true })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplashOverlay(onFinished: () -> Unit, vm: SplashViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) { delay(FADE_MS.toLong()); onFinished() } }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    AnimatedVisibility(!state.finished, exit = fadeOut(tween(FADE_MS))) {
        Box(
            Modifier.fillMaxSize().background(colorResource(R.color.splash_background))
                .clickable(enabled = state.ready, interactionSource = null, indication = null, onClick = vm::continueNow),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.logo_wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.width(240.dp),
                )
                // A fixed slot so the wordmark doesn't shift between progress and result.
                Column(Modifier.padding(top = 16.dp).height(STATUS_HEIGHT), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val progress = state.progress
                    if (state.ready) {
                        val found = state.newArticles
                        Text(
                            if (found == null) stringResource(R.string.splash_update_failed)
                            else if (found == 0) stringResource(R.string.splash_no_new)
                            else pluralStringResource(R.plurals.splash_new_articles, found, found),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (found != null && found > 0 && state.categories > 0) {
                            Text(
                                pluralStringResource(R.plurals.splash_in_categories, state.categories, state.categories),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.splash_tap_to_continue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(pulse),
                        )
                    } else {
                        val fraction = progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total }
                        if (fraction != null) {
                            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.width(160.dp))
                        } else {
                            LinearWavyProgressIndicator(Modifier.width(160.dp))
                        }
                        Text(
                            if (progress != null) stringResource(R.string.splash_updating_progress, progress.done + 1, progress.total)
                            else stringResource(R.string.splash_updating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
