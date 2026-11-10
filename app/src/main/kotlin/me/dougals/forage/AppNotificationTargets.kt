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

import android.content.Context
import android.content.Intent
import me.dougals.forage.data.notify.NotificationTargets
import me.dougals.forage.ui.EXTRA_DIGEST_FOLDERS

/** The notifications' destinations: [MainActivity], with the digest's categories as an extra that `ui/FolderRequest.kt` reads back. */
class AppNotificationTargets(private val context: Context) : NotificationTargets {
    override fun openApp(): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    override fun openCategories(folderIds: LongArray): Intent = openApp().putExtra(EXTRA_DIGEST_FOLDERS, folderIds)
}
