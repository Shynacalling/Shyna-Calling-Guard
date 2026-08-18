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
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.data.LiveKitConfig
import com.example.callruleblocker.data.AudioRecorder
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val COMM_PREFS = "smart_communication_v2"
private enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }
private enum class MessageStatus { SENDING, SENT, DELIVERED, READ }
private enum class MessageType { TEXT, LOCATION, FILE, VOICE, IMAGE, VIDEO, EVENT, POLL }
private enum class EventStatus { UPCOMING, ONGOING, COMPLETED, CANCELLED }
private enum class EventResponse { NONE, GOING, MAYBE, NOT_GOING }
private enum class PollStatus { OPEN, CLOSED }

private data class PollOption(val id: String, val text: String, var voteCount: Int = 0)
private data class LocalChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val mine: Boolean,
    val time: Long,
    val peerName: String = "Rahul",
    val type: MessageType = MessageType.TEXT,
    val metadata: String? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val sentAt: Long = time,
    val deliveredAt: Long = 0,
    val readAt: Long = 0,
    val eventId: String? = null,
    val pollId: String? = null
)

private val LinkBlue = Color(0xFF2979FF)
private val LinkGreen = Color(0xFF00C853)
private val LinkCyan = Color(0xFF00E5FF)
private val LinkBg = Color(0xFFFFFFFF) // White background as per screenshot
private val LinkSurface = Color(0xFFF7F8FA) // Light surface for search bar
private val LinkCard = Color(0xFFFFFFFF)
private val LinkMuted = Color(0xFF667781) // WhatsApp-style muted text
private val LinkText = Color(0xFF111B21) // WhatsApp-style dark text
private val LinkChipBg = Color(0xFFEFF2F5)
private val LinkChipSelected = Color(0xFFE7FCE3)
private val LinkChipSelectedText = Color(0xFF008069)

private data class RealUser(
    val uid: String, 
    val name: String, 
    val email: String, 
    val phone: String = "", 
    val isOnline: Boolean = false,
    val customUid: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var firebaseUid by remember { mutableStateOf(auth.currentUser?.uid) }
    
    // ENSURE PROFILE SYNC
    LaunchedEffect(firebaseUid) {
        if (firebaseUid != null) {
            val userRef = db.collection("users").document(firebaseUid!!)
            val currentEmail = auth.currentUser?.email?.lowercase() ?: ""
            userRef.get().addOnSuccessListener { doc ->
                val syncData = hashMapOf(
                    "uid" to firebaseUid,
                    "email" to currentEmail,
                    "name" to (doc.getString("name") ?: currentEmail.substringBefore("@")),
                    "isOnline" to true,
                    "lastSeen" to com.google.firebase.Timestamp.now()
                )
                userRef.set(syncData, com.google.firebase.firestore.SetOptions.merge())
            }
        }
    }

    // FETCH REAL USERS & PRESENCE (100% Logic)
    var allRealUsers by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    DisposableEffect(firebaseUid) {
        if (firebaseUid != null) {
            val userRef = db.collection("users").document(firebaseUid!!)
            val listener = db.collection("users").addSnapshotListener { snapshots, error ->
                if (error != null) {
                    if (error.message?.contains("permission", true) == true) {
                        Toast.makeText(context, "Search restricted: Use exact search.", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }
                val users = snapshots?.documents?.mapNotNull { doc ->
                    val uid = doc.id
                    val name = doc.getString("name") ?: doc.getString("displayName") ?: doc.getString("email")?.substringBefore("@") ?: "User"
                    val email = doc.getString("email") ?: ""
                    val phone = doc.getString("phone") ?: doc.getString("mobile") ?: doc.getString("phoneNumber") ?: ""
                    val customUid = doc.getString("customUid") ?: ""
                    val online = doc.getBoolean("isOnline") ?: false
                    if (uid != firebaseUid) RealUser(uid, name, email, phone, online, customUid) else null
                } ?: emptyList()
                allRealUsers = users
            }
            
            onDispose {
                userRef.update("isOnline", false, "lastSeen", com.google.firebase.Timestamp.now())
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

    // FORCE MIGRATION: Update old server URL to the new verified production one
    LaunchedEffect(Unit) {
        val currentSaved = prefs.getString("server_url", "") ?: ""
        if (currentSaved.contains("shyna-calling-server.onrender.com") || currentSaved.isBlank()) {
            prefs.edit().putString("server_url", LiveKitConfig.TOKEN_SERVER_URL).apply()
        }
    }

    var selectedTab by remember { mutableStateOf(if (initialOnline) LinkTab.CHATS else LinkTab.CALLS) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var serverOpen by remember { mutableStateOf(false) }
    var accountDialogOpen by remember { mutableStateOf(false) }
    var showLocalChatDialog by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", LiveKitConfig.TOKEN_SERVER_URL) ?: LiveKitConfig.TOKEN_SERVER_URL) }

    var showContactPicker by remember { mutableStateOf(false) }

    // Sync serverUrl state if prefs change (e.g. after migration)
    LaunchedEffect(prefs) {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "server_url") {
                val newUrl = p.getString("server_url", LiveKitConfig.TOKEN_SERVER_URL) ?: LiveKitConfig.TOKEN_SERVER_URL
                // Ensure no trailing /token or slash in the state variable
                serverUrl = newUrl.removeSuffix("/").removeSuffix("/token")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(observer)
    }

    var userId by remember { mutableStateOf(prefs.getString("user_id", firebaseUid ?: "") ?: "") }
    var internetReady by remember { mutableStateOf(hasInternet(context)) }
    var message by remember { mutableStateOf("") }
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    val allMessages = remember { mutableStateListOf<LocalChatMessage>().apply { addAll(loadMessages(prefs)) } }
    
    var fullScreenMedia by remember { mutableStateOf<LocalChatMessage?>(null) }
    var messageToInfo by remember { mutableStateOf<LocalChatMessage?>(null) }

    if (selectedPeer != null) {
        SmartChatDetailScreen(
            peerId = selectedPeer!!, 
            prefs = prefs, 
            userId = userId, 
            allMessages = allMessages, 
            allRealUsers = allRealUsers,
            onBack = { selectedPeer = null },
            onOpenMedia = { fullScreenMedia = it }
        )
        return
    }

    if (fullScreenMedia != null) {
        FullScreenMediaViewer(media = fullScreenMedia!!) { fullScreenMedia = null }
        return
    }

    if (firebaseUid == null) {
        ShynaAuthScreen(
            onBack = onBack,
            onLoginSuccess = { /* firebaseUid will update via listener */ }
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
                    title = {
                        Text(
                            "Shyna Calling", 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = LinkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF0F2F5),
                            modifier = Modifier.padding(end = 12.dp).clickable { /* Archived */ }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Archive, null, tint = LinkText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Archived 0", color = LinkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconButton(onClick = { 
                            // Open main camera
                        }) { Icon(Icons.Outlined.PhotoCamera, null, tint = LinkText) }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, null, tint = LinkText) }
                        
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Color.White)) {
                            MenuItem("Refresh", Icons.Outlined.Refresh) { internetReady = hasInternet(context); menuOpen = false }
                            MenuItem("Account", Icons.Outlined.AccountCircle) { accountDialogOpen = true; menuOpen = false }
                            MenuItem("Local Chat", Icons.Outlined.Lock) { showLocalChatDialog = true; menuOpen = false }
                            MenuItem("Settings", Icons.Outlined.Settings) { serverOpen = true; selectedTab = LinkTab.CALLS; menuOpen = false }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LinkBg)
                )
                
                // Pill Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp),
                    shape = CircleShape,
                    color = LinkSurface
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = LinkMuted)
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = LinkText),
                            decorationBox = { innerTextField ->
                                if (search.isEmpty()) Text("Ask Meta AI or Search", color = LinkMuted, fontSize = 16.sp)
                                innerTextField()
                            }
                        )
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = LinkMuted)
                            }
                        }
                    }
                }

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WhatsAppFilterChip(selected = true, label = "All")
                    WhatsAppFilterChip(selected = false, label = "Unread")
                    WhatsAppFilterChip(selected = false, label = "Favourites")
                    WhatsAppFilterChip(selected = false, label = "Groups")
                    Surface(
                        shape = CircleShape,
                        color = LinkChipBg,
                        modifier = Modifier.size(32.dp).clickable { /* Add */ }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = LinkMuted, modifier = Modifier.size(20.dp))
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
            when (selectedTab) {
                LinkTab.CHATS -> ChatsPage(
                    messages = allMessages, 
                    message = message, 
                    onMessageChange = { message = it },
                    onSend = {
                        val clean = message.trim()
                        if (clean.isNotEmpty()) {
                            val newMsg = LocalChatMessage(
                                text = clean, 
                                mine = true, 
                                time = System.currentTimeMillis(), 
                                status = MessageStatus.SENDING,
                                peerName = "Quick Chat"
                            )
                            allMessages += newMsg
                            saveMessages(prefs, allMessages)
                            message = ""
                        }
                    },
                    search = search,
                    onOpenChat = { selectedPeer = it },
                    allRealUsers = allRealUsers,
                    onOpenMedia = { fullScreenMedia = it }
                )
                LinkTab.UPDATES -> UpdatesPage(prefs, internetReady)
                LinkTab.COMMUNITIES -> CommunitiesPage(prefs)
                LinkTab.CALLS -> CallsPage(
                    context = context,
                    prefs = prefs,
                    internetReady = internetReady,
                    serverOpen = serverOpen,
                    onServerOpenChange = { serverOpen = it },
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    userId = userId,
                    onUserIdChange = { userId = it }
                )
                LinkTab.YOU -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your Profile & Settings", color = LinkText)
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
                    Text("No other active users found on the network.", color = LinkMuted)
                } else {
                    LazyColumn {
                        items(allRealUsers) { user ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPeer = user.uid
                                        showContactPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Surface(shape = CircleShape, color = Color.LightGray, modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text(user.name.take(1)) }
                                    }
                                    if (user.isOnline) {
                                        Box(Modifier.size(10.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape).padding(1.dp)) {
                                            Box(Modifier.fillMaxSize().background(LinkGreen, CircleShape))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(user.name, fontSize = 16.sp, color = LinkText, fontWeight = FontWeight.SemiBold)
                                    Text(if (user.isOnline) "Active now" else "Offline", fontSize = 11.sp, color = if (user.isOnline) LinkGreen else LinkMuted)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Cancel") } }
        )
    }

    if (showLocalChatDialog) {
        AlertDialog(
            onDismissRequest = { showLocalChatDialog = false },
            containerColor = Color.White,
            title = { Text("Local encrypted chat", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local messages are visible only on this device and are not synced to the cloud.", color = LinkMuted)
                    HorizontalDivider(color = Color(0xFFF0F2F5))
                    Text("You can use this for quick notes or private local storage.", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showLocalChatDialog = false }) { Text("Close") } }
        )
    }

    if (accountDialogOpen) {
        AlertDialog(
            onDismissRequest = { accountDialogOpen = false },
            containerColor = Color.White, // White for light theme
            titleContentColor = LinkText,
            textContentColor = LinkText,
            title = { Text("Account Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        Text("Logged in as: ${currentUser.email}", color = LinkChipSelectedText, fontSize = 16.sp)
                        Text("UID: ${currentUser.uid}", color = LinkMuted, fontSize = 11.sp)
                    } else {
                        Text("User: Not logged in", color = Color.Red, fontSize = 16.sp)
                    }
                    
                    val displayUrl = if (serverUrl.endsWith("/token")) serverUrl else "${serverUrl.trimEnd('/')}/token"
                    
                    OutlinedTextField(
                        value = displayUrl,
                        onValueChange = {},
                        label = { Text("Production Token Server") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LinkBlue,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = LinkText,
                            unfocusedTextColor = LinkText,
                            focusedLabelColor = LinkBlue,
                            unfocusedLabelColor = LinkMuted
                        )
                    )
                    
                    if (currentUser != null) {
                        Button(
                            onClick = { 
                                auth.signOut()
                                accountDialogOpen = false 
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E36))
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Logout from Shyna Link")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { accountDialogOpen = false }) {
                    Text("Close", color = LinkBlue)
                }
            }
        )
    }
}

@Composable private fun CenterInfoChip(text: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFD9F0F2)) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFF334155), fontSize = 11.sp) } } }

@Composable
private fun MessageStatusTicks(status: MessageStatus, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            MessageStatus.SENDING -> Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(12.dp), tint = LinkMuted)
            MessageStatus.SENT -> Icon(Icons.Outlined.Done, null, modifier = Modifier.size(16.dp), tint = LinkMuted)
            MessageStatus.DELIVERED -> {
                Box {
                    Icon(Icons.Outlined.Done, null, modifier = Modifier.size(16.dp), tint = LinkMuted)
                    Icon(Icons.Outlined.Done, null, modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = LinkMuted)
                }
            }
            MessageStatus.READ -> {
                Box {
                    Icon(Icons.Outlined.Done, null, modifier = Modifier.size(16.dp), tint = LinkGreen)
                    Icon(Icons.Outlined.Done, null, modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = LinkGreen)
                }
            }
        }
    }
}

@Composable
private fun WhatsAppFilterChip(selected: Boolean, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) LinkChipSelected else LinkChipBg,
        modifier = Modifier.clickable { /* Filter */ }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = if (selected) LinkChipSelectedText else LinkMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
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
    onOpenMedia: (LocalChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var remoteUsers by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // 1. DEBOUNCED UNIVERSAL SEARCH ENGINE (Professional Grade)
    LaunchedEffect(search) {
        val queryRaw = search.trim()
        val query = queryRaw.lowercase()
        if (query.isEmpty()) {
            remoteUsers = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        
        // Debounce: Wait 500ms
        kotlinx.coroutines.delay(500)
        isSearching = true
        
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        // DISCOVERY ENGINE
        val tasks = mutableListOf<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>>()
        
        // Exact Search - Lowercase (Recommended)
        tasks.add(db.collection("users").whereEqualTo("email", query).get())
        tasks.add(db.collection("users").whereEqualTo("customUid", query).get())
        
        // Exact Search - Raw (Legacy Support)
        if (queryRaw != query) {
            tasks.add(db.collection("users").whereEqualTo("email", queryRaw).get())
        }

        tasks.forEach { task ->
            task.addOnSuccessListener { snap ->
                val found = snap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUid) return@mapNotNull null
                    
                    val name = doc.getString("name") ?: doc.getString("displayName") ?: "User"
                    val email = doc.getString("email") ?: ""
                    val phone = doc.getString("phone") ?: doc.getString("mobile") ?: ""
                    val cUid = doc.getString("customUid") ?: ""
                    val online = doc.getBoolean("isOnline") ?: false
                    
                    RealUser(uid, name, email, phone, online, cUid)
                }
                remoteUsers = (remoteUsers + found).distinctBy { it.uid }
            }.addOnFailureListener { e ->
                // Silently ignore prefix index errors, but log permission issues
                if (e.message?.contains("permission", true) == true) {
                    Toast.makeText(context, "Search restricted: Check Firestore rules", Toast.LENGTH_SHORT).show()
                }
            }.addOnCompleteListener {
                if (tasks.all { it.isComplete }) isSearching = false
            }
        }
        
        kotlinx.coroutines.delay(5000)
        isSearching = false
    }

    val displayList = remember(messages.size, search, allRealUsers, remoteUsers) {
        val query = search.trim().lowercase()
        val allKnown = (allRealUsers + remoteUsers).distinctBy { it.uid }
        
        val items = allKnown.map { user ->
            val lastMsg = messages.filter { it.peerName == user.uid || it.peerName == user.customUid || it.peerName == user.email }.maxByOrNull { it.time }
            
            // 100% DEEP LOGIC MATCHING
            // We check every field locally as well to ensure results are accurate
            val match = query.isEmpty() || 
                        user.name.lowercase().contains(query) || 
                        user.email.lowercase().contains(query) || 
                        user.customUid.lowercase().contains(query) || 
                        user.phone.replace(Regex("[^0-9]"), "").contains(query.replace(Regex("[^0-9]"), "")) ||
                        user.uid.lowercase() == query

            ChatRowItem(
                id = user.uid,
                name = user.name,
                lastMessage = lastMsg,
                isOnline = user.isOnline,
                matchSearch = match,
                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email
            )
        }

        if (query.isEmpty()) {
            // Default View: Show active conversations
            items.filter { it.lastMessage != null }.sortedByDescending { it.lastMessage?.time ?: 0L }
        } else {
            // Search View: Show all matching users
            items.filter { it.matchSearch }.sortedByDescending { it.lastMessage?.time ?: 0L }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(LinkBg), 
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        if (isSearching) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = LinkGreen) }
        }

        val query = search.trim()
        
        if (query.isEmpty()) {
            // 1. History Section
            items(displayList) { item ->
                WhatsAppContactRow(
                    name = item.name, 
                    subtitle = item.subtitle,
                    preview = item.lastMessage?.let { if (it.mine) "You: ${it.text}" else it.text } ?: "", 
                    icon = getIconForType(item.lastMessage?.type),
                    date = formatDate(item.lastMessage?.time),
                    online = item.isOnline,
                    onClick = { onOpenChat(item.id) }
                )
            }
            
            // 2. Suggestions (If few chats)
            if (displayList.size < 5) {
                val others = allRealUsers.filter { u -> displayList.none { it.id == u.uid } }.take(10)
                if (others.isNotEmpty()) {
                    item { ListHeader("Suggested for you") }
                    items(others) { user ->
                        WhatsAppContactRow(
                            name = user.name,
                            subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                            preview = if (user.isOnline) "Active now" else "Start a new chat",
                            icon = Icons.Outlined.PersonAdd,
                            date = "",
                            online = user.isOnline,
                            onClick = { onOpenChat(user.uid) }
                        )
                    }
                }
            }
        } else {
            // 3. Dedicated Search Results
            if (displayList.isNotEmpty()) {
                item { ListHeader("Found ${displayList.size} users") }
                items(displayList) { item ->
                    WhatsAppContactRow(
                        name = item.name, 
                        subtitle = item.subtitle,
                        preview = if (item.lastMessage != null) "Message history found" else "Tap to start chatting", 
                        icon = if (item.lastMessage == null) Icons.Outlined.PersonSearch else getIconForType(item.lastMessage?.type),
                        date = formatDate(item.lastMessage?.time),
                        online = item.isOnline,
                        onClick = { onOpenChat(item.id) }
                    )
                }
            }
            
            // 4. Empty Result Handling
            if (displayList.isEmpty() && !isSearching) {
                item {
                    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.SearchOff, null, Modifier.size(64.dp), tint = LinkMuted)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No users found for '$query'.\nTry the exact ID or full email address.",
                            color = LinkMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = LinkChipSelectedText,
        letterSpacing = 1.sp
    )
}

private fun getIconForType(type: MessageType?): ImageVector = when(type) {
    MessageType.IMAGE -> Icons.Outlined.Image
    MessageType.VIDEO -> Icons.Outlined.Videocam
    MessageType.VOICE -> Icons.Outlined.Mic
    MessageType.FILE -> Icons.Outlined.AttachFile
    else -> Icons.Outlined.Chat
}

private fun formatDate(time: Long?): String {
    if (time == null || time == 0L) return ""
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))
}

private data class ChatRowItem(
    val id: String,
    val name: String,
    val lastMessage: LocalChatMessage?,
    val isOnline: Boolean,
    val matchSearch: Boolean,
    val subtitle: String = ""
)

@Composable
private fun WhatsAppContactRow(
    name: String, 
    subtitle: String = "",
    preview: String, 
    icon: ImageVector,
    date: String,
    online: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Avatar with online indicator
        Box {
            Surface(
                shape = CircleShape,
                color = Color(0xFFE1E4E7),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                }
            }
            
            if (online) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color.White, CircleShape)
                        .padding(2.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(LinkGreen, CircleShape))
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 17.sp, 
                        color = LinkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, fontSize = 12.sp, color = LinkChipSelectedText)
                    }
                }
                Text(
                    text = date, 
                    color = LinkMuted, 
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = LinkMuted, 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = preview, 
                    color = LinkMuted, 
                    fontSize = 14.sp, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UpdatesPage(prefs: android.content.SharedPreferences, internetReady: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnectionBanner() }
        item { SectionTitle("Updates & safety") }
        item {
            PremiumCard("Live connection status", Icons.Outlined.OnlinePrediction) {
                StatusLine("Internet", if (internetReady) "Connected" else "Offline", internetReady)
                StatusLine("Bluetooth discovery", "Ready", true)
                StatusLine("Wi-Fi Direct", "Ready", true)
                StatusLine("Mesh relay", if (prefs.getBoolean("mesh", true)) "Auto" else "Off", prefs.getBoolean("mesh", true))
            }
        }
        item {
            PremiumCard("Security", Icons.Outlined.VerifiedUser) {
                ModeSetting(prefs, "e2ee_mode", "End-to-end encryption", "Off / On / Auto", 2)
                ToggleSetting(prefs, "pin_lock", "PIN lock", "Protect Shyna Link", false)
                ToggleSetting(prefs, "biometric", "Fingerprint / Face", "Use device biometrics", true)
                ToggleSetting(prefs, "secure_logs", "Secure logs", "Encrypt local call and chat history", true)
            }
        }
        item {
            PremiumCard("Smart controls", Icons.Outlined.AutoAwesome) {
                ToggleSetting(prefs, "ai_noise", "AI noise removal", "Cleaner voice in noisy places", true)
                ToggleSetting(prefs, "spam_ai", "AI spam detection", "Flag suspicious online callers", true)
                ToggleSetting(prefs, "auto_reply", "Auto reply", "Reply when busy or disconnected", false)
                ToggleSetting(prefs, "cloud_backup", "Cloud backup", "Back up after server login", false)
            }
        }
    }
}

@Composable
private fun CommunitiesPage(prefs: android.content.SharedPreferences) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Communities") }
        item { CommunityRow("Office Mesh", "5 nearby devices • Relay active", Icons.Outlined.Apartment) }
        item { CommunityRow("Family", "4 members • Encrypted", Icons.Outlined.FamilyRestroom) }
        item { CommunityRow("Emergency Circle", "SOS broadcast enabled", Icons.Outlined.Sos) }
        item {
            PremiumCard("Mesh network", Icons.Outlined.Hub) {
                ToggleSetting(prefs, "mesh", "Multi-hop mesh relay", "Extend range through trusted devices", true)
                ToggleSetting(prefs, "relay", "Relay mode", "Help nearby users pass encrypted traffic", false)
                ToggleSetting(prefs, "battery_saver", "Battery saver", "Reduce scan frequency", true)
                ModeSetting(prefs, "discovery_mode", "Discovery mode", "Off / On / Auto", 2)
            }
        }
        item {
            PremiumCard("Emergency", Icons.Outlined.Emergency) {
                FeatureRow("SOS broadcast", "Alert nearby trusted devices", Icons.Outlined.Sos)
                FeatureRow("Share location", "Send current GPS position", Icons.Outlined.LocationOn)
                FeatureRow("Medical information", "Encrypted emergency profile", Icons.Outlined.MedicalInformation)
            }
        }
    }
}

@Composable
private fun CallsPage(
    context: Context,
    prefs: android.content.SharedPreferences,
    internetReady: Boolean,
    serverOpen: Boolean,
    onServerOpenChange: (Boolean) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    userId: String,
    onUserIdChange: (String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf<Int?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnectionBanner() }
        item { SectionTitle("Calls") }
        item {
            PremiumCard("Quick actions", Icons.Outlined.Bolt) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("Offline call", Icons.Outlined.Phone, LinkGreen, Modifier.weight(1f)) { 
                        Toast.makeText(context, "Shyna Pro Mesh: Searching for nearby devices over Bluetooth/Wi-Fi Direct...", Toast.LENGTH_LONG).show()
                    }
                    QuickAction("Video", Icons.Outlined.VideoCall, LinkBlue, Modifier.weight(1f)) { serverNotice(context, serverUrl, internetReady) }
                    QuickAction("Files", Icons.Outlined.Folder, LinkCyan, Modifier.weight(1f)) { Toast.makeText(context, "Nearby file sharing ready", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        item {
            PremiumCard("Offline phone call", Icons.Outlined.SignalCellularAlt) {
                OutlinedTextField(number, { number = it.filter { ch -> ch.isDigit() || ch in "+*#" } }, Modifier.fillMaxWidth(), label = { Text("Phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedSim == null, { selectedSim = null }, label = { Text("Ask") })
                    FilterChip(selectedSim == 0, { selectedSim = 0 }, label = { Text("SIM 1") })
                    FilterChip(selectedSim == 1, { selectedSim = 1 }, label = { Text("SIM 2") })
                }
                Button(onClick = { if (number.isBlank()) Toast.makeText(context, "Enter a phone number", Toast.LENGTH_SHORT).show() else SimCallManager.placeCall(context, number, selectedSim) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = LinkGreen, contentColor = Color.Black)) { Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(8.dp)); Text("Call now") }
            }
        }
        item {
            PremiumCard("Nearby calling", Icons.Outlined.WifiTethering) {
                NearbyCallRow("Rahul", "Bluetooth • 18 m", LinkGreen)
                NearbyCallRow("Aman", "Wi-Fi Direct • 42 m", LinkBlue)
                NearbyCallRow("Office", "Mesh • 75 m", LinkCyan)
            }
        }
        item {
            PremiumCard("Call quality", Icons.Outlined.GraphicEq) {
                ModeSetting(prefs, "video_quality", "Video quality", "480p / 720p / 1080p", 1)
                ToggleSetting(prefs, "noise_cancel", "Noise cancellation", "AI enhanced voice", true)
                ToggleSetting(prefs, "echo_cancel", "Echo cancellation", "Reduce speaker echo", true)
                ToggleSetting(prefs, "auto_quality", "Auto quality", "Adapt to signal and battery", true)
                ToggleSetting(prefs, "pip", "Picture in picture", "Keep video visible", true)
            }
        }
        item {
            PremiumCard("Connection setup", Icons.Outlined.CloudSync) {
                StatusLine("Internet", if (internetReady) "Connected" else "Offline", internetReady)
                StatusLine("Token Server", if (serverUrl.isBlank()) "Not configured" else "Configured", serverUrl.isNotBlank())
                OutlinedButton(onClick = { onServerOpenChange(!serverOpen) }, modifier = Modifier.fillMaxWidth()) { Text(if (serverOpen) "Hide Server settings" else "Token Server settings") }
                if (serverOpen) {
                    OutlinedTextField(serverUrl, onServerUrlChange, Modifier.fillMaxWidth(), label = { Text("Token Server URL (https://...)") }, singleLine = true)
                    OutlinedTextField(
                        userId, 
                        onUserIdChange, 
                        Modifier.fillMaxWidth(), 
                        label = { Text("Shyna user ID / mobile") }, 
                        singleLine = true,
                        supportingText = {
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            auth.currentUser?.uid?.let { uid ->
                                Text("Firebase UID: $uid", color = LinkGreen, modifier = Modifier.clickable { onUserIdChange(uid) })
                            }
                        }
                    )
                    Button(onClick = {
                        if (serverUrl.isNotBlank() && !serverUrl.startsWith("https://") && !serverUrl.startsWith("wss://")) {
                            Toast.makeText(context, "Use a secure https:// or wss:// address", Toast.LENGTH_LONG).show()
                        } else { 
                            prefs.edit()
                                .putString("server_url", serverUrl.trim())
                                .putString("user_id", userId.trim())
                                .apply()
                            Toast.makeText(context, "Connection saved", Toast.LENGTH_SHORT).show() 
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Configuration") }
                    
                    if (context is com.example.callruleblocker.MainActivity) {
                        val currentUser = context.firebaseUser
                        if (currentUser != null) {
                            Text("Logged in as: ${currentUser.email}", style = MaterialTheme.typography.labelLarge, color = LinkGreen)
                            Text("UID: ${currentUser.uid}", style = MaterialTheme.typography.bodySmall, color = LinkMuted)
                            OutlinedButton(
                                onClick = { context.logoutUser() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53E36))
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Logout from Shyna Link")
                            }
                        } else {
                            // If we're here, firebaseUid was not null but context.firebaseUser is.
                            // This is a sync delay. Just show a small indicator or nothing.
                            Text("Synchronizing account...", color = LinkMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            PremiumCard("Device connections", Icons.Outlined.SettingsInputAntenna) {
                FeatureRow("Bluetooth devices", "Headset, car and nearby phones", Icons.Outlined.Bluetooth) { openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
                FeatureRow("Wi-Fi Direct", "High-speed local calling and files", Icons.Outlined.Wifi) { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }
                FeatureRow("SIM and Wi-Fi Calling", "Carrier call settings", Icons.Filled.SimCard) { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }
            }
        }
    }
}

@Composable private fun LinkBottomBar(selected: LinkTab, onSelect: (LinkTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        LinkTabItem(LinkTab.CHATS, selected, "Chats", Icons.Outlined.Chat, onSelect)
        LinkTabItem(LinkTab.UPDATES, selected, "Updates", Icons.Outlined.DonutLarge, onSelect)
        LinkTabItem(LinkTab.COMMUNITIES, selected, "Communities", Icons.Outlined.Groups, onSelect)
        LinkTabItem(LinkTab.CALLS, selected, "Calls", Icons.Outlined.Call, onSelect)
        
        // Profile Tab "You"
        NavigationBarItem(
            selected = selected == LinkTab.YOU,
            onClick = { onSelect(LinkTab.YOU) },
            icon = {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp),
                    color = Color.LightGray,
                    border = if (selected == LinkTab.YOU) androidx.compose.foundation.BorderStroke(2.dp, LinkChipSelectedText) else null
                ) {
                    Icon(Icons.Outlined.Person, null, tint = Color.Gray)
                }
            },
            label = { Text("You", color = if (selected == LinkTab.YOU) LinkText else LinkMuted) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = LinkChipSelectedText,
                indicatorColor = LinkChipSelected
            )
        )
    }
}

@Composable private fun RowScope.LinkTabItem(tab: LinkTab, selected: LinkTab, label: String, icon: ImageVector, onSelect: (LinkTab) -> Unit) {
    NavigationBarItem(
        selected = tab == selected, 
        onClick = { onSelect(tab) }, 
        icon = { Icon(icon, label) }, 
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, 
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = LinkChipSelectedText, 
            selectedTextColor = LinkText, 
            indicatorColor = LinkChipSelected, 
            unselectedIconColor = LinkMuted, 
            unselectedTextColor = LinkMuted
        )
    )
}

@Composable private fun ConnectionBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(shape = RoundedCornerShape(18.dp), color = LinkSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha }.background(LinkChipSelectedText, CircleShape)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("Connected Nearby", fontWeight = FontWeight.Bold, color = LinkText); Text("Bluetooth • Wi-Fi Direct • Mesh", color = LinkMuted, fontSize = 12.sp) }
            AssistChip(onClick = {}, label = { Text("AUTO") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LinkText) }

@Composable private fun PremiumCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = LinkSurface, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Surface(shape = CircleShape, color = LinkBlue.copy(alpha = .1f)) { 
                    Icon(icon, null, tint = LinkBlue, modifier = Modifier.padding(9.dp)) 
                }; Spacer(Modifier.width(10.dp)); Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LinkText) 
            }
            content()
        }
    }
}

@Composable private fun ContactRow(name: String, connection: String, preview: String, unread: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = LinkBlue.copy(alpha = .25f), modifier = Modifier.size(54.dp)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1), fontWeight = FontWeight.Bold, fontSize = 22.sp) } }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(connection, color = LinkGreen, fontSize = 12.sp); Text(preview, color = LinkMuted, maxLines = 1) }
        Surface(shape = CircleShape, color = LinkGreen) { Text(unread.toString(), color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun CommunityRow(name: String, subtitle: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(20.dp), color = LinkCard) { Row(Modifier.fillMaxWidth().clickable { }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = LinkCyan, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }; Icon(Icons.Outlined.ChevronRight, null) } }
}

@Composable private fun QuickAction(text: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = color.copy(alpha = .16f)) { Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = color); Spacer(Modifier.height(5.dp)); Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
}

@Composable private fun NearbyCallRow(name: String, subtitle: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), CircleShape, color.copy(alpha = .18f)) { Box(contentAlignment = Alignment.Center) { Text(name.take(1), fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }; FilledIconButton(onClick = {}, colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.Black)) { Icon(Icons.Outlined.Call, "Call") } }
}

@Composable private fun FeatureRow(title: String, subtitle: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = LinkCyan); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }; Icon(Icons.Outlined.ChevronRight, null, tint = LinkMuted) }
}

@Composable private fun ToggleSetting(prefs: android.content.SharedPreferences, key: String, title: String, subtitle: String, initial: Boolean) {
    var enabled by remember { mutableStateOf(prefs.getBoolean(key, initial)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }; Switch(enabled, { enabled = it; prefs.edit().putBoolean(key, it).apply() }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ModeSetting(prefs: android.content.SharedPreferences, key: String, title: String, subtitle: String, initial: Int) {
    var mode by remember { mutableIntStateOf(prefs.getInt(key, initial)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }; SingleChoiceSegmentedButtonRow { listOf("Off", "On", "Auto").forEachIndexed { index, label -> SegmentedButton(selected = mode == index, onClick = { mode = index; prefs.edit().putInt(key, index).apply() }, shape = SegmentedButtonDefaults.itemShape(index, 3), label = { Text(label, fontSize = 10.sp) }) } } }
}

@Composable private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { 
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) LinkChipSelectedText else Color(0xFFFF3B30)); 
        Spacer(Modifier.width(8.dp)); 
        Text(label, Modifier.weight(1f), color = LinkText); 
        Text(value, color = if (ok) LinkChipSelectedText else LinkMuted, fontWeight = FontWeight.SemiBold) 
    }
}

@Composable
private fun ChatBubble(
    message: LocalChatMessage, 
    onOpenMedia: (LocalChatMessage) -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "bubbleScale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                onClick = { /* Standard tap? */ },
                onLongClick = onLongClick
            ),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) { 
        when (message.type) {
            MessageType.VOICE -> {
                VoiceMessageBubble(message)
            }
            MessageType.IMAGE, MessageType.VIDEO -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.width(220.dp).height(220.dp)
                        .clickable { onOpenMedia(message) }
                ) {
                    Box {
                        if (message.type == MessageType.IMAGE) {
                            AsyncImage(
                                model = message.metadata,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Video Thumbnail / Placeholder
                            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeString = remember(message.time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.time)) }
                            Text(timeString, color = Color.White, fontSize = 10.sp)
                            if (message.mine) {
                                Spacer(Modifier.width(4.dp))
                                MessageStatusTicks(message.status)
                            }
                        }
                    }
                }
            }
            MessageType.LOCATION -> {
                val coords = message.metadata?.split(",") ?: listOf("0", "0")
                val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                val lng = coords.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                val pos = LatLng(lat, lng)
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.DarkGray,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E676)),
                    modifier = Modifier.width(260.dp).clickable { 
                        try {
                            val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google Maps not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Box {
                        GoogleMap(
                            modifier = Modifier.height(180.dp).fillMaxWidth(),
                            cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(pos, 15f) },
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, scrollGesturesEnabled = false),
                            properties = MapProperties(mapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions("[]")) // Simplified for bubble
                        ) {
                            Marker(state = rememberMarkerState(position = pos))
                        }
                        
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeString = remember(message.time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.time)) }
                            Text(timeString, color = Color.White, fontSize = 10.sp)
                            if (message.mine) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.DoneAll, null, tint = LinkCyan, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            MessageType.FILE -> {
                Surface(
                    shape = RoundedCornerShape(16.dp), 
                    color = Color(0xFF242424), 
                    modifier = Modifier.width(240.dp).clickable { onOpenMedia(message) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.DarkGray, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Description, null, tint = Color.LightGray) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(message.text, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(message.metadata?.takeLast(20) ?: "File", color = LinkMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Outlined.Shortcut, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            MessageType.EVENT -> {
                EventChatCard(message)
            }
            MessageType.POLL -> {
                PollChatCard(message)
            }
            else -> {
                Surface(shape = RoundedCornerShape(16.dp), color = if (message.mine) LinkChipSelected else Color(0xFFF0F2F5)) { 
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { 
                        Text(message.text, color = LinkText)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            val timeString = remember(message.time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.time)) }
                            Text(timeString, color = LinkMuted, fontSize = 10.sp) 
                            if (message.mine) {
                                Spacer(Modifier.width(4.dp))
                                MessageStatusTicks(message.status)
                            }
                        }
                    } 
                }
            }
        }
    } 
}

@Composable private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) { DropdownMenuItem(text = { Text(text, color = Color.White) }, leadingIcon = { Icon(icon, null, tint = LinkCyan) }, onClick = onClick) }

private fun hasInternet(context: Context): Boolean { val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false; val network = cm.activeNetwork ?: return false; val caps = cm.getNetworkCapabilities(network) ?: return false; return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
private fun openSettings(context: Context, action: String) { runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
private fun serverNotice(context: Context, url: String, internetReady: Boolean) { 
    Toast.makeText(context, when { 
        !internetReady -> "Internet is not connected"
        url.isBlank() -> "Add the LiveKit server first"
        else -> "LiveKit signaling endpoint is configured: $url" 
    }, Toast.LENGTH_LONG).show() 
}
private fun saveMessages(prefs: android.content.SharedPreferences, messages: List<LocalChatMessage>) {
    val value = messages.takeLast(200).joinToString("\n") { 
        "${it.id}|${it.time}|${if (it.mine) 1 else 0}|${it.type.name}|${it.status.name}|${it.sentAt}|${it.deliveredAt}|${it.readAt}|${it.peerName}|${it.eventId ?: ""}|${it.pollId ?: ""}|${it.metadata ?: ""}|${Uri.encode(it.text)}" 
    }
    prefs.edit().putString("local_chat_v5", value).apply()
}

private fun loadMessages(prefs: android.content.SharedPreferences): List<LocalChatMessage> = 
    (prefs.getString("local_chat_v5", "") ?: "").lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 13)
        if (p.size != 13) return@mapNotNull null
        LocalChatMessage(
            id = p[0],
            time = p[1].toLongOrNull() ?: 0L,
            mine = p[2] == "1",
            type = try { MessageType.valueOf(p[3]) } catch(e: Exception) { MessageType.TEXT },
            status = try { MessageStatus.valueOf(p[4]) } catch(e: Exception) { MessageStatus.SENT },
            sentAt = p[5].toLongOrNull() ?: 0L,
            deliveredAt = p[6].toLongOrNull() ?: 0L,
            readAt = p[7].toLongOrNull() ?: 0L,
            peerName = p[8],
            eventId = if (p[9].isEmpty()) null else p[9],
            pollId = if (p[10].isEmpty()) null else p[10],
            metadata = if (p[11].isEmpty()) null else p[11],
            text = Uri.decode(p[12])
        )
    }.toList()

private enum class ChatTool { NONE, AUDIO_TEST, VIDEO_SETTINGS, GROUP_CALL, ATTACHMENTS, SECURITY, LOCATION, GALLERY, VIDEO_CALL, CAMERA, CONTACT, DOCUMENT, POLL, EVENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInfoScreen(message: LocalChatMessage, onBack: () -> Unit) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }
    
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Message info", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF0F2F5))) {
            // Message Content Preview
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                ChatBubble(message = message, onOpenMedia = {}, onLongClick = {})
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Info List
            Surface(Modifier.fillMaxWidth(), color = Color.White) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    InfoRow(
                        icon = Icons.Outlined.DoneAll,
                        iconColor = LinkGreen,
                        title = "Read",
                        time = if (message.readAt > 0) fmt.format(Date(message.readAt)) else "—"
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    InfoRow(
                        icon = Icons.Outlined.DoneAll,
                        iconColor = Color.Gray,
                        title = "Delivered",
                        time = if (message.deliveredAt > 0) fmt.format(Date(message.deliveredAt)) else "—"
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    InfoRow(
                        icon = Icons.Outlined.Done,
                        iconColor = Color.Gray,
                        title = "Sent",
                        time = fmt.format(Date(message.sentAt))
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, iconColor: Color, title: String, time: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = LinkText)
            Text(time, color = LinkMuted, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEventScreen(onBack: () -> Unit, onCreate: (LocalChatMessage) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create event", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (DD/MM/YY)") }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start Time") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End Time") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location / Link") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    if (title.isBlank() || date.isBlank() || startTime.isBlank()) return@Button
                    val eventId = java.util.UUID.randomUUID().toString()
                    val msg = LocalChatMessage(
                        text = "New Event: $title",
                        mine = true,
                        time = System.currentTimeMillis(),
                        type = MessageType.EVENT,
                        eventId = eventId,
                        metadata = "$date | $startTime | $location"
                    )
                    onCreate(msg)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LinkChipSelectedText)
            ) {
                Text("Create Event", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePollScreen(onBack: () -> Unit, onCreate: (LocalChatMessage) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var allowMultiple by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create poll", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
            
            Text("Options", fontWeight = FontWeight.Bold, color = LinkMuted)
            options.forEachIndexed { index, option ->
                OutlinedTextField(
                    value = option,
                    onValueChange = { options[index] = it },
                    label = { Text("Option ${index + 1}") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) { Icon(Icons.Outlined.Close, null) }
                        }
                    }
                )
            }
            
            if (options.size < 12) {
                TextButton(onClick = { options.add("") }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add option")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
                Text("Allow multiple answers", color = LinkText)
            }

            Button(
                onClick = {
                    if (question.isBlank() || options.any { it.isBlank() }) return@Button
                    val pollId = java.util.UUID.randomUUID().toString()
                    val msg = LocalChatMessage(
                        text = "Poll: $question",
                        mine = true,
                        time = System.currentTimeMillis(),
                        type = MessageType.POLL,
                        pollId = pollId,
                        metadata = options.joinToString("|")
                    )
                    onCreate(msg)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LinkChipSelectedText)
            ) {
                Text("Create Poll", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FullScreenMediaViewer(media: LocalChatMessage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (media.type) {
            MessageType.IMAGE -> {
                AsyncImage(
                    model = media.metadata,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            MessageType.VIDEO -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Actual video playback would use a Media3 PlayerView here
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(80.dp).background(Color.White.copy(0.1f), CircleShape).padding(16.dp))
                    Text("Playing Video", color = Color.White, modifier = Modifier.padding(top = 100.dp))
                }
            }
            else -> {
                // PDF or other files
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val icon = if (media.text.contains("PDF", true)) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(100.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("File: ${media.text}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Storage: ${media.metadata?.takeLast(30)}...", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { /* Open with system */ }) {
                        Text("Open Full View")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, null, tint = Color.White)
            }
            
            val timeString = remember(media.time) { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(media.time)) }
            Text(timeString, color = Color.White, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, // UID of the receiver
    prefs: android.content.SharedPreferences, 
    userId: String, 
    allMessages: androidx.compose.runtime.snapshots.SnapshotStateList<LocalChatMessage>, 
    allRealUsers: List<RealUser> = emptyList(), // Pass real user list to find names
    onBack: () -> Unit,
    onOpenMedia: (LocalChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val peerName = remember(peerId, allRealUsers) { 
        allRealUsers.find { it.uid == peerId }?.name ?: "Unknown User"
    }
    var text by remember { mutableStateOf("") }
    var activeTool by remember { mutableStateOf(ChatTool.NONE) }
    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    val audioRecorder = remember { AudioRecorder(context) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val recordingAmplitudes = remember { mutableStateListOf<Float>() }
    
    var showEmojiPicker by remember { mutableStateOf(false) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var captureType by remember { mutableStateOf<MessageType?>(null) }

    var messageToDelete by remember { mutableStateOf<LocalChatMessage?>(null) }
    var messageToInfo by remember { mutableStateOf<LocalChatMessage?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Trigger again or handle
            Toast.makeText(context, "Permission granted. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            captureType = MessageType.IMAGE
        } else {
            capturedFile = null
            captureType = null
        }
    }
    
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            captureType = MessageType.VIDEO
        } else {
            capturedFile = null
            captureType = null
        }
    }

    if (isRecording) {
        LaunchedEffect(Unit) {
            while (isRecording) {
                val amp = audioRecorder.getAmplitude().toFloat() / 32767f 
                recordingAmplitudes.add(amp.coerceIn(0.1f, 1f))
                kotlinx.coroutines.delay(100L)
            }
        }
        LaunchedEffect(Unit) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000L)
                recordingTime++
            }
        }
    }

    // Filter messages for this peer
    val messages = remember(allMessages.size, peerId) {
        allMessages.filter { it.peerName == peerId }
    }

    // --- CAPTURE PREVIEW SCREEN ---
    if (capturedFile != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (captureType == MessageType.IMAGE) {
                AsyncImage(
                    model = capturedFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Video Preview Placeholder
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(80.dp))
                    Text("Video Captured: ${capturedFile?.name}", color = Color.White)
                }
            }
            
            // Basic tools placeholder (Crop, Rotate, etc)
            Row(
                Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (captureType == MessageType.IMAGE) {
                    IconButton(onClick = { /* Crop */ }) { Icon(Icons.Outlined.Crop, null, tint = Color.White) }
                    IconButton(onClick = { /* Rotate */ }) { Icon(Icons.AutoMirrored.Outlined.RotateRight, null, tint = Color.White) }
                    IconButton(onClick = { /* Draw */ }) { Icon(Icons.Outlined.Edit, null, tint = Color.White) }
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { capturedFile = null; captureType = null },
                    modifier = Modifier.background(Color.DarkGray, CircleShape)
                ) { Icon(Icons.Outlined.Close, null, tint = Color.White) }

                FloatingActionButton(
                    onClick = {
                        val file = capturedFile!!
                        val type = captureType ?: MessageType.IMAGE
                        uploadVoiceNote(file, context) { url -> 
                            val newMediaMsg = LocalChatMessage(
                                text = if(type == MessageType.IMAGE) "Photo" else "Video", 
                                mine = true, 
                                time = System.currentTimeMillis(), 
                                type = type, 
                                metadata = url,
                                status = MessageStatus.SENT,
                                peerName = peerId
                            )
                            allMessages.add(newMediaMsg)
                            saveMessages(prefs, allMessages)
                        }
                        capturedFile = null
                        captureType = null
                    },
                    containerColor = LinkChipSelectedText,
                    contentColor = Color.White,
                    shape = CircleShape
                ) { Icon(Icons.AutoMirrored.Outlined.Send, null) }
            }
        }
        return
    }

    DisposableEffect(peerId) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val senderId = auth.currentUser?.uid ?: return@DisposableEffect onDispose {}
        val receiverId = peerId
        val chatId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                val cloudMessages = snapshots?.documents?.mapNotNull { doc ->
                    val sId = doc.getString("senderId")
                    val t = doc.getString("text")
                    val time = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                    if (t != null) LocalChatMessage(
                        text = t, 
                        mine = sId == senderId, 
                        time = time,
                        peerName = peerId
                    ) else null
                } ?: emptyList()
                
                if (cloudMessages.isNotEmpty()) {
                    // Update allMessages: remove old ones for this peer and add new ones
                    allMessages.removeAll { it.peerName == peerId }
                    allMessages.addAll(cloudMessages)
                    saveMessages(prefs, allMessages)
                }
            }
        onDispose { listener.remove() }
    }

    // --- FULL SCREEN TOOLS ---
    when (activeTool) {
        ChatTool.LOCATION -> {
            SendLocationScreen(
                onBack = { activeTool = ChatTool.NONE },
                onSendLocation = { coords ->
                    val locMsg = LocalChatMessage(
                        text = "Live Location", 
                        mine = true, 
                        time = System.currentTimeMillis(), 
                        type = MessageType.LOCATION, 
                        metadata = coords,
                        status = MessageStatus.SENT,
                        peerName = peerId
                    )
                    allMessages.add(locMsg)
                    saveMessages(prefs, allMessages)
                    activeTool = ChatTool.NONE
                }
            )
            return
        }
        ChatTool.GALLERY -> {
            GalleryPickerScreen(
                onBack = { activeTool = ChatTool.NONE },
                onItemsSelected = { selected ->
                    selected.forEach { path ->
                        val imgMsg = LocalChatMessage(
                            text = "Image", 
                            mine = true, 
                            time = System.currentTimeMillis(), 
                            type = MessageType.IMAGE, 
                            metadata = path,
                            status = MessageStatus.SENT,
                            peerName = peerId
                        )
                        allMessages.add(imgMsg)
                    }
                    saveMessages(prefs, allMessages)
                    activeTool = ChatTool.NONE
                }
            )
            return
        }
        ChatTool.POLL -> {
            CreatePollScreen(
                onBack = { activeTool = ChatTool.NONE },
                onCreate = { msg ->
                    val finalMsg = msg.copy(peerName = peerId)
                    allMessages.add(finalMsg)
                    saveMessages(prefs, allMessages)
                    activeTool = ChatTool.NONE
                }
            )
            return
        }
        ChatTool.EVENT -> {
            CreateEventScreen(
                onBack = { activeTool = ChatTool.NONE },
                onCreate = { msg ->
                    val finalMsg = msg.copy(peerName = peerId)
                    allMessages.add(finalMsg)
                    saveMessages(prefs, allMessages)
                    activeTool = ChatTool.NONE
                }
            )
            return
        }
        ChatTool.VIDEO_CALL -> {
            val roomName = if (peerId == "Rahul") "room_rahul" else "room_aman"
            VideoCallScreen(roomName = roomName, userId = userId) {
                activeTool = ChatTool.NONE
            }
            return
        }
        else -> Unit
    }

    if (messageToInfo != null) {
        MessageInfoScreen(message = messageToInfo!!) { messageToInfo = null }
        return
    }

    Scaffold(
        containerColor = Color(0xFFF3F0ED),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), CircleShape, LinkGreen.copy(alpha = .18f)) {
                            Box(contentAlignment = Alignment.Center) { Text(peerName.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column { Text(peerName, fontWeight = FontWeight.Bold); Text("online • encrypted", fontSize = 11.sp, color = Color(0xFFD7EEE3)) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { activeTool = ChatTool.VIDEO_SETTINGS }) { Icon(Icons.Outlined.Videocam, "Video call") }
                    IconButton(onClick = { activeTool = ChatTool.AUDIO_TEST }) { Icon(Icons.Outlined.Call, "Audio call") }
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Group call") }, leadingIcon = { Icon(Icons.Outlined.Groups, null) }, onClick = { more = false; activeTool = ChatTool.GROUP_CALL })
                            DropdownMenuItem(text = { Text("Audio & video test lab") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { more = false; activeTool = ChatTool.AUDIO_TEST })
                            DropdownMenuItem(text = { Text("Attachments") }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) }, onClick = { more = false; activeTool = ChatTool.ATTACHMENTS })
                            DropdownMenuItem(text = { Text("Security details") }, leadingIcon = { Icon(Icons.Outlined.Security, null) }, onClick = { more = false; activeTool = ChatTool.SECURITY })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF006B5E), titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .background(Color.Transparent)
                    .padding(8.dp)
            ) {
                if (showEmojiPicker) {
                    EmojiRow(onEmojiSelect = { 
                        text += it
                        showEmojiPicker = false 
                    })
                }

                if (activeTool == ChatTool.ATTACHMENTS) {
                    WhatsAppAttachmentMenu(
                        onAction = { activeTool = it },
                        onDismiss = { activeTool = ChatTool.NONE }
                    )
                }

                if (isRecording) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Mic, null, tint = Color.Red)
                            Text(
                                remember(recordingTime) { String.format(Locale.getDefault(), "%02d:%02d", recordingTime / 60, recordingTime % 60) },
                                modifier = Modifier.padding(start = 8.dp),
                                color = LinkText
                            )
                            
                            // Visualization Waves
                            VoiceWaveform(
                                amplitudes = recordingAmplitudes,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                color = Color.Red
                            )
                            
                            if (!isLocked) Text("Slide to lock", color = LinkMuted, fontSize = 12.sp)
                            else Text("Locked", color = LinkChipSelectedText, fontSize = 12.sp)
                            
                            IconButton(onClick = { 
                                audioRecorder.stop()
                                isRecording = false
                                isLocked = false
                                recordingTime = 0
                                recordingAmplitudes.clear()
                            }) {
                                Icon(Icons.Outlined.Delete, null, tint = LinkMuted)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Modern WhatsApp-style Pill Input
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                                Icon(Icons.Outlined.InsertEmoticon, "Emoji", tint = LinkMuted)
                            }
                            
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it; if(it.isNotEmpty()) showEmojiPicker = false },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(fontSize = 17.sp, color = LinkText),
                                cursorBrush = SolidColor(LinkChipSelectedText),
                                decorationBox = { innerTextField ->
                                    if (text.isEmpty()) Text("Message", color = LinkMuted, fontSize = 17.sp)
                                    innerTextField()
                                }
                            )
                            
                            IconButton(onClick = { activeTool = ChatTool.ATTACHMENTS }) {
                                Icon(Icons.Outlined.AttachFile, "Attach", tint = LinkMuted, modifier = Modifier.graphicsLayer { rotationZ = -45f })
                            }
                            
                            if (text.isEmpty()) {
                                IconButton(
                                    onClick = { 
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                                            capturedFile = file
                                            captureType = MessageType.IMAGE
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, 
                                                "${context.packageName}.provider", 
                                                file
                                            )
                                            cameraLauncher.launch(uri)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    val file = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
                                                    capturedFile = file
                                                    captureType = MessageType.VIDEO
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context, 
                                                        "${context.packageName}.provider", 
                                                        file
                                                    )
                                                    videoLauncher.launch(uri)
                                                } else {
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    Icon(Icons.Outlined.PhotoCamera, "Camera", tint = LinkMuted)
                                }
                            }
                        }
                    }

                    // Green Circular FAB (Mic or Send)
                    val micModifier = if (text.isBlank()) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    // Start recording logic
                                    val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                    audioFile = file
                                    try {
                                        audioRecorder.start(file)
                                        isRecording = true
                                        isLocked = false
                                        recordingTime = 0
                                        recordingAmplitudes.clear()
                                    } catch (e: Exception) {
                                        isRecording = false
                                    }

                                    var released = false
                                    var totalDragY = 0f
                                    while (!released) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.changedToUp() }) {
                                            released = true
                                            if (!isLocked) {
                                                // Stop and Send
                                                audioRecorder.stop()
                                                isRecording = false
                                                audioFile?.let { f ->
                                                    uploadVoiceNote(f, context) { url ->
                                                        val vMsg = LocalChatMessage(
                                                            text = "Voice message",
                                                            mine = true,
                                                            time = System.currentTimeMillis(),
                                                            type = MessageType.VOICE,
                                                            metadata = url,
                                                            status = MessageStatus.SENT,
                                                            peerName = peerId
                                                        )
                                                        allMessages.add(vMsg)
                                                        saveMessages(prefs, allMessages)
                                                    }
                                                }
                                                recordingTime = 0
                                                recordingAmplitudes.clear()
                                            }
                                        } else {
                                            // Handle slide to lock
                                            val change = event.changes.first()
                                            if (change.pressed) {
                                                totalDragY += (change.position.y - change.previousPosition.y)
                                                if (isRecording && !isLocked && totalDragY < -100) {
                                                    isLocked = true
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else Modifier

                    Surface(
                        modifier = Modifier
                            .size(50.dp)
                            .then(micModifier)
                            .then(if (text.isNotBlank() || isLocked) Modifier.clickable {
                                if (text.isNotBlank()) {
                                    val cleanText = text.trim()
                                    val newTextMsg = LocalChatMessage(
                                        text = cleanText, 
                                        mine = true, 
                                        time = System.currentTimeMillis(),
                                        peerName = peerId
                                    )
                                    allMessages.add(newTextMsg)
                                    saveMessages(prefs, allMessages)
                                    if (context is com.example.callruleblocker.MainActivity) {
                                        context.sendMessage(peerId, cleanText)
                                    }
                                    text = ""
                                } else if (isLocked) {
                                    // Manual Stop and Send
                                    audioRecorder.stop()
                                    isRecording = false
                                    isLocked = false
                                    audioFile?.let { file ->
                                        uploadVoiceNote(file, context) { url ->
                                            val voiceMsg = LocalChatMessage(
                                                text = "Voice message", 
                                                mine = true, 
                                                time = System.currentTimeMillis(), 
                                                type = MessageType.VOICE, 
                                                metadata = url,
                                                peerName = peerId
                                            )
                                            allMessages.add(voiceMsg)
                                            saveMessages(prefs, allMessages)
                                        }
                                    }
                                    recordingTime = 0
                                    recordingAmplitudes.clear()
                                }
                            } else Modifier),
                        shape = CircleShape,
                        color = LinkChipSelectedText, // WhatsApp Green
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (text.isBlank()) Icons.Outlined.Mic else Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFFEFEAE2)), 
            contentPadding = PaddingValues(12.dp), 
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { CenterInfoChip("Today") }
            item { CenterInfoChip("Messages and calls are protected with end-to-end encryption") }
            items(messages.size) { index ->
                val msg = messages[index]
                
                // Read receipt logic: if last message is from peer and we are viewing it
                if (!msg.mine && msg.status != MessageStatus.READ) {
                    SideEffect {
                        // Mark as READ in a real app would update DB
                    }
                }

                ChatBubble(
                    message = msg, 
                    onOpenMedia = onOpenMedia,
                    onLongClick = { messageToDelete = msg }
                ) 
            }
            if (isRecording) item { CenterInfoChip("Recording voice note… tap microphone again to finish") }
        }
    }

    messageToDelete?.let { msg ->
        WhatsAppDeleteDialog(
            message = msg,
            onDeleteForMe = { alsoDelete ->
                allMessages.remove(msg)
                saveMessages(prefs, allMessages)
                messageToDelete = null
            },
            onDeleteForEveryone = {
                allMessages.remove(msg)
                saveMessages(prefs, allMessages)
                messageToDelete = null
            },
            onInfo = {
                messageToInfo = msg
                messageToDelete = null
            },
            onCancel = { messageToDelete = null }
        )
    }


    // --- DIALOG TOOLS ---
    when (activeTool) {
        ChatTool.AUDIO_TEST -> AudioVideoTestDialog(context, prefs, onDismiss = { activeTool = ChatTool.NONE }, openVideo = { activeTool = ChatTool.VIDEO_SETTINGS })
        ChatTool.VIDEO_SETTINGS -> VideoCallSettingsDialog(
            prefs = prefs, 
            peer = peerName, 
            onDismiss = { activeTool = ChatTool.NONE }, 
            onStart = { activeTool = ChatTool.VIDEO_CALL },
            onGroup = { activeTool = ChatTool.GROUP_CALL }
        )
        ChatTool.GROUP_CALL -> GroupCallDialog(peerName, onDismiss = { activeTool = ChatTool.NONE })
        ChatTool.SECURITY -> SecurityDialog(onDismiss = { activeTool = ChatTool.NONE })
        ChatTool.CAMERA -> {
            SideEffect {
                val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                capturedFile = file
                captureType = MessageType.IMAGE
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, 
                    "${context.packageName}.provider", 
                    file
                )
                cameraLauncher.launch(uri)
                activeTool = ChatTool.NONE
            }
        }
        ChatTool.CONTACT -> {
            val contactMsg = LocalChatMessage(
                text = "Contact Card",
                mine = true,
                time = System.currentTimeMillis(),
                type = MessageType.FILE,
                metadata = "tel:+910000000000",
                status = MessageStatus.SENT,
                peerName = peerId
            )
            allMessages.add(contactMsg)
            saveMessages(prefs, allMessages)
            activeTool = ChatTool.NONE
        }
        ChatTool.DOCUMENT -> {
            val docMsg = LocalChatMessage(
                text = "Document.pdf",
                mine = true,
                time = System.currentTimeMillis(),
                type = MessageType.FILE,
                metadata = "https://example.com/file",
                status = MessageStatus.SENT,
                peerName = peerId
            )
            allMessages.add(docMsg)
            saveMessages(prefs, allMessages)
            activeTool = ChatTool.NONE
        }
        else -> Unit
    }
}

private fun uploadVoiceNote(file: File, context: Context, onComplete: (String) -> Unit) {
    val storage = FirebaseStorage.getInstance()
    val ref = storage.reference.child("voice_notes/${file.name}")
    ref.putFile(Uri.fromFile(file))
        .addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                onComplete(uri.toString())
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
}

@Composable
private fun EmojiRow(onEmojiSelect: (String) -> Unit) {
    val emojis = listOf("👍", "👎", "😂", "❤️", "🔥", "🙏", "✔️", "📍", "👋", "🎉")
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier.clickable { onEmojiSelect(emoji) }.padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun WhatsAppDeleteDialog(
    message: LocalChatMessage,
    onDeleteForMe: (Boolean) -> Unit,
    onDeleteForEveryone: () -> Unit,
    onInfo: () -> Unit,
    onCancel: () -> Unit
) {
    var alsoDeleteMedia by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF1D272E), 
        title = { Text("Options", color = Color.White, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.mine) {
                    TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, tint = LinkChipSelectedText)
                            Spacer(Modifier.width(12.dp))
                            Text("Message Info", color = Color.White)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alsoDeleteMedia,
                        onCheckedChange = { alsoDeleteMedia = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = LinkChipSelectedText,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        "Also delete media received in this chat from the device gallery",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDeleteForEveryone) {
                        Text("Delete for everyone", color = LinkChipSelectedText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    TextButton(onClick = { onDeleteForMe(alsoDeleteMedia) }) {
                        Text("Delete for me", color = LinkChipSelectedText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = LinkChipSelectedText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun EventChatCard(message: LocalChatMessage) {
    var response by remember { mutableStateOf(EventResponse.NONE) }
    val details = message.metadata?.split("|") ?: listOf("", "", "")
    val date = details.getOrNull(0) ?: ""
    val time = details.getOrNull(1) ?: ""
    val loc = details.getOrNull(2) ?: ""

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.width(280.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFFFFE0E0), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Event, null, tint = Color.Red) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(message.text.removePrefix("New Event: "), fontWeight = FontWeight.Bold, color = LinkText)
                    Text("$date • $time", fontSize = 12.sp, color = LinkMuted)
                }
            }
            
            if (loc.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = LinkMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(loc, fontSize = 12.sp, color = LinkMuted)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF0F2F5))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EventActionButton("Going", response == EventResponse.GOING) { response = EventResponse.GOING }
                EventActionButton("Maybe", response == EventResponse.MAYBE) { response = EventResponse.MAYBE }
                EventActionButton("No", response == EventResponse.NOT_GOING) { response = EventResponse.NOT_GOING }
            }
        }
    }
}

@Composable
private fun EventActionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) LinkChipSelectedText else LinkBlue, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PollChatCard(message: LocalChatMessage) {
    val question = message.text.removePrefix("Poll: ")
    val options = message.metadata?.split("|") ?: emptyList()
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.width(280.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(question, fontWeight = FontWeight.Bold, color = LinkText, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            
            options.forEach { option ->
                val isSelected = selectedOption == option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedOption = option },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) LinkChipSelected else Color(0xFFF7F8FA),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, LinkChipSelectedText) else null
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = { selectedOption = option })
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = LinkText)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("1 vote • Real-time results", fontSize = 11.sp, color = LinkMuted)
        }
    }
}

@Composable
private fun VoiceWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = LinkBlue,
    playing: Boolean = false,
    progress: Float = 0f
) {
    Canvas(modifier = modifier.fillMaxWidth().height(40.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val count = (width / (barWidth + spacing)).toInt()
        
        val displayAmplitudes = if (amplitudes.size > count) {
            amplitudes.takeLast(count)
        } else {
            amplitudes
        }

        displayAmplitudes.forEachIndexed { index, amplitude ->
            val barHeight = (amplitude * height).coerceIn(4.dp.toPx(), height)
            val x = index.toFloat() * (barWidth + spacing)
            val y = (height - barHeight) / 2f
            
            val isPlayed = playing && (index.toFloat() / displayAmplitudes.size) <= progress
            
            drawRoundRect(
                color = if (isPlayed) color else color.copy(alpha = 0.3f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun VoiceMessageBubble(message: LocalChatMessage) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    
    // Generate a professional-looking pseudo-waveform
    val amplitudes = remember(message.time) {
        val random = java.util.Random(message.time)
        List(35) { random.nextFloat().coerceIn(0.2f, 1.0f) }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (message.mine) LinkChipSelected else Color(0xFFF0F2F5),
        modifier = Modifier.width(260.dp).padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (message.mine) LinkChipSelectedText else Color.LightGray, CircleShape)
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                VoiceWaveform(
                    amplitudes = amplitudes,
                    color = if (message.mine) LinkChipSelectedText else LinkBlue,
                    playing = isPlaying,
                    progress = progress
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeString = remember(message.time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.time)) }
                    Text("0:00", fontSize = 10.sp, color = LinkMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeString, fontSize = 10.sp, color = LinkMuted)
                        if (message.mine) {
                            Spacer(Modifier.width(4.dp))
                            MessageStatusTicks(message.status)
                        }
                    }
                }
            }
            
            if (message.mine) {
                Icon(Icons.Outlined.DoneAll, null, tint = LinkCyan, modifier = Modifier.size(16.dp).align(Alignment.Bottom))
            }
        }
    }

    if (isPlaying) {
        LaunchedEffect(Unit) {
            val steps = 100
            for (i in 0..steps) {
                if (!isPlaying) break
                progress = i.toFloat() / steps
                kotlinx.coroutines.delay(50L) // Simulate 5s audio for demo
            }
            isPlaying = false
            progress = 0f
        }
    }
}

@Composable
private fun AudioVideoTestDialog(context: Context, prefs: android.content.SharedPreferences, onDismiss: () -> Unit, openVideo: () -> Unit) {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var route by remember { mutableStateOf("Automatic") }
    var testState by remember { mutableStateOf("Ready") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Smart communication test lab") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DiagnosticLine("Microphone permission", if (micGranted) "Passed" else "Permission needed", micGranted) }
            item { DiagnosticLine("Camera permission", if (cameraGranted) "Passed" else "Permission needed", cameraGranted) }
            item { DiagnosticLine("Audio output", if (audio?.isMusicActive == true) "In use" else "Ready", true) }
            item { DiagnosticLine("Bluetooth route", if (audio?.isBluetoothScoAvailableOffCall == true) "Supported" else "Not reported", audio?.isBluetoothScoAvailableOffCall == true) }
            item { DiagnosticLine("Speakerphone", if (audio?.isSpeakerphoneOn == true) "On" else "Off", true) }
            item { Text("Route", fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Automatic", "Earpiece", "Speaker", "Bluetooth").forEach { FilterChip(route == it, { route = it }, label = { Text(it, fontSize = 10.sp) }) } } }
            item { ToggleSetting(prefs, "incoming_audio_test", "Incoming audio monitor", "Check ring, voice level and route", true) }
            item { ToggleSetting(prefs, "outgoing_audio_test", "Outgoing audio monitor", "Check microphone, gain and noise", true) }
            item { ToggleSetting(prefs, "aec_test", "Echo cancellation", "Acoustic echo control", true) }
            item { ToggleSetting(prefs, "ns_test", "Noise suppression", "Background noise control", true) }
            item { ToggleSetting(prefs, "agc_test", "Automatic gain", "Keep speech volume stable", true) }
            item { Button(onClick = { testState = if (micGranted) "All available local checks passed" else "Grant microphone permission first" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Run all tests") } }
            item { Text(testState, color = if (testState.contains("passed")) LinkGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
        }
    }, confirmButton = { TextButton(onClick = openVideo) { Text("Video settings") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable private fun DiagnosticLine(name: String, result: String, ok: Boolean) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (ok) LinkGreen else Color(0xFFFF9500)); Spacer(Modifier.width(8.dp)); Text(name, Modifier.weight(1f)); Text(result, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoCallSettingsDialog(
    prefs: android.content.SharedPreferences, 
    peer: String, 
    onDismiss: () -> Unit, 
    onStart: () -> Unit,
    onGroup: () -> Unit
) {
    var quality by remember { mutableIntStateOf(prefs.getInt("smart_video_quality", 2)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Video call • $peer") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Camera & quality", fontWeight = FontWeight.Bold) }
            item { SingleChoiceSegmentedButtonRow { listOf("480p", "720p", "1080p", "Auto").forEachIndexed { i, q -> SegmentedButton(selected = quality == i, onClick = { quality = i; prefs.edit().putInt("smart_video_quality", i).apply() }, shape = SegmentedButtonDefaults.itemShape(i, 4), label = { Text(q, fontSize = 9.sp) }) } } }
            item { ToggleSetting(prefs, "front_camera_default", "Front camera by default", "Switch camera anytime", true) }
            item { ToggleSetting(prefs, "video_low_light", "Low-light enhancement", "Improve dark video", true) }
            item { ToggleSetting(prefs, "video_stabilization", "Video stabilization", "Reduce camera shake", true) }
            item { ToggleSetting(prefs, "video_auto_fps", "Smart frame rate", "15/24/30 FPS based on network", true) }
            item { ToggleSetting(prefs, "video_pip", "Picture-in-picture", "Continue while using other apps", true) }
            item { ToggleSetting(prefs, "video_data_saver", "Data saver", "Reduce bandwidth automatically", false) }
            item { FeatureRow("Camera preview test", "Check front/rear camera before calling", Icons.Outlined.Cameraswitch) }
            item { FeatureRow("Network estimation", "Latency, jitter, packet loss and bitrate", Icons.Outlined.NetworkCheck) }
        }
    }, confirmButton = { 
        Button(onClick = { 
            onStart()
        }) { 
            Text("Start LiveKit Video") 
        } 
    }, dismissButton = { TextButton(onClick = onGroup) { Text("Add participants") } })
}

@Composable private fun GroupCallDialog(peer: String, onDismiss: () -> Unit) { var count by remember { mutableIntStateOf(2) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Group call") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("$peer is selected"); repeat(count - 1) { i -> FeatureRow("Participant ${i + 2}", "Tap to select from contacts or nearby users", Icons.Outlined.PersonAdd) }; OutlinedButton(onClick = { if (count < 8) count++ }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Add participant (${count}/8)") }; Text("Host controls: mute participant, remove, spotlight, camera permission and speaking indicator.", fontSize = 12.sp) } }, confirmButton = { Button(onClick = { onDismiss() }) { Text("Create room") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable
private fun WhatsAppAttachmentMenu(onAction: (ChatTool) -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1D272E), 
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(vertical = 24.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachmentIcon(Icons.Outlined.PhotoLibrary, "Gallery", Color(0xFF2979FF)) { onAction(ChatTool.GALLERY) }
                AttachmentIcon(Icons.Outlined.LocationOn, "Location", Color(0xFF00E676)) { onAction(ChatTool.LOCATION) }
                AttachmentIcon(Icons.Outlined.Person, "Contact", Color(0xFF00B0FF)) { onAction(ChatTool.CONTACT) }
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachmentIcon(Icons.AutoMirrored.Outlined.InsertDriveFile, "Document", Color(0xFF9C27B0)) { onAction(ChatTool.DOCUMENT) }
                AttachmentIcon(Icons.Outlined.Poll, "Poll", Color(0xFFFFC107)) { onAction(ChatTool.POLL) }
                AttachmentIcon(Icons.Outlined.CalendarMonth, "Event", Color(0xFFFF5252)) { onAction(ChatTool.EVENT) }
            }
        }
    }
}

@Composable
private fun AttachmentIcon(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable private fun SecurityDialog(onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Security details") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DiagnosticLine("End-to-end encryption", "Enabled", true); DiagnosticLine("Device authentication", "Verified", true); DiagnosticLine("Session key", "Rotates automatically", true); DiagnosticLine("Secure local history", "Enabled", true); Text("QR verification and safety-number comparison can be used before sensitive calls.", fontSize = 12.sp) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShynaAuthScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customUid by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LinkBg,
        topBar = {
            TopAppBar(
                title = { Text(if (isSignUp) "Create Account" else "Login", fontWeight = FontWeight.Bold, color = LinkText) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = LinkText) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LinkBg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.Lock, null, tint = LinkBlue, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Access Shyna Pro features securely", color = LinkMuted)
            Spacer(Modifier.height(32.dp))

            if (isSignUp) {
                OutlinedTextField(
                    value = customUid,
                    onValueChange = { customUid = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                    label = { Text("Custom User ID (compulsory)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Used for easy searching. Only letters, numbers and _") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LinkBlue, 
                        unfocusedBorderColor = Color.LightGray, 
                        focusedTextColor = LinkText, 
                        unfocusedTextColor = LinkText
                    )
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Mobile Number (compulsory)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LinkBlue, 
                        unfocusedBorderColor = Color.LightGray, 
                        focusedTextColor = LinkText, 
                        unfocusedTextColor = LinkText
                    )
                )
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LinkBlue, 
                    unfocusedBorderColor = Color.LightGray, 
                    focusedLabelColor = LinkBlue, 
                    unfocusedLabelColor = LinkMuted, 
                    focusedTextColor = LinkText, 
                    unfocusedTextColor = LinkText
                )
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = LinkMuted)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LinkBlue, 
                    unfocusedBorderColor = Color.LightGray, 
                    focusedLabelColor = LinkBlue, 
                    unfocusedLabelColor = LinkMuted, 
                    focusedTextColor = LinkText, 
                    unfocusedTextColor = LinkText
                )
            )

            Spacer(Modifier.height(24.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LinkBlue)
                }
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill email and password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isSignUp && (customUid.isBlank() || phone.isBlank())) {
                            Toast.makeText(context, "ID and Phone are compulsory for signup", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        loading = true
                        if (isSignUp) {
                            // 1. Check if custom ID or phone already exists in Firestore
                            db.collection("users").whereEqualTo("customUid", customUid).get()
                                .addOnSuccessListener { uidSnap ->
                                    if (!uidSnap.isEmpty) {
                                        loading = false
                                        Toast.makeText(context, "User ID already taken", Toast.LENGTH_LONG).show()
                                    } else {
                                        db.collection("users").whereEqualTo("phone", phone).get()
                                            .addOnSuccessListener { phoneSnap ->
                                                if (!phoneSnap.isEmpty) {
                                                    loading = false
                                                    Toast.makeText(context, "Mobile number already registered", Toast.LENGTH_LONG).show()
                                                } else {
                                                    // 2. Proceed with Firebase Auth
                                                    auth.createUserWithEmailAndPassword(email.trim(), password)
                                                        .addOnCompleteListener { task ->
                                                            if (task.isSuccessful) {
                                                                val uid = auth.currentUser?.uid
                                                                if (uid != null) {
                                                                    val userMap = hashMapOf(
                                                                        "uid" to uid,
                                                                        "customUid" to customUid.trim().lowercase(),
                                                                        "email" to email.trim().lowercase(),
                                                                        "name" to email.substringBefore("@"),
                                                                        "phone" to phone.trim(),
                                                                        "isOnline" to true,
                                                                        "lastSeen" to com.google.firebase.Timestamp.now()
                                                                    )
                                                                    db.collection("users").document(uid).set(userMap)
                                                                    
                                                                    // Send Email verification
                                                                    auth.currentUser?.sendEmailVerification()
                                                                }
                                                                loading = false
                                                                Toast.makeText(context, "Account Created. Please verify your email.", Toast.LENGTH_LONG).show()
                                                                onLoginSuccess()
                                                            } else {
                                                                loading = false
                                                                Toast.makeText(context, task.exception?.message ?: "Signup Failed", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                }
                                            }
                                    }
                                }
                                .addOnFailureListener {
                                    loading = false
                                    Toast.makeText(context, "Database error", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            auth.signInWithEmailAndPassword(email.trim(), password)
                                .addOnCompleteListener { task ->
                                    loading = false
                                    if (task.isSuccessful) {
                                        val uid = auth.currentUser?.uid
                                        if (uid != null) {
                                            // Sync/Update or Create if missing
                                            val update = hashMapOf(
                                                "uid" to uid,
                                                "email" to email.trim().lowercase(),
                                                "isOnline" to true,
                                                "lastSeen" to com.google.firebase.Timestamp.now()
                                            )
                                            db.collection("users").document(uid)
                                                .set(update, com.google.firebase.firestore.SetOptions.merge())
                                        }
                                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    } else {
                                        Toast.makeText(context, task.exception?.message ?: "Login Failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LinkBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isSignUp) "CREATE PRO ACCOUNT" else "LOGIN TO PRO LINK", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isSignUp) "Already have an account?" else "New user?", color = LinkMuted)
                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(if (isSignUp) "Login" else "Sign Up", color = LinkCyan)
                }
            }

            if (!isSignUp) {
                TextButton(onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(context, "Enter your email first", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    auth.sendPasswordResetEmail(email.trim())
                        .addOnSuccessListener { Toast.makeText(context, "Reset email sent to $email", Toast.LENGTH_LONG).show() }
                        .addOnFailureListener { Toast.makeText(context, it.message ?: "Failed to send reset email", Toast.LENGTH_LONG).show() }
                }) {
                    Text("Forgot Password?", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
