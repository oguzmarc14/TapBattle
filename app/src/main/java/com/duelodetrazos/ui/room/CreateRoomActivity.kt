package com.duelodetrazos.ui.room

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.PlayerManager
import com.duelodetrazos.databinding.ActivityCreateRoomBinding
import com.duelodetrazos.ui.game.GameActivity
import com.google.android.material.snackbar.Snackbar
import com.parse.ParseException
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
                Snackbar.make(binding.root, "Por favor, introduce tu nombre", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnGenerate.isEnabled = false
            val code = generateRoomCode()
            binding.txtRoomCode.text = code

            val playerId = PlayerManager.getPlayerId(this)
            saveRoomToServer(code, playerId, playerName)
        }
    }

    private fun generateRoomCode(): String {
        val letters = ('A'..'Z').random().toString() + ('A'..'Z').random()
        val numbers = Random.nextInt(10, 99).toString()
        return letters + numbers
    }

    private fun saveRoomToServer(code: String, player1Id: String, player1Name: String) {
        val room = ParseObject("Room")

        room.put("code", code)
        room.put("status", "waiting")
        room.put("player1Id", player1Id)
        room.put("player1Name", player1Name)
        room.put("player2Id", "")
        room.put("player2Name", "")
        room.put("player1Score", 0)
        room.put("player2Score", 0)

        room.saveInBackground(SaveCallback { e ->
            if (e == null) {
                Snackbar.make(binding.root, "Sala creada. Esperando al segundo jugador!", Snackbar.LENGTH_LONG).show()

                val intent = Intent(this, GameActivity::class.java).apply {
                    putExtra("roomId", code)
                    putExtra("isPlayer1", true)
                    putExtra("player1Name", player1Name)
                }
                startActivity(intent)
                finish()

            } else {
                val errorMessage = if (e.code == ParseException.CONNECTION_FAILED) {
                    "Error de conexion. Verifica tu red."
                } else {
                    "Error al crear la sala: ${e.localizedMessage}"
                }
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                binding.btnGenerate.isEnabled = true
            }
        })
    }
}
