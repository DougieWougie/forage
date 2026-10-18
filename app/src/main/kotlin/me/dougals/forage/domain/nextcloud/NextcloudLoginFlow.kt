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

/** [loginUrl] is opened in the browser; [pollToken] is a secret, so this is never persisted. */
data class LoginFlowSession(val serverUrl: String, val loginUrl: String, val pollEndpoint: String, val pollToken: String) {
    override fun toString() = "LoginFlowSession($serverUrl)"
}

/**
 * Nextcloud Login Flow v2: the user logs in on their server's own web page (so 2FA and SSO work) and
 * the server hands back an app password created for this device.
 * See https://docs.nextcloud.com/server/stable/developer_manual/client_apis/LoginFlow/
 */
interface NextcloudLoginFlow {
    suspend fun start(serverUrl: String): LoginFlowSession
    /** null until the user has granted access in the browser. */
    suspend fun poll(session: LoginFlowSession): NextcloudCredentials?
    /** Deletes the app password on the server, removing it from Devices & sessions. */
    suspend fun revoke(credentials: NextcloudCredentials)
}
