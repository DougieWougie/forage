package me.dougals.forage.data.content

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress

class FetchedImage(val mimeType: String, val bytes: ByteArray)

/**
 * Loads reader images on behalf of the reader WebView, so the WebView itself never opens a socket: article HTML is
 * untrusted and could point `<img>` at the LAN or a metadata address. Goes through [GuardedHttp] and serves
 * responses whose content type is an image only.
 */
class ImageResourceFetcher(
    client: OkHttpClient,
    private val maxBytes: Long = 15_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) {
    private val http = GuardedHttp(client, addressPolicy)

    suspend fun fetch(url: String, allowPrivate: Boolean): Result<FetchedImage> = try {
        Result.success(
            http.get(url, allowPrivate) { r ->
                val type = r.body.contentType()
                if (type?.type != "image") throw IOException("Not an image: ${type ?: "no content type"}")
                FetchedImage("${type.type}/${type.subtype}", r.readBytesBounded(maxBytes))
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
