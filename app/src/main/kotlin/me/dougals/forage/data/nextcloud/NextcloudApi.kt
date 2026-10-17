package me.dougals.forage.data.nextcloud

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class FolderDto(val id: Long, val name: String)
@Serializable data class FeedDto(
    val id: Long, val url: String, val title: String, val folderId: Long? = null,
    val unreadCount: Int = 0, val link: String? = null,
)
@Serializable data class ItemDto(
    val id: Long, val guid: String, val url: String? = null, val title: String? = null,
    val author: String? = null, val pubDate: Long? = null, val body: String? = null,
    val feedId: Long, val unread: Boolean, val starred: Boolean, val lastModified: Long,
)

@Serializable data class FoldersResponse(val folders: List<FolderDto> = emptyList())
@Serializable data class FeedsResponse(val feeds: List<FeedDto> = emptyList())
@Serializable data class ItemsResponse(val items: List<ItemDto> = emptyList())
@Serializable data class CreateFolderRequest(val name: String)
@Serializable data class CreateFeedRequest(val url: String, val folderId: Long? = null)
@Serializable data class ItemIdsRequest(val items: List<Long>)

/** Nextcloud News REST API v1-3, relative to `/index.php/apps/news/api/v1-3/`. */
interface NextcloudApi {
    @GET("folders") suspend fun folders(): FoldersResponse
    @POST("folders") suspend fun createFolder(@Body body: CreateFolderRequest): FoldersResponse
    @DELETE("folders/{id}") suspend fun deleteFolder(@Path("id") id: Long)

    @GET("feeds") suspend fun feeds(): FeedsResponse
    @POST("feeds") suspend fun createFeed(@Body body: CreateFeedRequest): FeedsResponse
    @DELETE("feeds/{id}") suspend fun deleteFeed(@Path("id") id: Long)

    /** [type]: 0 feed, 1 folder, 2 starred, 3 all. */
    @GET("items") suspend fun items(
        @Query("type") type: Int,
        @Query("getRead") getRead: Boolean,
        @Query("batchSize") batchSize: Int = -1,
        @Query("offset") offset: Long = 0,
    ): ItemsResponse

    @GET("items/updated") suspend fun updatedItems(
        @Query("lastModified") lastModified: Long,
        @Query("type") type: Int = 3,
        @Query("id") id: Long = 0,
    ): ItemsResponse

    @PUT("items/read/multiple") suspend fun markRead(@Body body: ItemIdsRequest)
    @PUT("items/unread/multiple") suspend fun markUnread(@Body body: ItemIdsRequest)
    @PUT("items/star/multiple") suspend fun star(@Body body: ItemIdsRequest)
    @PUT("items/unstar/multiple") suspend fun unstar(@Body body: ItemIdsRequest)
}
