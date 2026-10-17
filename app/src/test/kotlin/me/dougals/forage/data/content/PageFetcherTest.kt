package me.dougals.forage.data.content

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

class PageFetcherTest {
    private fun fetcher(allowPrivate: Boolean = true, maxBytes: Long = 1_000_000) =
        OkHttpPageFetcher(OkHttpClient(), allowPrivateNetworks = allowPrivate, maxBytes = maxBytes)

    @Test fun returnsBodyText() = runTest {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().body("<p>hi</p>").build())
            assertEquals("<p>hi</p>", fetcher().fetchHtml(s.url("/").toString()))
        }
    }

    @Test fun httpErrorThrowsIOException() {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().code(404).build())
            assertThrows(IOException::class.java) { runBlocking { fetcher().fetchHtml(s.url("/").toString()) } }
        }
    }

    @Test fun loopbackIsBlockedByDefaultAndNoRequestIsSent() {
        MockWebServer().use { s ->
            s.start()
            assertThrows(IOException::class.java) {
                runBlocking { fetcher(allowPrivate = false).fetchHtml(s.url("/").toString()) }
            }
            assertEquals(0, s.requestCount)
        }
    }

    @Test fun oversizedBodiesAreRejected() {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().body("x".repeat(5_000)).build())
            assertThrows(IOException::class.java) { runBlocking { fetcher(maxBytes = 1_000).fetchHtml(s.url("/").toString()) } }
        }
    }

    private fun dnsReturning(vararg ip: String) = Dns { _ -> ip.map { InetAddress.getByName(it) } }

    @Test fun publicAddressesPass() {
        val dns = PublicOnlyDns(dnsReturning("93.184.216.34", "2606:2800:220:1:248:1893:25c8:1946"))
        assertEquals(2, dns.lookup("example.com").size)
    }

    @Test fun nonPublicAddressesAreRejected() {
        listOf(
            "127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.1.1", "169.254.169.254", "100.64.0.1",
            "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1",
        ).forEach { ip ->
            assertThrows(UnknownHostException::class.java, { PublicOnlyDns(dnsReturning(ip)).lookup("evil.test") }, ip)
        }
    }

    @Test fun oneBadAddressAmongPublicOnesRejectsTheHost() {
        // DNS-rebinding style answers: refuse the whole host rather than pick the "good" record.
        assertThrows(UnknownHostException::class.java) {
            PublicOnlyDns(dnsReturning("93.184.216.34", "10.0.0.1")).lookup("mixed.test")
        }
        assertTrue(true)
    }
}
