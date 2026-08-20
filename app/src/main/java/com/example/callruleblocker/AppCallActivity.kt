package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
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

        setContent {
            ShynaTheme(mode = ThemeMode.DARK) {
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
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(context, "Microphone access is required", Toast.LENGTH_LONG).show()
            onExit()
        }
    }

    DisposableEffect(callId) {
        val registration = CallSignalingManager.listenToCall(callId) { updatedCall ->
            call = updatedCall
            if (updatedCall.status == AppCallStatus.ENDED || 
                updatedCall.status == AppCallStatus.REJECTED || 
                updatedCall.status == AppCallStatus.MISSED) {
                onExit()
            }
        }
        onDispose { registration.remove() }
    }

    val joinCall = {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (call?.type == AppCallType.VIDEO) permissions.add(Manifest.permission.CAMERA)
        
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
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
                    if (call?.type == AppCallType.VIDEO) {
                        localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack
                    }
                    r.events.collect { event ->
                        if (event is RoomEvent.TrackSubscribed && event.track is VideoTrack) {
                            remoteVideoTrack = event.track as VideoTrack
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

    Box(Modifier.fillMaxSize().background(ShynaDesign.premiumGradient())) {
        if (call == null) {
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
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            onMuteToggle = {
                                isMuted = !isMuted
                                scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) }
                            },
                            onSpeakerToggle = {
                                isSpeakerOn = !isSpeakerOn
                                audioManager.isSpeakerphoneOn = isSpeakerOn
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
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
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
fun VoiceCallUI(call: AppCall, isIncoming: Boolean, isMuted: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onSpeakerToggle: () -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val peerPhoto = if (isIncoming) call.callerPhoto else call.receiverPhoto

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
            Text("Secure Voice Call", color = ShynaDesign.colors.BrandGreen, fontSize = 16.sp)
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
fun VideoCallUI(call: AppCall, isIncoming: Boolean, room: Room?, remoteTrack: VideoTrack?, localTrack: VideoTrack?, isMuted: Boolean, isCameraOff: Boolean, isSpeakerOn: Boolean, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onSpeakerToggle: () -> Unit, onSwitchCamera: () -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (room != null && remoteTrack != null) {
            VideoRenderer(remoteTrack, room, modifier = Modifier.fillMaxSize())
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
