package com.tba5854.syncbeats.sync.server

import android.util.Log
import com.tba5854.syncbeats.settings.Settings
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SyncApi {
    private const val TAG = "SyncApi"

    private var _service: SyncApiService? = null
    private val service: SyncApiService
        get() {
            if (_service == null) {
                var base = Settings.config.serverIp
                if (!base.endsWith("/")) base += "/"

                _service =
                        Retrofit.Builder()
                                .baseUrl(base)
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
                                .create(SyncApiService::class.java)
            }
            return _service!!
        }

    fun reset() {
        _service = null
    }

    fun uploadFile(file: File, fileName: String? = null): String {
        val md5 =
                file.inputStream().use { stream ->
                    val digest = MessageDigest.getInstance("MD5")
                    val buf = ByteArray(8192)
                    var read: Int
                    while (stream.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
                    digest.digest().joinToString("") { "%02x".format(it) }
                }

        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", fileName ?: file.name, requestFile)
        val fileNameBody = (fileName ?: file.name).toRequestBody("text/plain".toMediaTypeOrNull())
        val hashBody = md5.toRequestBody("text/plain".toMediaTypeOrNull())

        val response = service.uploadFile(body, fileNameBody, hashBody).execute()
        if (response.isSuccessful) {
            val fileId = response.body()?.fileId ?: throw RuntimeException("No file_id in response")
            Log.d(TAG, "Uploaded ${file.name} → $fileId (md5=$md5)")
            return fileId
        } else {
            val error = response.errorBody()?.string() ?: "unknown error"
            throw RuntimeException("Upload failed (${response.code()}): $error")
        }
    }

    fun listRooms(): List<RoomInfo> {
        val response = service.listRooms().execute()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            val error = response.errorBody()?.string() ?: "unknown error"
            throw RuntimeException("List rooms failed (${response.code()}): $error")
        }
    }

    fun listFiles(): List<RemoteFile> {
        val response = service.listFiles().execute()
        if (response.isSuccessful) {
            return response.body()?.files ?: emptyList()
        } else {
            val error = response.errorBody()?.string() ?: "unknown error"
            throw RuntimeException("List files failed (${response.code()}): $error")
        }
    }

    fun downloadFile(fileId: String, destDir: File): File {
        val response = service.downloadFile(fileId).execute()
        if (!response.isSuccessful) {
            val error = response.errorBody()?.string() ?: "unknown error"
            throw RuntimeException("Download failed (${response.code()}): $error")
        }

        val body = response.body() ?: throw RuntimeException("Empty response body")

        var fileName = fileId
        val cd = response.headers()["Content-Disposition"]
        if (cd != null) {
            val match = Regex("filename=\"([^\"]+)\"").find(cd)
            if (match != null) {
                fileName = match.groupValues[1]
            } else {
                val matchUnquoted = Regex("filename=([^;]+)").find(cd)
                if (matchUnquoted != null) {
                    fileName = matchUnquoted.groupValues[1].trim()
                }
            }
        }

        val dest = File(destDir, fileName)

        FileOutputStream(dest).use { out -> body.byteStream().use { it.copyTo(out) } }

        Log.d(TAG, "Downloaded $fileId (name=$fileName) → ${dest.absolutePath}")
        return dest
    }
}
