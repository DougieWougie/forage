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

import android.content.Intent

/** Extra on the digest notification's intent: the ids of the categories it announced. */
const val EXTRA_DIGEST_FOLDERS = "me.dougals.forage.DIGEST_FOLDERS"

/** A request to show the Unread list narrowed to [folderIds]. Compared by identity, so tapping the same digest twice acts twice. */
class FolderRequest(val folderIds: Set<Long>)

fun Intent.folderRequest(): FolderRequest? =
    getLongArrayExtra(EXTRA_DIGEST_FOLDERS)?.takeIf { it.isNotEmpty() }?.let { FolderRequest(it.toSet()) }
