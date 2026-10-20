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
package me.dougals.forage.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.dougals.forage.data.content.OfflineArchiver
import me.dougals.forage.data.nextcloud.NextcloudNotConfiguredException
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.model.SyncReport
import me.dougals.forage.domain.notify.NewArticleDigest
import me.dougals.forage.domain.repository.FeedRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkTest {
    private lateinit var context: Context

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @Test fun flushIsUniqueWorkRequiringNetwork() {
        val s = WorkManagerSchedulers(context)
        s.scheduleFlush()
        s.scheduleFlush()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("nextcloud-flush").get()
        assertEquals(WorkInfo.State.ENQUEUED, infos.first().state)
        assertEquals(NetworkType.CONNECTED, infos.first().constraints.requiredNetworkType)
    }

    @Test fun archiveIsUniquePerArticleAndRequiresNetwork() {
        val s = WorkManagerSchedulers(context)
        s.schedule(5)
        s.schedule(5)
        s.schedule(6)
        val wm = WorkManager.getInstance(context)
        assertEquals(1, wm.getWorkInfosForUniqueWork("archive-5").get().size)
        val info = wm.getWorkInfosForUniqueWork("archive-6").get().single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    private inline fun <reified W : ListenableWorker> build(
        crossinline create: (Context, WorkerParameters) -> ListenableWorker,
        attempt: Int = 0,
        input: androidx.work.Data = androidx.work.Data.EMPTY,
    ): W = TestListenableWorkerBuilder<W>(context, input)
        .setRunAttemptCount(attempt)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, p: WorkerParameters) = create(c, p)
        }).build()

    private fun flusher(result: Result<Unit>) = mockk<PendingChangeFlusher> { coEvery { flush() } returns result }
    private fun archiver(result: Result<Unit>, seen: MutableList<Long>) = mockk<OfflineArchiver> {
        coEvery { archive(any()) } coAnswers { seen += firstArg<Long>(); result }
    }
    private fun repo(result: Result<Unit>) = mockk<FeedRepository> { coEvery { refresh() } returns result.map { SyncReport() } }

    private fun digest(baseline: Boolean = true) = mockk<NewArticleDigest>(relaxed = true) { coEvery { hasBaseline() } returns baseline }

    @Test fun refreshWorkerAnnouncesWhatARefreshFoundOnlyOnceThereIsABaseline() = runBlocking {
        val report = SyncReport(newArticles = 2, newByFeed = mapOf(1L to 2))
        fun run(baseline: Boolean): NewArticleDigest {
            val d = digest(baseline)
            val repo = mockk<FeedRepository> { coEvery { refresh() } returns Result.success(report) }
            runBlocking { build<RefreshWorker>({ c, p -> RefreshWorker(c, p, repo, d) }).doWork() }
            return d
        }
        val announced = run(baseline = true)
        val skipped = run(baseline = false)
        io.mockk.coVerify(exactly = 1) { announced.announce(report) }
        io.mockk.coVerify(exactly = 0) { skipped.announce(any()) }
    }

    @Test fun refreshWorkerAnnouncesWhatArrivedEvenWhenSomeFeedsFailedAndStillRetries() = runBlocking {
        val report = SyncReport(newArticles = 1, newByFeed = mapOf(2L to 1))
        val d = digest()
        val repo = mockk<FeedRepository> {
            coEvery { refresh() } returns Result.failure(me.dougals.forage.domain.model.PartialRefreshException(report, java.io.IOException("down")))
        }
        val result = build<RefreshWorker>({ c, p -> RefreshWorker(c, p, repo, d) }).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        io.mockk.coVerify(exactly = 1) { d.announce(report) }
    }

    @Test fun flushWorkerMapsOutcomes() = runBlocking {
        fun run(result: Result<Unit>) =
            build<FlushWorker>({ c, p -> FlushWorker(c, p, flusher(result)) }).let { runBlocking { it.doWork() } }
        assertEquals(ListenableWorker.Result.success(), run(Result.success(Unit)))
        assertEquals(ListenableWorker.Result.retry(), run(Result.failure(java.io.IOException())))
        assertEquals(ListenableWorker.Result.failure(), run(Result.failure(NextcloudNotConfiguredException())))
    }

    @Test fun archiveWorkerRetriesThenGivesUp() = runBlocking {
        fun run(attempt: Int, result: Result<Unit>): ListenableWorker.Result {
            val seen = mutableListOf<Long>()
            val w = build<ArchiveWorker>(
                { c, p -> ArchiveWorker(c, p, archiver(result, seen)) },
                attempt, workDataOf(ArchiveWorker.KEY_ARTICLE_ID to 9L),
            )
            return runBlocking { w.doWork() }.also { assertEquals(listOf(9L), seen) }
        }
        assertEquals(ListenableWorker.Result.success(), run(0, Result.success(Unit)))
        assertEquals(ListenableWorker.Result.retry(), run(1, Result.failure(java.io.IOException())))
        assertEquals(ListenableWorker.Result.failure(), run(5, Result.failure(java.io.IOException())))
    }

    @Test fun refreshWorkerRetriesThenGivesUpUntilTheNextPeriod() = runBlocking {
        fun run(result: Result<Unit>, attempt: Int = 0) =
            build<RefreshWorker>({ c, p -> RefreshWorker(c, p, repo(result), digest()) }, attempt).let { runBlocking { it.doWork() } }
        assertEquals(ListenableWorker.Result.success(), run(Result.success(Unit)))
        assertEquals(ListenableWorker.Result.retry(), run(Result.failure(java.io.IOException())))
        assertEquals(ListenableWorker.Result.failure(), run(Result.failure(java.io.IOException()), attempt = 3))
    }

    @Test fun refreshWorkerDoesNotRetryWhenNextcloudIsNotConfigured() = runBlocking {
        val w = build<RefreshWorker>({ c, p -> RefreshWorker(c, p, repo(Result.failure(NextcloudNotConfiguredException())), digest()) })
        assertEquals(ListenableWorker.Result.failure(), w.doWork())
    }

    @Test fun periodicRefreshIsUniqueAndNeedsNetwork() {
        val s = WorkManagerSchedulers(context)
        s.schedulePeriodicRefresh(RefreshInterval.HOURLY, wifiOnly = false)
        s.schedulePeriodicRefresh(RefreshInterval.HOURLY, wifiOnly = false)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("periodic-refresh").get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), infos.single().periodicityInfo!!.repeatIntervalMillis)
    }

    @Test fun changingTheRefreshSettingsReplacesTheSchedule() {
        val s = WorkManagerSchedulers(context)
        s.schedulePeriodicRefresh(RefreshInterval.HOURLY, wifiOnly = false)
        s.schedulePeriodicRefresh(RefreshInterval.EVERY_6_HOURS, wifiOnly = true)
        val info = WorkManager.getInstance(context).getWorkInfosForUniqueWork("periodic-refresh").get().single()
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(6), info.periodicityInfo!!.repeatIntervalMillis)
    }

    @Test fun turningBackgroundRefreshOffCancelsIt() {
        val s = WorkManagerSchedulers(context)
        s.schedulePeriodicRefresh(RefreshInterval.HOURLY, wifiOnly = false)
        s.schedulePeriodicRefresh(RefreshInterval.OFF, wifiOnly = false)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("periodic-refresh").get()
        assertEquals(WorkInfo.State.CANCELLED, infos.single().state)
    }
}
