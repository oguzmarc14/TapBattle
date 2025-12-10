package com.duelodetrazos.ui.room

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.duelodetrazos.databinding.ActivityScoresBinding
import com.google.android.material.snackbar.Snackbar
import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery

// ScoresActivity: Muestra el historial de las partidas guardadas.
class ScoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoresBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvScores.layoutManager = LinearLayoutManager(this)
        fetchScores()
    }

    // Obtiene los resultados de las partidas desde el servidor de Back4App.
    private fun fetchScores() {
        binding.progressBar.visibility = View.VISIBLE

        val query = ParseQuery.getQuery<ParseObject>("MatchResult")
        query.orderByDescending("createdAt")

        query.findInBackground { scores, e ->
            binding.progressBar.visibility = View.GONE

            if (e == null) {
                if (scores.isEmpty()) {
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.rvScores.visibility = View.GONE
                } else {
                    binding.tvEmptyMessage.visibility = View.GONE
                    binding.rvScores.visibility = View.VISIBLE
                    binding.rvScores.adapter = ScoresAdapter(scores)
                }
            } else {
                val errorMessage = if (e.code == ParseException.CONNECTION_FAILED) {
                    "Error de conexion. No se pudieron cargar los puntajes."
                } else {
                    "Error al cargar los puntajes: ${e.localizedMessage}"
                }
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error al cargar los puntajes."
            }
        }
    }
}
