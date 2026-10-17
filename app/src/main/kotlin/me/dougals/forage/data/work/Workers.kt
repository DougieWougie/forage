package me.dougals.forage.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.dougals.forage.data.nextcloud.NextcloudNotConfiguredException
import java.util.concurrent.TimeUnit

fun interface SyncScheduler { fun scheduleFlush() }
fun interface ArchiveScheduler { fun schedule(articleId: Long) }

fun interface FlushAction { suspend fun flush(): Result<Unit> }
fun interface ArchiveAction { suspend fun archive(articleId: Long): Result<Unit> }

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

    private companion object { const val FLUSH_WORK = "nextcloud-flush" }
}

@HiltWorker
class FlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flusher: FlushAction,
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
    private val archiver: ArchiveAction,
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
