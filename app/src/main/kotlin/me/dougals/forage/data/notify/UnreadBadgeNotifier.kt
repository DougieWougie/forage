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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.dougals.forage.ForageApp
import me.dougals.forage.MainActivity
import me.dougals.forage.R
import me.dougals.forage.domain.repository.FeedRepository

/**
 * Keeps one silent notification alive while any article is unread. Launchers draw their icon badge from an app's active
 * notifications, so this is what puts the unread count on the icon; it disappears when everything has been read.
 */
class UnreadBadgeNotifier(private val context: Context, private val repo: FeedRepository) {
    private val manager = NotificationManagerCompat.from(context)

    fun start(scope: CoroutineScope) {
        scope.launch {
            repo.observeFeeds().map { feeds -> feeds.sumOf { it.unreadCount } }.distinctUntilChanged().collect(::show)
        }
    }

    // areNotificationsEnabled() is false while POST_NOTIFICATIONS is ungranted, so the guard covers the lint rule.
    @SuppressLint("MissingPermission")
    private fun show(unread: Int) {
        if (unread == 0 || !manager.areNotificationsEnabled()) return manager.cancel(BADGE_ID)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ForageApp.UNREAD_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.resources.getQuantityString(R.plurals.unread_articles, unread, unread))
            .setContentIntent(open)
            .setNumber(unread)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        manager.notify(BADGE_ID, notification)
    }

    private companion object {
        // The digest uses id 1.
        const val BADGE_ID = 2
    }
}
