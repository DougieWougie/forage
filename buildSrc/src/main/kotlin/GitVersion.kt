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

/** App version numbers derived from git, so a release needs a tag and nothing else. */
object GitVersion {
    const val FALLBACK_NAME = "0.1.0"
    const val FALLBACK_CODE = 1

    /**
     * [describe] is the output of `git describe --tags --match v*`: `v0.2.0` on a tagged commit,
     * `v0.2.0-5-g1a2b3c4` after it. Blank or missing (no tag, no git) gives [FALLBACK_NAME].
     */
    fun name(describe: String?): String {
        val tag = describe?.trim().orEmpty().removePrefix("v")
        return if (tag.firstOrNull()?.isDigit() == true) tag else FALLBACK_NAME
    }

    /** [commitCount] is `git rev-list --count HEAD`; it only ever grows, which is what Play needs. */
    fun code(commitCount: String?): Int =
        commitCount?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: FALLBACK_CODE
}
