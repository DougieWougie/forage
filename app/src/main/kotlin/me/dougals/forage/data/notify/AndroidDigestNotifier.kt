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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.dougals.forage.ForageApp
import me.dougals.forage.R
import me.dougals.forage.domain.notify.DigestLine
import me.dougals.forage.domain.notify.DigestNotifier

class AndroidDigestNotifier(private val context: Context) : DigestNotifier {
    private val manager = NotificationManagerCompat.from(context)

    // areNotificationsEnabled() is false while POST_NOTIFICATIONS is ungranted, so the guard covers the lint rule.
    @SuppressLint("MissingPermission")
    override fun post(lines: List<DigestLine>) {
        if (ForageApp.appVisible || !manager.areNotificationsEnabled()) return
        val total = lines.sumOf { it.count }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val labelled = lines.map { (it.folder ?: context.getString(R.string.uncategorized)) to it.count }
        val summary = labelled.joinToString(" · ") { (name, count) -> "$name $count" }
        val notification = NotificationCompat.Builder(context, ForageApp.NEW_ARTICLES_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.resources.getQuantityString(R.plurals.new_articles, total, total))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(labelled.joinToString("\n") { (name, count) -> "$name · $count" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(DIGEST_ID, notification)
    }

    override fun clear() = manager.cancel(DIGEST_ID)

    private companion object { const val DIGEST_ID = 1 }
}
