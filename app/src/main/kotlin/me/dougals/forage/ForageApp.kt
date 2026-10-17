package me.dougals.forage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import me.dougals.forage.data.work.WorkManagerSchedulers
import javax.inject.Inject

@HiltAndroidApp
class ForageApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var schedulers: WorkManagerSchedulers

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        schedulers.schedulePeriodicRefresh()
    }
}
