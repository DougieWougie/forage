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
package me.dougals.forage.domain.model

import java.io.IOException

/** The device has no usable network, so nothing could be reached. */
class OfflineException(cause: Throwable) : IOException("Offline", cause)

/** The network works but the server name does not resolve, which for a typed-in address usually means a typo. */
class HostNotFoundException(cause: Throwable) : IOException("Host not found", cause)

/** Whether the device currently has a network that claims internet access. */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}
