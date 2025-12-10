package com.duelodetrazos.ui.room

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duelodetrazos.databinding.ListItemScoreBinding
import com.parse.ParseObject

// ScoresAdapter: Adaptador para el RecyclerView que muestra el historial de partidas.
class ScoresAdapter(private val scores: List<ParseObject>) : RecyclerView.Adapter<ScoresAdapter.ScoreViewHolder>() {

    // Crea una nueva vista para cada item de la lista.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val binding = ListItemScoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScoreViewHolder(binding)
    }

    // Vincula los datos de una partida a una vista especifica.
    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val score = scores[position]
        holder.bind(score)
    }

    // Devuelve el numero total de items en la lista.
    override fun getItemCount(): Int = scores.size

    // ScoreViewHolder: Representa la vista de un solo item en la lista de puntajes.
    class ScoreViewHolder(private val binding: ListItemScoreBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(matchResult: ParseObject) {
            val player1Name = matchResult.getString("player1Name") ?: "N/A"
            val player2Name = matchResult.getString("player2Name") ?: "N/A"
            val winnerName = matchResult.getString("winnerName") ?: ""

            binding.tvPlayer1Name.text = player1Name
            binding.tvPlayer2Name.text = player2Name
            binding.tvPlayer1Score.text = matchResult.getNumber("player1Score")?.toString() ?: "0"
            binding.tvPlayer2Score.text = matchResult.getNumber("player2Score")?.toString() ?: "0"

            // Muestra una corona sobre el nombre del ganador.
            binding.ivWinnerCrown.visibility = View.VISIBLE
            val constraintLayout = binding.constraintLayoutContainer
            val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
            constraintSet.clone(constraintLayout)

            if (winnerName.isNotBlank() && winnerName == player1Name) {
                // Mover la corona a la posicion del Jugador 1.
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.START, binding.tvPlayer1Name.id, androidx.constraintlayout.widget.ConstraintSet.START)
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.END, binding.tvPlayer1Name.id, androidx.constraintlayout.widget.ConstraintSet.END)
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, binding.tvPlayer1Name.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
                constraintSet.applyTo(constraintLayout)
            } else if (winnerName.isNotBlank() && winnerName == player2Name) {
                // Mover la corona a la posicion del Jugador 2.
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.START, binding.tvPlayer2Name.id, androidx.constraintlayout.widget.ConstraintSet.START)
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.END, binding.tvPlayer2Name.id, androidx.constraintlayout.widget.ConstraintSet.END)
                constraintSet.connect(binding.ivWinnerCrown.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, binding.tvPlayer2Name.id, androidx.constraintlayout.widget.ConstraintSet.TOP)
                constraintSet.applyTo(constraintLayout)
            } else {
                // Si no hay un ganador claro (ej. por abandono), ocultar la corona.
                binding.ivWinnerCrown.visibility = View.GONE
            }
        }
    }
}
