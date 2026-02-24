package com.tba5854.syncbeats.sync.server

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class RoomState(
        @SerializedName("room_id") val roomId: String,
        @SerializedName("name") val name: String,
        @SerializedName("owner_id") val ownerId: String,
        @SerializedName("track_hash") val trackHash: String,
        @SerializedName("is_playing") val isPlaying: Boolean,
        @SerializedName("position") val position: Double,
        @SerializedName("start_at") val startAt: Long,
        @SerializedName("queue") val queue: List<String> = emptyList(),
        @SerializedName("current_index") val currentIndex: Int = -1
) {
        companion object {
                fun fromJson(json: JSONObject): RoomState {
                        val queueArray = json.optJSONArray("queue")
                        val queue =
                                if (queueArray != null) {
                                        List(queueArray.length()) { i -> queueArray.getString(i) }
                                } else emptyList()
                        return RoomState(
                                roomId = json.optString("room_id", ""),
                                name = json.optString("name", ""),
                                ownerId = json.optString("owner_id", ""),
                                trackHash = json.optString("track_hash", ""),
                                isPlaying = json.optBoolean("is_playing", false),
                                position = json.optDouble("position", 0.0),
                                startAt = json.optLong("start_at", 0L),
                                queue = queue,
                                currentIndex = json.optInt("current_index", -1)
                        )
                }
        }
}

data class NtpPong(
        @SerializedName("t1") val t1: Long,
        @SerializedName("t2") val t2: Long,
        @SerializedName("t3") val t3: Long
) {
        companion object {
                fun fromJson(json: JSONObject): NtpPong =
                        NtpPong(
                                t1 = json.getLong("t1"),
                                t2 = json.getLong("t2"),
                                t3 = json.getLong("t3")
                        )
        }
}

data class PlayEvent(
        @SerializedName("track_hash") val trackHash: String,
        @SerializedName("position") val position: Double,
        @SerializedName("start_at") val startAt: Long
) {
        companion object {
                fun fromJson(json: JSONObject): PlayEvent =
                        PlayEvent(
                                trackHash = json.optString("track_hash", ""),
                                position = json.optDouble("position", 0.0),
                                startAt = json.optLong("start_at", 0L)
                        )
        }
}

data class PauseEvent(@SerializedName("position") val position: Double) {
        companion object {
                fun fromJson(json: JSONObject): PauseEvent =
                        PauseEvent(position = json.optDouble("position", 0.0))
        }
}

data class SeekEvent(
        @SerializedName("position") val position: Double,
        @SerializedName("start_at") val startAt: Long
) {
        companion object {
                fun fromJson(json: JSONObject): SeekEvent =
                        SeekEvent(
                                position = json.optDouble("position", 0.0),
                                startAt = json.optLong("start_at", 0L)
                        )
        }
}

data class SyncError(
        @SerializedName("code") val code: String,
        @SerializedName("message") val message: String
) {
        companion object {
                fun fromJson(json: JSONObject): SyncError =
                        SyncError(
                                code = json.optString("code", "UNKNOWN"),
                                message = json.optString("message", "")
                        )
        }
}

data class RemoteFile(
        @SerializedName("file_id") val fileId: String,
        @SerializedName("file_name") val fileName: String
) {
        companion object {
                fun fromJson(json: JSONObject): RemoteFile =
                        RemoteFile(
                                fileId = json.getString("file_id"),
                                fileName = json.getString("file_name")
                        )
        }
}

data class FileListResponse(@SerializedName("files") val files: List<RemoteFile>)

data class UploadResponse(@SerializedName("file_id") val fileId: String)

data class RoomInfo(
        @SerializedName("room_id") val roomId: String,
        @SerializedName("name") val name: String,
        @SerializedName("owner_id") val ownerId: String? = null,
        @SerializedName("track_hash") val trackHash: String? = null,
        @SerializedName("is_playing") val isPlaying: Boolean = false,
        @SerializedName("position") val position: Double = 0.0,
        @SerializedName("start_at") val startAt: Long = 0L,
        @SerializedName("queue") val queue: List<String> = emptyList(),
        @SerializedName("current_index") val currentIndex: Int = -1,
        @SerializedName("member_count") val memberCount: Int = 0
)

data class RoomListResponse(@SerializedName("rooms") val rooms: List<RoomInfo>)

data class QueueUpdatedEvent(
        @SerializedName("room_id") val roomId: String,
        @SerializedName("queue") val queue: List<String>
) {
        companion object {
                fun fromJson(json: JSONObject): QueueUpdatedEvent {
                        val queueArray = json.optJSONArray("queue")
                        val queue =
                                if (queueArray != null) {
                                        List(queueArray.length()) { i -> queueArray.getString(i) }
                                } else emptyList()
                        return QueueUpdatedEvent(
                                roomId = json.optString("room_id", ""),
                                queue = queue
                        )
                }
        }
}
