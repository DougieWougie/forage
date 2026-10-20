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
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.dougals.forage.data.content.OfflineArchiver
import me.dougals.forage.data.nextcloud.NextcloudNotConfiguredException
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.domain.model.RefreshInterval
import me.dougals.forage.domain.notify.NewArticleDigest
import me.dougals.forage.domain.repository.FeedRepository
import java.util.concurrent.TimeUnit

fun interface SyncScheduler { fun scheduleFlush() }
fun interface ArchiveScheduler { fun schedule(articleId: Long) }

class WorkManagerSchedulers(context: Context) : SyncScheduler, ArchiveScheduler {
    private val wm = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleFlush() {
        // REPLACE is safe: a cancelled flush leaves its rows queued (deleted only after success).
        val request = OneTimeWorkRequestBuilder<FlushWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(FLUSH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun schedule(articleId: Long) {
        val request = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(ArchiveWorker.KEY_ARTICLE_ID to articleId))
            .build()
        wm.enqueueUniqueWork("archive-$articleId", ExistingWorkPolicy.KEEP, request)
    }

    /** Applies the chosen background refresh: cancels it for [RefreshInterval.OFF], otherwise (re)schedules it. */
    fun schedulePeriodicRefresh(interval: RefreshInterval, wifiOnly: Boolean) {
        if (interval == RefreshInterval.OFF) {
            wm.cancelUniqueWork(PERIODIC_REFRESH_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(interval.hours, TimeUnit.HOURS).setConstraints(constraints).build()
        // UPDATE keeps the existing schedule's timing where it can, so re-applying unchanged settings at launch is harmless.
        wm.enqueueUniquePeriodicWork(PERIODIC_REFRESH_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val FLUSH_WORK = "nextcloud-flush"
        const val PERIODIC_REFRESH_WORK = "periodic-refresh"
    }
}

@HiltWorker
class FlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flusher: PendingChangeFlusher,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = flusher.flush()
        return when {
            outcome.isSuccess -> Result.success()
            outcome.exceptionOrNull() is NextcloudNotConfiguredException -> Result.failure()
            else -> Result.retry()
        }
    }
}

@HiltWorker
class ArchiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val archiver: OfflineArchiver,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ARTICLE_ID, -1)
        if (id == -1L) return Result.failure()
        return when {
            archiver.archive(id).isSuccess -> Result.success()
            runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }
    }

    companion object {
        const val KEY_ARTICLE_ID = "articleId"
        private const val MAX_ATTEMPTS = 5
    }
}

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: FeedRepository,
    private val digest: NewArticleDigest,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val announceable = digest.hasBaseline()
        val outcome = repo.refresh()
        outcome.getOrNull()?.takeIf { announceable }?.let { digest.announce(it) }
        return when {
            outcome.isSuccess -> Result.success()
            // Nothing to retry until the user connects; the next period tries again.
            outcome.exceptionOrNull() is NextcloudNotConfiguredException -> Result.failure()
            runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }
    }

    private companion object { const val MAX_ATTEMPTS = 3 }
}
