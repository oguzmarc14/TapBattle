package com.duelodetrazos.ui.room

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.PlayerManager
import com.duelodetrazos.databinding.ActivityCreateRoomBinding
import com.duelodetrazos.ui.game.GameActivity
import com.parse.ParseObject
import com.parse.SaveCallback
import kotlin.random.Random

// CreateRoomActivity: Pantalla donde el anfitrion (Jugador 1) crea una nueva sala de juego.
class CreateRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGenerate.setOnClickListener {
            val playerName = binding.edtPlayerName.text.toString().trim()
            if (playerName.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce tu nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Desactivar el boton para evitar la creacion de multiples salas.
            binding.btnGenerate.isEnabled = false
            val code = generateRoomCode()
            binding.txtRoomCode.text = code

            val playerId = PlayerManager.getPlayerId(this)
            saveRoomToServer(code, playerId, playerName)
        }
    }

    // Genera un codigo de sala alfanumerico de 4 caracteres (ej. AB12).
    private fun generateRoomCode(): String {
        val letters = ('A'..'Z').random().toString() + ('A'..'Z').random()
        val numbers = Random.nextInt(10, 99).toString()
        return letters + numbers
    }

    // Guarda la nueva sala en el servidor de Back4App con los datos iniciales.
    private fun saveRoomToServer(code: String, player1Id: String, player1Name: String) {
        val room = ParseObject("Room")

        // Configuracion inicial de la sala.
        room.put("code", code)
        room.put("status", "waiting") // 'waiting' para que otros jugadores puedan unirse.
        room.put("player1Id", player1Id)
        room.put("player1Name", player1Name)
        room.put("player2Id", "")
        room.put("player2Name", "")
        room.put("player1Score", 0)
        room.put("player2Score", 0)

        room.saveInBackground(SaveCallback { e ->
            if (e == null) {
                // Si la sala se crea con exito, iniciar la actividad del juego.
                Toast.makeText(this, "Sala creada. Esperando al segundo jugador!", Toast.LENGTH_LONG).show()

                val intent = Intent(this, GameActivity::class.java).apply {
                    putExtra("roomId", code)
                    putExtra("isPlayer1", true)
                    putExtra("player1Name", player1Name)
                }
                startActivity(intent)
                finish() // Cerrar esta actividad para que el usuario no pueda volver.

            } else {
                // Si hay un error, notificar al usuario y reactivar el boton.
                Toast.makeText(this, "Error al crear la sala: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                binding.btnGenerate.isEnabled = true
            }
        })
    }
}
