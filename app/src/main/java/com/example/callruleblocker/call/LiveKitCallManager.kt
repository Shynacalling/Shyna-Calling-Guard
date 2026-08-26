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
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
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
        Log.d("ShynaCall", "TOKEN_REQUEST_STARTED room=$roomName user=$userId")
        if (!isNetworkAvailable()) {
            Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=no_internet")
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
                if (response.code == 401) {
                    Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=UNAUTHORIZED. Check Render Server Environment Variables (API Key/Secret).")
                    return@withContext null
                }
                
                if (!response.isSuccessful) {
                    Log.e("ShynaCall", "TOKEN_REQUEST_FAILED code=${response.code} url=$url")
                    return@withContext null
                }
                
                val responseData = response.body?.string() ?: run {
                    Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=empty_body")
                    return@withContext null
                }
                
                if (responseData.isBlank()) {
                    Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=blank_body")
                    return@withContext null
                }

                try {
                    val result = gson.fromJson(responseData, JsonObject::class.java)
                    val token = result.get("token")?.asString
                    if (token.isNullOrBlank()) {
                        Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=missing_token_field data=$responseData")
                        null
                    } else {
                        Log.d("ShynaCall", "TOKEN_REQUEST_SUCCESS")
                        token
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=malformed_json error=${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=exception error=${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun joinRoom(roomName: String, userId: String): Room {
        Log.d("ShynaCall", "JOIN_FLOW_STARTED ROOM_ID=$roomName")
        Log.d("ShynaCall", "RTC_ENGINE_INIT_START")
        
        val token = fetchToken(roomName, userId) 
        if (token == null) {
            Log.e("ShynaCall", "ROOM_CONNECT_FAILED reason=token_fetch_failed")
            throw IllegalStateException("Failed to fetch token")
        }
        
        Log.d("ShynaCall", "ROOM_CONNECT_START url=${LiveKitConfig.URL}")
        try {
            val r = LiveKit.create(context)
            Log.d("ShynaCall", "RTC_ENGINE_INIT_SUCCESS. Connecting with token...")
            
            r.connect(LiveKitConfig.URL, token)
            Log.d("ShynaCall", "ROOM_CONNECT_SUCCESS sid=${r.sid} state=${r.state}")
            room = r
            return r
        } catch (e: Exception) {
            Log.e("ShynaCall", "ROOM_CONNECT_FAILED error=${e.message}", e)
            if (e.message?.contains("401") == true || e.message?.contains("unauthorized") == true) {
                Log.e("ShynaCall", "CRITICAL: LiveKit server rejected token (401). Please verify LIVEKIT_API_KEY and LIVEKIT_API_SECRET on your Render server match the ones in your LiveKit Cloud project (kn60m55l).")
            }
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
            addProperty("callerUid", call.callerUid)
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
