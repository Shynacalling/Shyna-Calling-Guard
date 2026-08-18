package com.example.callruleblocker.call

import android.content.Context
import com.example.callruleblocker.data.LiveKitConfig
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import kotlinx.coroutines.*
import java.util.*

class LiveKitCallManager(private val context: Context) {
    private var room: Room? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun createToken(roomName: String, participantIdentity: String): String {
        val secretKey = Keys.hmacShaKeyFor(LiveKitConfig.API_SECRET.toByteArray())
        
        val claims = mutableMapOf<String, Any>()
        claims["video"] = mapOf(
            "roomJoin" to true,
            "room" to roomName,
            "canPublish" to true,
            "canSubscribe" to true
        )
        
        return Jwts.builder()
            .issuer(LiveKitConfig.API_KEY)
            .expiration(Date(System.currentTimeMillis() + 3600 * 1000)) // 1 hour
            .subject(participantIdentity)
            .claims(claims)
            .signWith(secretKey)
            .compact()
    }

    suspend fun joinRoom(roomName: String, userId: String): Room {
        val token = createToken(roomName, userId)
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
