package com.duelodetrazos.data.models

sealed class GameEventPayload {

    data class Spawn(
        val targetX: Float,
        val targetY: Float,
        val radius: Float,
        val round: Int
    ) : GameEventPayload()

    data class Hit(
        val playerId: String,
        val hitX: Float,
        val hitY: Float,
        val hitTimeMs: Long
    ) : GameEventPayload()

    data class Score(
        val player1Score: Int,
        val player2Score: Int,
        val lastWinnerId: String?
    ) : GameEventPayload()

    data class End(
        val winnerId: String?,
        val finalScoreP1: Int,
        val finalScoreP2: Int,
        val reason: String
    ) : GameEventPayload()
}
