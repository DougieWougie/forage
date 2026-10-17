package me.dougals.forage.data.nextcloud

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NextcloudApiProviderTest {
    private class MemCreds(var value: NextcloudCredentials?) : CredentialStore {
        override fun get() = value
        override fun save(credentials: NextcloudCredentials) { value = credentials }
        override fun clear() { value = null }
    }

    private lateinit var server: MockWebServer
    @BeforeEach fun up() { server = MockWebServer().apply { start() } }
    @AfterEach fun down() { server.close() }

    private fun provider(creds: NextcloudCredentials?, allowInsecure: Boolean = true) =
        RetrofitNextcloudApiProvider(MemCreds(creds), OkHttpClient(), allowInsecure)

    private val foldersJson = """{"folders":[{"id":1,"name":"Tech","opened":true,"feeds":[]}]}"""

    @Test fun callsVersionedEndpointWithBasicAuthAndParsesJson() = runTest {
        server.enqueue(MockResponse.Builder().body(foldersJson).build())
        val creds = NextcloudCredentials(server.url("/").toString(), "dougie", "app-pass")
        val result = provider(creds).api().folders()
        assertEquals(listOf(FolderDto(1, "Tech")), result.folders)
        val req = server.takeRequest()
        assertEquals("/index.php/apps/news/api/v1-3/folders", req.url.encodedPath)
        assertEquals("Basic ZG91Z2llOmFwcC1wYXNz", req.headers["Authorization"])
    }

    @Test fun serverUrlWithSubpathAndNoTrailingSlash() = runTest {
        server.enqueue(MockResponse.Builder().body(foldersJson).build())
        val creds = NextcloudCredentials(server.url("/nextcloud").toString(), "u", "p")
        provider(creds).api().folders()
        assertEquals("/nextcloud/index.php/apps/news/api/v1-3/folders", server.takeRequest().url.encodedPath)
    }

    @Test fun credentialsAreNotSentOverPlainHttpByDefault() = runTest {
        server.enqueue(MockResponse.Builder().body(foldersJson).build())
        val creds = NextcloudCredentials(server.url("/").toString(), "u", "p")
        provider(creds, allowInsecure = false).api().folders()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test fun unknownResponseFieldsAreIgnored() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"feeds":[{"id":5,"url":"u","title":"t","folderId":null,"unreadCount":3,"pinned":true,"ordering":0}],"starredCount":1}""").build())
        val creds = NextcloudCredentials(server.url("/").toString(), "u", "p")
        val feed = provider(creds).api().feeds().feeds.single()
        assertEquals(FeedDto(5, "u", "t", null, 3), feed)
    }

    @Test fun missingCredentialsThrowNotConfigured() {
        assertThrows(NextcloudNotConfiguredException::class.java) { provider(null).api() }
    }
}
