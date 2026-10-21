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
package me.dougals.forage.data.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.dougals.forage.ForageApp
import me.dougals.forage.MainActivity
import me.dougals.forage.R
import me.dougals.forage.domain.notify.DigestLine
import me.dougals.forage.domain.notify.DigestNotifier
import me.dougals.forage.ui.EXTRA_DIGEST_FOLDERS

class AndroidDigestNotifier(private val context: Context) : DigestNotifier {
    private val manager = NotificationManagerCompat.from(context)

    // areNotificationsEnabled() is false while POST_NOTIFICATIONS is ungranted, so the guard covers the lint rule.
    @SuppressLint("MissingPermission")
    override fun post(lines: List<DigestLine>) {
        if (ForageApp.appVisible || !manager.areNotificationsEnabled()) return
        val total = lines.sumOf { it.count }
        // UPDATE_CURRENT swaps in this digest's categories; the request code is shared because only one digest is shown.
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_DIGEST_FOLDERS, lines.map { it.folderId }.toLongArray()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val summary = if (lines.size == 1) detail(lines.single()) else lines.joinToString(" · ") { "${name(it)} ${it.count}" }
        val notification = NotificationCompat.Builder(context, ForageApp.NEW_ARTICLES_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.resources.getQuantityString(R.plurals.new_articles, total, total))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n") { expanded(it, if (lines.size == 1) MAX_HEADLINES_ALONE else MAX_HEADLINES_SHARED) }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(DIGEST_ID, notification)
    }

    private fun name(line: DigestLine) = line.folder ?: context.getString(R.string.uncategorized)

    private fun expanded(line: DigestLine, headlines: Int): String {
        val feeds = feedsOf(line)
        val heading = "${name(line)} · ${line.count}" + if (feeds.isEmpty()) "" else " — $feeds"
        return (listOf(heading) + line.headlines.take(headlines).map { "• ${it.trim().replace(WHITESPACE, " ")}" }).joinToString("\n")
    }

    /** A lone category has room for what the count means, so it says which feeds the articles came from. */
    private fun detail(line: DigestLine) = listOf(name(line), feedsOf(line)).filter { it.isNotEmpty() }.joinToString(" · ")

    private fun feedsOf(line: DigestLine): String {
        val shown = line.feeds.take(MAX_FEEDS).joinToString(", ")
        val hidden = line.feeds.size - MAX_FEEDS
        return if (hidden > 0) context.getString(R.string.digest_more_feeds, shown, hidden) else shown
    }

    override fun clear() = manager.cancel(DIGEST_ID)

    private companion object {
        const val DIGEST_ID = 1
        const val MAX_FEEDS = 2
        // Expanded text is a few lines tall, so categories sharing it quote less than a lone one.
        const val MAX_HEADLINES_ALONE = 3
        const val MAX_HEADLINES_SHARED = 2
        val WHITESPACE = Regex("\\s+")
    }
}
