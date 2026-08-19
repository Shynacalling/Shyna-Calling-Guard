package com.example.callruleblocker

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.callruleblocker.call.*
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.callruleblocker.ui.VideoRenderer
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import kotlin.time.Duration.Companion.milliseconds

class AppCallActivity : ComponentActivity() {
    private companion object {
        const val TAG = "ShynaCall"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val callId = intent.getStringExtra("callId") ?: return finish()
        val isIncoming = intent.getBooleanExtra("isIncoming", false)

        setContent {
            CallRuleBlockerTheme {
                AppCallScreen(callId, isIncoming, onExit = { finish() })
            }
        }
    }
}

@Composable
fun AppCallScreen(callId: String, isIncoming: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var call by remember { mutableStateOf<AppCall?>(null) }
    var room by remember { mutableStateOf<Room?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    val callManager = remember { LiveKitCallManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            // Permissions granted, will be re-checked in joinCall if triggered again
        } else {
            Toast.makeText(context, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
            onExit()
        }
    }

    val checkPermissions = {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (call?.type == AppCallType.VIDEO) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            false
        } else {
            true
        }
    }

    DisposableEffect(callId) {
        val registration = CallSignalingManager.listenToCall(callId) { updatedCall ->
            call = updatedCall
            if (updatedCall.status == AppCallStatus.ENDED || 
                updatedCall.status == AppCallStatus.REJECTED || 
                updatedCall.status == AppCallStatus.MISSED) {
                Log.d("ShynaCall", "CALL_ENDED: id=$callId status=${updatedCall.status}")
                onExit()
            }
        }
        onDispose {
            registration.remove()
        }
    }

    // Handle missed call timeout
    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.RINGING && !isIncoming) {
            delay(45000.milliseconds) // 45 seconds timeout
            if (call?.status == AppCallStatus.RINGING) {
                CallSignalingManager.updateCallStatus(callId, AppCallStatus.MISSED)
            }
        }
    }

    val joinCall = {
        if (checkPermissions()) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            scope.launch {
                try {
                    Log.d("ShynaCall", "TOKEN_REQUEST: room=${call?.roomName} user=${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid}")
                    Log.d("ShynaCall", "LIVEKIT_CONNECTING")
                    val r = callManager.joinRoom(call!!.roomName, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser!!.uid)
                    room = r
                    Log.d("ShynaCall", "LIVEKIT_CONNECTED")
                    Log.d("ShynaCall", "TOKEN_SUCCESS")
                    
                    if (call?.type == AppCallType.VIDEO) {
                        localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack
                    }

                    r.events.collect { event ->
                        when (event) {
                            is RoomEvent.TrackSubscribed -> {
                                if (event.track is VideoTrack) {
                                    Log.d("ShynaCall", "VIDEO_TRACK_SUBSCRIBED")
                                    remoteVideoTrack = event.track as VideoTrack
                                } else {
                                    Log.d("ShynaCall", "AUDIO_TRACK_SUBSCRIBED")
                                }
                            }
                            is RoomEvent.ParticipantConnected -> {
                                Log.d("ShynaCall", "REMOTE_PARTICIPANT_CONNECTED: ${event.participant.sid}")
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "CALL_FAILED: ${e.message}", e)
                    Toast.makeText(context, "Connection failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    CallSignalingManager.updateCallStatus(callId, AppCallStatus.FAILED)
                    onExit()
                }
            }
        }
    }

    if (call == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00A884))
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
                if (room == null && isIncoming) {
                    // Already joined in onAccept
                } else if (room == null && !isIncoming) {
                    LaunchedEffect(Unit) { joinCall() }
                }

                if (call!!.type == AppCallType.VIDEO) {
                    VideoCallUI(
                        call = call!!,
                        room = room,
                        remoteTrack = remoteVideoTrack,
                        localTrack = localVideoTrack,
                        isMuted = isMuted,
                        isCameraOff = isCameraOff,
                        isSpeakerOn = isSpeakerOn,
                        onMuteToggle = {
                            isMuted = !isMuted
                            scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) }
                        },
                        onCameraToggle = {
                            isCameraOff = !isCameraOff
                            scope.launch { room?.localParticipant?.setCameraEnabled(!isCameraOff) }
                        },
                        onSpeakerToggle = {
                            isSpeakerOn = !isSpeakerOn
                            audioManager.isSpeakerphoneOn = isSpeakerOn
                            if (isSpeakerOn) {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            }
                        },
                        onSwitchCamera = {
                            val localVideoTrack = room?.localParticipant?.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track as? io.livekit.android.room.track.LocalVideoTrack
                            localVideoTrack?.let { track ->
                                track.switchCamera()
                                isFrontCamera = !isFrontCamera
                            }
                        },
                        onEndCall = {
                            CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED)
                            onExit()
                        }
                    )
                } else {
                    VoiceCallUI(
                        call = call!!,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        onMuteToggle = {
                            isMuted = !isMuted
                            scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) }
                        },
                        onSpeakerToggle = {
                            isSpeakerOn = !isSpeakerOn
                            audioManager.isSpeakerphoneOn = isSpeakerOn
                            if (isSpeakerOn) {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            }
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

    DisposableEffect(Unit) {
        onDispose {
            callManager.leaveRoom()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
}

@Composable
fun IncomingCallUI(call: AppCall, onAccept: () -> Unit, onReject: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121B22))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(120.dp), color = Color.Gray) {
                if (call.callerPhoto != null) {
                    AsyncImage(model = call.callerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(30.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(call.callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Shyna ${call.type.name.lowercase().replaceFirstChar { it.uppercase() }} Call", color = Color(0xFF00A884), fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(onClick = onReject, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.CallEnd, null)
            }
            FloatingActionButton(onClick = onAccept, containerColor = Color(0xFF00A884), contentColor = Color.White, shape = CircleShape) {
                Icon(if (call.type == AppCallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call, null)
            }
        }
    }
}

@Composable
fun OutgoingCallUI(call: AppCall, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121B22))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(120.dp), color = Color.Gray) {
                // Should show receiver info here, but signaling currently has caller info
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(30.dp), tint = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text("Ringing...", color = Color.White, fontSize = 24.sp)
        }

        FloatingActionButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, null)
        }
    }
}

@Composable
fun VoiceCallUI(call: AppCall, isMuted: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onSpeakerToggle: () -> Unit, onEndCall: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121B22))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(120.dp), color = Color.Gray) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(30.dp), tint = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text(call.callerName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("00:00", color = Color.Gray, fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMuteToggle, modifier = Modifier.background(if (isMuted) Color.White else Color.DarkGray, CircleShape)) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = if (isMuted) Color.Black else Color.White)
            }
            FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.CallEnd, null)
            }
            IconButton(onClick = onSpeakerToggle, modifier = Modifier.background(if (isSpeakerOn) Color.White else Color.DarkGray, CircleShape)) {
                Icon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, null, tint = if (isSpeakerOn) Color.Black else Color.White)
            }
        }
    }
}

@Composable
fun VideoCallUI(call: AppCall, room: Room?, remoteTrack: VideoTrack?, localTrack: VideoTrack?, isMuted: Boolean, isCameraOff: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onSpeakerToggle: () -> Unit, onSwitchCamera: () -> Unit, onEndCall: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (room != null && remoteTrack != null) {
            VideoRenderer(remoteTrack, room, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for remote...", color = Color.White)
            }
        }

        if (room != null && localTrack != null && !isCameraOff) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(100.dp, 150.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)) {
                VideoRenderer(localTrack, room, modifier = Modifier.fillMaxSize())
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSpeakerToggle, modifier = Modifier.background(if (isSpeakerOn) Color.White else Color.DarkGray.copy(0.6f), CircleShape)) {
                Icon(Icons.Default.VolumeUp, null, tint = if (isSpeakerOn) Color.Black else Color.White)
            }
            IconButton(onClick = onCameraToggle, modifier = Modifier.background(if (isCameraOff) Color.Red else Color.DarkGray.copy(0.6f), CircleShape)) {
                Icon(if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, null, tint = Color.White)
            }
            IconButton(onClick = onMuteToggle, modifier = Modifier.background(if (isMuted) Color.Red else Color.DarkGray.copy(0.6f), CircleShape)) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
            }
            IconButton(onClick = onSwitchCamera, modifier = Modifier.background(Color.DarkGray.copy(0.6f), CircleShape)) {
                Icon(Icons.Default.SwitchCamera, null, tint = Color.White)
            }
            FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.CallEnd, null)
            }
        }
    }
}
