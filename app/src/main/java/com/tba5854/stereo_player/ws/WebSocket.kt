package com.tba5854.stereo_player.ws

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class WS(port: Int, private val name:String) : WebSocketServer(InetSocketAddress(port)) {

    private val clients = mutableListOf<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
        clients.add(conn)
        conn.send("Host:${name}");
        println("WS : New connection: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        clients.remove(conn)
        println("WS : Closed connection: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        println("WS : Received: $message")
        // Broadcast to all clients
        for (client in clients) {
            if (client != conn) client.send(message)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        println("WS : Error: ${ex.message}")
    }

    override fun onStart() {
        println("WS : Server started")
    }
}
