package com.duelodetrazos.ui.room

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.PlayerManager
import com.duelodetrazos.databinding.ActivityJoinRoomBinding
import com.duelodetrazos.ui.game.GameActivity
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

            // Validacion de los campos de entrada.
            if (code.length != 4) {
                Toast.makeText(this, "El codigo debe tener 4 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (playerName.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce tu nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Desactivar el boton para evitar multiples intentos de union.
            binding.btnJoin.isEnabled = false
            joinRoom(code, playerName)
        }
    }

    // Busca una sala disponible y une al jugador.
    private fun joinRoom(code: String, player2Name: String) {
        // Consulta para encontrar una sala con el codigo y estado 'waiting'.
        val query = ParseQuery.getQuery<ParseObject>("Room")
        query.whereEqualTo("code", code)
        query.whereEqualTo("status", "waiting")

        query.getFirstInBackground { room, e ->
            if (e != null || room == null) {
                // Si no se encuentra la sala o hay un error, notificar y reactivar el boton.
                Toast.makeText(this, "No existe una sala disponible con ese codigo.", Toast.LENGTH_LONG).show()
                binding.btnJoin.isEnabled = true
                return@getFirstInBackground
            }

            // Si se encuentra la sala, asignar al Jugador 2.
            val player2Id = PlayerManager.getPlayerId(this)
            room.put("player2Id", player2Id)
            room.put("player2Name", player2Name)
            room.put("status", "ready") // La sala esta lista para empezar.

            room.saveInBackground(SaveCallback { err ->
                if (err == null) {
                    // Si se une con exito, iniciar la actividad del juego.
                    Toast.makeText(this, "Te has unido! Comenzando la partida...", Toast.LENGTH_LONG).show()

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
                    // Si hay un error al guardar, notificar y reactivar el boton.
                    Toast.makeText(this, "Error al unirse: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    binding.btnJoin.isEnabled = true
                }
            })
        }
    }
}
