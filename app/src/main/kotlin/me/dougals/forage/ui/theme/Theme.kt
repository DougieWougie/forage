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
package me.dougals.forage.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android < 12: newsprint paper, near-black ink, one vermilion accent. High contrast.
private val Light = lightColorScheme(
    primary = Color(0xFFB4360F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0), onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF3D3B35), tertiary = Color(0xFF1F6B4F), tertiaryContainer = Color(0xFFBFF0D8),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFFBFAF6), onBackground = Color(0xFF121210),
    surface = Color(0xFFFBFAF6), onSurface = Color(0xFF121210),
    surfaceVariant = Color(0xFFEAE7DE), onSurfaceVariant = Color(0xFF3F3D36),
    outline = Color(0xFF6E6B60),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A5B), onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF7A2200), onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFD6D3C8), tertiary = Color(0xFF7ED6A9), tertiaryContainer = Color(0xFF00503A),
    onTertiaryContainer = Color(0xFFBFF0D8),
    background = Color(0xFF0E0E0C), onBackground = Color(0xFFF3F1EA),
    surface = Color(0xFF0E0E0C), onSurface = Color(0xFFF3F1EA),
    surfaceVariant = Color(0xFF26251F), onSurfaceVariant = Color(0xFFD0CDC1),
    outline = Color(0xFF99968A),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForageTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = ForageTypography, content = content)
}
