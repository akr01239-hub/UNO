package com.uno.game.models

import com.google.gson.annotations.SerializedName

data class UnoCard(
    val id: String,
    val color: String,  // red, green, blue, yellow, wild
    val value: String   // 0-9, skip, reverse, draw2, wild, wild_draw4
) {
    fun getColorInt(): Int = when (color) {
        "red"    -> 0xFFE53935.toInt()
        "green"  -> 0xFF43A047.toInt()
        "blue"   -> 0xFF1E88E5.toInt()
        "yellow" -> 0xFFFDD835.toInt()
        "wild"   -> 0xFF6A1B9A.toInt()
        else     -> 0xFF424242.toInt()
    }

    fun getDisplayValue(): String = when (value) {
        "skip"       -> "⊘"
        "reverse"    -> "⇄"
        "draw2"      -> "+2"
        "wild"       -> "★"
        "wild_draw4" -> "+4"
        else         -> value
    }

    fun isWild(): Boolean = color == "wild"
    fun isActionCard(): Boolean = value in listOf("skip", "reverse", "draw2", "wild", "wild_draw4")
}

data class Player(
    val id: String,
    val username: String,
    @SerializedName("avatar_color") val avatarColor: String = "#FF6B6B",
    val cardCount: Int = 0,
    val hand: List<UnoCard>? = null,
    val saidUno: Boolean = false,
    @SerializedName("seat_position") val seatPosition: Int = 0,
    @SerializedName("is_connected") val isConnected: Boolean = true
)

data class Room(
    val id: String,
    @SerializedName("room_code") val roomCode: String,
    @SerializedName("host_id") val hostId: String,
    val status: String,
    @SerializedName("max_players") val maxPlayers: Int = 6,
    @SerializedName("current_players") val currentPlayers: Int = 0,
    val players: List<Player> = emptyList()
)

data class GameState(
    val roomId: String,
    val status: String,           // waiting, playing, finished
    val currentPlayerId: String?,
    val currentColor: String?,
    val direction: Int,
    val pendingDraw: Int,
    val topCard: UnoCard?,
    val deckCount: Int,
    val winner: String?,
    val players: List<Player>,
    val lastEvent: LastEvent? = null
)

data class LastEvent(
    val success: Boolean,
    val card: UnoCard?,
    val drawnCards: List<UnoCard>?,
    val drawCount: Int?,
    val unoAlert: Boolean?,
    val winner: String?
)

data class CreatePlayerRequest(
    val username: String,
    @SerializedName("avatarColor") val avatarColor: String
)

data class CreateRoomRequest(
    @SerializedName("hostId") val hostId: String,
    @SerializedName("maxPlayers") val maxPlayers: Int = 6
)
