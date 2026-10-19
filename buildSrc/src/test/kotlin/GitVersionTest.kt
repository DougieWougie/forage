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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitVersionTest {
    @Test fun `a tagged commit gives the tag without the v`() = assertEquals("0.2.0", GitVersion.name("v0.2.0\n"))

    @Test fun `commits after a tag are kept so builds can be told apart`() =
        assertEquals("0.2.0-5-g1a2b3c4", GitVersion.name("v0.2.0-5-g1a2b3c4"))

    @Test fun `no tag falls back`() {
        assertEquals(GitVersion.FALLBACK_NAME, GitVersion.name(null))
        assertEquals(GitVersion.FALLBACK_NAME, GitVersion.name(""))
        assertEquals(GitVersion.FALLBACK_NAME, GitVersion.name("fatal: No names found"))
    }

    @Test fun `commit count becomes the version code`() = assertEquals(95, GitVersion.code("95\n"))

    @Test fun `missing or nonsense count falls back`() {
        assertEquals(GitVersion.FALLBACK_CODE, GitVersion.code(null))
        assertEquals(GitVersion.FALLBACK_CODE, GitVersion.code(""))
        assertEquals(GitVersion.FALLBACK_CODE, GitVersion.code("0"))
        assertEquals(GitVersion.FALLBACK_CODE, GitVersion.code("abc"))
    }
}
