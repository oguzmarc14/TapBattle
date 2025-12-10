package com.duelodetrazos.ui.room

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.PlayerManager
import com.duelodetrazos.databinding.ActivityJoinRoomBinding
import com.duelodetrazos.ui.game.GameActivity
import com.google.android.material.snackbar.Snackbar
import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.SaveCallback

// JoinRoomActivity: Pantalla donde el Jugador 2 se une a una sala existente.
class JoinRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnJoin.setOnClickListener {
            val code = binding.edtCode.text.toString().uppercase().trim()
            val playerName = binding.edtPlayerName.text.toString().trim()

            if (code.length != 4) {
                Snackbar.make(binding.root, "El codigo debe tener 4 caracteres", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (playerName.isEmpty()) {
                Snackbar.make(binding.root, "Por favor, introduce tu nombre", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnJoin.isEnabled = false
            joinRoom(code, playerName)
        }
    }

    private fun joinRoom(code: String, player2Name: String) {
        val query = ParseQuery.getQuery<ParseObject>("Room")
        query.whereEqualTo("code", code)
        query.whereEqualTo("status", "waiting")

        query.getFirstInBackground { room, e ->
            if (e != null || room == null) {
                val errorMessage = if (e?.code == ParseException.CONNECTION_FAILED) {
                    "Error de conexion. Verifica tu red."
                } else {
                    "No existe una sala disponible con ese codigo."
                }
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                binding.btnJoin.isEnabled = true
                return@getFirstInBackground
            }

            val player2Id = PlayerManager.getPlayerId(this)
            room.put("player2Id", player2Id)
            room.put("player2Name", player2Name)
            room.put("status", "ready")

            room.saveInBackground(SaveCallback { err ->
                if (err == null) {
                    Snackbar.make(binding.root, "Te has unido! Comenzando la partida...", Snackbar.LENGTH_LONG).show()

                    val player1Name = room.getString("player1Name") ?: "Jugador 1"

                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("roomId", code)
                        putExtra("isPlayer1", false)
                        putExtra("player1Name", player1Name)
                        putExtra("player2Name", player2Name)
                    }
                    startActivity(intent)
                    finish()

                } else {
                    val saveErrorMessage = if (err.code == ParseException.CONNECTION_FAILED) {
                        "Error de conexion. No se pudo unir a la sala."
                    } else {
                        "Error al unirse: ${err.localizedMessage}"
                    }
                    Snackbar.make(binding.root, saveErrorMessage, Snackbar.LENGTH_LONG).show()
                    binding.btnJoin.isEnabled = true
                }
            })
        }
    }
}
