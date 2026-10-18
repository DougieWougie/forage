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
package me.dougals.forage.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.FeedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModeAwareFeedRepositoryTest {
    private val local = mockk<FeedRepository>(relaxed = true)
    private val nextcloud = mockk<FeedRepository>(relaxed = true)
    private val mode = MutableStateFlow(SyncMode.LOCAL)
    private val repo = ModeAwareFeedRepository(mode, local, nextcloud)

    @Test fun writesFollowTheCurrentMode() = runTest {
        repo.setRead(1, true)
        coVerify { local.setRead(1, true) }
        mode.value = SyncMode.NEXTCLOUD
        repo.setRead(2, true); repo.setSaved(2, true); repo.markFeedRead(3); repo.markOlderRead(4); repo.setReadBatch(listOf(5), false); repo.refresh()
        coVerify {
            nextcloud.setRead(2, true); nextcloud.setSaved(2, true); nextcloud.markFeedRead(3)
            nextcloud.markOlderRead(4); nextcloud.setReadBatch(listOf(5), false); nextcloud.refresh()
        }
        coVerify(exactly = 0) { local.refresh() }
    }

    @Test fun structuralChangesFollowTheModeToo() = runTest {
        repo.addFeed("u", 1); repo.createFolder("f"); repo.removeFeed(1); repo.deleteFolder(1)
        coVerify { local.addFeed("u", 1); local.createFolder("f"); local.removeFeed(1); local.deleteFolder(1) }
        coVerify(exactly = 0) { nextcloud.addFeed(any(), any()) }
    }

    @Test fun movingFeedsFollowsTheMode() = runTest {
        repo.moveFeed(1, 2)
        coVerify { local.moveFeed(1, 2) }
        mode.value = SyncMode.NEXTCLOUD
        repo.moveFeed(3, null)
        coVerify { nextcloud.moveFeed(3, null) }
    }

    @Test fun searchAlwaysReadsFromLocalRoom() = runTest {
        val hits = flowOf(emptyList<me.dougals.forage.domain.model.Article>())
        every { local.search("rust") } returns hits
        mode.value = SyncMode.NEXTCLOUD
        assertEquals(hits, repo.search("rust"))
        coVerify(exactly = 0) { nextcloud.search(any()) }
    }

    @Test fun readsComeFromSharedRoomRegardlessOfMode() = runTest {
        val articles = flowOf(emptyList<me.dougals.forage.domain.model.Article>())
        every { local.observeUnread() } returns articles
        mode.value = SyncMode.NEXTCLOUD
        assertEquals(articles, repo.observeUnread())
    }

    @Test fun resultsAreReturnedUnchanged() = runTest {
        coEvery { local.refresh() } returns Result.failure(RuntimeException("x"))
        assertEquals("x", repo.refresh().exceptionOrNull()!!.message)
    }
}
