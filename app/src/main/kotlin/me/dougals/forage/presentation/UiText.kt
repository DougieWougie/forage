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
package me.dougals.forage.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text a view model wants shown, without resolving it itself: view models stay free of a [Context],
 * tests compare values, and the screen picks the language at display time.
 * Arguments may be plain values or other [UiText].
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    data class Raw(val value: String) : UiText
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.resolved(context))
        is Plural -> context.resources.getQuantityString(id, count, *args.resolved(context))
        is Raw -> value
        is Joined -> parts.joinToString(separator) { it.resolve(context) }
    }
}

private fun List<Any>.resolved(context: Context): Array<Any> = map { if (it is UiText) it.resolve(context) else it }.toTypedArray()

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)

/** The failure's own message when it has one (it comes from a server or the network), otherwise [fallback]. */
fun Throwable.toUiText(@StringRes fallback: Int): UiText = message?.let(UiText::Raw) ?: UiText.Res(fallback)
