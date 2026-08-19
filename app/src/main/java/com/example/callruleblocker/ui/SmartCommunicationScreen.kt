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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
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
import com.example.callruleblocker.data.AudioRecorder
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

private const val TAG = "ShynaDiscovery"
private const val COMM_PREFS = "smart_communication_v2"
private enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }
private enum class MessageStatus { SENDING, SENT, DELIVERED, READ }
private enum class MessageType { TEXT, LOCATION, FILE, VOICE, IMAGE, VIDEO, EVENT, POLL, CONTACT }
private enum class EventStatus { UPCOMING, ONGOING, COMPLETED, CANCELLED }
private enum class EventResponse { NONE, GOING, MAYBE, NOT_GOING }
private enum class PollStatus { OPEN, CLOSED }

private data class PollOption(val id: String, val text: String, var voteCount: Int = 0)
private data class LocalChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val mine: Boolean,
    val time: Long,
    val peerName: String = "", // Used as senderId in Firestore
    val type: MessageType = MessageType.TEXT,
    val metadata: String? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val sentAt: Long = time,
    val deliveredAt: Long = 0,
    val readAt: Long = 0,
    val eventId: String? = null,
    val pollId: String? = null,
    val senderId: String = ""
)

private val LinkBlue = Color(0xFF2979FF)
private val LinkGreen = Color(0xFF00C853)
private val LinkCyan = Color(0xFF00E5FF)
private val LinkBg = Color(0xFFFFFFFF) 
private val LinkSurface = Color(0xFFF7F8FA) 
private val LinkCard = Color(0xFFFFFFFF)
private val LinkMuted = Color(0xFF667781) 
private val LinkText = Color(0xFF111B21) 
private val LinkChipBg = Color(0xFFEFF2F5)
private val LinkChipSelected = Color(0xFFE7FCE3)
private val LinkChipSelectedText = Color(0xFF008069)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
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
                val syncData = mutableMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to email,
                    "normalizedEmail" to email.trim().lowercase(),
                    "displayName" to (user.displayName ?: ""),
                    "name" to (user.displayName ?: email.substringBefore("@")),
                    "phone" to (user.phoneNumber ?: ""),
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "isOnline" to true,
                    "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
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
        if (firebaseUid != null) {
            isLoadingUsers = true
            Log.d("ShynaDiscovery", "Fetching users from Firestore. Project: ${db.app.options.projectId}")
            val listener = db.collection("users")
                .limit(500) 
                .addSnapshotListener { snapshots, error ->
                    isLoadingUsers = false
                    if (error != null) {
                        Log.e("ShynaDiscovery", "Firestore error: ${error.message}", error)
                        Toast.makeText(context, "Search unavailable: ${error.code}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    val users = snapshots?.documents?.mapNotNull { doc ->
                        val uid = doc.id
                        val email = doc.getString("email") ?: ""
                        val name = doc.getString("name") ?: doc.getString("displayName") ?: email.substringBefore("@")
                        
                        RealUser(
                            uid = uid, 
                            name = name, 
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
                    
                    Log.d("ShynaDiscovery", "SEARCH_RESULTS=${users.size}")
                    Log.d("ShynaDiscovery", "Successfully loaded ${users.size} users from Firestore")
                    allRealUsers = users
                }
            
            onDispose {
                db.collection("users").document(firebaseUid!!).update("isOnline", false, "lastSeen", com.google.firebase.Timestamp.now())
                listener.remove()
            }
        } else {
            onDispose {}
        }
    }
    
    DisposableEffect(auth) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            firebaseUid = it.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val prefs = remember { context.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableStateOf(if (initialOnline) LinkTab.CHATS else LinkTab.CALLS) }
    var menuOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var serverOpen by remember { mutableStateOf(false) }
    var accountDialogOpen by remember { mutableStateOf(false) }
    var showLocalChatDialog by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", LiveKitConfig.TOKEN_SERVER_URL) ?: LiveKitConfig.TOKEN_SERVER_URL) }

    var showContactPicker by remember { mutableStateOf(false) }

    var internetReady by remember { mutableStateOf(hasInternet(context)) }
    var message by remember { mutableStateOf("") }
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    var locationTargetPeer by remember { mutableStateOf<String?>(null) }

    val currentUserId = firebaseUid ?: ""
    
    // NEW: FETCH ACTIVE CHATS FROM FIRESTORE (Robust OR Query)
    val allMessages = remember { mutableStateListOf<LocalChatMessage>() }
    DisposableEffect(firebaseUid) {
        if (firebaseUid != null) {
            val chatsRef = db.collection("chats")
            // Listen for chats where user is either user1 or user2
            val listener = chatsRef.addSnapshotListener { snapshots, _ ->
                val docs = snapshots?.documents ?: emptyList()
                val chats = docs.mapNotNull { doc ->
                    val u1 = doc.getString("user1") ?: ""
                    val u2 = doc.getString("user2") ?: ""
                    if (u1 != firebaseUid && u2 != firebaseUid) return@mapNotNull null
                    
                    val peer = if (u1 == firebaseUid) u2 else u1
                    LocalChatMessage(
                        id = doc.id,
                        text = doc.getString("lastMessage") ?: "",
                        mine = false,
                        time = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                        peerName = peer,
                        type = try { MessageType.valueOf(doc.getString("type") ?: "TEXT") } catch (e: Exception) { MessageType.TEXT }
                    )
                }.sortedByDescending { it.time }
                
                allMessages.clear()
                allMessages.addAll(chats)
            }
            onDispose { listener.remove() }
        } else {
            onDispose {}
        }
    }

    var fullScreenMedia by remember { mutableStateOf<LocalChatMessage?>(null) }
    var messageToInfo by remember { mutableStateOf<LocalChatMessage?>(null) }

    if (locationTargetPeer != null) {
        SendLocationScreen(
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
        return
    }
    
    if (selectedPeer != null) {
        SmartChatDetailScreen(
            peerId = selectedPeer!!, 
            prefs = prefs, 
            userId = currentUserId, 
            allMessages = allMessages, 
            allRealUsers = allRealUsers,
            onBack = { selectedPeer = null },
            onOpenMedia = { fullScreenMedia = it },
            onLocationClick = { locationTargetPeer = it }
        )
        return
    }

    if (fullScreenMedia != null) {
        FullScreenMediaViewer(media = fullScreenMedia!!) { fullScreenMedia = null }
        return
    }

    if (firebaseUid == null || isForceSetup) {
        ShynaAuthScreen(
            onBack = onBack,
            onLoginSuccess = { 
                isForceSetup = false
                firebaseUid = auth.currentUser?.uid 
            }
        )
        return
    }

    if (messageToInfo != null) {
        MessageInfoScreen(message = messageToInfo!!) { messageToInfo = null }
        return
    }

    Scaffold(
        containerColor = LinkBg,
        topBar = {
            Column(Modifier.background(LinkBg)) {
                TopAppBar(
                    title = { Text("Shyna Calling", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LinkText, modifier = Modifier.padding(start = 8.dp)) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, null, tint = LinkText) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Color.White)) {
                            MenuItem("Refresh", Icons.Outlined.Refresh) { 
                                internetReady = hasInternet(context)
                                // Explicit server refresh
                                db.collection("users").get(Source.SERVER).addOnSuccessListener {
                                    Log.d(TAG, "Manual refresh successful")
                                }
                                menuOpen = false 
                            }
                            MenuItem("Account", Icons.Outlined.AccountCircle) { accountDialogOpen = true; menuOpen = false }
                            MenuItem("Settings", Icons.Outlined.Settings) { serverOpen = true; selectedTab = LinkTab.CALLS; menuOpen = false }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LinkBg)
                )
                
                // Pill Search Bar
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp), shape = CircleShape, color = LinkSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Outlined.Search, null, tint = LinkMuted)
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = LinkText),
                            decorationBox = { innerTextField ->
                                if (search.isEmpty()) Text("Search Shyna users...", color = LinkMuted, fontSize = 16.sp)
                                innerTextField()
                            }
                        )
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, null, tint = LinkMuted) }
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
                    containerColor = Color(0xFF1D1B20),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.AutoMirrored.Outlined.Chat, null) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(LinkBg).padding(padding)) {
            if (isLoadingUsers && allRealUsers.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = LinkBlue)
            } else {
                when (selectedTab) {
                    LinkTab.CHATS -> ChatsPage(
                        messages = allMessages, 
                        message = message, 
                        onMessageChange = { message = it },
                        onSend = { /* handled in detail */ },
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
                        Text("${selectedTab.name} - Coming Soon", color = LinkMuted) 
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            containerColor = Color.White,
            title = { Text("Start new chat", fontWeight = FontWeight.Bold) },
            text = {
                if (allRealUsers.isEmpty()) {
                    Text("No Shyna users found.", color = LinkMuted)
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
                                onClick = {
                                    selectedPeer = user.uid
                                    showContactPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Cancel") } }
        )
    }

    if (accountDialogOpen) {
        AlertDialog(
            onDismissRequest = { accountDialogOpen = false },
            containerColor = Color.White,
            title = { Text("Account Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        Text("Logged in as: ${currentUser.email}", color = LinkChipSelectedText, fontSize = 16.sp)
                        Text("UID: ${currentUser.uid}", color = LinkMuted, fontSize = 11.sp)
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
            confirmButton = { TextButton(onClick = { accountDialogOpen = false }) { Text("Close", color = LinkBlue) } }
        )
    }
}

@Composable
private fun ChatsPage(
    messages: List<LocalChatMessage>, 
    message: String, 
    onMessageChange: (String) -> Unit, 
    onSend: () -> Unit, 
    search: String, 
    onOpenChat: (String) -> Unit,
    allRealUsers: List<RealUser> = emptyList(),
    onOpenMedia: (LocalChatMessage) -> Unit = {},
    currentUid: String = "",
    isLoading: Boolean = false
) {
    val displayList = remember(messages.size, search, allRealUsers) {
        val rawQuery = search.trim()
        val query = rawQuery.lowercase()
        Log.d("ShynaDiscovery", "SEARCH_QUERY='$query'")
        
        val items = allRealUsers.map { user ->
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
                isOnline = user.isOnline,
                matchSearch = match,
                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                photoUrl = user.photoUrl
            )
        }

        if (rawQuery.isEmpty()) {
            items.filter { it.lastMessage != null }.sortedByDescending { it.lastMessage?.time ?: 0L }
        } else {
            items.filter { it.matchSearch }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(LinkBg), contentPadding = PaddingValues(top = 8.dp)) {
        val query = search.trim()
        if (query.isEmpty()) {
            items(displayList) { item ->
                ShynaContactRow(
                    name = item.name, 
                    subtitle = item.subtitle,
                    preview = item.lastMessage?.text ?: "No messages", 
                    icon = getIconForType(item.lastMessage?.type),
                    date = formatDate(item.lastMessage?.time),
                    online = item.isOnline,
                    photoUrl = item.photoUrl,
                    onClick = { onOpenChat(item.id) }
                )
            }
            if (displayList.isEmpty() && allRealUsers.isNotEmpty()) {
                item { ListHeader("Suggested for you") }
                items(allRealUsers.take(15)) { user ->
                    ShynaContactRow(
                        name = user.name + (if(user.uid == currentUid) " (You)" else ""),
                        subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                        preview = if (user.isOnline) "Active now" else "Start a new chat",
                        icon = Icons.Outlined.Person,
                        date = "",
                        online = user.isOnline,
                        photoUrl = user.photoUrl,
                        onClick = { onOpenChat(user.uid) }
                    )
                }
            } else if (allRealUsers.isEmpty() && !isLoading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = LinkMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("No Shyna users found on server.\nCheck your Firestore Rules.", color = LinkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp)
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
                        onClick = { onOpenChat(item.id) }
                    )
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.SearchOff, null, Modifier.size(64.dp), tint = LinkMuted)
                        Spacer(Modifier.height(16.dp))
                        Text("No users found matching '$query'", color = LinkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShynaContactRow(name: String, subtitle: String = "", preview: String, icon: ImageVector, date: String, online: Boolean = false, photoUrl: String? = null, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Surface(shape = CircleShape, color = Color(0xFFE1E4E7), modifier = Modifier.size(56.dp)) {
                if (photoUrl != null) {
                    AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
                }
            }
            if (online) {
                Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape).padding(2.dp)) {
                    Box(Modifier.fillMaxSize().background(LinkGreen, CircleShape))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LinkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = LinkChipSelectedText)
                }
                Text(text = date, color = LinkMuted, fontSize = 12.sp)
            }
            Text(text = preview, color = LinkMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListHeader(title: String) {
    Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LinkChipSelectedText, letterSpacing = 1.sp)
}

@Composable
private fun LinkBottomBar(selected: LinkTab, onSelect: (LinkTab) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        LinkTabItem(LinkTab.CHATS, selected, "Chats", Icons.Outlined.Chat, onSelect)
        LinkTabItem(LinkTab.UPDATES, selected, "Updates", Icons.Outlined.DonutLarge, onSelect)
        LinkTabItem(LinkTab.COMMUNITIES, selected, "Groups", Icons.Outlined.Groups, onSelect)
        LinkTabItem(LinkTab.CALLS, selected, "Calls", Icons.Outlined.Call, onSelect)
        NavigationBarItem(
            selected = selected == LinkTab.YOU,
            onClick = { onSelect(LinkTab.YOU) },
            icon = { Icon(Icons.Outlined.Person, null) },
            label = { Text("You") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = LinkChipSelectedText, indicatorColor = LinkChipSelected)
        )
    }
}

@Composable private fun RowScope.LinkTabItem(tab: LinkTab, selected: LinkTab, label: String, icon: ImageVector, onSelect: (LinkTab) -> Unit) {
    NavigationBarItem(selected = tab == selected, onClick = { onSelect(tab) }, icon = { Icon(icon, label) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = LinkChipSelectedText, indicatorColor = LinkChipSelected))
}

private fun getIconForType(type: MessageType?): ImageVector = when(type) {
    MessageType.IMAGE -> Icons.Outlined.Image
    MessageType.VIDEO -> Icons.Outlined.Videocam
    else -> Icons.Outlined.Chat
}

private fun formatDate(time: Long?): String {
    if (time == null || time == 0L) return ""
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))
}

private fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) { DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick) }

private data class ChatRowItem(val id: String, val name: String, val lastMessage: LocalChatMessage?, val isOnline: Boolean, val matchSearch: Boolean, val subtitle: String = "", val photoUrl: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShynaAuthScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
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
        
        // Dynamic Glowing Orb
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LinkBlue.copy(alpha = 0.15f), Color.Transparent),
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
                    color = LinkCyan,
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
                                    Icon(Icons.Outlined.CalendarMonth, null, tint = LinkCyan, modifier = Modifier.size(22.dp))
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
                                Text("Fast Login Link", color = LinkCyan.copy(0.8f), fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    if (loading) {
                        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LinkCyan, strokeWidth = 3.dp)
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
                                            val update = hashMapOf(
                                                "uid" to uid,
                                                "email" to email.trim(),
                                                "normalizedEmail" to email.trim().lowercase(),
                                                "isOnline" to true,
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
                                colors = ButtonDefaults.buttonColors(containerColor = LinkCyan.copy(alpha = 0.15f))
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
                tint = if(isFocused) LinkCyan else Color.White.copy(alpha = 0.5f),
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
            focusedBorderColor = LinkCyan.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            cursorColor = LinkCyan,
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

    // Auto-fill logic for pincode in Edit Profile
    LaunchedEffect(editPincode) {
        if (editPincode.length == 6) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = java.net.URL("https://api.postalpincode.in/pincode/$editPincode").readText()
                    if (response.contains("Success")) {
                        val districtMatch = Regex("\"District\":\"(.*?)\"").find(response)
                        val stateMatch = Regex("\"State\":\"(.*?)\"").find(response)
                        districtMatch?.groupValues?.get(1)?.let { editDistrict = it }
                        stateMatch?.groupValues?.get(1)?.let { editState = it }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pincode lookup failed in YouPage", e)
            }
        }
    }

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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
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
                                db.collection("users").document(currentUser.uid)
                                    .update("photoUrl", downloadUri.toString())
                                    .addOnSuccessListener {
                                        isUploading = false
                                        Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isUploading = false
                            Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Profile" else "Your Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LinkText
            )
            
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                    if (isSavingProfile) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = LinkCyan)
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
                            colors = ButtonDefaults.buttonColors(containerColor = LinkCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier.background(LinkBlue.copy(0.1f), CircleShape)
                ) {
                    Icon(Icons.Outlined.Edit, "Edit", tint = LinkBlue)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = Color.LightGray,
                modifier = Modifier
                    .size(140.dp)
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .shadow(8.dp, CircleShape),
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White)
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
                        Icon(Icons.Outlined.Person, null, modifier = Modifier.size(70.dp), tint = Color.Gray)
                    }
                }
            }
            if (isUploading) {
                CircularProgressIndicator(Modifier.size(140.dp), color = LinkCyan, strokeWidth = 4.dp)
            }
            Surface(
                shape = CircleShape,
                color = LinkCyan,
                modifier = Modifier
                    .size(42.dp)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .shadow(4.dp, CircleShape)
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        
        if (isEditing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditField(value = editFirstName, onValueChange = { editFirstName = it }, label = "First Name", Modifier.weight(1f))
                EditField(value = editLastName, onValueChange = { editLastName = it }, label = "Last Name", Modifier.weight(1f))
            }
        } else {
            Text(currentUser.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = LinkText)
            Text(if (currentUser.customUid.isNotEmpty()) "@${currentUser.customUid}" else "No User ID set", color = LinkMuted, fontSize = 16.sp)
        }

        Spacer(Modifier.height(40.dp))

        if (isEditing) {
            EditField(value = editCustomUid, onValueChange = { editCustomUid = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, label = "User ID (Handle)")
            EditField(value = editPhone, onValueChange = { editPhone = it }, label = "Mobile Number", keyboardType = KeyboardType.Phone)
            
            // Editable DOB
            Surface(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F2F5))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = LinkBlue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    val formattedDob = remember(editDob) {
                        if (editDob == null) "Select Birth Date"
                        else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(editDob!!))
                    }
                    Text(
                        text = formattedDob,
                        color = LinkText,
                        fontSize = 16.sp
                    )
                }
            }

            EditField(value = editPincode, onValueChange = { editPincode = it }, label = "Pincode", keyboardType = KeyboardType.Number)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditField(value = editDistrict, onValueChange = { editDistrict = it }, label = "District", Modifier.weight(1f))
                EditField(value = editState, onValueChange = { editState = it }, label = "State", Modifier.weight(1f))
            }
            
            ProfileInfoCard(label = "Email Address (Fixed)", value = currentUser.email, icon = Icons.Outlined.AlternateEmail)
        } else {
            ProfileInfoCard(label = "User ID", value = currentUser.customUid.ifEmpty { "Not set" }, icon = Icons.Outlined.AccountCircle)
            ProfileInfoCard(label = "Date of Birth", value = currentUser.dob?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not set", icon = Icons.Outlined.CalendarMonth)
            ProfileInfoCard(label = "Location", value = listOfNotNull(currentUser.district.ifEmpty { null }, currentUser.state.ifEmpty { null }, "India").joinToString(", "), icon = Icons.Outlined.LocationOn)
            ProfileInfoCard(label = "Email Address", value = currentUser.email, icon = Icons.Outlined.AlternateEmail)
            ProfileInfoCard(label = "Mobile Number", value = currentUser.phone.ifEmpty { "Not linked" }, icon = Icons.Outlined.Phone)
            ProfileInfoCard(label = "App Version", value = "v$appVersion Premium", icon = Icons.Outlined.Verified)
        }

        Spacer(Modifier.height(48.dp))

        if (!isEditing) {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E36).copy(alpha = 0.9f)),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                Spacer(Modifier.width(12.dp))
                Text("LOGOUT FROM SHYNA", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LinkCyan,
            unfocusedBorderColor = Color.LightGray,
            focusedLabelColor = LinkCyan
        )
    )
}

@Composable
private fun ProfileInfoCard(label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F2F5))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = LinkBlue.copy(0.1f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = LinkBlue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(label, fontSize = 13.sp, color = LinkMuted, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LinkText)
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
                            val bitmap = try {
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
                            
                            if (bitmap != null) onConfirm(bitmap)
                            else Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LinkCyan),
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
    prefs: android.content.SharedPreferences, 
    userId: String, 
    allMessages: List<LocalChatMessage>, 
    allRealUsers: List<RealUser>, 
    onBack: () -> Unit, 
    onOpenMedia: (LocalChatMessage) -> Unit,
    onLocationClick: (String) -> Unit = {}
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

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }

    // REAL-TIME FIRESTORE LISTENER
    DisposableEffect(chatId) {
        val listener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Chat listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val newMessages = snapshots?.documents?.mapNotNull { doc ->
                    val sId = doc.getString("senderId") ?: ""
                    LocalChatMessage(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        mine = sId == userId,
                        time = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                        peerName = if (sId == userId) peerId else userId,
                        type = try { MessageType.valueOf(doc.getString("type") ?: "TEXT") } catch (e: Exception) { MessageType.TEXT },
                        metadata = doc.getString("metadata"),
                        senderId = sId
                    )
                } ?: emptyList()
                chatMessages.clear()
                chatMessages.addAll(newMessages)
                
                // Auto scroll to bottom
                if (newMessages.isNotEmpty()) {
                    scope.launch {
                        listState.animateScrollToItem(newMessages.size - 1)
                    }
                }
            }
        onDispose { listener.remove() }
    }

    val sendMessage = { msgText: String, type: MessageType, metadata: String? ->
        if (msgText.isNotBlank() || type != MessageType.TEXT) {
            val message = hashMapOf(
                "senderId" to userId,
                "receiverId" to peerId,
                "text" to msgText,
                "type" to type.name,
                "metadata" to metadata,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            val chatUpdate = hashMapOf(
                "user1" to if (userId < peerId) userId else peerId,
                "user2" to if (userId < peerId) peerId else userId,
                "lastMessage" to when(type) {
                    MessageType.TEXT -> msgText
                    MessageType.LOCATION -> "📍 Location"
                    MessageType.FILE -> "📄 Document"
                    MessageType.CONTACT -> "👤 Contact"
                    MessageType.IMAGE -> "📷 Image"
                    else -> "New message"
                },
                "type" to type.name,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
            db.collection("chats").document(chatId).collection("messages").add(message)
                .addOnSuccessListener { text = "" }
                .addOnFailureListener { Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show() }
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
        } else {
            recordingDuration = 0L
        }
    }

    val uploadAndSend = { uri: Uri, type: MessageType, label: String ->
        isUploadingMedia = true
        val storageRef = FirebaseStorage.getInstance().reference
        val fileRef = storageRef.child("chat_media/${chatId}/${System.currentTimeMillis()}")
        
        fileRef.putFile(uri).addOnSuccessListener {
            fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                sendMessage(label, type, downloadUri.toString())
                isUploadingMedia = false
            }
        }.addOnFailureListener {
            isUploadingMedia = false
            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSend(it, MessageType.FILE, it.lastPathSegment ?: "Document") }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSend(it, MessageType.IMAGE, "Shared an image") }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { 
            // Save bitmap to file first
            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val out = java.io.FileOutputStream(file)
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            out.close()
            uploadAndSend(Uri.fromFile(file), MessageType.IMAGE, "Photo from Camera")
        }
    }
    
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSend(it, MessageType.VOICE, "Voice Note") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, modifier = Modifier.size(38.dp), color = Color.White.copy(0.2f)) {
                            if (peer?.photoUrl != null) {
                                AsyncImage(model = peer.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.padding(8.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val displayName = if (peer?.customUid?.isNotEmpty() == true) "$peerName (@${peer.customUid})" else peerName
                            Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (peer?.isOnline == true) "online" else "last seen recently",
                                fontSize = 11.sp,
                                color = if (peer?.isOnline == true) Color(0xFFB3E5FC) else Color.White.copy(0.7f)
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
    IconButton(onClick = { /* Call */ }) { Icon(Icons.Outlined.Call, null) }
    IconButton(onClick = { /* More */ }) { Icon(Icons.Outlined.MoreVert, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF005D4B), 
                    titleContentColor = Color.White, 
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column {
                if (showAttachmentMenu) {
                    PremiumAttachmentHub(
                        onLocation = { 
                            onLocationClick(peerId)
                            showAttachmentMenu = false 
                        },
                        onDocument = { 
                            docLauncher.launch("*/*")
                            showAttachmentMenu = false 
                        },
                        onContact = { 
                            showContactPicker = true
                            showAttachmentMenu = false 
                        },
                        onGallery = { 
                            galleryLauncher.launch("image/*")
                            showAttachmentMenu = false 
                        },
                        onCamera = { 
                            cameraLauncher.launch(null)
                            showAttachmentMenu = false 
                        },
                        onAudio = { 
                            audioLauncher.launch("audio/*")
                            showAttachmentMenu = false 
                        }
                    )
                }
                
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 10.dp).imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFFF0F2F5)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                IconButton(onClick = { /* Emoji */ }) { Icon(Icons.Outlined.EmojiEmotions, null, tint = Color.Gray) }
                                BasicTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                    textStyle = TextStyle(fontSize = 16.sp),
                                    decorationBox = { innerTextField ->
                                        if (text.isEmpty()) Text("Message", color = Color.Gray)
                                        innerTextField()
                                    }
                                )
                                IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { 
                                    Icon(
                                        imageVector = if (showAttachmentMenu) Icons.Outlined.Close else Icons.Outlined.AttachFile, 
                                        null, 
                                        tint = Color.Gray,
                                        modifier = Modifier.graphicsLayer { rotationZ = if (showAttachmentMenu) 0f else -45f }
                                    ) 
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = { 
                                if (text.isNotBlank()) {
                                    sendMessage(text, MessageType.TEXT, null)
                                } else {
                                    if (isRecording) {
                                        recorder.stop()
                                        currentRecordingFile?.let { file ->
                                            uploadAndSend(Uri.fromFile(file), MessageType.VOICE, "Voice Note")
                                        }
                                        isRecording = false
                                        currentRecordingFile = null
                                    } else {
                                        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                                        currentRecordingFile = file
                                        recorder.start(file)
                                        isRecording = true
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            containerColor = if (isRecording) Color.Red else Color(0xFF00A884),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                imageVector = if (text.isBlank()) {
                                    if (isRecording) Icons.Filled.Pause else Icons.Outlined.Mic
                                } else Icons.AutoMirrored.Outlined.Send, 
                                null
                            )
                        }
                    }
                    if (isRecording) {
                        Text(
                            text = "Recording: ${recordingDuration / 1000}s",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 70.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize()
        ) {
            // High-end Premium Chat Background (Pattern-like)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFFEFE7DE))
                // Subtle pattern lines
                val stroke = 1.dp.toPx()
                for (i in 0..size.width.toInt() step 60) {
                    drawLine(Color.White.copy(0.15f), Offset(i.toFloat(), 0f), Offset(i.toFloat(), size.height), stroke)
                }
                for (i in 0..size.height.toInt() step 60) {
                    drawLine(Color.White.copy(0.15f), Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()), stroke)
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatMessages) { msg ->
                    ShynaMessageBubble(
                        msg = msg,
                        onLocationClick = { 
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$it?q=$it"))
                            context.startActivity(intent)
                        },
                        onMediaClick = { onOpenMedia(it) }
                    )
                }
            }
        }
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text("Select Contact to Share") },
            text = {
                LazyColumn {
                    items(allRealUsers) { u ->
                        if (u.uid != userId) {
                            ShynaContactRow(
                                name = u.name,
                                subtitle = u.email,
                                preview = "Share this contact",
                                icon = Icons.Outlined.AccountBox,
                                date = "",
                                online = u.isOnline,
                                photoUrl = u.photoUrl,
                                onClick = {
                                    sendMessage(u.name, MessageType.CONTACT, u.uid)
                                    showContactPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShynaMessageBubble(
    msg: LocalChatMessage, 
    onLocationClick: (String) -> Unit = {}, 
    onContactClick: (String) -> Unit = {},
    onMediaClick: (LocalChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val alignment = if (msg.mine) Alignment.End else Alignment.Start
    val color = if (msg.mine) Color(0xFFD9FDD3) else Color.White
    val shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (msg.mine) 12.dp else 0.dp,
        bottomEnd = if (msg.mine) 0.dp else 12.dp
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = color,
            shape = shape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp).clickable { 
                when(msg.type) {
                    MessageType.LOCATION -> onLocationClick(msg.metadata ?: "")
                    MessageType.IMAGE, MessageType.VIDEO -> onMediaClick(msg)
                    MessageType.CONTACT -> {
                        // Metadata contains UID, text contains Name
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.NAME, msg.text)
                        }
                        context.startActivity(intent)
                    }
                    else -> {}
                }
            }
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                when(msg.type) {
                    MessageType.LOCATION -> {
                        Column {
                            Text("📍 Shared Location", fontWeight = FontWeight.Bold, color = Color(0xFF007AFF), fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))) {
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
                            Text(msg.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    MessageType.IMAGE -> {
                        Column {
                            AsyncImage(
                                model = msg.metadata,
                                contentDescription = "Shared Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .aspectRatio(if (msg.text == "landscape") 1.5f else 0.75f),
                                contentScale = ContentScale.Crop
                            )
                            if (msg.text != "landscape" && msg.text != "portrait") {
                                Text(msg.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    MessageType.VIDEO -> {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = msg.metadata, // Assuming thumbnail or first frame
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).aspectRatio(1f),
                                contentScale = ContentScale.Crop
                            )
                            Surface(shape = CircleShape, color = Color.Black.copy(0.5f), modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    MessageType.VOICE -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            // Simple Waveform visualization (static lines for now)
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(15) { i ->
                                    Box(Modifier.width(2.dp).height((10..30).random().dp).background(if (msg.mine) LinkChipSelectedText else Color.Gray))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("0:12", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    MessageType.FILE -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(0.05f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Icon(Icons.Outlined.Description, null, tint = Color(0xFFE53935))
                            Spacer(Modifier.width(8.dp))
                            Text(msg.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Download, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                    MessageType.CONTACT -> {
                        Column(Modifier.background(Color.Black.copy(0.05f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Outlined.Person, null, tint = Color.Gray, modifier = Modifier.padding(8.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(msg.text, fontWeight = FontWeight.Bold, color = LinkText)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(0.1f))
                            Text("ADD TO CONTACTS", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
                        }
                    }
                    else -> {
                        Text(msg.text, fontSize = 16.sp, color = Color.Black)
                    }
                }
                
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeStr = remember(msg.time) {
                        if (msg.time == 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.time))
                    }
                    Text(
                        timeStr,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    if (msg.mine) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.DoneAll, null, modifier = Modifier.size(15.dp), tint = Color(0xFF53BDEB))
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
    onCamera: () -> Unit,
    onAudio: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                AttachmentItem("Document", Icons.Outlined.Description, Color(0xFF7F66FF), onDocument)
                AttachmentItem("Camera", Icons.Outlined.PhotoCamera, Color(0xFFFF4595), onCamera)
                AttachmentItem("Gallery", Icons.Outlined.Collections, Color(0xFFBB66FF), onGallery)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                AttachmentItem("Audio", Icons.Outlined.Headphones, Color(0xFFFF9830), onAudio)
                AttachmentItem("Location", Icons.Outlined.LocationOn, Color(0xFF06D755), onLocation)
                AttachmentItem("Contact", Icons.Outlined.Person, Color(0xFF009DE2), onContact)
            }
        }
    }
}

@Composable
private fun AttachmentItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(14.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun FullScreenMediaViewer(media: LocalChatMessage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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
@Composable private fun MessageInfoScreen(message: LocalChatMessage, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Message Info") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) { Text("Message: ${message.text}"); Text("Sent: ${formatDate(message.time)}") }
    }
}
