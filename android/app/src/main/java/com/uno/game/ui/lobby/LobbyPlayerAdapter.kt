package com.uno.game.ui.lobby

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.R

class LobbyPlayerAdapter : ListAdapter<LobbyPlayer, LobbyPlayerAdapter.VH>(Diff()) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvPlayerName)
        val tvSeat: TextView = v.findViewById(R.id.tvSeat)
        val vColor: View = v.findViewById(R.id.vAvatar)

        fun bind(p: LobbyPlayer) {
            tvName.text = p.username
            tvSeat.text = "Seat ${p.seatPosition + 1}"
            try { vColor.setBackgroundColor(Color.parseColor(p.avatarColor)) } catch (_: Exception) {}
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lobby_player, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    class Diff : DiffUtil.ItemCallback<LobbyPlayer>() {
        override fun areItemsTheSame(a: LobbyPlayer, b: LobbyPlayer) = a.id == b.id
        override fun areContentsTheSame(a: LobbyPlayer, b: LobbyPlayer) = a == b
    }
}
