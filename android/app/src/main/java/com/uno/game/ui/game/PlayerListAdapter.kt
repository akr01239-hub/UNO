package com.uno.game.ui.game

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.R
import com.uno.game.models.Player

class PlayerListAdapter(
    private val onLongClick: (Player) -> Unit
) : ListAdapter<Player, PlayerListAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvPlayerName)
        val tvCards: TextView = itemView.findViewById(R.id.tvCardCount)
        val tvUno: TextView = itemView.findViewById(R.id.tvUnoAlert)
        val vAvatar: View = itemView.findViewById(R.id.vAvatar)

        fun bind(player: Player) {
            tvName.text = player.username
            tvCards.text = "🃏 ${player.cardCount}"
            tvUno.visibility = if (player.saidUno) View.VISIBLE else View.GONE
            try {
                vAvatar.setBackgroundColor(Color.parseColor(player.avatarColor))
            } catch (_: Exception) {}

            // Pulse animation for UNO
            if (player.cardCount == 1 && !player.saidUno) {
                tvCards.setTextColor(Color.RED)
            } else {
                tvCards.setTextColor(Color.WHITE)
            }
            itemView.alpha = if (player.isConnected) 1f else 0.4f
            itemView.setOnLongClickListener { onLongClick(player); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class PlayerDiffCallback : DiffUtil.ItemCallback<Player>() {
    override fun areItemsTheSame(old: Player, new: Player) = old.id == new.id
    override fun areContentsTheSame(old: Player, new: Player) = old == new
}
