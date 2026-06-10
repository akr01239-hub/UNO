package com.uno.game.ui.lobby

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.uno.game.databinding.ActivityLobbyBinding
import com.uno.game.network.ApiService
import com.uno.game.network.SocketManager
import com.uno.game.ui.game.GameActivity
import com.uno.game.utils.PreferencesManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class LobbyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLobbyBinding
    private lateinit var playerAdapter: LobbyPlayerAdapter
    private var roomCode: String = ""
    private var playerId: String = ""
    private var hostId: String = ""

    companion object {
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_IS_HOST = "is_host"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomCode = intent.getStringExtra(EXTRA_ROOM_CODE) ?: return
        val isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)
        playerId = PreferencesManager.getPlayerId(this) ?: return
        val username = PreferencesManager.getUsername(this) ?: return

        binding.tvRoomCode.text = "Room: $roomCode"
        binding.btnStart.visibility = if (isHost) View.VISIBLE else View.GONE

        playerAdapter = LobbyPlayerAdapter()
        binding.rvLobbyPlayers.apply {
            layoutManager = LinearLayoutManager(this@LobbyActivity)
            adapter = playerAdapter
        }

        setupSocket()
        SocketManager.joinRoom(roomCode, playerId, username)

        binding.btnStart.setOnClickListener {
            SocketManager.startGame(roomCode, playerId)
        }

        binding.btnCopyCode.setOnClickListener {
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Room Code", roomCode))
            Toast.makeText(this, "Room code copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSocket() {
        SocketManager.onRoomUpdate = { json ->
            val players = json.optJSONArray("players")
            val list = mutableListOf<LobbyPlayer>()
            if (players != null) {
                for (i in 0 until players.length()) {
                    val p = players.getJSONObject(i)
                    list.add(LobbyPlayer(
                        id = p.optString("id"),
                        username = p.optString("username"),
                        avatarColor = p.optString("avatar_color", "#FF6B6B"),
                        seatPosition = p.optInt("seat_position")
                    ))
                }
            }
            hostId = json.optString("hostId")
            runOnUiThread {
                playerAdapter.submitList(list)
                binding.tvPlayerCount.text = "${list.size}/6 Players"
                binding.btnStart.isEnabled = list.size >= 2
            }
        }

        SocketManager.onGameStarted = { state ->
            runOnUiThread {
                val intent = Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_ROOM_CODE, roomCode)
                }
                startActivity(intent)
                finish()
            }
        }

        SocketManager.onError = { msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.onRoomUpdate = null
        SocketManager.onGameStarted = null
    }
}

data class LobbyPlayer(
    val id: String,
    val username: String,
    val avatarColor: String,
    val seatPosition: Int
)
