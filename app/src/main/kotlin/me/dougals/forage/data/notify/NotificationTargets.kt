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

import android.content.Intent

/** Notification channel ids; the channels themselves are created at start-up. */
object NotificationChannels {
    const val NEW_ARTICLES = "new-articles"
    const val UNREAD = "unread-count"
}

/**
 * Where a notification leads. The notifiers are told what to open instead of naming the activity, so `data` does not
 * depend on the UI; the app's own implementation lives next to the activity.
 */
interface NotificationTargets {
    /** Opens the app on its usual first screen. */
    fun openApp(): Intent

    /** Opens the Unread list narrowed to the categories with these folder ids. */
    fun openCategories(folderIds: LongArray): Intent
}
