package com.duelodetrazos.ui.room

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duelodetrazos.databinding.ActivityRoomBinding

// RoomActivity: Es la pantalla de menu principal de la aplicacion.
class RoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtonClickListeners()
    }

    // Configura los listeners para los botones del menu principal.
    private fun setupButtonClickListeners() {
        // Navega a la pantalla de creacion de sala.
        binding.btnCreateRoom.setOnClickListener {
            val intent = Intent(this, CreateRoomActivity::class.java)
            startActivity(intent)
        }

        // Navega a la pantalla para unirse a una sala.
        binding.btnJoinRoom.setOnClickListener {
            val intent = Intent(this, JoinRoomActivity::class.java)
            startActivity(intent)
        }

        // Navega a la pantalla de historial de puntajes.
        binding.btnScores.setOnClickListener {
            val intent = Intent(this, ScoresActivity::class.java)
            startActivity(intent)
        }
    }
}
