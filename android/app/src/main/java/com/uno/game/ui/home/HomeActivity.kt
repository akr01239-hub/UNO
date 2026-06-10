package com.uno.game.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.uno.game.databinding.ActivityHomeBinding
import com.uno.game.network.ApiService
import com.uno.game.network.SocketManager
import com.uno.game.ui.lobby.LobbyActivity
import com.uno.game.utils.PreferencesManager
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val avatarColors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
        "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F"
    )
    private var selectedColor = "#FF6B6B"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Connect socket
        SocketManager.connect()

        // Check if already registered
        val savedUsername = PreferencesManager.getUsername(this)
        if (savedUsername != null) {
            binding.etUsername.setText(savedUsername)
        }

        setupColorPicker()

        binding.btnCreateRoom.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            if (username.isBlank()) {
                Toast.makeText(this, "Enter a username!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createRoomFlow(username)
        }

        binding.btnJoinRoom.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val code = binding.etRoomCode.text.toString().trim().uppercase()
            if (username.isBlank()) {
                Toast.makeText(this, "Enter a username!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.isBlank()) {
                Toast.makeText(this, "Enter a room code!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinRoomFlow(username, code)
        }
    }

    private fun setupColorPicker() {
        val colorViews = listOf(
            binding.color1, binding.color2, binding.color3, binding.color4,
            binding.color5, binding.color6, binding.color7, binding.color8
        )
        colorViews.forEachIndexed { i, view ->
            if (i < avatarColors.size) {
                view.setBackgroundColor(Color.parseColor(avatarColors[i]))
                view.setOnClickListener {
                    selectedColor = avatarColors[i]
                    colorViews.forEach { v -> v.scaleX = 1f; v.scaleY = 1f }
                    view.scaleX = 1.3f; view.scaleY = 1.3f
                }
            }
        }
    }

    private fun createRoomFlow(username: String) {
        binding.btnCreateRoom.isEnabled = false
        lifecycleScope.launch {
            val playerResult = ApiService.createPlayer(username, selectedColor)
            playerResult.onSuccess { player ->
                PreferencesManager.savePlayer(this@HomeActivity, player.id, username)
                val roomResult = ApiService.createRoom(player.id)
                roomResult.onSuccess { room ->
                    val intent = Intent(this@HomeActivity, LobbyActivity::class.java).apply {
                        putExtra(LobbyActivity.EXTRA_ROOM_CODE, room.roomCode)
                        putExtra(LobbyActivity.EXTRA_IS_HOST, true)
                    }
                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(this@HomeActivity, "Failed to create room", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this@HomeActivity, "Failed to register: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            binding.btnCreateRoom.isEnabled = true
        }
    }

    private fun joinRoomFlow(username: String, code: String) {
        binding.btnJoinRoom.isEnabled = false
        lifecycleScope.launch {
            val playerResult = ApiService.createPlayer(username, selectedColor)
            playerResult.onSuccess { player ->
                PreferencesManager.savePlayer(this@HomeActivity, player.id, username)
                val roomResult = ApiService.getRoom(code)
                roomResult.onSuccess { _ ->
                    val intent = Intent(this@HomeActivity, LobbyActivity::class.java).apply {
                        putExtra(LobbyActivity.EXTRA_ROOM_CODE, code)
                        putExtra(LobbyActivity.EXTRA_IS_HOST, false)
                    }
                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(this@HomeActivity, "Room not found!", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this@HomeActivity, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            binding.btnJoinRoom.isEnabled = true
        }
    }
}
