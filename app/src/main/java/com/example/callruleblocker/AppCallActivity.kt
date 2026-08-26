package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class AppCallActivity : ComponentActivity() {
    private var currentCallId: String? = null
    private val autoAcceptState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreenBehavior()
        val callId = intent.getStringExtra("callId") ?: return finish()
        val isIncoming = intent.getBooleanExtra("isIncoming", false)
        autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
        currentCallId = callId
        setContent {
            ShynaTheme(mode = ThemeMode.DARK) {
                AppCallScreen(callId, isIncoming, autoAcceptState, onExit = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val callId = intent.getStringExtra("callId") ?: return
        if (callId != currentCallId) {
            currentCallId = callId
            autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
            val isIncoming = intent.getBooleanExtra("isIncoming", false)
            setContent {
                ShynaTheme(mode = ThemeMode.DARK) {
                    AppCallScreen(callId, isIncoming, autoAcceptState, onExit = { finish() })
                }
            }
        } else {
            autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
        }
    }

    private fun setupScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun AppCallScreen(callId: String, isIncoming: Boolean, autoAcceptState: State<Boolean>, onExit: () -> Unit) {
    val autoAccept by autoAcceptState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var call by remember { mutableStateOf<AppCall?>(null) }
    var room by remember { mutableStateOf<Room?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var connectionState by remember { mutableStateOf(io.livekit.android.room.Room.State.DISCONNECTED) }
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var callDuration by remember { mutableLongStateOf(0L) }
    var connectedAt by remember { mutableStateOf<Long?>(null) }
    var networkType by remember { mutableStateOf("Unknown") }
    var isScreenSharing by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    
    val callManager = remember { LiveKitCallManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val wakeLock = remember { 
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Shyna:ProximityLock") 
    }

    var joinCallLambda by remember { mutableStateOf<(() -> Unit)?>(null) }
    var routeAudioLambda by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val routeAudio: (Boolean) -> Unit = { speaker ->
        try {
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val speakerDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                val earpieceDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (speaker && speakerDevice != null) audioManager.setCommunicationDevice(speakerDevice)
                else if (!speaker && earpieceDevice != null) audioManager.setCommunicationDevice(earpieceDevice)
                else audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speaker
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener { }.build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (e: Exception) { Log.e("ShynaCall", "[AUDIO] Route Error: ${e.message}") }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val camNeeded = call?.type == AppCallType.VIDEO
        val camGranted = if (camNeeded) permissions[Manifest.permission.CAMERA] == true else true
        if (micGranted && camGranted) joinCallLambda?.invoke()
        else { Toast.makeText(context, "Required permissions denied", Toast.LENGTH_LONG).show(); onExit() }
    }

    val joinCall: () -> Unit = {
        if (!isJoining && room == null) {
            isJoining = true
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (call?.type == AppCallType.VIDEO) permissions.add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            
            if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                scope.launch {
                    try {
                        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUid == null || call == null) { onExit(); return@launch }
                        val r = callManager.joinRoom(call!!.roomName, currentUid)
                        room = r
                        routeAudio(isSpeakerOn)
                        scope.launch { 
                            delay(2000)
                            if (room?.state == Room.State.CONNECTED) r.localParticipant.setMicrophoneEnabled(true)
                        }
                        if (call?.type == AppCallType.VIDEO) {
                            scope.launch { delay(1200); r.localParticipant.setCameraEnabled(!isCameraOff) }
                        }
                        localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack
                        r.remoteParticipants.values.forEach { p -> p.videoTrackPublications.firstOrNull()?.second?.let { remoteVideoTrack = it as? VideoTrack } }
                        CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ACTIVE, callId)
                        CallSignalingManager.updateCallStatus(callId, AppCallStatus.CONNECTED)
                        r.events.collect { event ->
                            connectionState = r.state
                            when (event) {
                                is RoomEvent.TrackSubscribed -> {
                                    if (event.track is VideoTrack) remoteVideoTrack = event.track as VideoTrack
                                    else routeAudioLambda?.invoke(isSpeakerOn)
                                }
                                is RoomEvent.Disconnected -> {
                                    if (r.state == Room.State.DISCONNECTED && call?.status == AppCallStatus.CONNECTED) CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED, "livekit_disconnected")
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException) {
                            CallSignalingManager.updateCallStatus(callId, AppCallStatus.FAILED, "connection_exception")
                            onExit()
                        }
                    } finally { isJoining = false }
                }
            } else { permissionLauncher.launch(permissions.toTypedArray()); isJoining = false }
        }
    }
    
    SideEffect { joinCallLambda = joinCall; routeAudioLambda = routeAudio }

    DisposableEffect(call?.status) {
        var ringtone: Ringtone? = null
        if (isIncoming && call?.status == AppCallStatus.RINGING) {
            try {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.play()
            } catch (e: Exception) { Log.e("ShynaCall", "Ringtone Error: ${e.message}") }
        }
        onDispose { ringtone?.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            networkType = if (com.example.callruleblocker.data.NetworkDetector.isWifi(context)) "Wi-Fi" else if (com.example.callruleblocker.data.NetworkDetector.isMobile(context)) "Mobile Data" else "No Network"
            delay(5000)
        }
    }

    BackHandler(enabled = true) { Log.d("ShynaCall", "Back blocked") }

    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.RINGING) {
            delay(45000)
            if (call?.status == AppCallStatus.RINGING) { CallSignalingManager.updateCallStatus(callId, AppCallStatus.MISSED); onExit() }
        }
    }

    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.CONNECTED && connectedAt == null) connectedAt = System.currentTimeMillis()
    }

    LaunchedEffect(connectedAt) {
        if (connectedAt != null) {
            while (true) { callDuration = (System.currentTimeMillis() - connectedAt!!) / 1000; delay(500) }
        }
    }

    LaunchedEffect(call?.status, isScreenSharing) {
        if (call?.status == AppCallStatus.CONNECTED) {
            AppCallService.start(context, callId, if(isIncoming) call?.callerName ?: "" else call?.receiverName ?: "", call?.type == AppCallType.VIDEO)
        }
    }

    LaunchedEffect(isSpeakerOn) { routeAudio(isSpeakerOn) }

    DisposableEffect(isSpeakerOn, call?.type) {
        if (!isSpeakerOn && call?.type == AppCallType.VOICE) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val distance = event?.values?.get(0) ?: 10f
                    if (distance < (event?.sensor?.maximumRange ?: 10f)) { if (!wakeLock.isHeld) wakeLock.acquire() }
                    else if (wakeLock.isHeld) wakeLock.release()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensorManager.unregisterListener(listener); if (wakeLock.isHeld) wakeLock.release() }
        } else onDispose { if (wakeLock.isHeld) wakeLock.release() }
    }

    DisposableEffect(callId) {
        val registration = CallSignalingManager.listenToCall(callId) { updatedCall ->
            call = updatedCall
            when (updatedCall.status) {
                AppCallStatus.RINGING -> {
                    if (isIncoming) {
                        CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.INCOMING, callId)
                        if (autoAccept) { CallSignalingManager.updateCallStatus(callId, AppCallStatus.ACCEPTED, "auto_accept"); joinCall() }
                    }
                }
                AppCallStatus.ACCEPTED, AppCallStatus.CONNECTED -> CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ACTIVE, callId)
                AppCallStatus.ENDED, AppCallStatus.REJECTED, AppCallStatus.MISSED, AppCallStatus.FAILED -> {
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
        if (connectionState == Room.State.RECONNECTING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).zIndex(10f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Reconnecting...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen) }
        } else {
            when (call!!.status) {
                AppCallStatus.RINGING -> {
                    if (isIncoming) {
                        IncomingCallUI(call!!, 
                            onAccept = { CallSignalingManager.updateCallStatus(callId, AppCallStatus.ACCEPTED, "user_accept"); joinCall() }, 
                            onReject = { CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED, "user_reject"); onExit() }
                        )
                    } else {
                        OutgoingCallUI(call!!, onCancel = { CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED, "caller_cancel"); onExit() })
                    }
                }
                AppCallStatus.ACCEPTED, AppCallStatus.CONNECTED -> {
                    if (room == null && !isIncoming) { LaunchedEffect(Unit) { joinCall() } }
                    val screenShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            val data = result.data ?: return@rememberLauncherForActivityResult
                            scope.launch { room?.localParticipant?.setScreenShareEnabled(true, io.livekit.android.room.track.screencapture.ScreenCaptureParams(mediaProjectionPermissionResultData = data)); isScreenSharing = true }
                        }
                    }
                    if (call!!.type == AppCallType.VIDEO) {
                        VideoCallUI(
                            call = call!!, isIncoming = isIncoming, room = room, scope = scope, duration = callDuration, networkType = networkType, remoteTrack = remoteVideoTrack, localTrack = localVideoTrack,
                            isMuted = isMuted, isCameraOff = isCameraOff, isSpeakerOn = isSpeakerOn, isScreenSharing = isScreenSharing, screenShareLauncher = screenShareLauncher,
                            onMuteToggle = { isMuted = !isMuted; scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) } },
                            onCameraToggle = { isCameraOff = !isCameraOff; scope.launch { room?.localParticipant?.setCameraEnabled(!isCameraOff) } },
                            onSpeakerToggle = { isSpeakerOn = !isSpeakerOn },
                            onScreenShareToggle = { isScreenSharing = it },
                            onSwitchCamera = { (room?.localParticipant?.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track as? io.livekit.android.room.track.LocalVideoTrack)?.switchCamera(); isFrontCamera = !isFrontCamera },
                            onEndCall = { CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED, "user_hangup"); onExit() }
                        )
                    } else {
                        VoiceCallUI(
                            call = call!!, isIncoming = isIncoming, room = room, scope = scope, duration = callDuration, networkType = networkType,
                            isMuted = isMuted, isSpeakerOn = isSpeakerOn, isScreenSharing = isScreenSharing, screenShareLauncher = screenShareLauncher,
                            onMuteToggle = { isMuted = !isMuted; scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) } },
                            onSpeakerToggle = { isSpeakerOn = !isSpeakerOn },
                            onScreenShareToggle = { isScreenSharing = it },
                            onEndCall = { CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED, "user_hangup"); onExit() }
                        )
                    }
                }
                else -> onExit()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ENDED, callId)
            AppCallService.stop(context)
            callManager.leaveRoom()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(callId.hashCode())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            val bytesPerSec = if (call?.type == AppCallType.VIDEO) 500000L else 50000L
            val totalBytes = callDuration * bytesPerSec
            com.example.callruleblocker.data.NetworkUsageTracker.track(context, "calls", sent = totalBytes / 2, received = totalBytes / 2)
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            call?.let { c ->
                if (currentUid != null) {
                    val finalCall = c.copy(duration = callDuration)
                    CallSignalingManager.saveCallHistory(finalCall, currentUid)
                    if (c.status != AppCallStatus.RINGING) CallSignalingManager.saveCallMessageToChat(finalCall)
                    if (c.status == AppCallStatus.CONNECTED || c.status == AppCallStatus.ACCEPTED) CallSignalingManager.updateCallStatus(callId, AppCallStatus.ENDED, "activity_disposed")
                }
            }
        }
    }
}

@Composable
fun IncomingCallUI(call: AppCall, onAccept: () -> Unit, onReject: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale")
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, modifier = Modifier.size(180.dp).scale(scale).border(BorderStroke(2.dp, Color.White.copy(0.2f)), CircleShape), color = Color.DarkGray) {
                if (!call.callerPhoto.isNullOrBlank()) AsyncImage(model = call.callerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(50.dp), tint = Color.LightGray)
            }
            Spacer(Modifier.height(40.dp))
            Text(call.callerName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Incoming Shyna ${call.type.name.lowercase()} call...", color = Color.Gray, fontSize = 16.sp)
        }
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = onReject, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(8.dp)); Text("Decline", color = Color.White, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = onAccept, containerColor = Color(0xFF25D366), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(if (call.type == AppCallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(8.dp)); Text("Accept", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OutgoingCallUI(call: AppCall, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, modifier = Modifier.size(180.dp), border = BorderStroke(2.dp, Color.White.copy(0.2f)), color = Color.DarkGray) {
                val photo = call.receiverPhoto
                if (!photo.isNullOrBlank()) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(50.dp), tint = Color.LightGray)
            }
            Spacer(Modifier.height(40.dp))
            Text(call.receiverName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Calling...", color = Color.Gray, fontSize = 16.sp)
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(onClick = { onCancel() }, modifier = Modifier.size(72.dp), containerColor = Color(0xFFE53935), shape = CircleShape) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(8.dp)); Text("Cancel", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun VoiceCallUI(call: AppCall, isIncoming: Boolean, room: io.livekit.android.room.Room?, scope: kotlinx.coroutines.CoroutineScope, duration: Long, networkType: String, isMuted: Boolean, isSpeakerOn: Boolean, isScreenSharing: Boolean, screenShareLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>, onMuteToggle: () -> Unit, onSpeakerToggle: () -> Unit, onScreenShareToggle: (Boolean) -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val peerPhoto = if (isIncoming) call.callerPhoto else call.receiverPhoto
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."
    val mContext = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = peerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp)); Text(text = "End-to-end encrypted", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, modifier = Modifier.size(220.dp), color = Color.DarkGray) {
                if (!peerPhoto.isNullOrBlank()) AsyncImage(model = peerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(60.dp), tint = Color.LightGray)
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1F2C34)) {
            Column(modifier = Modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val stateName = room?.state?.name ?: "IDLE"
                Text(text = if(room?.state == io.livekit.android.room.Room.State.CONNECTED) statusText else "Connecting... ($stateName)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallActionButton(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, label = "Speaker", isActive = isSpeakerOn, onClick = onSpeakerToggle)
                        CallActionButton(icon = Icons.Default.Videocam, label = "Video", isActive = false, onClick = { CallSignalingManager.updateCallType(call.id, AppCallType.VIDEO); scope.launch { room?.localParticipant?.setCameraEnabled(true) }; Toast.makeText(mContext, "Switching to video...", Toast.LENGTH_SHORT).show() })
                        CallActionButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "Mute", isActive = isMuted, onClick = onMuteToggle)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallActionButton(icon = Icons.Default.MoreHoriz, label = "More", isActive = false, onClick = { Toast.makeText(mContext, "Call Security: Active", Toast.LENGTH_SHORT).show() })
                        CallActionButton(icon = Icons.Default.FileUpload, label = "Share", isActive = isScreenSharing, onClick = { if (isScreenSharing) { scope.launch { room?.localParticipant?.setScreenShareEnabled(false); onScreenShareToggle(false) } } else { val mediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager; screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) } })
                        CallActionButton(icon = Icons.Default.CallEnd, label = "End", isActive = false, isEndButton = true, onClick = onEndCall)
                    }
                }
            }
        }
    }
}

@Composable
fun CallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, isEndButton: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = if (isEndButton) Color(0xFFE53935) else if (isActive) Color.White else Color.White.copy(0.1f), onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = if (isEndButton) Color.White else if (isActive) Color.Black else Color.White, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(8.dp)); Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun VideoCallUI(call: AppCall, isIncoming: Boolean, room: io.livekit.android.room.Room?, scope: kotlinx.coroutines.CoroutineScope, duration: Long, networkType: String, remoteTrack: VideoTrack?, localTrack: VideoTrack?, isMuted: Boolean, isCameraOff: Boolean, isSpeakerOn: Boolean, isScreenSharing: Boolean, screenShareLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onSpeakerToggle: () -> Unit, onScreenShareToggle: (Boolean) -> Unit, onSwitchCamera: () -> Unit, onEndCall: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."
    val mContext = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (room != null && remoteTrack != null) VideoRenderer(remoteTrack, room, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen); Spacer(Modifier.height(16.dp)); Text("Connecting to $peerName...", color = Color.White) } }
        Row(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent))).padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val stateName = room?.state?.name ?: "IDLE"
                Text(if(room?.state == io.livekit.android.room.Room.State.CONNECTED) statusText else "Connecting... ($stateName)", color = Color.White.copy(0.8f), fontSize = 12.sp)
            }
            IconButton(onClick = onSwitchCamera) { Icon(Icons.Default.SwitchCamera, null, tint = Color.White) }
        }
        if (room != null && localTrack != null && !isCameraOff) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).size(100.dp, 150.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray).border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp))) { VideoRenderer(localTrack, room, modifier = Modifier.fillMaxSize()) }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1F2C34).copy(alpha = 0.9f)) {
            Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CallActionButtonSmall(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, isActive = isSpeakerOn, onClick = onSpeakerToggle)
                CallActionButtonSmall(icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, isActive = isCameraOff, onClick = onCameraToggle)
                CallActionButtonSmall(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, isActive = isMuted, onClick = onMuteToggle)
                CallActionButtonSmall(icon = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare, isActive = isScreenSharing, onClick = { if (isScreenSharing) { scope.launch { room?.localParticipant?.setScreenShareEnabled(false); onScreenShareToggle(false) } } else { val mediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager; screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) } })
                FloatingActionButton(onClick = onEndCall, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(60.dp)) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
fun CallActionButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = if (isActive) Color.White else Color.White.copy(0.15f), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = if (isActive) Color.Black else Color.White, modifier = Modifier.size(24.dp)) }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s) 
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
