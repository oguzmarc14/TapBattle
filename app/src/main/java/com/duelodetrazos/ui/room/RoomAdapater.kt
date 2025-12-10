package com.duelodetrazos.ui.room

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duelodetrazos.databinding.ItemAlbumBinding
import com.duelodetrazos.ui.game.GameActivity

class RoomAdapter(
    private val items: List<AlbumItem>
) : RecyclerView.Adapter<RoomAdapter.AlbumViewHolder>() {

    class AlbumViewHolder(val binding: ItemAlbumBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAlbumBinding.inflate(inflater, parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.txtAlbumTitle.text = item.title
        holder.binding.imgAlbum.setImageResource(item.imageRes)

        holder.binding.root.setOnClickListener {

            when (item.title) {

                "Crear sala" -> {
                    context.startActivity(
                        Intent(context, CreateRoomActivity::class.java)
                    )
                }

                "Unirme a sala" -> {
                    context.startActivity(
                        Intent(context, JoinRoomActivity::class.java)
                    )
                }

                "Modo de juego" -> {
                    context.startActivity(
                        Intent(context, GameActivity::class.java)
                    )
                }

                "Historial" -> {
                    // Después lo implementamos
                }

                "Resultados" -> {
                    // Después lo implementamos
                }
            }
        }
    }


    override fun getItemCount(): Int = items.size
}
