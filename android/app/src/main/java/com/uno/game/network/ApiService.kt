package com.uno.game.network

import com.google.gson.Gson
import com.uno.game.BuildConfig
import com.uno.game.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val BASE_URL = BuildConfig.SERVER_URL

    suspend fun createPlayer(username: String, avatarColor: String): Result<Player> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(CreatePlayerRequest(username, avatarColor))
                    .toRequestBody(JSON)
                val req = Request.Builder().url("$BASE_URL/api/players").post(body).build()
                val res = client.newCall(req).execute()
                val json = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    Result.success(gson.fromJson(json, Player::class.java))
                } else {
                    Result.failure(Exception("Server error: ${res.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createRoom(hostId: String, maxPlayers: Int = 6): Result<Room> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(CreateRoomRequest(hostId, maxPlayers)).toRequestBody(JSON)
                val req = Request.Builder().url("$BASE_URL/api/rooms").post(body).build()
                val res = client.newCall(req).execute()
                val json = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    Result.success(gson.fromJson(json, Room::class.java))
                } else {
                    Result.failure(Exception("Server error: ${res.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getRoom(roomCode: String): Result<Room> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$BASE_URL/api/rooms/$roomCode").get().build()
                val res = client.newCall(req).execute()
                val json = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    Result.success(gson.fromJson(json, Room::class.java))
                } else {
                    Result.failure(Exception("Room not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getPlayerStats(playerId: String): Result<Player> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$BASE_URL/api/players/$playerId/stats").get().build()
                val res = client.newCall(req).execute()
                val json = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    Result.success(gson.fromJson(json, Player::class.java))
                } else {
                    Result.failure(Exception("Player not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
