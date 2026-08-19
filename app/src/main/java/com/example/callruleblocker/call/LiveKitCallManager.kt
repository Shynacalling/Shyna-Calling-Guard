package com.example.callruleblocker.call

import android.content.Context
import android.util.Log
import com.example.callruleblocker.data.LiveKitConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LiveKitCallManager(private val context: Context) {
    private var room: Room? = null
    private val client = OkHttpClient()
    private val gson = Gson()

    private fun getTokenServerUrl(): String {
        val prefs = context.getSharedPreferences("smart_communication_v2", Context.MODE_PRIVATE)
        return prefs.getString("server_url", LiveKitConfig.TOKEN_SERVER_URL) ?: LiveKitConfig.TOKEN_SERVER_URL
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchToken(roomName: String, userId: String): String? = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            println("LiveKit: No internet connection")
            return@withContext null
        }

        val baseUrl = getTokenServerUrl().trimEnd('/')
        val url = "$baseUrl/token"
        
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
                if (!response.isSuccessful) {
                    println("LiveKit: HTTP Error ${response.code}")
                    return@withContext null
                }
                
                val responseData = response.body?.string() ?: return@withContext null
                if (responseData.isBlank()) return@withContext null

                try {
                    val result = gson.fromJson(responseData, JsonObject::class.java)
                    val token = result.get("token")?.asString
                    if (token.isNullOrBlank()) {
                        println("LiveKit: Response missing token field")
                        null
                    } else {
                        token
                    }
                } catch (e: Exception) {
                    println("LiveKit: Malformed JSON response: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun joinRoom(roomName: String, userId: String): Room {
        val token = fetchToken(roomName, userId) ?: throw IllegalStateException("Failed to fetch token. Check your internet or server configuration.")
        
        try {
            val r = LiveKit.create(context)
            r.connect(LiveKitConfig.URL, token)
            room = r
            
            // Enable audio/video by default
            r.localParticipant.setMicrophoneEnabled(true)
            r.localParticipant.setCameraEnabled(true)
            
            return r
        } catch (e: Exception) {
            println("LiveKit: Connection failure: ${e.message}")
            throw e
        }
    }

    fun leaveRoom() {
        room?.disconnect()
        room = null
    }

    suspend fun notifyReceiver(call: AppCall): Boolean = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
        val idToken = try {
            user.getIdToken(false).await().token ?: return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }

        val baseUrl = getTokenServerUrl().trimEnd('/')
        val url = "$baseUrl/notify-call"

        val json = JsonObject().apply {
            addProperty("callId", call.id)
            addProperty("receiverUid", call.receiverUid)
            addProperty("callerName", call.callerName)
            addProperty("callType", call.type.name)
        }

        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $idToken")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ShynaCall", "FCM_NOTIFY_SERVER: success=$success code=${response.code}")
                success
            }
        } catch (e: Exception) {
            Log.e("ShynaCall", "FCM_NOTIFY_FAILED", e)
            false
        }
    }
}
