package com.uno.game.ui.game

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.uno.game.audio.SoundManager
import com.uno.game.audio.VoiceChatManager
import com.uno.game.databinding.ActivityGameBinding
import com.uno.game.models.UnoCard
import com.uno.game.utils.PreferencesManager

class GameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var handAdapter: CardHandAdapter
    private lateinit var playerListAdapter: PlayerListAdapter
    private var voiceChatManager: VoiceChatManager? = null
    private var pendingWildCard: UnoCard? = null

    companion object {
        const val EXTRA_ROOM_CODE = "room_code"
        const val MIC_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE) ?: finish().let { return }
        val playerId = PreferencesManager.getPlayerId(this) ?: return

        viewModel.roomCode = roomCode
        viewModel.currentPlayerId = playerId
        viewModel.initSocket()

        setupUI()
        observeGame()
        requestMicPermission()
    }

    private fun setupUI() {
        // Hand RecyclerView (horizontal scroll)
        handAdapter = CardHandAdapter { card ->
            onCardClicked(card)
        }
        binding.rvHand.apply {
            layoutManager = LinearLayoutManager(this@GameActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = handAdapter
        }

        // Other players list
        playerListAdapter = PlayerListAdapter { player ->
            // Long click challenges UNO
            viewModel.challengeUno(player.id)
            Toast.makeText(this, "Challenged ${player.username} for UNO!", Toast.LENGTH_SHORT).show()
        }
        binding.rvPlayers.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = playerListAdapter
        }

        // Draw card button
        binding.btnDraw.setOnClickListener {
            if (viewModel.isMyTurn()) {
                viewModel.drawCard()
                SoundManager.playCardDraw()
            } else {
                Toast.makeText(this, "It's not your turn!", Toast.LENGTH_SHORT).show()
            }
        }

        // UNO button
        binding.btnUno.setOnClickListener {
            viewModel.sayUno()
            SoundManager.playUno()
            binding.btnUno.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                .withEndAction { binding.btnUno.animate().scaleX(1f).scaleY(1f).duration = 150 }
        }

        // Voice chat toggle
        binding.btnVoice.setOnClickListener {
            voiceChatManager?.let { vm ->
                val muted = vm.toggleMute()
                binding.btnVoice.alpha = if (muted) 0.5f else 1.0f
                Toast.makeText(this, if (muted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        // Sound mute
        binding.btnMuteSound.setOnClickListener {
            val muted = SoundManager.toggleMute()
            binding.btnMuteSound.alpha = if (muted) 0.5f else 1.0f
        }
    }

    private fun onCardClicked(card: UnoCard) {
        if (!viewModel.isMyTurn()) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show()
            return
        }
        if (card.isWild()) {
            pendingWildCard = card
            showColorPicker()
        } else {
            viewModel.playCard(card.id)
            SoundManager.playCardPlay()
        }
    }

    private fun showColorPicker() {
        val colors = arrayOf("🔴 Red", "🟢 Green", "🔵 Blue", "🟡 Yellow")
        val colorValues = arrayOf("red", "green", "blue", "yellow")
        AlertDialog.Builder(this)
            .setTitle("Choose Color")
            .setItems(colors) { _, which ->
                pendingWildCard?.let { card ->
                    viewModel.playCard(card.id, colorValues[which])
                    SoundManager.playCardPlay()
                    pendingWildCard = null
                }
            }
            .setCancelable(true)
            .show()
    }

    private fun observeGame() {
        viewModel.gameState.observe(this) { state ->
            // Update top card
            state.topCard?.let { card ->
                binding.cardTop.setCard(card)
                binding.tvCurrentColor.setBackgroundColor(getColorFromString(state.currentColor))
                binding.tvDeckCount.text = "Deck: ${state.deckCount}"
            }

            // My hand
            val myHand = state.players.find { it.id == viewModel.currentPlayerId }?.hand
            myHand?.let { handAdapter.submitList(it) }

            // Other players
            playerListAdapter.submitList(viewModel.getOtherPlayers())

            // Turn indicator
            val isMyTurn = state.currentPlayerId == viewModel.currentPlayerId
            binding.tvTurnIndicator.text = if (isMyTurn) "YOUR TURN!" else "Waiting..."
            binding.tvTurnIndicator.alpha = if (isMyTurn) 1f else 0.5f
            binding.btnDraw.isEnabled = isMyTurn

            // Pending draw
            if (state.pendingDraw > 0) {
                binding.btnDraw.text = "Draw ${state.pendingDraw}"
                binding.btnDraw.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            } else {
                binding.btnDraw.text = "Draw Card"
                binding.btnDraw.setBackgroundColor(getColor(android.R.color.holo_blue_dark))
            }

            // UNO button visibility
            val myCardCount = myHand?.size ?: 0
            binding.btnUno.visibility = if (myCardCount == 1) View.VISIBLE else View.GONE

            // Direction arrow
            binding.tvDirection.text = if (state.direction == 1) "→ Clockwise" else "← Counter"
        }

        viewModel.unoEvent.observe(this) { playerId ->
            val playerName = viewModel.gameState.value?.players
                ?.find { it.id == playerId }?.username ?: "Someone"
            Toast.makeText(this, "🃏 $playerName said UNO!", Toast.LENGTH_SHORT).show()
            SoundManager.playUno()
        }

        viewModel.winnerEvent.observe(this) { winnerId ->
            val winnerName = viewModel.gameState.value?.players
                ?.find { it.id == winnerId }?.username ?: "Unknown"
            SoundManager.playWin()
            AlertDialog.Builder(this)
                .setTitle("🎉 Game Over!")
                .setMessage("$winnerName wins! 🏆")
                .setPositiveButton("Back to Lobby") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }

        viewModel.errorMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_CODE
            )
        } else {
            initVoiceChat()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initVoiceChat()
            } else {
                Toast.makeText(this, "Microphone denied - voice chat disabled", Toast.LENGTH_LONG).show()
                binding.btnVoice.isEnabled = false
                binding.btnVoice.alpha = 0.4f
            }
        }
    }

    private fun initVoiceChat() {
        voiceChatManager = VoiceChatManager(this, viewModel.roomCode)
        voiceChatManager?.initialize()
        binding.btnVoice.isEnabled = true
    }

    private fun getColorFromString(color: String?): Int = when (color) {
        "red"    -> getColor(android.R.color.holo_red_light)
        "green"  -> getColor(android.R.color.holo_green_light)
        "blue"   -> getColor(android.R.color.holo_blue_light)
        "yellow" -> getColor(android.R.color.holo_orange_light)
        else     -> getColor(android.R.color.darker_gray)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceChatManager?.release()
        SoundManager.release()
    }
}
