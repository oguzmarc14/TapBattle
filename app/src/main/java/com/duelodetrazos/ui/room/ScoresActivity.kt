package com.duelodetrazos.ui.room

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.duelodetrazos.databinding.ActivityScoresBinding
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

        // Consulta a la clase "MatchResult" para obtener los datos.
        val query = ParseQuery.getQuery<ParseObject>("MatchResult")
        query.orderByDescending("createdAt") // Ordenar para mostrar los mas recientes primero.

        query.findInBackground { scores, e ->
            binding.progressBar.visibility = View.GONE

            if (e == null) {
                // Si la consulta es exitosa.
                if (scores.isEmpty()) {
                    // Mostrar un mensaje si no hay partidas guardadas.
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.rvScores.visibility = View.GONE
                } else {
                    // Si hay partidas, mostrarlas en el RecyclerView.
                    binding.tvEmptyMessage.visibility = View.GONE
                    binding.rvScores.visibility = View.VISIBLE
                    binding.rvScores.adapter = ScoresAdapter(scores)
                }
            } else {
                // Si hay un error en la consulta, mostrar un mensaje.
                Toast.makeText(this, "Error al cargar los puntajes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error al cargar los puntajes."
            }
        }
    }
}
