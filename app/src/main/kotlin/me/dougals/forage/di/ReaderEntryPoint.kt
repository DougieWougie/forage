package me.dougals.forage.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.dougals.forage.data.content.ImageResourceFetcher

/** Lets the reader WebView client (not Hilt-managed) reach the guarded image fetcher. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderEntryPoint {
    fun imageFetcher(): ImageResourceFetcher
}
