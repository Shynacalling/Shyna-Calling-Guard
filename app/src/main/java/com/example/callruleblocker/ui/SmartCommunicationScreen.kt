package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import android.provider.ContactsContract
import androidx.compose.ui.platform.LocalConfiguration
import android.os.Build
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.data.LiveKitConfig
import com.example.callruleblocker.data.SessionManager
import com.example.callruleblocker.data.AudioRecorder
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

private const val TAG = "ShynaDiscovery"
private const val COMM_PREFS = "smart_communication_v2"
private enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }
private enum class MessageStatus { SENDING, SENT, DELIVERED, READ }
private enum class MessageType { TEXT, LOCATION, FILE, VOICE, IMAGE, VIDEO, EVENT, POLL, CONTACT }
private enum class ConnectionStatus { NONE, PENDING, ACCEPTED, BLOCKED, IGNORED }

private data class Connection(
    val id: String = "",
    val user1: String = "",
    val user2: String = "",
    val status: ConnectionStatus = ConnectionStatus.NONE,
    val initiator: String = "",
    val blockedBy: String? = null,
    val firstMessage: String? = null,
    val attemptCount: Int = 0,
    val temporaryBlockedUntil: Long = 0,
    val lastResendAt: Long = 0,
    val resendCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long = 0,
    val ignoredAt: Long = 0,
    val blockedAt: Long = 0
)
private data class LocalChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chatId: String = "",
    val text: String,
    val mine: Boolean,
    val time: Long,
    val peerName: String = "", 
    val type: MessageType = MessageType.TEXT,
    val metadata: String? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val sentAt: Long = 0,
    val deliveredAt: Long = 0,
    val readAt: Long = 0,
    val eventId: String? = null,
    val pollId: String? = null,
    val senderId: String = "",
    val receiverId: String = "",
    val isRead: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap()
)

// --- ADVANCED PREMIUM THEME ENGINE ---
private enum class ThemeMode { LIGHT, DARK, SYSTEM }

private data class ShynaColors(
    val PrimaryBg: Color,
    val SurfaceBg: Color,
    val HeaderBg: Color,
    val IncomingBubble: Color,
    val OutgoingBubble: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val BrandGreen: Color,
    val TickRead: Color,
    val AccentBlue: Color,
    val DividerColor: Color,
    val SelectionOverlay: Color,
    val isDark: Boolean
)

private val ShynaDarkPalette = ShynaColors(
    PrimaryBg = Color(0xFF0B141B),
    SurfaceBg = Color(0xFF121B22),
    HeaderBg = Color(0xFF202C33).copy(alpha = 0.95f),
    IncomingBubble = Color(0xFF202C33),
    OutgoingBubble = Color(0xFF005C4B),
    TextPrimary = Color(0xFFE9EDEF),
    TextSecondary = Color(0xFF8696A0),
    BrandGreen = Color(0xFF00A884),
    TickRead = Color(0xFF53BDEB),
    AccentBlue = Color(0xFF2979FF),
    DividerColor = Color(0xFF222D34),
    SelectionOverlay = Color(0xFF00A884).copy(alpha = 0.15f),
    isDark = true
)

private val ShynaLightPalette = ShynaColors(
    PrimaryBg = Color(0xFFF0F2F5),
    SurfaceBg = Color(0xFFFFFFFF),
    HeaderBg = Color(0xFFFFFFFF).copy(alpha = 0.98f),
    IncomingBubble = Color(0xFFFFFFFF),
    OutgoingBubble = Color(0xFFE7FFDB),
    TextPrimary = Color(0xFF111B21),
    TextSecondary = Color(0xFF667781),
    BrandGreen = Color(0xFF008069),
    TickRead = Color(0xFF53BDEB),
    AccentBlue = Color(0xFF2979FF),
    DividerColor = Color(0xFFE9EDEF),
    SelectionOverlay = Color(0xFF008069).copy(alpha = 0.08f),
    isDark = false
)

private val LocalShynaColors = staticCompositionLocalOf { ShynaDarkPalette }

@Composable
private fun ShynaTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colors = if (darkTheme) ShynaDarkPalette else ShynaLightPalette
    CompositionLocalProvider(LocalShynaColors provides colors) {
        content()
    }
}





// Shortcut object for easy access
private object ShynaDesign {
    val colors: ShynaColors @Composable get() = LocalShynaColors.current
}


private data class RealUser(
    val uid: String, 
    val name: String, 
    val firstName: String = "",
    val lastName: String = "",
    val email: String, 
    val phone: String = "", 
    val normalizedPhone: String = "",
    val normalizedEmail: String = "",
    val isOnline: Boolean = false,
    val customUid: String = "",
    val photoUrl: String? = null,
    val dob: Long? = null,
    val age: Int? = null,
    val pincode: String = "",
    val district: String = "",
    val state: String = "",
    val country: String = "India"
)

private fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) { DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick) }

private data class ChatRowItem(
    val id: String, 
    val name: String, 
    val lastMessage: LocalChatMessage?, 
    val isOnline: Boolean, 
    val matchSearch: Boolean, 
    val subtitle: String = "", 
    val photoUrl: String? = null,
    val unreadCount: Int = 0
)

private fun getIconForType(type: MessageType?): ImageVector = when(type) {
    MessageType.LOCATION -> Icons.Outlined.LocationOn
    MessageType.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
    MessageType.VOICE -> Icons.Outlined.Mic
    MessageType.IMAGE -> Icons.Outlined.Image
    MessageType.VIDEO -> Icons.Outlined.Videocam
    MessageType.EVENT -> Icons.Outlined.Event
    MessageType.POLL -> Icons.Outlined.Poll
    MessageType.CONTACT -> Icons.Outlined.Person
    else -> Icons.AutoMirrored.Outlined.Chat
}

private fun formatDate(time: Long?): String {
    if (time == null || time == 0L) return ""
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(time))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    
    // THEME STATE
    var themeMode by remember { 
        mutableStateOf(try { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.LIGHT.name)!!) } catch(e: Exception) { ThemeMode.LIGHT }) 
    }

    ShynaTheme(mode = themeMode) {
        SmartCommunicationContent(
            initialOnline = initialOnline,
            onBack = onBack,
            themeMode = themeMode,
            onThemeChange = { 
                themeMode = it
                prefs.edit().putString("theme_mode", it.name).apply()
            }
        )
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartCommunicationContent(
    initialOnline: Boolean, 
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var firebaseUid by remember { mutableStateOf(auth.currentUser?.uid) }
    var isForceSetup by remember { mutableStateOf(false) }

    // Check if profile setup is required for the current user
    LaunchedEffect(firebaseUid) {
        if (firebaseUid != null) {
            db.collection("users").document(firebaseUid!!).get().addOnSuccessListener { doc ->
                if (!doc.exists() || doc.getString("customUid").isNullOrBlank()) {
                    isForceSetup = true
                } else {
                    isForceSetup = false
                }
            }
        }
    }
    
    // ENSURE PROFILE SYNC (Final Foolproof implementation)
    LaunchedEffect(firebaseUid) {
        if (firebaseUid != null) {
            val user = auth.currentUser
            if (user != null) {
                Log.d("ShynaDiscovery", "PROFILE_SYNC_START (Screen)")
                val email = user.email ?: ""
                val sessionId = SessionManager.getLocalSessionId(context)
                                ?: SessionManager.startNewSession(context)
                val deviceId = SessionManager.getDeviceId(context)

                val syncData = mutableMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to email,
                    "normalizedEmail" to email.trim().lowercase(),
                    "displayName" to (user.displayName ?: ""),
                    "name" to (user.displayName ?: email.substringBefore("@")),
                    "phone" to (user.phoneNumber ?: ""),
                    "isOnline" to true,
                    "activeSessionId" to sessionId,
                    "deviceId" to deviceId,
                    "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                // Only add photoUrl if it's not empty in Auth
                val authPhoto = user.photoUrl?.toString()
                if (!authPhoto.isNullOrBlank()) {
                    syncData["photoUrl"] = authPhoto
                }
                
                db.collection("users").document(user.uid)
                    .set(syncData, SetOptions.merge())
                    .addOnSuccessListener { Log.d("ShynaDiscovery", "PROFILE_SYNC_SUCCESS uid=${user.uid} (Screen)") }
                    .addOnFailureListener { Log.e("ShynaDiscovery", "PROFILE_SYNC_FAILED (Screen)", it) }
            }
        }
    }

    // FETCH ALL REGISTERED USERS (Directory Mode for Discovery)
    var allRealUsers by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(false) }

    DisposableEffect(firebaseUid) {
        val currentUid = firebaseUid
        var userListener: com.google.firebase.firestore.ListenerRegistration? = null
        
        if (currentUid != null) {
            isLoadingUsers = true
            userListener = db.collection("users")
                .limit(500) 
                .addSnapshotListener { snapshots, error ->
                    isLoadingUsers = false
                    if (error != null) {
                        Log.e("ShynaDiscovery", "Firestore error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val users = snapshots?.documents?.mapNotNull { doc ->
                        val email = doc.getString("email") ?: ""
                        RealUser(
                            uid = doc.id, 
                            name = doc.getString("name") ?: doc.getString("displayName") ?: email.substringBefore("@"), 
                            firstName = doc.getString("firstName") ?: "",
                            lastName = doc.getString("lastName") ?: "",
                            email = email, 
                            phone = doc.getString("phone") ?: "", 
                            normalizedPhone = doc.getString("normalizedPhone") ?: "", 
                            normalizedEmail = doc.getString("normalizedEmail") ?: email.trim().lowercase(), 
                            isOnline = doc.getBoolean("isOnline") ?: false, 
                            customUid = doc.getString("customUid") ?: "", 
                            photoUrl = doc.getString("photoUrl"),
                            dob = doc.getLong("dob"),
                            age = doc.getLong("age")?.toInt(),
                            pincode = doc.getString("pincode") ?: "",
                            district = doc.getString("district") ?: "",
                            state = doc.getString("state") ?: "",
                            country = doc.getString("country") ?: "India"
                        )
                    } ?: emptyList()
                    allRealUsers = users
                }
        }
        
        onDispose {
            if (currentUid != null) {
                db.collection("users").document(currentUid).update("isOnline", false, "lastSeen", com.google.firebase.Timestamp.now())
            }
            userListener?.remove()
        }
    }
    
    DisposableEffect(auth) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            firebaseUid = it.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    var selectedTab by remember { mutableStateOf(if (initialOnline) LinkTab.CHATS else LinkTab.CALLS) }
    var menuOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var serverOpen by remember { mutableStateOf(false) }
    var accountDialogOpen by remember { mutableStateOf(false) }

    var showContactPicker by remember { mutableStateOf(false) }

    var isNetworkAvailable by remember { mutableStateOf(hasInternet(context)) }
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    var locationTargetPeer by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedPeer == null) { onBack() }

    // FETCH CONNECTIONS
    val connections = remember { mutableStateListOf<Connection>() }
    DisposableEffect(firebaseUid) {
        if (firebaseUid != null) {
            val listener = db.collection("connections")
                .whereArrayContains("users", firebaseUid!!)
                .addSnapshotListener { snapshots, _ ->
                    val list = snapshots?.documents?.mapNotNull { doc ->
                        val users = doc.get("users") as? List<String> ?: return@mapNotNull null
                        Connection(
                            id = doc.id,
                            user1 = users.getOrNull(0) ?: "",
                            user2 = users.getOrNull(1) ?: "",
                            status = try { ConnectionStatus.valueOf(doc.getString("status") ?: "NONE") } catch (e: Exception) { ConnectionStatus.NONE },
                            initiator = doc.getString("initiator") ?: "",
                            blockedBy = doc.getString("blockedBy"),
                            firstMessage = doc.getString("firstMessage"),
                            attemptCount = doc.getLong("attemptCount")?.toInt() ?: 0,
                            temporaryBlockedUntil = doc.getLong("temporaryBlockedUntil") ?: 0L,
                            lastResendAt = doc.getLong("lastResendAt") ?: 0L,
                            resendCount = doc.getLong("resendCount")?.toInt() ?: 0,
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            acceptedAt = doc.getLong("acceptedAt") ?: 0L,
                            ignoredAt = doc.getLong("ignoredAt") ?: 0L,
                            blockedAt = doc.getLong("blockedAt") ?: 0L
                        )
                    } ?: emptyList()
                    connections.clear()
                    connections.addAll(list)
                }
            onDispose { listener.remove() }
        } else {
            onDispose {}
        }
    }

    fun getConnectionWith(peerId: String): Connection? {
        return connections.find { it.user1 == peerId || it.user2 == peerId }
    }
    
    val currentUserId = firebaseUid ?: ""

    // GLOBAL "DELIVERED" UPDATER: Automatically marks incoming messages as Delivered when app is open
    DisposableEffect(firebaseUid) {
        val currentUid = firebaseUid
        var deliveredListener: com.google.firebase.firestore.ListenerRegistration? = null
        if (currentUid != null) {
            deliveredListener = db.collectionGroup("messages")
                .whereEqualTo("receiverId", currentUid)
                .whereEqualTo("status", MessageStatus.SENT.name)
                .addSnapshotListener { snapshots, _ ->
                    snapshots?.documents?.forEach { doc ->
                        doc.reference.update("status", MessageStatus.DELIVERED.name, "deliveredAt", com.google.firebase.Timestamp.now())
                    }
                }
        }
        onDispose { deliveredListener?.remove() }
    }
    
    // NEW: FETCH ACTIVE CHATS FROM FIRESTORE (Robust OR Query)
    val allMessages = remember { mutableStateListOf<LocalChatMessage>() }
    DisposableEffect(firebaseUid) {
        val currentUid = firebaseUid
        var chatsListener: com.google.firebase.firestore.ListenerRegistration? = null
        if (currentUid != null) {
            chatsListener = db.collection("chats").addSnapshotListener { snapshots, _ ->
                val docs = snapshots?.documents ?: emptyList()
                val chats = docs.mapNotNull { doc ->
                    val u1 = doc.getString("user1") ?: ""
                    val u2 = doc.getString("user2") ?: ""
                    if (u1 != currentUid && u2 != currentUid) return@mapNotNull null
                    
                    val peer = if (u1 == currentUid) u2 else u1
                    val unreadCount = doc.getLong("unreadCount_$currentUid")?.toInt() ?: 0
                    LocalChatMessage(
                        id = doc.id,
                        text = doc.getString("lastMessage") ?: "",
                        mine = false,
                        time = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                        peerName = peer,
                        type = try { MessageType.valueOf(doc.getString("type") ?: "TEXT") } catch (e: Exception) { MessageType.TEXT },
                        metadata = unreadCount.toString()
                    )
                }.sortedByDescending { it.time }
                
                allMessages.clear()
                allMessages.addAll(chats)
            }
        }
        onDispose { chatsListener?.remove() }
    }

    var fullScreenMedia by remember { mutableStateOf<LocalChatMessage?>(null) }
    var messageToInfo by remember { mutableStateOf<LocalChatMessage?>(null) }
    var showBlockedListDialog by remember { mutableStateOf(false) }

    if (showBlockedListDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedListDialog = false },
            containerColor = ShynaDesign.colors.SurfaceBg,
            title = { Text("Blocked Users", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                val blockedConnections = connections.filter { it.status == ConnectionStatus.BLOCKED && it.blockedBy == currentUserId }
                if (blockedConnections.isEmpty()) {
                    Text("No blocked users.", color = ShynaDesign.colors.TextSecondary)
                } else {
                    LazyColumn {
                        items(blockedConnections) { conn ->
                            val otherId = if (conn.user1 == currentUserId) conn.user2 else conn.user1
                            val user = allRealUsers.find { it.uid == otherId }
                            ListItem(
                                headlineContent = { Text(user?.name ?: "Unknown", color = ShynaDesign.colors.TextPrimary) },
                                supportingContent = { Text(user?.email ?: "", color = ShynaDesign.colors.TextSecondary) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        db.collection("connections").document(conn.id).delete()
                                    }) { Text("UNBLOCK", color = ShynaDesign.colors.BrandGreen) }
                                },
                                colors = ListItemDefaults.colors(containerColor = ShynaDesign.colors.HeaderBg)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlockedListDialog = false }) { Text("Close", color = ShynaDesign.colors.BrandGreen) } }
        )
    }

    val currentScreen = when {
        selectedPeer != null -> "chat"
        locationTargetPeer != null -> "location"
        fullScreenMedia != null -> "media"
        firebaseUid == null || isForceSetup -> "auth"
        messageToInfo != null -> "info"
        else -> "main"
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
        },
        label = "ScreenTransition"
    ) { screen ->
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                "chat" -> SmartChatDetailScreen(
                    peerId = selectedPeer!!, 
                    userId = currentUserId, 
                    allRealUsers = allRealUsers,
                    connection = getConnectionWith(selectedPeer!!),
                    onBack = { selectedPeer = null },
                    onOpenMedia = { fullScreenMedia = it },
                    onLocationClick = { locationTargetPeer = it },
                    onMessageInfo = { messageToInfo = it }
                )
                "location" -> SendLocationScreen(
                    onBack = { locationTargetPeer = null },
                    onSendLocation = { loc ->
                        val peerId = locationTargetPeer!!
                        val chatId = if (currentUserId < peerId) "${currentUserId}_${peerId}" else "${peerId}_${currentUserId}"
                        val message = hashMapOf(
                            "senderId" to currentUserId,
                            "receiverId" to peerId,
                            "text" to "📍 Shared Location",
                            "type" to MessageType.LOCATION.name,
                            "metadata" to loc,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        val chatUpdate = hashMapOf(
                            "user1" to if (currentUserId < peerId) currentUserId else peerId,
                            "user2" to if (currentUserId < peerId) peerId else currentUserId,
                            "lastMessage" to "📍 Location",
                            "type" to MessageType.LOCATION.name,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
                        db.collection("chats").document(chatId).collection("messages").add(message)
                        
                        locationTargetPeer = null
                    }
                )
                "media" -> FullScreenMediaViewer(media = fullScreenMedia!!) { fullScreenMedia = null }
                "auth" -> ShynaAuthScreen(
                    onBack = onBack,
                    onLoginSuccess = { 
                        isForceSetup = false
                        firebaseUid = auth.currentUser?.uid 
                    }
                )
                "info" -> MessageInfoScreen(message = messageToInfo!!) { messageToInfo = null }
                else -> Scaffold(
                    containerColor = ShynaDesign.colors.PrimaryBg,
                    topBar = {
                        Column(
                            modifier = Modifier
                                .background(ShynaDesign.colors.HeaderBg)
                                .shadow(if (ShynaDesign.colors.isDark) 0.dp else 4.dp, spotColor = Color.Black.copy(0.1f))
                        ) {
                            TopAppBar(
                                title = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Shyna", 
                                            fontSize = 28.sp, 
                                            fontWeight = FontWeight.ExtraBold, 
                                            color = ShynaDesign.colors.BrandGreen,
                                            letterSpacing = (-0.5).sp
                                        )
                                        Text(
                                            " Guard", 
                                            fontSize = 28.sp, 
                                            fontWeight = FontWeight.Medium, 
                                            color = ShynaDesign.colors.TextPrimary,
                                            letterSpacing = (-0.5).sp
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { menuOpen = true }) { 
                                        Icon(
                                            Icons.Outlined.MoreVert, 
                                            null, 
                                            tint = ShynaDesign.colors.TextPrimary,
                                            modifier = Modifier.graphicsLayer { alpha = 0.8f }
                                        ) 
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen, 
                                        onDismissRequest = { menuOpen = false }, 
                                        modifier = Modifier.background(ShynaDesign.colors.SurfaceBg).shadow(8.dp, RoundedCornerShape(12.dp))
                                    ) {
                                        MenuItem("Refresh", Icons.Outlined.Refresh) { 
                                            isNetworkAvailable = hasInternet(context)
                                            db.collection("users").get(com.google.firebase.firestore.Source.SERVER)
                                            menuOpen = false 
                                        }
                                        MenuItem("Privacy Settings", Icons.Outlined.Security) { menuOpen = false }
                                        MenuItem("Blocked", Icons.Outlined.Block) { showBlockedListDialog = true; menuOpen = false }
                                        MenuItem("Account", Icons.Outlined.AccountCircle) { accountDialogOpen = true; menuOpen = false }
                                        MenuItem("Advanced", Icons.Outlined.Settings) { serverOpen = true; menuOpen = false }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )

                            // Network Status Indicator (Compact Modern)
                            AnimatedVisibility(visible = !isNetworkAvailable) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = Color(0xFFFDECEA),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Outlined.WifiOff, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Offline Mode", color = Color(0xFFD32F2F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            // Advanced Search Bar (Integrated)
                            Box(modifier = Modifier.padding(bottom = 12.dp)) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(48.dp), 
                                    shape = RoundedCornerShape(14.dp), 
                                    color = if (ShynaDesign.colors.isDark) ShynaDesign.colors.SurfaceBg else Color(0xFFF2F2F7),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                                        Icon(Icons.Outlined.Search, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        BasicTextField(
                                            value = search,
                                            onValueChange = { search = it },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 16.sp, color = ShynaDesign.colors.TextPrimary),
                                            cursorBrush = SolidColor(ShynaDesign.colors.BrandGreen),
                                            decorationBox = { innerTextField ->
                                                if (search.isEmpty()) Text("Search people & messages", color = ShynaDesign.colors.TextSecondary, fontSize = 15.sp)
                                                innerTextField()
                                            }
                                        )
                                        if (search.isNotEmpty()) {
                                            IconButton(onClick = { search = "" }, modifier = Modifier.size(24.dp)) { 
                                                Icon(Icons.Outlined.Close, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp)) 
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = { LinkBottomBar(selectedTab) { selectedTab = it } },
                    floatingActionButton = {
                        if (firebaseUid != null) {
                            FloatingActionButton(
                                onClick = { showContactPicker = true },
                                containerColor = ShynaDesign.colors.BrandGreen,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp)
                            ) { Icon(Icons.AutoMirrored.Outlined.Chat, null) }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg).padding(padding)) {
                        if (isLoadingUsers && allRealUsers.isEmpty()) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = ShynaDesign.colors.BrandGreen)
                        } else {
                            when (selectedTab) {
                                LinkTab.CHATS -> ChatsPage(
                                    messages = allMessages, 
                                    connections = connections,
                                    search = search,
                                    onOpenChat = { selectedPeer = it },
                                    allRealUsers = allRealUsers,
                                    onOpenMedia = { fullScreenMedia = it },
                                    currentUid = currentUserId,
                                    isLoading = isLoadingUsers
                                )
                                LinkTab.YOU -> YouPage(
                                    currentUser = allRealUsers.find { it.uid == firebaseUid } ?: RealUser(
                                        uid = firebaseUid ?: "", 
                                        name = auth.currentUser?.displayName ?: "Me", 
                                        email = auth.currentUser?.email ?: "",
                                        normalizedEmail = auth.currentUser?.email?.lowercase() ?: "",
                                        isOnline = true
                                    ),
                                    onLogout = { auth.signOut() }
                                )
                                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                                    Text("${selectedTab.name} - Coming Soon", color = ShynaDesign.colors.TextSecondary) 
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            containerColor = ShynaDesign.colors.SurfaceBg,
            title = { Text("Start new chat", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                if (allRealUsers.isEmpty()) {
                    Text("No Shyna users found.", color = ShynaDesign.colors.TextSecondary)
                } else {
                    LazyColumn {
                        items(allRealUsers) { user ->
                            ShynaContactRow(
                                name = user.name,
                                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                                preview = if (user.isOnline) "Active now" else "Offline",
                                icon = Icons.Outlined.Person,
                                date = "",
                                online = user.isOnline,
                                photoUrl = user.photoUrl,
                                unreadCount = 0,
                                onClick = {
                                    selectedPeer = user.uid
                                    showContactPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } }
        )
    }

    if (accountDialogOpen) {
        AlertDialog(
            onDismissRequest = { accountDialogOpen = false },
            containerColor = ShynaDesign.colors.SurfaceBg,
            title = { Text("Account Settings", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        Text("Logged in as: ${currentUser.email}", color = ShynaDesign.colors.BrandGreen, fontSize = 16.sp)
                        Text("UID: ${currentUser.uid}", color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
                        
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ShynaDesign.colors.DividerColor)
                        
                        Text("App Theme", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                val selected = themeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { onThemeChange(mode) },
                                    label = { Text(mode.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ShynaDesign.colors.BrandGreen,
                                        selectedLabelColor = Color.White,
                                        labelColor = ShynaDesign.colors.TextSecondary
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = { auth.signOut(); accountDialogOpen = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E36))
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Logout")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accountDialogOpen = false }) { Text("Close", color = ShynaDesign.colors.BrandGreen) } }
        )
    }
}





@Composable
private fun RequestCard(
    name: String,
    photoUrl: String?,
    message: String,
    onAccept: () -> Unit,
    onIgnore: () -> Unit,
    onBlock: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = ShynaDesign.colors.SurfaceBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape, 
                    modifier = Modifier.size(54.dp), 
                    color = ShynaDesign.colors.HeaderBg
                ) {
                    if (photoUrl != null) AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    else Icon(Icons.Outlined.Person, null, modifier = Modifier.padding(12.dp), tint = ShynaDesign.colors.TextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ShynaDesign.colors.TextPrimary)
                    Text("New Message Request", fontSize = 13.sp, color = ShynaDesign.colors.TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = ShynaDesign.colors.HeaderBg, 
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = ShynaDesign.colors.TextPrimary.copy(alpha = 0.9f),
                    lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("ACCEPT", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                
                OutlinedButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ShynaDesign.colors.TextSecondary.copy(0.4f))
                ) { Text("IGNORE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShynaDesign.colors.TextPrimary) }
                
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.height(46.dp)
                ) { Text("BLOCK", color = Color.Red.copy(0.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}





@Composable
private fun ShimmerItem() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        ShynaDesign.colors.HeaderBg.copy(alpha = 0.6f),
        ShynaDesign.colors.HeaderBg.copy(alpha = 0.2f),
        ShynaDesign.colors.HeaderBg.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp).background(brush))
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).background(brush))
        }
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsPage(
    messages: List<LocalChatMessage>, 
    connections: List<Connection>,
    search: String, 
    onOpenChat: (String) -> Unit,
    allRealUsers: List<RealUser> = emptyList(),
    onOpenMedia: (LocalChatMessage) -> Unit = {},
    currentUid: String = "",
    isLoading: Boolean = false
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }
    val pullToRefreshState = rememberPullToRefreshState()

    val displayList = remember(messages.size, search, allRealUsers, connections.size) {
        val rawQuery = search.trim()
        val query = rawQuery.lowercase()
        Log.d("ShynaDiscovery", "SEARCH_QUERY='$query'")
        
        val items = allRealUsers.mapNotNull { user ->
            val conn = connections.find { it.user1 == user.uid || it.user2 == user.uid }
            
            // BLOCK/IGNORE FILTERING
            if (conn?.status == ConnectionStatus.BLOCKED || conn?.status == ConnectionStatus.IGNORED) return@mapNotNull null

            val lastMsg = messages.find { it.peerName == user.uid }
            
            // ROBUST MATCHING
            val nameMatch = user.name.lowercase().contains(query)
            val emailMatch = user.email.lowercase().contains(query)
            val phoneMatch = user.phone.contains(query)
            val idMatch = user.customUid.lowercase().contains(query)
            val uidMatch = user.uid.lowercase() == query
            
            val match = query.isEmpty() || nameMatch || emailMatch || phoneMatch || idMatch || uidMatch

            ChatRowItem(
                id = user.uid,
                name = user.name,
                lastMessage = lastMsg,
                isOnline = if (conn?.status == ConnectionStatus.ACCEPTED) user.isOnline else false,
                matchSearch = match,
                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                photoUrl = user.photoUrl,
                unreadCount = lastMsg?.metadata?.toIntOrNull() ?: 0
            )
        }

        if (rawQuery.isEmpty()) {
            items.filter { it.lastMessage != null }.sortedByDescending { it.lastMessage?.time ?: 0L }
        } else {
            items.filter { it.matchSearch }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                db.collection("users").get(com.google.firebase.firestore.Source.SERVER)
                db.collection("connections").get(com.google.firebase.firestore.Source.SERVER)
                delay(1000)
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg)
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            // ADVANCED STORIES ROW (Online Users)
            val onlineUsers = allRealUsers.filter { it.uid != currentUid && it.isOnline }.take(10)
            if (onlineUsers.isNotEmpty() && search.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        ListHeader("Active Now")
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(onlineUsers) { user ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenChat(user.uid) }) {
                                    Box {
                                        Surface(
                                            shape = CircleShape, 
                                            modifier = Modifier.size(62.dp),
                                            color = ShynaDesign.colors.SurfaceBg,
                                            border = BorderStroke(2.5.dp, Brush.sweepGradient(listOf(ShynaDesign.colors.BrandGreen, ShynaDesign.colors.AccentBlue, ShynaDesign.colors.BrandGreen)))
                                        ) {
                                            if (user.photoUrl != null) AsyncImage(model = user.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                            else Icon(Icons.Outlined.Person, null, modifier = Modifier.padding(14.dp), tint = ShynaDesign.colors.TextSecondary)
                                        }
                                        Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).background(Color.White, CircleShape).padding(2.dp)) {
                                            Box(Modifier.fillMaxSize().background(ShynaDesign.colors.BrandGreen, CircleShape))
                                        }
                                    }
                                    Text(user.name.split(" ")[0], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading && displayList.isEmpty()) {
                items(6) { ShimmerItem() }
            }
            val query = search.trim()
            if (query.isEmpty()) {
                val myRequests = connections.filter { it.status == ConnectionStatus.PENDING && it.initiator != currentUid }
                
                if (myRequests.isNotEmpty()) {
                    item { ListHeader("New Communication Requests (${myRequests.size})") }
                    items(myRequests) { conn ->
                        val otherId = if (conn.user1 == currentUid) conn.user2 else conn.user1
                        val sender = allRealUsers.find { it.uid == otherId }
                        val db = remember { FirebaseFirestore.getInstance() }
                        RequestCard(
                            name = sender?.name ?: "Unknown User",
                            photoUrl = sender?.photoUrl,
                            message = conn.firstMessage ?: "Wants to connect",
                            onAccept = { 
                                db.collection("connections").document(conn.id).update("status", ConnectionStatus.ACCEPTED.name, "acceptedAt", System.currentTimeMillis())
                            },
                            onIgnore = {
                                val resends = conn.resendCount
                                val days = when(resends) {
                                    0 -> 1L
                                    1 -> 3L
                                    2 -> 7L
                                    3 -> 30L
                                    4 -> 60L
                                    5 -> 90L
                                    6 -> 180L
                                    else -> 365L
                                }
                                val cooldown = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000)
                                db.collection("connections").document(conn.id).update(
                                    "status", ConnectionStatus.IGNORED.name, 
                                    "ignoredAt", System.currentTimeMillis(),
                                    "temporaryBlockedUntil", cooldown
                                )
                            },
                            onBlock = {
                                db.collection("connections").document(conn.id).update("status", ConnectionStatus.BLOCKED.name, "blockedBy", currentUid, "blockedAt", System.currentTimeMillis())
                            }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                items(displayList) { item ->
                    ShynaContactRow(
                        name = item.name, 
                        subtitle = item.subtitle,
                        preview = item.lastMessage?.text ?: "No messages", 
                        icon = getIconForType(item.lastMessage?.type),
                        date = formatDate(item.lastMessage?.time),
                        online = item.isOnline,
                        photoUrl = item.photoUrl,
                        unreadCount = item.unreadCount,
                        onClick = { onOpenChat(item.id) }
                    )
                }
                if (displayList.isEmpty() && allRealUsers.isNotEmpty()) {
                    val filteredSuggestions = allRealUsers.filter { user ->
                        val conn = connections.find { it.user1 == user.uid || it.user2 == user.uid }
                        conn?.status != ConnectionStatus.BLOCKED && conn?.status != ConnectionStatus.IGNORED
                    }.take(15)

                    if (filteredSuggestions.isNotEmpty()) {
                        item { ListHeader("Suggested for you") }
                        items(filteredSuggestions) { user ->
                            ShynaContactRow(
                                name = user.name + (if(user.uid == currentUid) " (You)" else ""),
                                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                                preview = if (user.isOnline) "Active now" else "Start a new chat",
                                icon = Icons.Outlined.Person,
                                date = "",
                                online = false,
                                photoUrl = user.photoUrl,
                                unreadCount = 0,
                                onClick = { onOpenChat(user.uid) }
                            )
                        }
                    }
                } else if (allRealUsers.isEmpty() && !isLoading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = ShynaDesign.colors.TextSecondary)
                            Spacer(Modifier.height(12.dp))
                            Text("No Shyna users found on server.\nCheck your Firestore Rules.", color = ShynaDesign.colors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                if (displayList.isNotEmpty()) {
                    item { ListHeader("Search Results (${displayList.size})") }
                    items(displayList) { item ->
                        ShynaContactRow(
                            name = item.name, 
                            subtitle = item.subtitle,
                            preview = if (item.lastMessage != null) "Message history found" else "Tap to start chatting", 
                            icon = if (item.lastMessage == null) Icons.Outlined.PersonSearch else getIconForType(item.lastMessage?.type),
                            date = formatDate(item.lastMessage?.time),
                            online = item.isOnline,
                            photoUrl = item.photoUrl,
                            unreadCount = item.unreadCount,
                            onClick = { onOpenChat(item.id) }
                        )
                    }
                } else {
                    item {
                        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.SearchOff, null, Modifier.size(64.dp), tint = ShynaDesign.colors.TextSecondary)
                            Spacer(Modifier.height(16.dp))
                            Text("No users found matching '$query'", color = ShynaDesign.colors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}





@Composable
private fun ShynaContactRow(
    name: String, 
    subtitle: String = "", 
    preview: String, 
    icon: ImageVector, 
    date: String, 
    online: Boolean = false, 
    photoUrl: String? = null, 
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Gradient Ring for Online Status
                val ringBrush = if (online) {
                    Brush.sweepGradient(listOf(ShynaDesign.colors.BrandGreen, ShynaDesign.colors.AccentBlue, ShynaDesign.colors.BrandGreen))
                } else {
                    SolidColor(ShynaDesign.colors.DividerColor)
                }
                
                Surface(
                    shape = CircleShape, 
                    modifier = Modifier.size(60.dp),
                    color = ShynaDesign.colors.SurfaceBg,
                    border = BorderStroke(2.dp, ringBrush)
                ) {
                    Box(modifier = Modifier.padding(3.dp)) {
                        Surface(shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                            if (photoUrl != null) {
                                AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.background(ShynaDesign.colors.HeaderBg)) { 
                                    Icon(Icons.Outlined.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(30.dp)) 
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name, 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 17.sp, 
                            color = ShynaDesign.colors.TextPrimary, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle, 
                                fontSize = 12.sp, 
                                color = ShynaDesign.colors.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = date, 
                        color = if (unreadCount > 0) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary, 
                        fontSize = 12.sp, 
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != Icons.Outlined.Chat) {
                        Icon(icon, null, modifier = Modifier.size(15.dp).padding(end = 4.dp), tint = ShynaDesign.colors.TextSecondary)
                    }
                    Text(
                        text = preview, 
                        color = if (unreadCount > 0) ShynaDesign.colors.TextPrimary else ShynaDesign.colors.TextSecondary, 
                        fontSize = 14.5.sp, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis, 
                        modifier = Modifier.weight(1f),
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (unreadCount > 0) {
                        Surface(
                            color = ShynaDesign.colors.BrandGreen, 
                            shape = RoundedCornerShape(10.dp), 
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}





@Composable
private fun ListHeader(title: String) {
    Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 12.sp, fontWeight = FontWeight.Black, color = ShynaDesign.colors.BrandGreen, letterSpacing = 1.5.sp)
}

@Composable
private fun LinkBottomBar(selected: LinkTab, onSelect: (LinkTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ShynaDesign.colors.HeaderBg,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor.copy(alpha = 0.5f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent, 
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            LinkTabItem(LinkTab.CHATS, selected, "Chats", Icons.Outlined.Chat, onSelect)
            LinkTabItem(LinkTab.UPDATES, selected, "Updates", Icons.Outlined.DonutLarge, onSelect)
            LinkTabItem(LinkTab.COMMUNITIES, selected, "Groups", Icons.Outlined.Groups, onSelect)
            LinkTabItem(LinkTab.CALLS, selected, "Calls", Icons.Outlined.Call, onSelect)
            
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val youSelected = selected == LinkTab.YOU
            NavigationBarItem(
                selected = youSelected,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(LinkTab.YOU) 
                },
                icon = { 
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = if (youSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary.copy(0.1f),
                    ) {
                        Icon(
                            Icons.Outlined.Person, 
                            null, 
                            tint = if (youSelected) Color.White else ShynaDesign.colors.TextSecondary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                },
                label = { Text("You", fontWeight = if (youSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedTextColor = ShynaDesign.colors.BrandGreen,
                    unselectedTextColor = ShynaDesign.colors.TextSecondary
                )
            )
        }
    }
}





@Composable private fun RowScope.LinkTabItem(tab: LinkTab, selected: LinkTab, label: String, icon: ImageVector, onSelect: (LinkTab) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    NavigationBarItem(
        selected = tab == selected, 
        onClick = { 
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(tab) 
        }, 
        icon = { Icon(icon, label) }, 
        label = { Text(label, fontWeight = FontWeight.Medium) }, 
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = ShynaDesign.colors.BrandGreen, 
            selectedTextColor = ShynaDesign.colors.BrandGreen,
            indicatorColor = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f),
            unselectedIconColor = ShynaDesign.colors.TextSecondary,
            unselectedTextColor = ShynaDesign.colors.TextSecondary
        )
    )
}

// End of helper functions
@Composable
private fun ShynaAuthScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customUid by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    // NEW REGISTRATION FIELDS
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf<Long?>(null) }
    var pincode by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    val country = "India"
    
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Google Sign-In States
    val currentUser = remember { auth.currentUser }
    var isGoogleConfirmationMode by remember { mutableStateOf(currentUser != null) }
    var googleUserUid by remember { mutableStateOf(currentUser?.uid ?: "") }

    LaunchedEffect(currentUser) {
        if (currentUser != null && (firstName.isEmpty() || email.isEmpty())) {
            email = currentUser.email ?: ""
            val parts = (currentUser.displayName ?: "").split(" ", limit = 2)
            firstName = parts.getOrNull(0) ?: ""
            lastName = parts.getOrNull(1) ?: ""
        }
    }

    // REAL GOOGLE SIGN-IN SETUP
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("118812641303-0ulisr49hrhaj8tflf5kq078rjmjjgne.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            loading = true
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        googleUserUid = user.uid
                        email = user.email ?: ""
                        val parts = (user.displayName ?: "").split(" ", limit = 2)
                        firstName = parts.getOrNull(0) ?: ""
                        lastName = parts.getOrNull(1) ?: ""

                        // Check if user already exists with custom fields
                        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                            loading = false
                            if (doc.exists() && doc.contains("customUid") && doc.getString("customUid")?.isNotEmpty() == true) {
                                // User already fully set up
                                onLoginSuccess()
                            } else {
                                // Switch to confirmation mode to collect User ID and Phone
                                isGoogleConfirmationMode = true
                            }
                        }.addOnFailureListener {
                            loading = false
                            isGoogleConfirmationMode = true
                        }
                    }
                } else {
                    loading = false
                    Toast.makeText(context, "Google Sign-In failed: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            loading = false
            Log.e(TAG, "Google sign in failed", e)
            Toast.makeText(context, "Google sign in canceled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    // PINCODE LOOKUP LOGIC
    LaunchedEffect(pincode) {
        if (pincode.length == 6) {
            loading = true
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = java.net.URL("https://api.postalpincode.in/pincode/$pincode").readText()
                    if (response.contains("Success")) {
                        // Very basic parsing since I don't have a JSON library like Gson/Moshi handy
                        // We'll extract District and State using regex or simple splits
                        val districtMatch = Regex("\"District\":\"(.*?)\"").find(response)
                        val stateMatch = Regex("\"State\":\"(.*?)\"").find(response)
                        
                        districtMatch?.groupValues?.get(1)?.let { district = it }
                        stateMatch?.groupValues?.get(1)?.let { state = it }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pincode lookup failed", e)
            } finally {
                loading = false
            }
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dob = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Premium Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "auth_bg")
    val bgPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(Modifier.fillMaxSize()) {
        // Futuristic Layered Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F1E),
                            Color(0xFF1A1A2E).copy(alpha = bgPulse),
                            Color(0xFF16213E)
                        )
                    )
                )
        )
        
        val accentBlue = ShynaDesign.colors.AccentBlue
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentBlue.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(60.dp))

            // Shyna Calling Premium Animated Logo
            val logoAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(1200), label = "logo")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha }
            ) {
                Text(
                    text = "Shyna",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = "Calling",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = ShynaDesign.colors.BrandGreen,
                    letterSpacing = 4.sp,
                    modifier = Modifier.offset(y = (-8).dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            // Premium Glassmorphism Container with Crossfade for Mode Switching
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                shape = RoundedCornerShape(36.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(0.2f), Color.Transparent, Color.White.copy(0.1f))
                    )
                ),
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isGoogleConfirmationMode) {
                        Text(
                            text = "Complete Profile",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Confirm these details to finish linking your Google account",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )
                    } else {
                        Crossfade(targetState = isSignUp, label = "auth_mode") { signup ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (signup) "Create Account" else "Welcome Back",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (signup) "Start your premium calling journey" else "Secure sign in to Shyna Calling",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 6.dp, bottom = 32.dp)
                                )
                            }
                        }
                    }

                    if (isSignUp || isGoogleConfirmationMode) {
                        if (!isGoogleConfirmationMode) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuthTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = "First Name",
                                    icon = Icons.Outlined.Badge,
                                    modifier = Modifier.weight(1f)
                                )
                                AuthTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = "Last Name",
                                    icon = Icons.Outlined.Badge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(18.dp))

                            // Date of Birth Button
                            Surface(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.15f))
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.CalendarMonth, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    val dobText = remember(dob) {
                                        if (dob == null) "Date of Birth" 
                                        else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dob!!))
                                    }
                                    Text(
                                        text = dobText,
                                        color = if (dob == null) Color.White.copy(0.5f) else Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }

                        AuthTextField(
                            value = customUid,
                            onValueChange = { customUid = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                            label = "User ID (e.g. shyna_01)",
                            icon = Icons.Outlined.AccountCircle
                        )
                        Spacer(Modifier.height(18.dp))
                        AuthTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                            label = "Mobile Number",
                            icon = Icons.Outlined.Phone,
                            keyboardType = KeyboardType.Phone
                        )
                        Spacer(Modifier.height(18.dp))

                        if (!isGoogleConfirmationMode) {
                            AuthTextField(
                                value = pincode,
                                onValueChange = { if(it.length <= 6) pincode = it },
                                label = "Pincode",
                                icon = Icons.Outlined.LocationOn,
                                keyboardType = KeyboardType.Number
                            )
                            Spacer(Modifier.height(18.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuthTextField(
                                    value = district,
                                    onValueChange = { district = it },
                                    label = "District",
                                    icon = Icons.Outlined.HomeWork,
                                    modifier = Modifier.weight(1f)
                                )
                                AuthTextField(
                                    value = state,
                                    onValueChange = { state = it },
                                    label = "State",
                                    icon = Icons.Outlined.Map,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }

                    if (!isGoogleConfirmationMode) {
                        AuthTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address",
                            icon = Icons.Outlined.AlternateEmail,
                            keyboardType = KeyboardType.Email
                        )
                        Spacer(Modifier.height(18.dp))

                        AuthTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Secure Password",
                            icon = Icons.Outlined.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onVisibilityChange = { passwordVisible = !passwordVisible }
                        )

                        if (!isSignUp) {
                            TextButton(
                                onClick = { /* login link logic */ },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Fast Login Link", color = ShynaDesign.colors.BrandGreen.copy(0.8f), fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    if (loading) {
                        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen, strokeWidth = 3.dp)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isGoogleConfirmationMode) {
                                    val currentUid = auth.currentUser?.uid ?: googleUserUid
                                    if (currentUid.isBlank()) {
                                        Toast.makeText(context, "Error: No authenticated user", Toast.LENGTH_SHORT).show()
                                        isGoogleConfirmationMode = false
                                        return@Button
                                    }
                                    if (customUid.isBlank() || phone.isBlank()) {
                                        Toast.makeText(context, "User ID and Phone are mandatory", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    loading = true
                                    val updates = hashMapOf(
                                        "uid" to currentUid,
                                        "email" to email,
                                        "normalizedEmail" to email.trim().lowercase(),
                                        "firstName" to firstName.trim(),
                                        "lastName" to lastName.trim(),
                                        "name" to "$firstName $lastName".trim(),
                                        "displayName" to "$firstName $lastName".trim(),
                                        "customUid" to customUid.trim().lowercase(),
                                        "phone" to phone.trim(),
                                        "normalizedPhone" to phone.trim().replace(Regex("[^0-9+]"), ""),
                                        "updatedAt" to com.google.firebase.Timestamp.now(),
                                        "isOnline" to true
                                    )
                                    db.collection("users").document(currentUid)
                                        .set(updates, SetOptions.merge())
                                        .addOnSuccessListener {
                                            loading = false
                                            isGoogleConfirmationMode = false
                                            Toast.makeText(context, "Welcome to Shyna!", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        }
                                        .addOnFailureListener {
                                            loading = false
                                            Toast.makeText(context, "Setup failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    return@Button
                                }

                                if (email.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                loading = true
                                if (isSignUp) {
                                    if (firstName.isBlank() || lastName.isBlank() || dob == null || pincode.length < 6) {
                                        Toast.makeText(context, "Please complete all profile details", Toast.LENGTH_SHORT).show()
                                        loading = false
                                        return@Button
                                    }
                                    
                                    auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = auth.currentUser
                                            val uid = user?.uid ?: return@addOnCompleteListener
                                            val normEmail = email.trim().lowercase()
                                            val normPhone = phone.trim().replace(Regex("[^0-9+]"), "")
                                            
                                            val birthCal = Calendar.getInstance().apply { timeInMillis = dob!! }
                                            val birthYear = birthCal.get(Calendar.YEAR)
                                            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                            val calculatedAge = currentYear - birthYear

                                            val sessionId = SessionManager.getLocalSessionId(context) 
                                                            ?: SessionManager.startNewSession(context)
                                            val deviceId = SessionManager.getDeviceId(context)

                                            val userMap = hashMapOf(
                                                "uid" to uid, 
                                                "firstName" to firstName.trim(),
                                                "lastName" to lastName.trim(),
                                                "name" to "$firstName $lastName".trim(),
                                                "displayName" to "$firstName $lastName".trim(),
                                                "dob" to dob,
                                                "age" to calculatedAge,
                                                "pincode" to pincode.trim(),
                                                "district" to district.trim(),
                                                "state" to state.trim(),
                                                "country" to "India",
                                                "customUid" to customUid.trim().lowercase(), 
                                                "email" to email.trim(), 
                                                "normalizedEmail" to normEmail,
                                                "phone" to phone.trim(), 
                                                "normalizedPhone" to normPhone,
                                                "isOnline" to true, 
                                                "activeSessionId" to sessionId,
                                                "deviceId" to deviceId,
                                                "createdAt" to Timestamp.now(),
                                                "updatedAt" to Timestamp.now()
                                            )
                                            db.collection("users").document(uid).set(userMap, SetOptions.merge()).addOnSuccessListener { 
                                                Toast.makeText(context, "Welcome to Shyna Calling!", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess() 
                                            }
                                        } else {
                                            loading = false
                                            Toast.makeText(context, task.exception?.message ?: "Signup Failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = auth.currentUser
                                            val uid = user?.uid ?: return@addOnCompleteListener
                                            
                                            val sessionId = SessionManager.getLocalSessionId(context) 
                                                            ?: SessionManager.startNewSession(context)
                                            val deviceId = SessionManager.getDeviceId(context)

                                            val update = hashMapOf(
                                                "uid" to uid,
                                                "email" to email.trim(),
                                                "normalizedEmail" to email.trim().lowercase(),
                                                "isOnline" to true,
                                                "activeSessionId" to sessionId,
                                                "deviceId" to deviceId,
                                                "updatedAt" to Timestamp.now()
                                            )
                                            db.collection("users").document(uid).set(update, SetOptions.merge()).addOnCompleteListener { 
                                                Toast.makeText(context, "Welcome to Shyna!", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess() 
                                            }
                                        } else {
                                            loading = false
                                            Toast.makeText(context, task.exception?.message ?: "Login Failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .shadow(12.dp, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.25f)
                            )
                        ) {
                            val btnText = if (isGoogleConfirmationMode) "FINISH SETUP" 
                                          else if (isSignUp) "GET STARTED" else "LOG IN"
                            Text(
                                btnText, 
                                fontWeight = FontWeight.ExtraBold, 
                                color = Color.White, 
                                letterSpacing = 2.sp,
                                fontSize = 16.sp
                            )
                        }
                        
                        if (!isSignUp && !isGoogleConfirmationMode) {
                            TextButton(
                                onClick = {
                                    if (email.isNotBlank()) {
                                        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener {
                                            if (it.isSuccessful) {
                                                Toast.makeText(context, "Reset email sent!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Enter email first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Forgot Password?", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }

                    if (!isGoogleConfirmationMode) {
                        Spacer(Modifier.height(36.dp))

                        // Premium Social and Toggle Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    try {
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch Google Sign-In", e)
                                        Toast.makeText(context, "Google Play Services error", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.GTranslate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Google", color = Color.White, fontSize = 14.sp)
                                }
                            }

                            Button(
                                onClick = { 
                                    isSignUp = !isSignUp 
                                },
                                modifier = Modifier.weight(0.9f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f))
                            ) {
                                Text(if (isSignUp) "Login" else "Register", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { 
                                auth.signOut()
                                isGoogleConfirmationMode = false
                                googleUserUid = ""
                                // Force parent to re-evaluate firebaseUid
                                onLoginSuccess() 
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Switch Account / Logout", color = Color.White.copy(0.5f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            TextButton(onClick = {
                if (email.isBlank()) {
                    Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                } else {
                    auth.sendPasswordResetEmail(email.trim()).addOnSuccessListener {
                        Toast.makeText(context, "Reset link sent to your email", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Trouble signing in?", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
            
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 16.dp).background(Color.White.copy(0.05f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White.copy(0.6f))
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}





@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        placeholder = { Text(label, color = Color.White.copy(alpha = 0.35f), fontSize = 15.sp) },
        leadingIcon = { 
            Icon(
                icon, 
                null, 
                tint = if(isFocused) ShynaDesign.colors.BrandGreen else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            ) 
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onVisibilityChange) {
                    Icon(
                        if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ShynaDesign.colors.BrandGreen.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            cursorColor = ShynaDesign.colors.BrandGreen,
            focusedContainerColor = Color.White.copy(alpha = 0.03f),
            unfocusedContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun YouPage(currentUser: RealUser, onLogout: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val appVersion = remember { 
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("1.0.0")
    }

    var isEditing by remember { mutableStateOf(false) }
    var editFirstName by remember { mutableStateOf(currentUser.firstName) }
    var editLastName by remember { mutableStateOf(currentUser.lastName) }
    var editPhone by remember { mutableStateOf(currentUser.phone) }
    var editPincode by remember { mutableStateOf(currentUser.pincode) }
    var editDistrict by remember { mutableStateOf(currentUser.district) }
    var editState by remember { mutableStateOf(currentUser.state) }
    var editDob by remember { mutableStateOf(currentUser.dob) }
    var editCustomUid by remember { mutableStateOf(currentUser.customUid) }

    var showImagePicker by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            showImagePicker = true
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = editDob)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    editDob = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK", color = ShynaDesign.colors.BrandGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = ShynaDesign.colors.TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = ShynaDesign.colors.HeaderBg)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showImagePicker && selectedImageUri != null) {
        ProfileImageEditorDialog(
            imageUri = selectedImageUri!!,
            onDismiss = { showImagePicker = false },
            onConfirm = { croppedBitmap ->
                showImagePicker = false
                isUploading = true
                try {
                    val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
                    val out = java.io.FileOutputStream(file)
                    croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()

                    val ref = storage.reference.child("profile_pics/${currentUser.uid}.jpg")
                    ref.putFile(Uri.fromFile(file))
                        .addOnSuccessListener {
                            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                                // 1. Update Firestore
                                db.collection("users").document(currentUser.uid)
                                    .update("photoUrl", downloadUri.toString())
                                    .addOnSuccessListener {
                                        // 2. Update Firebase Auth Profile (for persistence and cross-app sync)
                                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                                            photoUri = downloadUri
                                        }
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                            isUploading = false
                                            Toast.makeText(context, "Profile picture updated everywhere!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isUploading = false
                            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                } catch (e: Exception) {
                    isUploading = false
                    Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ShynaDesign.colors.PrimaryBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Profile" else "Your Profile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = ShynaDesign.colors.TextPrimary
            )
            
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Cancel", color = ShynaDesign.colors.TextSecondary)
                    }
                    if (isSavingProfile) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = ShynaDesign.colors.BrandGreen)
                    } else {
                        Button(
                            onClick = {
                                isSavingProfile = true
                                val updates = hashMapOf(
                                    "firstName" to editFirstName.trim(),
                                    "lastName" to editLastName.trim(),
                                    "name" to "${editFirstName.trim()} ${editLastName.trim()}".trim(),
                                    "customUid" to editCustomUid.trim().lowercase(),
                                    "phone" to editPhone.trim(),
                                    "normalizedPhone" to editPhone.trim().replace(Regex("[^0-9+]"), ""),
                                    "pincode" to editPincode.trim(),
                                    "district" to editDistrict.trim(),
                                    "state" to editState.trim(),
                                    "dob" to editDob,
                                    "updatedAt" to Timestamp.now()
                                )
                                db.collection("users").document(currentUser.uid)
                                    .set(updates, SetOptions.merge())
                                    .addOnSuccessListener {
                                        isSavingProfile = false
                                        isEditing = false
                                        Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        isSavingProfile = false
                                        Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier.background(ShynaDesign.colors.BrandGreen.copy(0.15f), CircleShape)
                ) {
                    Icon(Icons.Outlined.Edit, "Edit", tint = ShynaDesign.colors.BrandGreen)
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = ShynaDesign.colors.SurfaceBg,
                modifier = Modifier
                    .size(150.dp)
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .shadow(12.dp, CircleShape),
                border = BorderStroke(4.dp, ShynaDesign.colors.HeaderBg)
            ) {
                if (currentUser.photoUrl != null) {
                    AsyncImage(
                        model = currentUser.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, null, modifier = Modifier.size(80.dp), tint = ShynaDesign.colors.TextSecondary)
                    }
                }
            }
            if (isUploading) {
                CircularProgressIndicator(Modifier.size(150.dp), color = ShynaDesign.colors.BrandGreen, strokeWidth = 5.dp)
            }
            Surface(
                shape = CircleShape,
                color = ShynaDesign.colors.BrandGreen,
                modifier = Modifier
                    .size(44.dp)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .shadow(6.dp, CircleShape),
                border = BorderStroke(2.dp, ShynaDesign.colors.PrimaryBg)
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(11.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        
        if (!isEditing) {
            Text(currentUser.name, fontSize = 28.sp, fontWeight = FontWeight.Black, color = ShynaDesign.colors.TextPrimary)
            Text(if (currentUser.customUid.isNotEmpty()) "@${currentUser.customUid}" else "No User ID set", color = ShynaDesign.colors.BrandGreen, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(44.dp))

        if (isEditing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditField(value = editFirstName, onValueChange = { editFirstName = it }, label = "First Name", Modifier.weight(1f))
                EditField(value = editLastName, onValueChange = { editLastName = it }, label = "Last Name", Modifier.weight(1f))
            }
            EditField(value = editCustomUid, onValueChange = { editCustomUid = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, label = "User ID (Handle)")
            EditField(value = editPhone, onValueChange = { editPhone = it }, label = "Mobile Number", keyboardType = KeyboardType.Phone)
            
            Surface(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(18.dp))
                    val formattedDob = remember(editDob) {
                        if (editDob == null) "Select Birth Date"
                        else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(editDob!!))
                    }
                    Text(text = formattedDob, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
                }
            }

            EditField(value = editPincode, onValueChange = { editPincode = it }, label = "Pincode", keyboardType = KeyboardType.Number)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditField(value = editDistrict, onValueChange = { editDistrict = it }, label = "District", Modifier.weight(1f))
                EditField(value = editState, onValueChange = { editState = it }, label = "State", Modifier.weight(1f))
            }
            ProfileInfoCard(label = "Email Address (Verified)", value = currentUser.email, icon = Icons.Outlined.AlternateEmail)
        } else {
            ProfileInfoCard(label = "User ID", value = currentUser.customUid.ifEmpty { "Not set" }, icon = Icons.Outlined.AccountCircle)
            ProfileInfoCard(label = "Date of Birth", value = currentUser.dob?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not set", icon = Icons.Outlined.CalendarMonth)
            ProfileInfoCard(label = "Location", value = listOfNotNull(currentUser.district.ifEmpty { null }, currentUser.state.ifEmpty { null }, "India").joinToString(", "), icon = Icons.Outlined.LocationOn)
            ProfileInfoCard(label = "Email Address", value = currentUser.email, icon = Icons.Outlined.AlternateEmail)
            ProfileInfoCard(label = "Mobile Number", value = currentUser.phone.ifEmpty { "Not linked" }, icon = Icons.Outlined.Phone)
            ProfileInfoCard(label = "App Version", value = "v$appVersion Premium", icon = Icons.Outlined.Verified)
        }

        Spacer(Modifier.height(56.dp))

        if (!isEditing) {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E36).copy(alpha = 0.9f)),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Text("LOGOUT FROM SHYNA", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}





@Composable
private fun EditField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ShynaDesign.colors.BrandGreen,
            unfocusedBorderColor = ShynaDesign.colors.DividerColor,
            focusedLabelColor = ShynaDesign.colors.BrandGreen,
            unfocusedLabelColor = ShynaDesign.colors.TextSecondary,
            focusedContainerColor = ShynaDesign.colors.SurfaceBg,
            unfocusedContainerColor = ShynaDesign.colors.SurfaceBg
        )
    )
}

@Composable
private fun ProfileInfoCard(label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = ShynaDesign.colors.SurfaceBg,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = ShynaDesign.colors.BrandGreen.copy(0.12f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(label, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ShynaDesign.colors.TextPrimary)
            }
        }
    }
}





@Composable
private fun ProfileImageEditorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    var rotation by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        scale *= zoomChange
        offset += offsetChange
        rotation += rotationChange
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        title = null,
        text = {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Edit Profile Photo", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(Color(0xFF111111))
                            .transformable(state = transformState),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale.coerceIn(0.5f, 5f),
                                    scaleY = scale.coerceIn(0.5f, 5f),
                                    rotationZ = rotation,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                        
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 2.dp.toPx()
                            drawCircle(
                                Color.White.copy(0.5f), 
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(48.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { rotation -= 90f },
                            modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)
                        ) { Icon(Icons.AutoMirrored.Outlined.RotateLeft, "Rotate Left", tint = Color.White) }
                        
                        IconButton(
                            onClick = { 
                                scale = 1f
                                offset = Offset.Zero
                                rotation = 0f
                            },
                            modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)
                        ) { Icon(Icons.Outlined.RestartAlt, "Reset", tint = Color.White) }
                        
                        IconButton(
                            onClick = { rotation += 90f },
                            modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)
                        ) { Icon(Icons.AutoMirrored.Outlined.RotateRight, "Rotate Right", tint = Color.White) }
                    }
                    Text("Pinch to zoom • Two-finger rotate • Drag to move", color = Color.White.copy(0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
                
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White) }
                    Button(
                        onClick = {
                            val originalBitmap = try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
                                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        decoder.isMutableRequired = true
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                                }
                            } catch (e: Exception) { null }
                            
                            if (originalBitmap != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // 1. Apply transformations
                                        val matrix = android.graphics.Matrix()
                                        matrix.postRotate(rotation)
                                        matrix.postScale(scale, scale)
                                        val transformed = android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                                        
                                        // 2. Compress and save to cache
                                        val file = File(context.cacheDir, "dp_${auth.currentUser?.uid}.jpg")
                                        val out = java.io.FileOutputStream(file)
                                        transformed.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                        out.close()
                                        
                                        // 3. Upload to Storage
                                        val storage = FirebaseStorage.getInstance()
                                        val ref = storage.reference.child("profiles/${auth.currentUser?.uid}.jpg")
                                        ref.putFile(Uri.fromFile(file)).await()
                                        val downloadUrl = ref.downloadUrl.await().toString()
                                        
                                        // 4. Update Firestore
                                        val uid = auth.currentUser?.uid
                                        if (uid != null) {
                                            FirebaseFirestore.getInstance().collection("users").document(uid)
                                                .update("photoUrl", downloadUrl).await()
                                        }

                                        withContext(Dispatchers.Main) {
                                            onConfirm(transformed)
                                            onDismiss()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to update profile picture", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SET AS DP", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, 
    userId: String, 
    allRealUsers: List<RealUser>, 
    connection: Connection?,
    onBack: () -> Unit, 
    onOpenMedia: (LocalChatMessage) -> Unit,
    onLocationClick: (String) -> Unit = {},
    onMessageInfo: (LocalChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val peer = allRealUsers.find { it.uid == peerId }
    val peerName = peer?.name ?: "Shyna User"
    val chatId = remember(userId, peerId) { if (userId < peerId) "${userId}_${peerId}" else "${peerId}_${userId}" }
    
    var text by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<LocalChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val callManager = remember { com.example.callruleblocker.call.LiveKitCallManager(context) }

    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var replyMessage by remember { mutableStateOf<LocalChatMessage?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    val sheetState = rememberModalBottomSheetState()
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var messageMenuTarget by remember { mutableStateOf<LocalChatMessage?>(null) }
    var showDeleteDialogTarget by remember { mutableStateOf<LocalChatMessage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    var isEmojiPickerOpen by remember { mutableStateOf(false) }
    var reactionTargetMsgId by remember { mutableStateOf<String?>(null) }

    BackHandler { 
        if (reactionTargetMsgId != null) reactionTargetMsgId = null
        else if (isEmojiPickerOpen) isEmojiPickerOpen = false
        else if (isSelectionMode) selectedMessageIds = emptySet()
        else if (isSearchMode) isSearchMode = false
        else onBack() 
    }

    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // REAL-TIME FIRESTORE LISTENER
    DisposableEffect(chatId) {
        val listener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                val newMessages = snapshots?.documents?.mapNotNull { doc ->
                    val sId = doc.getString("senderId") ?: ""
                    val deletedFor = doc.get("deletedFor") as? List<String> ?: emptyList()
                    if (deletedFor.contains(userId)) return@mapNotNull null
                    LocalChatMessage(
                        id = doc.id,
                        chatId = chatId,
                        text = doc.getString("text") ?: "",
                        mine = sId == userId,
                        time = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                        peerName = if (sId == userId) peerId else userId,
                        type = try { MessageType.valueOf(doc.getString("type") ?: "TEXT") } catch (e: Exception) { MessageType.TEXT },
                        metadata = doc.getString("metadata"),
                        status = try { MessageStatus.valueOf(doc.getString("status") ?: "SENT") } catch (e: Exception) { MessageStatus.SENT },
                        senderId = sId,
                        receiverId = doc.getString("receiverId") ?: "",
                        isRead = doc.getBoolean("isRead") ?: false,
                        isDeletedForEveryone = doc.getBoolean("isDeletedForEveryone") ?: false,
                        deletedFor = deletedFor,
                        reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap()
                    )
                } ?: emptyList()
                chatMessages.clear()
                chatMessages.addAll(newMessages)
            }
        onDispose { listener.remove() }
    }

    val toggleReaction = { msgId: String, emoji: String ->
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(msgId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(msgRef)
            val currentReactions = (snapshot.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
            if (currentReactions[userId] == emoji) currentReactions.remove(userId)
            else currentReactions[userId] = emoji
            transaction.update(msgRef, "reactions", currentReactions)
            null
        }
    }

    val sendMessage = { msgText: String, type: MessageType, metadata: String? ->
        if (msgText.isNotBlank() || type != MessageType.TEXT) {
            val now = com.google.firebase.Timestamp.now()
            val message = hashMapOf(
                "senderId" to userId,
                "receiverId" to peerId,
                "text" to msgText,
                "type" to type.name,
                "metadata" to metadata,
                "timestamp" to now,
                "status" to MessageStatus.SENT.name,
                "isRead" to false
            )
            val chatUpdate = hashMapOf(
                "user1" to if (userId < peerId) userId else peerId,
                "user2" to if (userId < peerId) peerId else userId,
                "lastMessage" to msgText,
                "type" to type.name,
                "timestamp" to now,
                "unreadCount_$peerId" to FieldValue.increment(1)
            )
            db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
            db.collection("chats").document(chatId).collection("messages").add(message)
            text = ""
        }
    }

    // Voice Recording Logic
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = System.currentTimeMillis() - start
                delay(100)
            }
        }
    }

    val uploadAndSend = { uri: Uri, type: MessageType, label: String ->
        isUploadingMedia = true
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "file"}"
        val fileRef = storageRef.child("chat_media/$chatId/$fileName")
        
        scope.launch(Dispatchers.IO) {
            try {
                var finalUri = uri
                if (type == MessageType.IMAGE) {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                    if (bitmap != null) {
                        val file = File(context.cacheDir, "temp_comp_${System.currentTimeMillis()}.jpg")
                        val out = java.io.FileOutputStream(file)
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
                        out.close()
                        finalUri = Uri.fromFile(file)
                    }
                }

                withContext(Dispatchers.Main) {
                    fileRef.putFile(finalUri)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) task.exception?.let { throw it }
                            fileRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            sendMessage(label, type, downloadUri.toString())
                            isUploadingMedia = false
                        }
                        .addOnFailureListener { e ->
                            isUploadingMedia = false
                            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isUploadingMedia = false
                    Toast.makeText(context, "Processing failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            val type = if (mimeType?.startsWith("video") == true) MessageType.VIDEO else MessageType.IMAGE
            uploadAndSend(it, type, if (type == MessageType.VIDEO) "Video" else "Shared Image")
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { 
        it?.let { 
            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val out = java.io.FileOutputStream(file)
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            uploadAndSend(Uri.fromFile(file), MessageType.IMAGE, "Photo")
        } 
    }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = c.getString(nameIndex)
                        sendMessage(name, MessageType.CONTACT, null)
                    }
                }
            }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uploadAndSend(it, MessageType.FILE, "Document") } }
    val updateConnectionStatus = { newStatus: ConnectionStatus, blockedBy: String? ->
        val data = hashMapOf("status" to newStatus.name, "blockedBy" to blockedBy, "updatedAt" to FieldValue.serverTimestamp())
        db.collection("connections").document(chatId).set(data, SetOptions.merge())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ShynaDesign.colors.PrimaryBg,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                if (isSelectionMode) {
                    SelectionHeader(
                        selectedCount = selectedMessageIds.size,
                        onClose = { selectedMessageIds = emptySet() },
                        onReply = { 
                            replyMessage = chatMessages.find { it.id == selectedMessageIds.first() }
                            selectedMessageIds = emptySet()
                        },
                        onStar = { },
                        onDelete = { showDeleteDialogTarget = chatMessages.find { it.id == selectedMessageIds.first() } },
                        onForward = { },
                        onInfo = {
                            onMessageInfo(chatMessages.find { it.id == selectedMessageIds.first() }!!)
                            selectedMessageIds = emptySet()
                        },
                        canReply = selectedMessageIds.size == 1,
                        canInfo = selectedMessageIds.size == 1 && chatMessages.find { it.id == selectedMessageIds.first() }?.mine == true
                    )
                } else if (isSearchMode) {
                    ChatSearchHeader(
                        query = chatSearchQuery, 
                        onQueryChange = { chatSearchQuery = it }, 
                        onClose = { isSearchMode = false },
                        onNext = { },
                        onPrev = { }
                    )
                }
else {
                    ChatHeader(
                        peer = peer,
                        peerName = peerName,
                        status = if (peer?.isOnline == true) "online" else "last seen recently",
                        onBack = onBack,
                        onProfileClick = { },
                        onVideoCall = { 
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            com.example.callruleblocker.call.CallSignalingManager.startCall(
                                callerUid = userId, callerName = auth.currentUser?.displayName ?: "User", callerPhoto = auth.currentUser?.photoUrl?.toString(),
                                receiverUid = peerId, type = com.example.callruleblocker.call.AppCallType.VIDEO,
                                onCallCreated = { call ->
                                    scope.launch {
                                        callManager.notifyReceiver(call)
                                    }
                                    context.startActivity(Intent(context, com.example.callruleblocker.AppCallActivity::class.java).apply { putExtra("callId", call.id); putExtra("isIncoming", false) })
                                },
                                onError = { }
                            )
                        },
                        onVoiceCall = { 
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            com.example.callruleblocker.call.CallSignalingManager.startCall(
                                callerUid = userId, callerName = auth.currentUser?.displayName ?: "User", callerPhoto = auth.currentUser?.photoUrl?.toString(),
                                receiverUid = peerId, type = com.example.callruleblocker.call.AppCallType.VOICE,
                                onCallCreated = { call ->
                                    scope.launch {
                                        callManager.notifyReceiver(call)
                                    }
                                    context.startActivity(Intent(context, com.example.callruleblocker.AppCallActivity::class.java).apply { putExtra("callId", call.id); putExtra("isIncoming", false) })
                                },
                                onError = { }
                            )
                        },
                        onMenuClick = { menuOpen = true },
                        menuOpen = menuOpen,
                        onMenuDismiss = { menuOpen = false },
                        onSearchClick = { isSearchMode = true; menuOpen = false },
                        onBlockClick = { updateConnectionStatus(ConnectionStatus.BLOCKED, userId); menuOpen = false },
                        isBlocked = connection?.status == ConnectionStatus.BLOCKED && connection.blockedBy == userId,
                        onUnblockClick = { updateConnectionStatus(ConnectionStatus.NONE, null); menuOpen = false }
                    )
                }
            }
        },
        bottomBar = {
            Column {
                if (replyMessage != null) ReplyPreview(message = replyMessage!!, onCancel = { replyMessage = null })
                if (showAttachmentMenu) {
                    ModalBottomSheet(onDismissRequest = { showAttachmentMenu = false }, sheetState = sheetState) {
                        PremiumAttachmentHub(
                            onLocation = { 
                                try {
                                    fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { loc ->
                                            if (loc != null) {
                                                sendMessage("📍 Live Location", MessageType.LOCATION, "${loc.latitude},${loc.longitude}")
                                            } else {
                                                Toast.makeText(context, "Enhancing GPS accuracy...", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                } catch (e: SecurityException) {
                                    Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                                }
                                showAttachmentMenu = false 
                            },
                            onDocument = { docLauncher.launch("*/*"); showAttachmentMenu = false },
                            onContact = { contactLauncher.launch(null); showAttachmentMenu = false },
                            onGallery = { galleryLauncher.launch("image/* video/*"); showAttachmentMenu = false },
                            onPoll = { 
                                // Integrated Poll Logic
                                sendMessage("📊 New Poll", MessageType.POLL, "Poll Question?")
                                showAttachmentMenu = false 
                            },
                            onEvent = { 
                                // Integrated Event Logic
                                sendMessage("📅 New Event", MessageType.EVENT, "Event Title")
                                showAttachmentMenu = false 
                            },
                            onAudio = { docLauncher.launch("audio/*"); showAttachmentMenu = false }
                        )
                    }
                }
                ChatComposer(
                    text = text, onTextChange = { text = it }, isRecording = isRecording, recordingDuration = recordingDuration,
                    onAttachClick = { showAttachmentMenu = !showAttachmentMenu }, onCameraClick = { cameraLauncher.launch(null) },
                    onSendClick = { if (text.isNotBlank()) { sendMessage(text, MessageType.TEXT, null); replyMessage = null } },
                    onMicClick = {
                        if (isRecording) {
                            recorder.stop()
                            currentRecordingFile?.let { uploadAndSend(Uri.fromFile(it), MessageType.VOICE, "Voice Note") }
                            isRecording = false
                        } else {
                            val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                            currentRecordingFile = file; recorder.start(file); isRecording = true
                        }
                    },
                    placeholder = "Message",
                    onEmojiClick = { isEmojiPickerOpen = !isEmojiPickerOpen },
                    isEmojiPickerOpen = isEmojiPickerOpen
                )
                if (isEmojiPickerOpen) {
                    EmojiPicker(onEmojiSelected = { text += it })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), reverseLayout = true) {
                val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                val yesterday = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000))
                val grouped = chatMessages.groupBy { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it.time)) }
                grouped.forEach { (date, messages) ->
                    items(messages, key = { it.id }) { msg ->
                        ShynaMessageBubble(
                            msg = msg, 
                            isSelected = selectedMessageIds.contains(msg.id),
                            isSelectionMode = isSelectionMode,
                            onLocationClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:$it?q=$it"))) },
                            onMediaClick = { onOpenMedia(it) },
                            onLongClick = { reactionTargetMsgId = msg.id },
                            onClick = { 
                                if (isSelectionMode) {
                                    selectedMessageIds = if (selectedMessageIds.contains(msg.id)) selectedMessageIds - msg.id else selectedMessageIds + msg.id 
                                }
                            },
                            onReaction = { toggleReaction(msg.id, it) }
                        )
                    }
                    item { DateDivider(date = if (date == today) "Today" else if (date == yesterday) "Yesterday" else date) }
                }
            }
            
            if (reactionTargetMsgId != null) {
                ReactionPicker(
                    onEmojiSelected = { 
                        toggleReaction(reactionTargetMsgId!!, it)
                        reactionTargetMsgId = null
                    },
                    onDismiss = { reactionTargetMsgId = null }
                )
            }
            if (!isAtBottom) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(42.dp),
                    containerColor = ShynaDesign.colors.HeaderBg, contentColor = ShynaDesign.colors.BrandGreen, shape = CircleShape
                ) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
            }
        }
    }

    if (messageMenuTarget != null) {
        AlertDialog(
            onDismissRequest = { messageMenuTarget = null },
            confirmButton = { TextButton(onClick = { messageMenuTarget = null }) { Text("Close") } },
            title = { Text("Options") },
            text = {
                Column {
                    ListItem(headlineContent = { Text("Message Info") }, modifier = Modifier.clickable { onMessageInfo(messageMenuTarget!!); messageMenuTarget = null })
                    ListItem(headlineContent = { Text("Delete") }, modifier = Modifier.clickable { showDeleteDialogTarget = messageMenuTarget; messageMenuTarget = null })
                }
            }
        )
    }

    if (showDeleteDialogTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogTarget = null },
            title = { Text("Delete message?") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("chats").document(chatId).collection("messages").document(showDeleteDialogTarget!!.id).delete()
                    showDeleteDialogTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialogTarget = null }) { Text("Cancel") } }
        )
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text("Share Contact") },
            text = {
                LazyColumn {
                    items(allRealUsers) { u ->
                        ListItem(headlineContent = { Text(u.name) }, modifier = Modifier.clickable { sendMessage(u.name, MessageType.CONTACT, u.uid); showContactPicker = false })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Cancel") } }
        )
    }
}





@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShynaMessageBubble(
    msg: LocalChatMessage, 
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLocationClick: (String) -> Unit = {}, 
    onMediaClick: (LocalChatMessage) -> Unit = {},
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onReaction: (String) -> Unit = {}
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    
    // PREMIUM GRADIENT FOR OUTGOING
    val outgoingGradient = Brush.linearGradient(
        colors = listOf(ShynaDesign.colors.BrandGreen, Color(0xFF00C49F))
    )
    
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (msg.mine) 18.dp else 4.dp,
        bottomEnd = if (msg.mine) 4.dp else 18.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) ShynaDesign.colors.SelectionOverlay else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start) {
            Surface(
                color = if (msg.mine) Color.Transparent else ShynaDesign.colors.IncomingBubble,
                shape = shape,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .then(
                        if (msg.mine) Modifier.background(outgoingGradient, shape)
                        else Modifier
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.15f)),
                        onClick = { 
                            if (isSelectionMode) onClick()
                            else {
                                if (msg.isDeletedForEveryone) return@combinedClickable
                                when(msg.type) {
                                    MessageType.LOCATION -> onLocationClick(msg.metadata ?: "")
                                    MessageType.IMAGE, MessageType.VIDEO -> onMediaClick(msg)
                                    MessageType.CONTACT -> {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = ContactsContract.RawContacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.NAME, msg.text)
                                        }
                                        context.startActivity(intent)
                                    }
                                    else -> onClick()
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (msg.isDeletedForEveryone) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Outlined.Block, null, tint = ShynaDesign.colors.TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (msg.mine) "You deleted this message" else "This message was deleted",
                                fontSize = 14.sp,
                                color = ShynaDesign.colors.TextSecondary.copy(alpha = 0.6f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        when(msg.type) {
                            MessageType.LOCATION -> {
                                Column {
                                    Text("📍 Shared Location", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                msg.metadata?.let { coords ->
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords"))
                                                    context.startActivity(intent)
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = "https://maps.googleapis.com/maps/api/staticmap?center=${msg.metadata}&zoom=15&size=400x400&key=YOUR_MAPS_API_KEY",
                                            contentDescription = "Map Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.1f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.LocationOn, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    Text(msg.text, fontSize = 15.sp, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            MessageType.IMAGE -> {
                                Column {
                                    AsyncImage(
                                        model = msg.metadata,
                                        contentDescription = "Shared Image",
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).aspectRatio(if (msg.text == "landscape") 1.5f else 0.85f),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (msg.text != "landscape" && msg.text != "portrait") {
                                        Text(msg.text, fontSize = 15.sp, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                            MessageType.VIDEO -> {
                                Box(contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = msg.metadata, 
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).aspectRatio(1f),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(shape = CircleShape, color = Color.Black.copy(0.5f), modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFFFFFFFF), modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                            MessageType.VOICE -> {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        repeat(18) { i ->
                                            Box(Modifier.width(2.5.dp).height((8..28).random().dp).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(1.dp)))
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text("0:12", fontSize = 11.sp, color = Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
                                }
                            }
                            MessageType.FILE -> {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(0.1f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = Color.White)
                                    Spacer(Modifier.width(10.dp))
                                    Text(msg.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.Download, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                            MessageType.CONTACT -> {
                                Column(Modifier.background(Color.Black.copy(0.1f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = Color.White.copy(0.1f), modifier = Modifier.size(40.dp)) {
                                            Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.padding(8.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(msg.text, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
                                    TextButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.NAME, msg.text)
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("ADD TO CONTACTS", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            else -> {
                                Text(msg.text, fontSize = 16.sp, color = ShynaDesign.colors.TextPrimary, lineHeight = 22.sp)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val timeStr = remember(msg.time) {
                            if (msg.time == 0L) "" else SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.time)).lowercase()
                        }
                        Text(timeStr, fontSize = 10.5.sp, color = ShynaDesign.colors.TextSecondary.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                        if (msg.mine) {
                            Spacer(Modifier.width(6.dp))
                            val (tickIcon, tickColor) = when(msg.status) {
                                MessageStatus.SENDING -> Icons.Outlined.AccessTime to ShynaDesign.colors.TextSecondary.copy(0.5f)
                                MessageStatus.SENT -> Icons.Outlined.Done to ShynaDesign.colors.TextSecondary.copy(0.6f)
                                MessageStatus.DELIVERED -> Icons.Outlined.DoneAll to ShynaDesign.colors.TextSecondary.copy(0.6f)
                                MessageStatus.READ -> Icons.Outlined.DoneAll to ShynaDesign.colors.TickRead
                            }
                            Icon(tickIcon, null, modifier = Modifier.size(15.dp), tint = tickColor)
                        }
                    }
                }
            }
            
            // Reactions Display
            if (msg.reactions.isNotEmpty()) {
                val uniqueReactions = msg.reactions.values.distinct()
                Row(
                    modifier = Modifier
                        .offset(y = (-10).dp, x = if (msg.mine) 0.dp else 4.dp)
                        .background(ShynaDesign.colors.SurfaceBg, CircleShape)
                        .border(1.dp, ShynaDesign.colors.DividerColor, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    uniqueReactions.forEach { emoji ->
                        Text(
                            text = emoji, 
                            fontSize = 12.sp,
                            modifier = Modifier.clip(CircleShape).clickable { onReaction(emoji) }.padding(horizontal = 2.dp)
                        )
                    }
                    if (msg.reactions.size > 1) {
                        Text("${msg.reactions.size}", fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
}





@Composable
private fun PremiumAttachmentHub(
    onLocation: () -> Unit,
    onDocument: () -> Unit,
    onContact: () -> Unit,
    onGallery: () -> Unit,
    onPoll: () -> Unit,
    onEvent: () -> Unit,
    onAudio: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ShynaDesign.colors.SurfaceBg,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.padding(top = 8.dp, bottom = 48.dp, start = 24.dp, end = 24.dp), 
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text("Share Content", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ShynaDesign.colors.TextPrimary))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AttachmentItem("Gallery", Icons.Outlined.Collections, Color(0xFFE91E63), onGallery)
                AttachmentItem("Camera", Icons.Outlined.PhotoCamera, Color(0xFF9C27B0), onGallery) 
                AttachmentItem("Location", Icons.Outlined.LocationOn, Color(0xFF4CAF50), onLocation)
                AttachmentItem("Contact", Icons.Outlined.Person, Color(0xFF2196F3), onContact)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AttachmentItem("Document", Icons.Outlined.Description, Color(0xFF795548), onDocument)
                AttachmentItem("Poll", Icons.Outlined.Poll, Color(0xFFFF9800), onPoll)
                AttachmentItem("Event", Icons.Outlined.CalendarMonth, Color(0xFF607D8B), onEvent)
                AttachmentItem("Audio", Icons.Outlined.Headphones, Color(0xFF00BCD4), onAudio)
            }
        }
    }
}





@Composable
private fun AttachmentItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        modifier = Modifier
            .width(80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 40.dp),
                onClick = onClick
            )
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(64.dp),
            border = BorderStroke(1.5.dp, color.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Medium)
    }
}





@Composable
private fun FullScreenMediaViewer(media: LocalChatMessage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    BackHandler { onDismiss() }
    
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        if (media.type == MessageType.VIDEO) {
            // Video player placeholder logic (in a real app, use ExoPlayer)
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(model = media.metadata, contentDescription = null, modifier = Modifier.fillMaxWidth())
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(80.dp))
            }
        } else {
            AsyncImage(model = media.metadata, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        
        // Premium Controls (Top Bar)
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
            }
            
            Row {
                IconButton(
                    onClick = {
                        // Download Logic Placeholder
                        Toast.makeText(context, "Downloading to Gallery...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)
                ) { Icon(Icons.Outlined.Download, null, tint = Color.White) }
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        // Delete Logic Placeholder (would need Firestore ref)
                        Toast.makeText(context, "Deleting message...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)
                ) { Icon(Icons.Outlined.Delete, null, tint = Color.White) }
            }
        }
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable 
private fun MessageInfoScreen(message: LocalChatMessage, onBack: () -> Unit) {
    val sdf = remember { SimpleDateFormat("EEEE, hh:mm a", Locale.getDefault()) }
    BackHandler { onBack() }
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Message Info") }, 
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF005D4B), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            ) 
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF0F2F5))) {
            Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                ShynaMessageBubble(msg = message)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    InfoRow(
                        icon = Icons.Outlined.Done,
                        label = "Sent",
                        time = if (message.sentAt > 0) sdf.format(Date(message.sentAt)) else "Pending",
                        color = Color.Gray
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(0.5f))
                    InfoRow(
                        icon = Icons.Outlined.DoneAll,
                        label = "Delivered",
                        time = if (message.deliveredAt > 0) sdf.format(Date(message.deliveredAt)) else "Pending",
                        color = Color.Gray
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(0.5f))
                    InfoRow(
                        icon = Icons.Outlined.DoneAll,
                        label = "Read",
                        time = if (message.readAt > 0) sdf.format(Date(message.readAt)) else "Pending",
                        color = Color(0xFF53BDEB)
                    )
                }
            }
        }
    }
}





@Composable
private fun InfoRow(icon: ImageVector, label: String, time: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(time, fontSize = 14.sp, color = Color.Gray)
        }
    }
}





@Composable
private fun ChatHeader(
    peer: RealUser?,
    peerName: String,
    status: String,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onSearchClick: () -> Unit,
    onBlockClick: () -> Unit,
    isBlocked: Boolean,
    onUnblockClick: () -> Unit
) {
    Surface(
        color = ShynaDesign.colors.HeaderBg,
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shadowElevation = if (ShynaDesign.colors.isDark) 0.dp else 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(end = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onProfileClick)
                    .padding(vertical = 4.dp)
            ) {
                Box {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        color = ShynaDesign.colors.TextSecondary.copy(0.1f)
                    ) {
                        if (peer?.photoUrl != null) {
                            AsyncImage(model = peer.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Outlined.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(10.dp))
                        }
                    }
                    if (status == "online") {
                        Box(Modifier.size(12.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape).padding(1.5.dp)) {
                            Box(Modifier.fillMaxSize().background(ShynaDesign.colors.BrandGreen, CircleShape))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(peerName, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (status == "online") "Active now" else status, 
                        fontSize = 12.sp, 
                        color = if (status == "online") ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary, 
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(onClick = onVideoCall) { Icon(Icons.Outlined.Videocam, null, tint = ShynaDesign.colors.TextPrimary, modifier = Modifier.size(26.dp)) }
            IconButton(onClick = onVoiceCall) { Icon(Icons.Outlined.Call, null, tint = ShynaDesign.colors.TextPrimary, modifier = Modifier.size(22.dp)) }
            Box {
                IconButton(onClick = onMenuClick) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss, modifier = Modifier.background(ShynaDesign.colors.HeaderBg)) {
                    DropdownMenuItem(text = { Text("View Profile", color = ShynaDesign.colors.TextPrimary) }, onClick = onProfileClick)
                    DropdownMenuItem(text = { Text("Search in Chat", color = ShynaDesign.colors.TextPrimary) }, onClick = onSearchClick)
                    DropdownMenuItem(text = { Text("Clear Chat", color = ShynaDesign.colors.TextPrimary) }, onClick = { /* TODO */ })
                    if (isBlocked) {
                        DropdownMenuItem(text = { Text("Unblock User", color = ShynaDesign.colors.TextPrimary) }, onClick = onUnblockClick)
                    } else {
                        DropdownMenuItem(text = { Text("Block User", color = Color.Red.copy(0.8f)) }, onClick = onBlockClick)
                    }
                }
            }
        }
    }
}





@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onClose: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    canReply: Boolean,
    canInfo: Boolean
) {
    Surface(
        color = Color(0xFF202C33),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null, tint = Color.White) }
            Text("$selectedCount", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp))
            
            if (canReply) IconButton(onClick = onReply) { Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Color.White) }
            IconButton(onClick = onStar) { Icon(Icons.Outlined.Star, null, tint = Color.White) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = Color.White) }
            IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Outlined.Forward, null, tint = Color.White) }
            if (canInfo) IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, null, tint = Color.White) }
        }
    }
}





@Composable
private fun ChatSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Surface(
        color = Color(0xFF202C33),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White) }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) Text("Search...", color = Color.White.copy(0.5f), fontSize = 18.sp)
                    innerTextField()
                }
            )
            IconButton(onClick = onPrev) { Icon(Icons.Outlined.KeyboardArrowUp, null, tint = Color.White) }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Color.White) }
        }
    }
}





@Composable
private fun ReplyPreview(
    message: LocalChatMessage,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF202C33).copy(alpha = 0.5f)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color(0xFF00A884)))
            Column(Modifier.padding(8.dp).weight(1f)) {
                Text(if (message.mine) "You" else message.peerName, color = Color(0xFF00A884), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(message.text, color = Color.White.copy(0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(16.dp)) }
        }
    }
}





@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    isRecording: Boolean,
    recordingDuration: Long,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
    placeholder: String,
    onEmojiClick: () -> Unit,
    isEmojiPickerOpen: Boolean
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    Surface(
        color = ShynaDesign.colors.HeaderBg, 
        modifier = Modifier.navigationBarsPadding().imePadding(),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor.copy(alpha = 0.5f))
    ) {
        Column {
            if (isRecording) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    color = if (ShynaDesign.colors.isDark) ShynaDesign.colors.SurfaceBg else Color(0xFFF2F2F7),
                    border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor.copy(alpha = 0.3f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        IconButton(onClick = onEmojiClick) { 
                            Icon(
                                if (isEmojiPickerOpen) Icons.Outlined.Keyboard else Icons.Outlined.SentimentSatisfiedAlt, 
                                null, 
                                tint = ShynaDesign.colors.TextSecondary, 
                                modifier = Modifier.size(26.dp)
                            ) 
                        }
                        
                        if (isRecording) {
                            Row(
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                val minutes = (recordingDuration / 1000) / 60
                                val seconds = (recordingDuration / 1000) % 60
                                val timeText = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                                Text(
                                    text = timeText,
                                    color = ShynaDesign.colors.TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Text("Slide to cancel", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                            }
                        } else {
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp),
                                textStyle = TextStyle(fontSize = 17.sp, color = ShynaDesign.colors.TextPrimary),
                                cursorBrush = SolidColor(ShynaDesign.colors.BrandGreen),
                                decorationBox = { innerTextField ->
                                    if (text.isEmpty()) Text(placeholder, color = ShynaDesign.colors.TextSecondary, fontSize = 16.sp)
                                    innerTextField()
                                }
                            )
                            IconButton(onClick = onAttachClick) { 
                                Icon(Icons.Outlined.AttachFile, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -45f }) 
                            }
                            if (text.isEmpty()) {
                                IconButton(onClick = onCameraClick) { 
                                    Icon(Icons.Outlined.PhotoCamera, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(24.dp)) 
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (text.isNotBlank()) onSendClick() else onMicClick() 
                    },
                    containerColor = ShynaDesign.colors.BrandGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp),
                    elevation = FloatingActionButtonDefaults.elevation(2.dp)
                ) {
                    val icon = if (text.isNotBlank()) Icons.AutoMirrored.Outlined.Send else if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic
                    Icon(icon, null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(44.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(emojis.size) { index ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onEmojiSelected(emojis[index]) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emojis[index], fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun ReactionPicker(onEmojiSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val reactions = listOf("👍", "👎", "❤️", "🔥", "😂", "😮", "😢", "😡")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ShynaDesign.colors.HeaderBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reactions.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { onEmojiSelected(emoji); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateDivider(date: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = ShynaDesign.colors.HeaderBg.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                date.uppercase(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ShynaDesign.colors.TextSecondary,
                letterSpacing = 1.2.sp
            )
        }
    }
}
