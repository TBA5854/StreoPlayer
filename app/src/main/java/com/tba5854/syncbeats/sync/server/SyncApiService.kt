package com.tba5854.syncbeats.sync.server

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface SyncApiService {
    @Multipart
    @POST("files/upload")
    fun uploadFile(
            @Part file: MultipartBody.Part,
            @Part("file_name") fileName: RequestBody? = null,
            @Part("hash") hash: RequestBody
    ): Call<UploadResponse>

    @GET("files/list") fun listFiles(): Call<FileListResponse>

    @GET("rooms/list") fun listRooms(): Call<List<RoomInfo>>

    @GET("files/download")
    @Streaming
    fun downloadFile(@Query("file_id") fileId: String): Call<ResponseBody>
}
