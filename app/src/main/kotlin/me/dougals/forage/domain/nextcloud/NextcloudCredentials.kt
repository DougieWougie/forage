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
package me.dougals.forage.domain.nextcloud

/**
 * [appPassword] is a Nextcloud app password, never the account password. [fromLoginFlow] marks one the
 * server created for Forage, which is safe to revoke on disconnect; one the user pasted in may be shared.
 */
data class NextcloudCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val fromLoginFlow: Boolean = false,
)

interface CredentialStore {
    fun get(): NextcloudCredentials?
    fun save(credentials: NextcloudCredentials)
    fun clear()
}
