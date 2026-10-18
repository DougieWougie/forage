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
package me.dougals.forage.presentation.nav

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import me.dougals.forage.testutil.awaitUntil
import me.dougals.forage.testutil.feed
import me.dougals.forage.ui.TabBadges
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TabBadgesViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()


    @Test fun totalsUnreadArticlesAndCountsFeedsWithNewOnes() = runTest {
        val repo = mockk<FeedRepository>(relaxed = true)
        every { repo.observeFeeds() } returns flowOf(listOf(feed(1, unread = 4), feed(2), feed(3, unread = 2)))
        TabBadgesViewModel(repo).state.test {
            assertEquals(TabBadges(unread = 6, updatedFeeds = 2), awaitUntil { it.unread > 0 })
        }
    }
}
