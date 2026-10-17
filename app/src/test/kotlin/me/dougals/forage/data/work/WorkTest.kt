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
import kotlinx.coroutines.runBlocking
import me.dougals.forage.data.nextcloud.NextcloudNotConfiguredException
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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

    @Test fun flushWorkerMapsOutcomes() = runBlocking {
        fun run(result: Result<Unit>) =
            build<FlushWorker>({ c, p -> FlushWorker(c, p) { result } }).let { runBlocking { it.doWork() } }
        assertEquals(ListenableWorker.Result.success(), run(Result.success(Unit)))
        assertEquals(ListenableWorker.Result.retry(), run(Result.failure(java.io.IOException())))
        assertEquals(ListenableWorker.Result.failure(), run(Result.failure(NextcloudNotConfiguredException())))
    }

    @Test fun archiveWorkerRetriesThenGivesUp() = runBlocking {
        fun run(attempt: Int, result: Result<Unit>): ListenableWorker.Result {
            val seen = mutableListOf<Long>()
            val w = build<ArchiveWorker>(
                { c, p -> ArchiveWorker(c, p) { id -> seen += id; result } },
                attempt, workDataOf(ArchiveWorker.KEY_ARTICLE_ID to 9L),
            )
            return runBlocking { w.doWork() }.also { assertEquals(listOf(9L), seen) }
        }
        assertEquals(ListenableWorker.Result.success(), run(0, Result.success(Unit)))
        assertEquals(ListenableWorker.Result.retry(), run(1, Result.failure(java.io.IOException())))
        assertEquals(ListenableWorker.Result.failure(), run(5, Result.failure(java.io.IOException())))
    }
}
