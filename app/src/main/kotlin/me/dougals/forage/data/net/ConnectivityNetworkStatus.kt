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
package me.dougals.forage.data.net

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import me.dougals.forage.domain.model.NetworkStatus

class ConnectivityNetworkStatus(private val cm: ConnectivityManager?) : NetworkStatus {
    override fun isOnline(): Boolean {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
