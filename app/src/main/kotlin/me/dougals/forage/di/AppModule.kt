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
package me.dougals.forage.di

import android.content.Context
import android.webkit.CookieManager
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.dougals.forage.domain.auth.SessionCapture
import me.dougals.forage.data.auth.SessionCookieInterceptor
import me.dougals.forage.domain.auth.SessionStore
import me.dougals.forage.data.auth.encryptedSessionStore
import me.dougals.forage.data.content.ImageResourceFetcher
import me.dougals.forage.data.content.OfflineArchiver
import me.dougals.forage.data.content.OkHttpPageFetcher
import me.dougals.forage.data.content.PageFetcher
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.domain.nextcloud.CredentialStore
import me.dougals.forage.data.nextcloud.NextcloudApiProvider
import me.dougals.forage.domain.nextcloud.NextcloudLoginFlow
import me.dougals.forage.data.nextcloud.NextcloudSyncer
import me.dougals.forage.data.nextcloud.OkHttpNextcloudLoginFlow
import me.dougals.forage.data.nextcloud.PendingChangeFlusher
import me.dougals.forage.data.nextcloud.PrefsSyncStateStore
import me.dougals.forage.data.nextcloud.RetrofitNextcloudApiProvider
import me.dougals.forage.domain.nextcloud.SyncStateStore
import me.dougals.forage.data.nextcloud.encryptedCredentialStore
import me.dougals.forage.data.opml.OpmlTransfer
import me.dougals.forage.domain.opml.OpmlExchange
import me.dougals.forage.data.suggestions.AssetSuggestionCatalogue
import me.dougals.forage.domain.suggest.SuggestionCatalogue
import me.dougals.forage.data.remote.FeedFetcher
import me.dougals.forage.data.remote.FeedParser
import me.dougals.forage.data.remote.FeedSource
import me.dougals.forage.data.repository.LocalFeedRepository
import me.dougals.forage.data.repository.ModeAwareFeedRepository
import me.dougals.forage.data.repository.NextcloudFeedRepository
import me.dougals.forage.data.settings.DataStoreSettingsRepository
import me.dougals.forage.data.settings.settingsDataStore
import me.dougals.forage.data.work.ArchiveAction
import me.dougals.forage.data.work.ArchiveScheduler
import me.dougals.forage.data.work.FlushAction
import me.dougals.forage.data.work.RefreshAction
import me.dougals.forage.data.work.SyncScheduler
import me.dougals.forage.data.work.WorkManagerSchedulers
import me.dougals.forage.domain.model.RefreshProgress
import me.dougals.forage.domain.repository.FeedRepository
import me.dougals.forage.domain.repository.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Client for fetching feeds and article pages: carries per-host session cookies, nothing else. */
@Qualifier annotation class WebClient

/** Client for the Nextcloud API: no cookie interceptor; Basic auth is added by the API provider. */
@Qualifier annotation class ApiClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): ForageDatabase =
        Room.databaseBuilder(c, ForageDatabase::class.java, "forage.db")
            .addMigrations(ForageDatabase.MIGRATION_1_2).build()

    @Provides @Singleton fun sessionStore(@ApplicationContext c: Context): SessionStore = encryptedSessionStore(c)
    @Provides @Singleton fun credentialStore(@ApplicationContext c: Context): CredentialStore = encryptedCredentialStore(c)
    @Provides @Singleton
    fun syncState(@ApplicationContext c: Context): SyncStateStore =
        PrefsSyncStateStore(c.getSharedPreferences("sync_state", Context.MODE_PRIVATE))

    @Provides @Singleton
    fun settings(@ApplicationContext c: Context): SettingsRepository = DataStoreSettingsRepository(c.settingsDataStore)

    @Provides @Singleton @WebClient
    fun webClient(sessions: SessionStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(SessionCookieInterceptor(sessions))
        .build()

    @Provides @Singleton @ApiClient
    fun apiClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun nextcloudApi(creds: CredentialStore, @ApiClient client: OkHttpClient): NextcloudApiProvider =
        RetrofitNextcloudApiProvider(creds, client)

    @Provides @Singleton
    fun nextcloudLoginFlow(@ApiClient client: OkHttpClient): NextcloudLoginFlow = OkHttpNextcloudLoginFlow(client)

    @Provides @Singleton
    fun feedSource(@WebClient client: OkHttpClient, settings: SettingsRepository): FeedSource =
        FeedFetcher(client, FeedParser(), allowLocalNetwork = { settings.settings.first().allowLocalNetwork })

    @Provides @Singleton
    fun pageFetcher(@WebClient client: OkHttpClient, settings: SettingsRepository): PageFetcher =
        OkHttpPageFetcher(client, allowPrivateNetworks = { settings.settings.first().allowLocalNetwork })

    @Provides @Singleton
    fun imageFetcher(@ApiClient client: OkHttpClient): ImageResourceFetcher = ImageResourceFetcher(client)

    @Provides @Singleton fun schedulers(@ApplicationContext c: Context) = WorkManagerSchedulers(c)
    @Provides fun syncScheduler(s: WorkManagerSchedulers): SyncScheduler = s
    @Provides fun archiveScheduler(s: WorkManagerSchedulers): ArchiveScheduler = s

    @Provides @Singleton fun flusher(db: ForageDatabase, api: NextcloudApiProvider) = PendingChangeFlusher(db, api)
    @Provides @Singleton
    fun syncer(db: ForageDatabase, api: NextcloudApiProvider, state: SyncStateStore, archive: ArchiveScheduler) =
        NextcloudSyncer(db, api, state, archive)
    @Provides @Singleton fun archiver(db: ForageDatabase, fetcher: PageFetcher) = OfflineArchiver(db, fetcher)

    @Provides fun flushAction(f: PendingChangeFlusher): FlushAction = f
    @Provides fun archiveAction(a: OfflineArchiver): ArchiveAction = a
    @Provides fun refreshAction(repo: FeedRepository): RefreshAction = RefreshAction { repo.refresh().map { } }

    @Provides @Singleton fun refreshProgress() = RefreshProgress()

    @Provides @Singleton
    fun feedRepository(
        db: ForageDatabase, source: FeedSource, api: NextcloudApiProvider, flusher: PendingChangeFlusher,
        syncer: NextcloudSyncer, sync: SyncScheduler, archive: ArchiveScheduler, settings: SettingsRepository,
        progress: RefreshProgress,
    ): FeedRepository = ModeAwareFeedRepository(
        mode = settings.settings.map { it.syncMode }.distinctUntilChanged(),
        local = LocalFeedRepository(db, source, archive, progress = progress),
        nextcloud = NextcloudFeedRepository(db, api, flusher, syncer, sync, archive),
    )

    @Provides @Singleton fun suggestionCatalogue(@ApplicationContext c: Context): SuggestionCatalogue = AssetSuggestionCatalogue(c)

    @Provides fun opmlExchange(repo: FeedRepository): OpmlExchange = OpmlTransfer(repo)

    @Provides
    fun sessionCapture(store: SessionStore) = SessionCapture({ url -> CookieManager.getInstance().getCookie(url) }, store)
}
