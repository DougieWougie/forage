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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.SyncMode
import me.dougals.forage.domain.repository.FeedRepository
import io.mockk.coEvery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeAwareFeedRepositoryTest {
    private val local = mockk<FeedRepository>(relaxed = true)
    private val nextcloud = mockk<FeedRepository>(relaxed = true)
    private val mode = MutableStateFlow(SyncMode.LOCAL)
    private var online = true
    private val repo = ModeAwareFeedRepository(mode, local, nextcloud, network = { online })

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

    @Test fun offlineStorageAlwaysLivesInLocalRoom() = runTest {
        mode.value = SyncMode.NEXTCLOUD
        repo.clearOfflineCopies(true); repo.observeOfflineUsage()
        coVerify { local.clearOfflineCopies(true); local.observeOfflineUsage() }
        coVerify(exactly = 0) { nextcloud.clearOfflineCopies(any()) }
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

    @Test fun everyReadIsServedByLocalRoomInBothModes() = runTest {
        val articles = flowOf(emptyList<me.dougals.forage.domain.model.Article>())
        val folders = flowOf(emptyList<me.dougals.forage.domain.model.Folder>())
        val feeds = flowOf(emptyList<me.dougals.forage.domain.model.Feed>())
        every { local.observeSaved() } returns articles
        every { local.observeFeedArticles(7) } returns articles
        every { local.observeArticle(8) } returns flowOf(null)
        every { local.observeFolders() } returns folders
        every { local.observeFeeds() } returns feeds
        listOf(SyncMode.LOCAL, SyncMode.NEXTCLOUD).forEach {
            mode.value = it
            assertEquals(articles, repo.observeSaved())
            assertEquals(articles, repo.observeFeedArticles(7))
            assertEquals(folders, repo.observeFolders())
            assertEquals(feeds, repo.observeFeeds())
            repo.observeArticle(8)
        }
        coVerify { local.observeArticle(8) }
        coVerify(exactly = 0) { nextcloud.observeSaved(); nextcloud.observeFeedArticles(any()); nextcloud.observeArticle(any()); nextcloud.observeFolders(); nextcloud.observeFeeds() }
    }

    @Test fun resultsAreReturnedUnchanged() = runTest {
        coEvery { local.refresh() } returns Result.failure(RuntimeException("x"))
        assertEquals("x", repo.refresh().exceptionOrNull()!!.message)
    }

    @Test fun refreshesNeverOverlapAndEachCallerRunsItsOwn() = runTest {
        var running = 0
        var peak = 0
        var runs = 0
        coEvery { local.refresh() } coAnswers {
            running++; runs++; peak = maxOf(peak, running)
            kotlinx.coroutines.delay(100)
            running--
            Result.success(me.dougals.forage.domain.model.SyncReport())
        }
        List(3) { launch { repo.refresh() } }.joinAll()
        assertEquals(1, peak)
        assertEquals(3, runs)
    }

    @Test fun nextcloudAddFeedWaitsForARunningRefresh() = runTest {
        mode.value = SyncMode.NEXTCLOUD
        var running = 0
        var peak = 0
        coEvery { nextcloud.refresh() } coAnswers {
            running++; peak = maxOf(peak, running); kotlinx.coroutines.delay(100); running--
            Result.success(me.dougals.forage.domain.model.SyncReport())
        }
        coEvery { nextcloud.addFeed(any(), any()) } coAnswers {
            running++; peak = maxOf(peak, running); kotlinx.coroutines.delay(100); running--
            Result.failure(RuntimeException())
        }
        listOf(launch { repo.refresh() }, launch { repo.addFeed("u", null) }).joinAll()
        assertEquals(1, peak)
    }

    @Test fun aFailedRefreshReleasesTheLock() = runTest {
        coEvery { local.refresh() } throws IllegalStateException("boom") andThen Result.success(me.dougals.forage.domain.model.SyncReport())
        runCatching { repo.refresh() }
        assertEquals(true, repo.refresh().isSuccess)
    }

    private fun dnsFailure() = me.dougals.forage.data.remote.FeedFetchException("x", cause = java.net.UnknownHostException("a.example"))

    @Test fun aFailedLookupWhileOfflineIsReportedAsOffline() = runTest {
        online = false
        coEvery { local.addFeed(any(), any()) } returns Result.failure(dnsFailure())
        coEvery { local.refresh() } returns Result.failure(dnsFailure())
        assertTrue(repo.addFeed("u", null).exceptionOrNull() is me.dougals.forage.domain.model.OfflineException)
        assertTrue(repo.refresh().exceptionOrNull() is me.dougals.forage.domain.model.OfflineException)
    }

    @Test fun aFailedLookupWhileOnlineIsAMistypedAddress() = runTest {
        coEvery { local.addFeed(any(), any()) } returns Result.failure(dnsFailure())
        assertTrue(repo.addFeed("u", null).exceptionOrNull() is me.dougals.forage.domain.model.HostNotFoundException)
    }

    @Test fun theGuardsRefusalIsNeitherOfflineNorATypo() = runTest {
        online = false
        val refusal = me.dougals.forage.data.net.NonPublicAddressException("internal.example")
        coEvery { local.addFeed(any(), any()) } returns Result.failure(refusal)
        assertEquals(refusal, repo.addFeed("u", null).exceptionOrNull())
    }

    @Test fun otherFailuresPassThroughUntouched() = runTest {
        online = false
        val e = java.io.IOException("HTTP 500")
        coEvery { local.refresh() } returns Result.failure(e)
        assertEquals(e, repo.refresh().exceptionOrNull())
    }

    @Test fun aPartialRefreshKeepsItsReportButClassifiesTheCause() = runTest {
        online = false
        val report = me.dougals.forage.domain.model.SyncReport(newArticles = 2)
        coEvery { local.refresh() } returns Result.failure(me.dougals.forage.domain.model.PartialRefreshException(report, dnsFailure()))
        val e = repo.refresh().exceptionOrNull() as me.dougals.forage.domain.model.PartialRefreshException
        assertEquals(report, e.report)
        assertTrue(e.cause is me.dougals.forage.domain.model.OfflineException)
    }
}
