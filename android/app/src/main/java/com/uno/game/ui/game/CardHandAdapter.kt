package com.uno.game.ui.game

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.R
import com.uno.game.models.UnoCard

class CardHandAdapter(
    private val onCardClick: (UnoCard) -> Unit
) : ListAdapter<UnoCard, CardHandAdapter.CardViewHolder>(CardDiffCallback()) {

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: UnoCardView = itemView.findViewById(R.id.unoCard)

        fun bind(card: UnoCard) {
            cardView.setCard(card)
            itemView.setOnClickListener {
                // Pop-up animation
                val animator = ObjectAnimator.ofFloat(itemView, "translationY", 0f, -40f, 0f)
                animator.duration = 250
                animator.start()
                onCardClick(card)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class CardDiffCallback : DiffUtil.ItemCallback<UnoCard>() {
    override fun areItemsTheSame(old: UnoCard, new: UnoCard) = old.id == new.id
    override fun areContentsTheSame(old: UnoCard, new: UnoCard) = old == new
}
