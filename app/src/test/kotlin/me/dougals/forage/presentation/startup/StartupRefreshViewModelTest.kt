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
package me.dougals.forage.presentation.startup

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.FeedProgress
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.testutil.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class StartupRefreshViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repo = mockk<FeedRepository>()
    private val progress = RefreshProgress()

    @Test fun refreshesOnceAtLaunchAndStopsShowingProgressWhenDone() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.refresh() } coAnswers { gate.await(); Result.success(SyncReport(newArticles = 3)) }
        val vm = StartupRefreshViewModel(repo, progress)
        advanceUntilIdle()
        assertTrue(vm.state.value.refreshing)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.refreshing)
        io.mockk.coVerify(exactly = 1) { repo.refresh() }
    }

    @Test fun aFailedRefreshStillClearsTheProgressBar() = runTest {
        coEvery { repo.refresh() } returns Result.failure(java.io.IOException("offline"))
        val vm = StartupRefreshViewModel(repo, progress)
        advanceUntilIdle()
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun exposesFeedProgress() = runTest {
        coEvery { repo.refresh() } coAnswers { CompletableDeferred<Unit>().await(); Result.success(SyncReport()) }
        val vm = StartupRefreshViewModel(repo, progress)
        progress.report(2, 5)
        assertEquals(FeedProgress(2, 5), vm.state.value.progress)
    }
}
