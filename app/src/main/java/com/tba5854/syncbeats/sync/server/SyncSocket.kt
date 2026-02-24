package com.tba5854.syncbeats.sync.server

import android.util.Log
import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject

interface SyncListener {
    fun onConnected()
    fun onDisconnected(reason: String?)
    fun onRoomCreated(state: RoomState)
    fun onRoomJoined(state: RoomState)
    fun onRoomLeft(roomId: String)
    fun onMemberJoined(userId: String, username: String)
    fun onMemberLeft(userId: String, username: String)
    fun onRoomState(state: RoomState)
    fun onTrackChanged(state: RoomState)
    fun onQueueUpdated(event: QueueUpdatedEvent)
    fun onPlay(event: PlayEvent)
    fun onPause(event: PauseEvent)
    fun onSeek(event: SeekEvent)
    fun onNtpPong(pong: NtpPong)
    fun onSyncError(error: SyncError)
}

class SyncSocket(
        private val serverUrl: String,
        private val userId: String,
        private val listener: SyncListener,
        private val username: String = userId
) {

    private val TAG = "SyncSocket"
    private var client: WebSocketClient? = null

    val isConnected: Boolean
        get() = client?.isOpen == true

    fun connect() {
        val wsUrl =
                serverUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/') +
                        "/ws?user_id=$userId&username=${java.net.URLEncoder.encode(username, "UTF-8")}"

        client =
                object : WebSocketClient(URI(wsUrl)) {
                    override fun onOpen(handshake: ServerHandshake?) {
                        Log.d(TAG, "Connected to $wsUrl")
                        listener.onConnected()
                    }

                    override fun onMessage(message: String?) {
                        message ?: return
                        try {
                            val json = JSONObject(message)
                            val event = json.getString("event")
                            val payload = json.getJSONObject("payload")
                            dispatch(event, payload)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse message: $message", e)
                        }
                    }

                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        Log.d(TAG, "Disconnected: $reason")
                        listener.onDisconnected(reason)
                    }

                    override fun onError(ex: Exception?) {
                        Log.e(TAG, "WS error", ex)
                    }
                }
        client?.connect()
    }

    fun disconnect() {
        client?.close()
        client = null
    }

    fun createRoom(roomName: String) =
            send(
                    "room:create",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_name", roomName)
                    }
            )

    fun joinRoom(roomId: String) =
            send(
                    "room:join",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                    }
            )

    fun leaveRoom(roomId: String) =
            send(
                    "room:leave",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                    }
            )

    fun requestState(roomId: String) =
            send("room:state:request", JSONObject().apply { put("room_id", roomId) })

    fun setTrack(roomId: String, trackHash: String) =
            send(
                    "track:set",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                        put("track_hash", trackHash)
                    }
            )

    fun play(roomId: String, position: Double) =
            send(
                    "sync:play",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                        put("position", position)
                    }
            )

    fun pause(roomId: String, position: Double) =
            send(
                    "sync:pause",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                        put("position", position)
                    }
            )

    fun seek(roomId: String, position: Double) =
            send(
                    "sync:seek",
                    JSONObject().apply {
                        put("user_id", userId)
                        put("room_id", roomId)
                        put("position", position)
                    }
            )

    fun sendNtp() = send("sync:ntp", JSONObject().apply { put("t1", System.currentTimeMillis()) })

    fun queueAdd(roomId: String, trackHash: String, position: Int = -1) =
            send(
                    "queue:add",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("track_hash", trackHash)
                        put("position", position)
                    }
            )

    fun queueRemove(roomId: String, index: Int) =
            send(
                    "queue:remove",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("index", index)
                    }
            )

    fun queueMove(roomId: String, from: Int, to: Int) =
            send(
                    "queue:move",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("from", from)
                        put("to", to)
                    }
            )

    fun queueNext(roomId: String, fromIndex: Int) =
            send(
                    "queue:next",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("from_index", fromIndex)
                    }
            )

    fun queuePrev(roomId: String, fromIndex: Int) =
            send(
                    "queue:prev",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("from_index", fromIndex)
                    }
            )

    fun queuePlayAt(roomId: String, index: Int) =
            send(
                    "queue:play_at",
                    JSONObject().apply {
                        put("room_id", roomId)
                        put("index", index)
                    }
            )

    private fun send(event: String, payload: JSONObject) {
        val envelope =
                JSONObject().apply {
                    put("event", event)
                    put("payload", payload)
                }
        val msg = envelope.toString()
        Log.d(TAG, "→ $msg")
        try {
            client?.send(msg)
        } catch (e: WebsocketNotConnectedException) {
            Log.e(TAG, "Socket disconnected, failed to send: $event")
        } catch (e: Exception) {
            Log.e(TAG, "Exception pushing to socket", e)
        }
    }

    private fun dispatch(event: String, payload: JSONObject) {
        Log.d(TAG, "← $event: $payload")
        when (event) {
            "room:created" -> listener.onRoomCreated(RoomState.fromJson(payload))
            "room:joined" -> listener.onRoomJoined(RoomState.fromJson(payload))
            "room:left" -> listener.onRoomLeft(payload.optString("room_id", ""))
            "room:member:joined" ->
                    listener.onMemberJoined(
                            payload.getString("user_id"),
                            payload.optString("username", payload.getString("user_id"))
                    )
            "room:member:left" ->
                    listener.onMemberLeft(
                            payload.getString("user_id"),
                            payload.optString("username", payload.getString("user_id"))
                    )
            "room:state" -> listener.onRoomState(RoomState.fromJson(payload))
            "track:changed" -> listener.onTrackChanged(RoomState.fromJson(payload))
            "queue:updated" -> listener.onQueueUpdated(QueueUpdatedEvent.fromJson(payload))
            "sync:play" -> listener.onPlay(PlayEvent.fromJson(payload))
            "sync:pause" -> listener.onPause(PauseEvent.fromJson(payload))
            "sync:seek" -> listener.onSeek(SeekEvent.fromJson(payload))
            "sync:ntp:pong" -> {
                val pong = NtpPong.fromJson(payload)
                NtpSync.recordPong(pong, System.currentTimeMillis())
                listener.onNtpPong(pong)
            }
            "error" -> listener.onSyncError(SyncError.fromJson(payload))
            else -> Log.w(TAG, "Unknown event: $event")
        }
    }
}
