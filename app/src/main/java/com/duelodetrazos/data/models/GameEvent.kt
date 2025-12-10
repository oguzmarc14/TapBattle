package com.duelodetrazos.data.models

import java.util.Date

data class GameEvent(
    val objectId: String,
    val roomCode: String,
    val type: String,
    val playerId: String?,
    val payload: Map<String, Any?>,
    val createdAt: Date
)
