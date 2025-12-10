package com.duelodetrazos

import android.content.Context
import java.util.UUID

// PlayerManager: Gestiona la identidad unica del jugador en el dispositivo.
object PlayerManager {

    private const val PREFS_NAME = "duelo_prefs"
    private const val KEY_PLAYER_ID = "PLAYER_ID"

    /**
     * Devuelve un ID de jugador unico para este dispositivo.
     * Si no existe uno, lo crea y lo guarda en SharedPreferences para usos futuros.
     * Esto asegura que el mismo dispositivo siempre tenga el mismo ID de jugador.
     */
    fun getPlayerId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(KEY_PLAYER_ID, null)

        if (existingId != null) {
            return existingId
        }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PLAYER_ID, newId).apply()
        return newId
    }
}
