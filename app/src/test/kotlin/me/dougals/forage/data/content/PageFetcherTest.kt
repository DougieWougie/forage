package me.dougals.forage.data.content

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class PageFetcherTest {
    @Test fun returnsBodyText() = runTest {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().body("<p>hi</p>").build())
            assertEquals("<p>hi</p>", OkHttpPageFetcher(OkHttpClient()).fetchHtml(s.url("/").toString()))
        }
    }

    @Test fun httpErrorThrowsIOException() {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().code(404).build())
            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking { OkHttpPageFetcher(OkHttpClient()).fetchHtml(s.url("/").toString()) }
            }
        }
    }
}
