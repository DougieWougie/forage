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

    @Test fun ipLiteralHostsBypassDnsSoTheyAreCheckedExplicitly() {
        MockWebServer().use { s ->
            s.start()
            val literal = s.url("/").newBuilder().host("127.0.0.1").build().toString()
            assertThrows(IOException::class.java) { runBlocking { fetcher(allowPrivate = false).fetchHtml(literal) } }
            assertThrows(IOException::class.java) {
                runBlocking { fetcher(allowPrivate = false).fetchHtml("http://[::1]:${s.port}/") }
            }
            assertEquals(0, s.requestCount)
        }
    }

    @Test fun redirectToAnInternalLiteralIsRefusedBeforeConnecting() {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://169.254.169.254/latest/meta-data/").build())
            // Policy lets the test server (loopback) through but is otherwise the real one.
            val f = OkHttpPageFetcher(OkHttpClient(), addressPolicy = { it.isLoopbackAddress || isPublicAddress(it) })
            val e = assertThrows(IOException::class.java) { runBlocking { f.fetchHtml(s.url("/").toString()) } }
            assertTrue(e.message!!.contains("non-public"), e.message)
            assertEquals(1, s.requestCount)
        }
    }

    @Test fun redirectsAreFollowedAndCappedWhenAllowed() {
        MockWebServer().use { s ->
            s.start()
            s.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/final").build())
            s.enqueue(MockResponse.Builder().body("<p>ok</p>").build())
            assertEquals("<p>ok</p>", runBlocking { fetcher().fetchHtml(s.url("/start").toString()) })
            repeat(12) { s.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/again").build()) }
            assertThrows(IOException::class.java) { runBlocking { fetcher().fetchHtml(s.url("/loop").toString()) } }
        }
    }

    @Test fun strictAddressClassification() {
        listOf(
            "0.1.2.3", "10.1.1.1", "100.64.0.1", "127.0.0.2", "169.254.1.1", "172.31.0.1", "192.0.0.8", "192.0.2.1",
            "192.168.0.1", "198.18.0.1", "198.19.255.1", "198.51.100.7", "203.0.113.9", "224.0.0.1", "240.0.0.1",
            "255.255.255.255", "::", "::1", "::2", "fe80::1", "fec0::1", "fc00::1", "ff02::1", "64:ff9b::7f00:1",
            "64:ff9b:1::1", "2002:7f00:1::", "2001::1", "2001:db8::1", "::ffff:10.0.0.1", "1000::1",
        ).forEach { assertTrue(!isPublicAddress(InetAddress.getByName(it)), "$it must be rejected") }
        listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "198.20.0.1", "2606:4700:4700::1111", "2a00:1450:4001::1")
            .forEach { assertTrue(isPublicAddress(InetAddress.getByName(it)), "$it must be allowed") }
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
