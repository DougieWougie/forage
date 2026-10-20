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
package me.dougals.forage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.dougals.forage.data.content.ImageResourceFetcher
import me.dougals.forage.data.content.ThumbnailFetcher
import me.dougals.forage.data.work.WorkManagerSchedulers
import me.dougals.forage.domain.repository.SettingsRepository
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class ForageApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var schedulers: WorkManagerSchedulers
    @Inject lateinit var images: ImageResourceFetcher
    @Inject lateinit var settings: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Article-list thumbnails: fetched by [ThumbnailFetcher] (guarded, cookie-free), cached on disk under the app cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(ThumbnailFetcher.Factory(images) { settings.settings.first().allowLocalNetwork }) }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("thumbnails").toOkioPath()).maxSizeBytes(THUMBNAIL_CACHE_BYTES).build()
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Follows the settings, so changing the interval or Wi-Fi-only takes effect immediately and applies at launch.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.map { it.refreshInterval to it.refreshWifiOnly }.distinctUntilChanged()
                .collect { (interval, wifiOnly) -> schedulers.schedulePeriodicRefresh(interval, wifiOnly) }
        }
    }

    /** Low importance: no sound or vibration, so a digest never interrupts. Users can tune it in system settings. */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(NEW_ARTICLES_CHANNEL, getString(R.string.channel_new_articles), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.channel_new_articles_body) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val NEW_ARTICLES_CHANNEL = "new-articles"
        private const val THUMBNAIL_CACHE_BYTES = 64L * 1024 * 1024
    }
}
