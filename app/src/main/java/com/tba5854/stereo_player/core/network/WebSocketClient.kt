//package com.tba5854.stereo_player.core.network
//
//import android.util.JsonToken
//import com.tba5854.stereo_player.music_player.ExoPlayerManager
//import org.java_websocket.WebSocket
//import org.java_websocket.handshake.ClientHandshake
//import org.java_websocket.server.WebSocketServer
//import org.java_websocket.client.WebSocketClient
//import org.java_websocket.handshake.ServerHandshake
//import java.net.URI
//import java.net.InetSocketAddress
//import android.os.Handler
//import android.os.Looper
//
//class WSS(port: Int, private val name:String, private val playerManager: ExoPlayerManager) : WebSocketServer(InetSocketAddress(port)) {
//
//    private val clients = mutableListOf<WebSocket>()
//    private var isPlaying = false;
//    override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
//        clients.add(conn)
////        conn.send("Host:${name}");
//        println("WS : New connection: ${conn.remoteSocketAddress}")
//    }
//
//    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
//        clients.remove(conn)
//        println("WS : Closed connection: ${conn.remoteSocketAddress}")
//    }
//
//    override fun onMessage(conn: WebSocket, message: String) {
//        println("WS : Received: $message")
//
//        if (message == "TIME_SYNC_REQUEST") {
//            // Send current server time
//            conn.send("TIME_SYNC:${System.currentTimeMillis()}")
//            return
//        }
//
//        if (message.startsWith("PLAY:")) {
//            val parts = message.split(":")
//            val targetTime = parts[1].toLong()
//
//            // Broadcast the play command to all
//            for (client in clients) {
//                client.send("PLAY:$targetTime")
//            }
//
//            // Server plays on schedule
//            val delay = targetTime - System.currentTimeMillis()
//            if (delay > 0) {
//                Handler(Looper.getMainLooper()).postDelayed({
//                    playerManager.play()
//                }, delay)
//            } else {
//                playerManager.play()
//            }
//        }
//    }
//
//
//    override fun onError(conn: WebSocket?, ex: Exception) {
//        println("WS : Error: ${ex.message}")
//    }
//
//    override fun onStart() {
//        println("WS : Server started")
//    }
//
//}
//
//class WSC(serverUri: URI, private val playerManager: ExoPlayerManager) : WebSocketClient(serverUri) {
//
//    private var offset: Long = 0L  // serverTime - clientTime
//
//    override fun onOpen(handshakedata: ServerHandshake?) {
//        println("WebSocket Opened")
//        send("TIME_SYNC_REQUEST")
//    }
//
//    override fun onMessage(message: String?) {
//        println("Received message: $message")
//        message?.let {
//            when {
//                it.startsWith("TIME_SYNC:") -> {
//                    val serverTime = it.split(":")[1].toLong()
//                    val clientTime = System.currentTimeMillis()
//                    offset = serverTime - clientTime
//                    println("⏱ Offset synced: $offset ms")
//                }
//
//                it.startsWith("PLAY:") -> {
//                    val serverTargetTime = it.split(":")[1].toLong()
//                    val localTargetTime = serverTargetTime - offset
//                    val delay = localTargetTime - System.currentTimeMillis()
//
//                    println("🕓 Will play in $delay ms (offset: $offset)")
//
//                    if (delay > 0) {
//                        Handler(Looper.getMainLooper()).postDelayed({
//                            playerManager.play()
//                        }, delay)
//                    } else {
//                        playerManager.play()
//                    }
//                }
//            }
//        }
//    }
//
//    override fun onClose(code: Int, reason: String?, remote: Boolean) {
//        println("WebSocket Closed: $reason")
//    }
//
//    override fun onError(ex: Exception?) {
//        ex?.printStackTrace()
//    }
//}
