package com.example.callruleblocker.call

import android.content.Context
import com.example.callruleblocker.data.LiveKitConfig
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject

class LiveKitCallManager(private val context: Context) {
    private var room: Room? = null
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun fetchToken(roomName: String, userId: String): String? = withContext(Dispatchers.IO) {
        val url = "${LiveKitConfig.TOKEN_SERVER_URL}/token"
        val json = JsonObject().apply {
            addProperty("roomName", roomName)
            addProperty("participantName", userId)
        }
        
        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseData = response.body?.string() ?: return@withContext null
                val result = gson.fromJson(responseData, JsonObject::class.java)
                return@withContext result.get("token")?.asString
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun joinRoom(roomName: String, userId: String): Room {
        val token = fetchToken(roomName, userId) ?: throw IllegalStateException("Failed to fetch token from server")
        val r = LiveKit.create(context)
        r.connect(LiveKitConfig.URL, token)
        room = r
        
        // Enable audio/video by default
        r.localParticipant.setMicrophoneEnabled(true)
        r.localParticipant.setCameraEnabled(true)
        
        return r
    }

    fun leaveRoom() {
        room?.disconnect()
        room = null
    }
}
