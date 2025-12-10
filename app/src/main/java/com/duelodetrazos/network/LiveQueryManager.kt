package com.duelodetrazos.network

import com.parse.ParseObject
import com.parse.livequery.ParseLiveQueryClient
import org.json.JSONObject
import java.net.URI

// LiveQueryManager: Gestiona la comunicacion en tiempo real con el servidor de Back4App.
// Utiliza WebSockets para enviar y recibir eventos del juego de forma instantanea.
object LiveQueryManager {

    // Envia un evento de juego al servidor.
    fun sendEvent(roomId: String, type: String, data: JSONObject) {
        val event = ParseObject("GameEvent").apply {
            put("roomCode", roomId)
            put("type", type)
            put("payload", data)
        }
        event.saveInBackground()
    }
}
