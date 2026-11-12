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
import me.dougals.forage.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

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

/**
 * The failure's own message when it has one (it comes from a server or the network), otherwise [fallback].
 * Web addresses are removed first: a feed URL can carry a secret token, and an error line is easy to screenshot.
 */
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    if (isOffline()) UiText.Res(R.string.error_offline) else message?.withoutUrls()?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: UiText.Res(fallback)

/**
 * True when the network could not be reached at all (DNS failure, no route, refused). The SSRF guard also throws
 * [UnknownHostException], for a host that resolves to a private address; that one is not an offline error.
 */
internal fun Throwable.isOffline(): Boolean = generateSequence(this) { it.cause }.take(8).any {
    (it is UnknownHostException && it.message?.contains("non-public") != true) || it is ConnectException || it is NoRouteToHostException
}

private val urlPattern = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://\S+""")

/** Replaces anything shaped like `scheme://…` with a short placeholder, keeping the rest of the message. */
internal fun String.withoutUrls(): String = replace(urlPattern, "<link>")
