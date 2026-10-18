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
package me.dougals.forage.data.opml

import me.dougals.forage.domain.opml.ImportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.repository.FeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpmlTransferTest {
    private val repo = mockk<FeedRepository>(relaxed = true)
    private fun feed(id: Long, url: String, title: String = "t", folder: Long? = null) = Feed(id, url, title, null, folder, 0)

    private fun setup(folders: List<Folder> = listOf(Folder(1, "Tech")), feeds: List<Feed> = listOf(feed(1, "https://existing"))) {
        every { repo.observeFolders() } returns flowOf(folders)
        every { repo.observeFeeds() } returns flowOf(feeds)
        coEvery { repo.addFeed(any(), any()) } returns Result.success(feed(99, "x"))
        coEvery { repo.createFolder("News") } returns Result.success(Folder(10, "News"))
    }

    private val opml = """
        <opml version="2.0"><body>
          <outline text="tech"><outline text="LWN" xmlUrl="https://lwn.net/rss"/><outline text="Dup" xmlUrl="https://existing"/></outline>
          <outline text="News"><outline text="BBC" xmlUrl="https://bbc/rss"/></outline>
          <outline text="Loose" xmlUrl="https://loose/rss"/>
        </body></opml>""".trimIndent()

    @Test fun createsMissingFoldersOnceReusesExistingByNameAndSkipsKnownFeeds() = runTest {
        setup()
        val result = OpmlTransfer(repo).import(opml.byteInputStream()).getOrThrow()
        assertEquals(ImportResult(added = 3, skipped = 1, failed = 0), result)
        coVerify(exactly = 1) { repo.createFolder(any()) }
        coVerify { repo.addFeed("https://lwn.net/rss", 1); repo.addFeed("https://bbc/rss", 10); repo.addFeed("https://loose/rss", null) }
        coVerify(exactly = 0) { repo.addFeed("https://existing", any()) }
    }

    @Test fun aFailingFeedIsCountedAndTheRestContinue() = runTest {
        setup()
        coEvery { repo.addFeed("https://bbc/rss", any()) } returns Result.failure(RuntimeException("404"))
        val result = OpmlTransfer(repo).import(opml.byteInputStream()).getOrThrow()
        assertEquals(ImportResult(added = 2, skipped = 1, failed = 1), result)
    }

    @Test fun feedsStillImportWhenFolderCreationFails() = runTest {
        setup()
        coEvery { repo.createFolder("News") } returns Result.failure(RuntimeException("no"))
        OpmlTransfer(repo).import(opml.byteInputStream()).getOrThrow()
        coVerify { repo.addFeed("https://bbc/rss", null) }
    }

    @Test fun notOpmlIsAFailure() = runTest {
        setup()
        assertTrue(OpmlTransfer(repo).import("<html/>".byteInputStream()).isFailure)
        assertTrue(OpmlTransfer(repo).import("not xml at all".byteInputStream()).isFailure)
    }

    @Test fun exportGroupsByFolderAndRoundTrips() = runTest {
        setup(feeds = listOf(feed(1, "https://lwn.net/rss", "LWN", folder = 1), feed(2, "https://loose/rss", "Loose")))
        val xml = OpmlTransfer(repo).export()
        assertEquals(
            listOf(OpmlFeed("LWN", "https://lwn.net/rss", "Tech"), OpmlFeed("Loose", "https://loose/rss", null)),
            Opml.parse(xml.byteInputStream()),
        )
    }

    @Test fun nonHttpUrlsAreRejectedBeforeTouchingTheRepository() = runTest {
        setup()
        val hostile = """
            <opml version="2.0"><body>
              <outline text="a" xmlUrl="file:///etc/passwd"/>
              <outline text="b" xmlUrl="javascript:alert(1)"/>
              <outline text="c" xmlUrl="ftp://example.com/feed"/>
              <outline text="d" xmlUrl="not a url"/>
              <outline text="ok" xmlUrl="HTTPS://example.com/feed"/>
            </body></opml>""".trimIndent()
        val result = OpmlTransfer(repo).import(hostile.byteInputStream()).getOrThrow()
        assertEquals(ImportResult(added = 1, skipped = 0, failed = 4), result)
        coVerify(exactly = 1) { repo.addFeed(any(), any()) }
    }
}
