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
package me.dougals.forage.presentation.splash

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.FeedProgress
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repo = mockk<FeedRepository>()
    private val progress = RefreshProgress()

    @Test fun reportsNewArticlesThenWaitsForATap() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.refresh() } coAnswers { gate.await(); Result.success(SyncReport(newArticles = 12)) }
        val vm = SplashViewModel(repo, progress)
        assertFalse(vm.state.value.ready)

        vm.continueNow() // too early: the result hasn't been shown yet
        gate.complete(Unit)
        advanceTimeBy(1)
        assertTrue(vm.state.value.ready)
        assertEquals(12, vm.state.value.newArticles)
        assertFalse(vm.state.value.finished)

        vm.continueNow()
        advanceTimeBy(1)
        assertTrue(vm.state.value.finished)
    }

    @Test fun continuesOnItsOwnIfNobodyTaps() = runTest {
        coEvery { repo.refresh() } returns Result.success(SyncReport())
        val vm = SplashViewModel(repo, progress)
        advanceTimeBy(SplashViewModel.AUTO_MS - 1)
        assertFalse(vm.state.value.finished)
        advanceTimeBy(2)
        assertTrue(vm.state.value.finished)
        assertEquals(0, vm.state.value.newArticles)
    }

    @Test fun aFailedRefreshHasNoCount() = runTest {
        coEvery { repo.refresh() } returns Result.failure(java.io.IOException("offline"))
        val vm = SplashViewModel(repo, progress)
        advanceTimeBy(1)
        assertTrue(vm.state.value.ready)
        assertNull(vm.state.value.newArticles)
    }

    @Test fun givesUpOnASlowRefresh() = runTest {
        coEvery { repo.refresh() } coAnswers { CompletableDeferred<Unit>().await(); Result.success(SyncReport()) }
        val vm = SplashViewModel(repo, progress)
        advanceTimeBy(SplashViewModel.TIMEOUT_MS + 1)
        assertTrue(vm.state.value.ready)
        assertNull(vm.state.value.newArticles)
    }

    @Test fun exposesFeedProgress() = runTest {
        coEvery { repo.refresh() } coAnswers { CompletableDeferred<Unit>().await(); Result.success(SyncReport()) }
        val vm = SplashViewModel(repo, progress)
        progress.report(2, 5)
        assertEquals(FeedProgress(2, 5), vm.state.value.progress)
    }
}
