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
package me.dougals.forage.domain.app

/** Whether an activity of the app is on screen. Notifications that would only linger in front of the user are skipped while it is. */
interface AppVisibility {
    val isVisible: Boolean
}

/** The holder the activity updates; everything else sees it as [AppVisibility]. */
class AppVisibilityTracker : AppVisibility {
    @Volatile override var isVisible: Boolean = false
}
