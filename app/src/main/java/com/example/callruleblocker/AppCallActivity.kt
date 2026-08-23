package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.callruleblocker.call.*
import com.example.callruleblocker.ui.ShynaDesign
import com.example.callruleblocker.ui.ShynaTheme
import com.example.callruleblocker.ui.ThemeMode
import com.example.callruleblocker.ui.VideoRenderer
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class AppCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val callId = intent.getStringExtra("callId") ?: return finish()
        val isIncoming = intent.getBooleanExtra("isIncoming", false)
        val autoAccept = intent.getBooleanExtra("autoAccept", false)

        setContent {
            ShynaTheme(mode = ThemeMode.DARK) {
                AppCallScreen(callId, isIncoming, autoAccept, onExit = { finish() })
            }
        }
    }
}

@Composable
fun AppCallScreen(callId: String, isIncoming: Boolean, autoAccept: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var call by remember { mutableStateOf<AppCall?>(null) }
    var room by remember { mutableStateOf<Room?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(call?.type == AppCallType.VIDEO) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var callDuration by remember { mutableLongStateOf(0L) }
    var networkType by remember { mutableStateOf("Unknown") }

    val callManager = remember { LiveKitCallManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // RINGTONE LOGIC
    DisposableEffect(call?.status) {
        var ringtone: Ringtone? = null
        if (isIncoming && call?.status == AppCallStatus.RINGING) {
            try {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.play()
                Log.d("ShynaCall", "Incoming ringtone started")
            } catch (e: Exception) {
                Log.e("ShynaCall", "Ringtone Error: ${e.message}")
            }
        }
        onDispose {
            ringtone?.stop()
        }
    }

    // MONITOR NETWORK
    LaunchedEffect(Unit) {
        while (true) {
            networkType = if (com.example.callruleblocker.data.NetworkDetector.isWifi(context)) "Wi-Fi" 
                          else if (com.example.callruleblocker.data.NetworkDetector.isMobile(context)) "Mobile Data" 
                          else "No Network"
            delay(5000)
        }
    }

    // BACK BUTTON PROTECTION
    BackHandler(enabled = true) {
        // Prevent accidental exit during active call. 
        // User must explicitly press "End Call" or "Reject".
        Log.d("ShynaCall", "Back gesture blocked in Call Screen")
    }

    // TIMEOUT LOGIC (45 Seconds)
    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.RINGING) {
            delay(45000)
            if (call?.status == AppCallStatus.RINGING) {
                CallSignalingManager.updateCallStatus(callId, AppCallStatus.MISSED)
                onExit()
            }
        }
    }

    // CALL TIMER
    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.CONNECTED) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(context, "Microphone access is required", Toast.LENGTH_LONG).show()
            onExit()
        }
    }

    val routeAudio = { speaker: Boolean ->
        Log.d("ShynaCall", "[AUDIO] Routing Request: speaker=$speaker")
        scope.launch {
            delay(150) // Stability delay
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val devices = audioManager.availableCommunicationDevices
                    val speakerDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    val earpieceDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    
                    if (speaker && speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                        Log.d("ShynaCall", "[AUDIO] Route set to SPEAKER (S+)")
                    } else if (!speaker && earpieceDevice != null) {
                        audioManager.setCommunicationDevice(earpieceDevice)
                        Log.d("ShynaCall", "[AUDIO] Route set to EARPIECE (S+)")
                    } else if (!speaker) {
                        audioManager.clearCommunicationDevice()
                        Log.d("ShynaCall", "[AUDIO] Route CLEARED (Earpiece Fallback)")
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "[AUDIO] S-API Error: ${e.message}")
                }
            }
            
            try {
                audioManager.isSpeakerphoneOn = speaker
                Log.d("ShynaCall", "[AUDIO] isSpeakerphoneOn set to $speaker")
            } catch (e: Exception) {
                Log.e("ShynaCall", "[AUDIO] Legacy Toggle Error: ${e.message}")
            }
        }
    }

    val joinCall = {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (call?.type == AppCallType.VIDEO) permissions.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            // Initial Audio Route
            routeAudio(isSpeakerOn)

            scope.launch {
                try {
                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (currentUid == null || call == null) {
                        Log.e("ShynaCall", "Join Failed: UID or Call is null")
                        onExit()
                        return@launch
                    }
                    val r = callManager.joinRoom(call!!.roomName, currentUid)
                    room = r
                    Log.d("ShynaCall", "[CALL] Room Connected: ${call!!.roomName}")
                    
                    // Critical Audio Setup
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    routeAudio(isSpeakerOn)
                    
                    // INITIAL TRACK SYNC
                    // Match the working pattern in VideoCallScreen.kt
                    localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack

                    // Check remote participants already in room
                    r.remoteParticipants.values.forEach { p ->
                        val remoteTrack = p.videoTrackPublications.firstOrNull()?.second as? VideoTrack
                        if (remoteTrack != null) {
                            remoteVideoTrack = remoteTrack
                            Log.d("ShynaCall", "[VIDEO] Remote Track Synced from ${p.identity}")
                        }
                    }
                    
                    scope.launch { 
                        r.localParticipant.setMicrophoneEnabled(!isMuted)
                        Log.d("ShynaCall", "[AUDIO] Local Mic Enabled: ${!isMuted}")
                    }
                    if (call?.type == AppCallType.VIDEO) {
                        scope.launch { 
                            r.localParticipant.setCameraEnabled(!isCameraOff)
                            Log.d("ShynaCall", "[VIDEO] Local Camera Enabled: ${!isCameraOff}")
                        }
                    }
                    
                    CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ACTIVE, callId)
                    CallSignalingManager.updateCallStatus(callId, AppCallStatus.CONNECTED)

                    r.events.collect { event ->
                        when (event) {
                            is RoomEvent.TrackSubscribed -> {
                                if (event.track is VideoTrack) {
                                    remoteVideoTrack = event.track as VideoTrack
                                    Log.d("ShynaCall", "[VIDEO] Remote Track Subscribed: ${event.track.sid}")
                                } else {
                                    Log.d("ShynaCall", "[AUDIO] Remote Audio Subscribed: ${event.track.sid}")
                                }
                            }
                            is RoomEvent.TrackUnsubscribed -> {
                                if (event.track == remoteVideoTrack) {
                                    remoteVideoTrack = null
                                    Log.d("ShynaCall", "[VIDEO] Remote Track Unsubscribed")
                                }
                            }
                            is RoomEvent.TrackPublished -> {
                                Log.d("ShynaCall", "[CALL] Track Published by ${event.participant.identity}: ${event.publication.sid}")
                                if (event.participant == r.localParticipant && event.publication.track is VideoTrack) {
                                    localVideoTrack = event.publication.track as VideoTrack
                                    Log.d("ShynaCall", "[VIDEO] Local Track Synced")
                                }
                            }
                            is RoomEvent.ParticipantConnected -> {
                                Log.d("ShynaCall", "[CALL] Participant Joined: ${event.participant.identity}")
                            }
                            is RoomEvent.ParticipantDisconnected -> {
                                Log.d("ShynaCall", "[CALL] Participant Left: ${event.participant.identity}")
                                if (r.remoteParticipants.isEmpty()) {
                                    Log.d("ShynaCall", "[CALL] Room Empty - Closing Screen")
                                    CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED)
                                }
                            }
                            is RoomEvent.Connected -> {
                                Log.d("ShynaCall", "[CALL] Room Connection Established")
                            }
                            is RoomEvent.Disconnected -> {
                                Log.d("ShynaCall", "[CALL] Room Disconnected")
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "Join Failed: ${e.message}")
                    onExit()
                }
            }
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    DisposableEffect(callId) {
        val registration = CallSignalingManager.listenToCall(callId) { updatedCall ->
            call = updatedCall
            
            // Sync with Global Call Controller
            when (updatedCall.status) {
                AppCallStatus.RINGING -> {
                    if (isIncoming) {
                        CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.INCOMING, callId)
                        if (autoAccept) {
                            CallSignalingManager.updateCallStatus(callId, AppCallStatus.ACCEPTED)
                            joinCall()
                        }
                    }
                }
                AppCallStatus.ACCEPTED, AppCallStatus.CONNECTED -> {
                    CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ACTIVE, callId)
                }
                AppCallStatus.ENDED, AppCallStatus.REJECTED, AppCallStatus.MISSED -> {
                    CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ENDED, callId)
                    onExit()
                }
                else -> {}
            }
        }
        onDispose { registration.remove() }
    }

    val globalState by CallStateController.globalState.collectAsState()

    Box(Modifier.fillMaxSize().background(ShynaDesign.premiumGradient())) {
        if (globalState == GlobalCallState.INTERRUPTED) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Call Interrupted", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("A higher priority call is active.", color = Color.White.copy(0.7f), fontSize = 16.sp)
                    Spacer(Modifier.height(24.dp))
                    Text("Please wait...", color = ShynaDesign.colors.BrandGreen)
                }
            }
        } else if (call == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
            }
        } else {
            when (call!!.status) {
                AppCallStatus.RINGING -> {
                    if (isIncoming) {
                        IncomingCallUI(call!!, 
                            onAccept = { 
                                CallSignalingManager.updateCallStatus(callId, AppCallStatus.ACCEPTED)
                                joinCall()
                            }, 
                            onReject = { 
                                CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED)
                                onExit()
                            }
                        )
                    } else {
                        OutgoingCallUI(call!!, onCancel = {
                            CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED)
                            onExit()
                        })
                    }
                }
                AppCallStatus.ACCEPTED, AppCallStatus.CONNECTED -> {
                    if (room == null && !isIncoming) {
                        LaunchedEffect(Unit) { joinCall() }
                    }

                    if (call!!.type == AppCallType.VIDEO) {
                        VideoCallUI(
                            call = call!!,
                            isIncoming = isIncoming,
                            room = room,
                            duration = callDuration,
                            networkType = networkType,
                            remoteTrack = remoteVideoTrack,
                            localTrack = localVideoTrack,
                            isMuted = isMuted,
                            isCameraOff = isCameraOff,
                            isSpeakerOn = isSpeakerOn,
                            onMuteToggle = {
                                isMuted = !isMuted
                                scope.launch { 
                                    room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                                    routeAudio(isSpeakerOn)
                                }
                            },
                            onCameraToggle = {
                                isCameraOff = !isCameraOff
                                scope.launch { 
                                    room?.localParticipant?.setCameraEnabled(!isCameraOff)
                                    routeAudio(isSpeakerOn)
                                }
                            },
                            onSpeakerToggle = {
                                isSpeakerOn = !isSpeakerOn
                                routeAudio(isSpeakerOn)
                            },
                            onSwitchCamera = {
                                val track = room?.localParticipant?.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track as? io.livekit.android.room.track.LocalVideoTrack
                                track?.switchCamera()
                                isFrontCamera = !isFrontCamera
                            },
                            onEndCall = {
                                CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED)
                                onExit()
                            }
                        )
                    } else {
                        VoiceCallUI(
                            call = call!!,
                            isIncoming = isIncoming,
                            duration = callDuration,
                            networkType = networkType,
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            onMuteToggle = {
                                isMuted = !isMuted
                                scope.launch { 
                                    room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                                    routeAudio(isSpeakerOn)
                                }
                            },
                            onSpeakerToggle = {
                                isSpeakerOn = !isSpeakerOn
                                routeAudio(isSpeakerOn)
                            },
                            onEndCall = {
                                CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED)
                                onExit()
                            }
                        )
                    }
                }
                else -> onExit()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            callManager.leaveRoom()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // TRACK CALL USAGE (Estimation: ~50KB per second for voice, ~500KB for video)
            val bytesPerSec = if (call?.type == AppCallType.VIDEO) 500000L else 50000L
            val totalBytes = callDuration * bytesPerSec
            com.example.callruleblocker.data.NetworkUsageTracker.track(context, "calls", sent = totalBytes / 2, received = totalBytes / 2)

            // SAVE FINAL RECORD TO HISTORY
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            call?.let { c ->
                if (currentUid != null) {
                    val finalCall = c.copy(duration = callDuration)
                    CallSignalingManager.saveCallHistory(finalCall, currentUid)
                    
                    // SAVE CALL SYSTEM MESSAGE TO CHAT
                    if (c.status != AppCallStatus.RINGING) {
                        saveCallMessageToChat(finalCall)
                    }
                }
            }
        }
    }
}

private fun saveCallMessageToChat(call: AppCall) {
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val chatId = if (call.callerUid < call.receiverUid) "${call.callerUid}_${call.receiverUid}" else "${call.receiverUid}_${call.callerUid}"
    
    val msg = mapOf(
        "text" to if(call.type == AppCallType.VIDEO) "Video call" else "Audio call",
        "senderId" to call.callerUid,
        "timestamp" to com.google.firebase.Timestamp.now(),
        "type" to "CALL",
        "callId" to call.id,
        "callType" to call.type.name,
        "callStatus" to call.status.name,
        "callDuration" to call.duration
    )
    
    db.collection("chats").document(chatId).collection("messages").add(msg)
    
    val statusLabel = when(call.status) {
        AppCallStatus.MISSED -> "Missed ${call.type.name.lowercase()} call"
        AppCallStatus.REJECTED -> "Rejected ${call.type.name.lowercase()} call"
        else -> "${if(call.type == AppCallType.VIDEO) "📹" else "📞"} ${call.type.name.lowercase()} call"
    }
    db.collection("chats").document(chatId).set(mapOf("lastMessage" to statusLabel, "timestamp" to com.google.firebase.Timestamp.now()), com.google.firebase.firestore.SetOptions.merge())
}

@Composable
fun IncomingCallUI(call: AppCall, onAccept: () -> Unit, onReject: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape, 
                modifier = Modifier.size(160.dp).scale(scale).border(BorderStroke(4.dp, ShynaDesign.colors.BrandGreen), CircleShape),
                color = ShynaDesign.colors.SurfaceBg
            ) {
                if (!call.callerPhoto.isNullOrBlank()) {
                    AsyncImage(model = call.callerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(40.dp), tint = ShynaDesign.colors.TextSecondary)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(call.callerName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("Incoming Shyna ${call.type.name.lowercase()} call...", color = ShynaDesign.colors.BrandGreen, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(onClick = onReject, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(80.dp)) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FloatingActionButton(onClick = onAccept, containerColor = ShynaDesign.colors.BrandGreen, shape = CircleShape, modifier = Modifier.size(80.dp)) {
                Icon(if (call.type == AppCallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
fun OutgoingCallUI(call: AppCall, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(160.dp), border = BorderStroke(2.dp, Color.White.copy(0.2f)), color = ShynaDesign.colors.SurfaceBg) {
                val photo = call.receiverPhoto
                if (!photo.isNullOrBlank()) {
                    AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(40.dp), tint = ShynaDesign.colors.TextSecondary)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(call.receiverName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("Calling...", color = ShynaDesign.colors.BrandGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        FloatingActionButton(
            onClick = { onCancel() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).size(80.dp),
            containerColor = Color(0xFFE53935),
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun VoiceCallUI(call: AppCall, isIncoming: Boolean, duration: Long, networkType: String, isMuted: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onSpeakerToggle: () -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val peerPhoto = if (isIncoming) call.callerPhoto else call.receiverPhoto
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(180.dp), border = BorderStroke(3.dp, ShynaDesign.colors.BrandGreen), color = ShynaDesign.colors.SurfaceBg) {
                if (!peerPhoto.isNullOrBlank()) {
                    AsyncImage(model = peerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(50.dp), tint = ShynaDesign.colors.TextSecondary)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(peerName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text(statusText, color = ShynaDesign.colors.BrandGreen, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            
            if (call.status == AppCallStatus.CONNECTED) {
                Text(networkType, color = Color.White.copy(0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMuteToggle, modifier = Modifier.size(64.dp).background(if (isMuted) Color.White else Color.White.copy(0.1f), CircleShape)) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = if (isMuted) Color.Black else Color.White)
            }
            FloatingActionButton(onClick = onEndCall, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(84.dp)) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onSpeakerToggle, modifier = Modifier.size(64.dp).background(if (isSpeakerOn) Color.White else Color.White.copy(0.1f), CircleShape)) {
                Icon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, null, tint = if (isSpeakerOn) Color.Black else Color.White)
            }
        }
    }
}

@Composable
fun VideoCallUI(call: AppCall, isIncoming: Boolean, room: Room?, duration: Long, networkType: String, remoteTrack: VideoTrack?, localTrack: VideoTrack?, isMuted: Boolean, isCameraOff: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onSpeakerToggle: () -> Unit, onSwitchCamera: () -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (room != null && remoteTrack != null) {
            VideoRenderer(remoteTrack, room, modifier = Modifier.fillMaxSize())
            
            // Top Overlay for Name and Duration
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent))).padding(24.dp)) {
                Column {
                    Text(peerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(statusText, color = Color.White.copy(0.8f), fontSize = 14.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(networkType, color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to $peerName...", color = Color.White)
                }
            }
        }

        if (room != null && localTrack != null && !isCameraOff) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(120.dp, 180.dp).clip(RoundedCornerShape(16.dp)).background(Color.DarkGray).border(2.dp, Color.White.copy(0.3f), RoundedCornerShape(16.dp))) {
                VideoRenderer(localTrack, room, modifier = Modifier.fillMaxSize())
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ControlIcon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, isSpeakerOn, onSpeakerToggle)
                ControlIcon(if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, isCameraOff, onCameraToggle)
                ControlIcon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, isMuted, onMuteToggle)
                ControlIcon(Icons.Default.SwitchCamera, false, onSwitchCamera)
            }
            Spacer(Modifier.height(30.dp))
            FloatingActionButton(onClick = onEndCall, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.align(Alignment.CenterHorizontally).size(84.dp)) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp).background(if (active) Color.White else Color.Black.copy(0.5f), CircleShape).border(1.dp, Color.White.copy(0.2f), CircleShape)) {
        Icon(icon, null, tint = if (active) Color.Black else Color.White)
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
