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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ShynaDiscovery"
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
    val email: String, 
    val phone: String = "", 
    val normalizedPhone: String = "",
    val normalizedEmail: String = "",
    val isOnline: Boolean = false,
    val customUid: String = "",
    val photoUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var firebaseUid by remember { mutableStateOf(auth.currentUser?.uid) }
    
    // ENSURE PROFILE SYNC (Deep Fix: Source of Truth)
    LaunchedEffect(firebaseUid) {
        if (firebaseUid != null) {
            val userRef = db.collection("users").document(firebaseUid!!)
            val user = auth.currentUser
            val currentEmail = user?.email ?: ""
            val normalizedEmail = currentEmail.trim().lowercase()
            
            userRef.get().addOnSuccessListener { doc ->
                val existingName = doc.getString("name") ?: doc.getString("displayName")
                val safeName = if (existingName == null || existingName == "null" || existingName.isBlank()) {
                    user?.displayName ?: currentEmail.substringBefore("@")
                } else existingName

                val existingPhone = doc.getString("phone") ?: ""
                val normalizedPhone = existingPhone.replace(Regex("[^0-9+]"), "")

                val syncData = hashMapOf(
                    "uid" to firebaseUid,
                    "email" to currentEmail,
                    "normalizedEmail" to normalizedEmail,
                    "name" to safeName,
                    "displayName" to safeName,
                    "phone" to existingPhone,
                    "normalizedPhone" to normalizedPhone,
                    "isOnline" to true,
                    "lastSeen" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
                userRef.set(syncData, SetOptions.merge())
            }
        }
    }

    // FETCH ALL REGISTERED USERS (Directory Mode for Discovery)
    var allRealUsers by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(false) }

    DisposableEffect(firebaseUid) {
        if (firebaseUid != null) {
            isLoadingUsers = true
            Log.d(TAG, "Fetching users from Firestore. Project: ${db.app.options.projectId}")
            val listener = db.collection("users")
                .limit(200) 
                .addSnapshotListener { snapshots, error ->
                    isLoadingUsers = false
                    if (error != null) {
                        Log.e(TAG, "Firestore error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    val users = snapshots?.documents?.mapNotNull { doc ->
                        val uid = doc.id
                        val email = doc.getString("email") ?: ""
                        val normEmail = doc.getString("normalizedEmail") ?: email.trim().lowercase()
                        val name = doc.getString("name") ?: doc.getString("displayName") ?: email.substringBefore("@")
                        val phone = doc.getString("phone") ?: ""
                        val normPhone = doc.getString("normalizedPhone") ?: phone.replace(Regex("[^0-9+]"), "")
                        val cUid = doc.getString("customUid") ?: ""
                        val online = doc.getBoolean("isOnline") ?: false
                        val photoUrl = doc.getString("photoUrl")
                        
                        RealUser(uid, name, email, phone, normPhone, normEmail, online, cUid, photoUrl)
                    } ?: emptyList()
                    
                    Log.d(TAG, "Loaded ${users.size} users from Firestore")
                    allRealUsers = users
                }
            
            onDispose {
                db.collection("users").document(firebaseUid!!).update("isOnline", false, "lastSeen", Timestamp.now())
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
                        onOpenMedia = { fullScreenMedia = it }
                    )
                    LinkTab.YOU -> YouPage(
                        currentUser = allRealUsers.find { it.uid == firebaseUid } ?: RealUser(firebaseUid ?: "", auth.currentUser?.displayName ?: "Me", auth.currentUser?.email ?: "", "", "", "", true, ""),
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
    onOpenMedia: (LocalChatMessage) -> Unit = {}
) {
    val displayList = remember(messages.size, search, allRealUsers) {
        val rawQuery = search.trim()
        val query = rawQuery.lowercase().replace(" ", "")
        
        val items = allRealUsers.map { user ->
            val lastMsg = messages.filter { it.peerName == user.uid }.maxByOrNull { it.time }
            
            // ROBUST MATCHING: Name, Email, Phone, CustomID, UID
            val nameClean = user.name.lowercase().replace(" ", "")
            val emailClean = user.normalizedEmail
            val phoneClean = user.normalizedPhone
            val idClean = user.customUid.lowercase()
            
            val match = query.isEmpty() || 
                        nameClean.contains(query) || 
                        emailClean.contains(query) || 
                        phoneClean.contains(query) || 
                        idClean.contains(query) ||
                        user.uid.lowercase() == query.lowercase()

            ChatRowItem(
                id = user.uid,
                name = user.name,
                lastMessage = lastMsg,
                isOnline = user.isOnline,
                matchSearch = match,
                subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email
            )
        }

        if (rawQuery.isEmpty()) {
            // Home: Only show people you have already chatted with
            items.filter { it.lastMessage != null }.sortedByDescending { it.lastMessage?.time ?: 0L }
        } else {
            // Search: Show all matching users including self
            items.filter { it.matchSearch }.sortedByDescending { it.lastMessage?.time ?: 0L }
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
                    onClick = { onOpenChat(item.id) }
                )
            }
            if (displayList.isEmpty() && allRealUsers.isNotEmpty()) {
                item { ListHeader("Suggested for you") }
                items(allRealUsers.take(10)) { user ->
                    ShynaContactRow(
                        name = user.name,
                        subtitle = if (user.customUid.isNotEmpty()) "@${user.customUid}" else user.email,
                        preview = if (user.isOnline) "Active now" else "Start a new chat",
                        icon = Icons.Outlined.Person,
                        date = "",
                        online = user.isOnline,
                        onClick = { onOpenChat(user.uid) }
                    )
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
private fun ShynaContactRow(name: String, subtitle: String = "", preview: String, icon: ImageVector, date: String, online: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Surface(shape = CircleShape, color = Color(0xFFE1E4E7), modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
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

private fun loadMessages(prefs: android.content.SharedPreferences): List<LocalChatMessage> = emptyList()
private fun saveMessages(prefs: android.content.SharedPreferences, messages: List<LocalChatMessage>) {}

@Composable private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) { DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick) }

private data class ChatRowItem(val id: String, val name: String, val lastMessage: LocalChatMessage?, val isOnline: Boolean, val matchSearch: Boolean, val subtitle: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShynaAuthScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customUid by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

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

                    if (isSignUp) {
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
                    }

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

                    Spacer(Modifier.height(30.dp))

                    if (loading) {
                        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LinkCyan, strokeWidth = 3.dp)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                loading = true
                                if (isSignUp) {
                                    auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = auth.currentUser
                                            val uid = user?.uid ?: return@addOnCompleteListener
                                            val normEmail = email.trim().lowercase()
                                            val normPhone = phone.trim().replace(Regex("[^0-9+]"), "")
                                            val userMap = hashMapOf(
                                                "uid" to uid, 
                                                "customUid" to customUid.trim().lowercase(), 
                                                "email" to email.trim(), 
                                                "normalizedEmail" to normEmail,
                                                "name" to email.substringBefore("@"), 
                                                "displayName" to email.substringBefore("@"),
                                                "phone" to phone.trim(), 
                                                "normalizedPhone" to normPhone,
                                                "isOnline" to true, 
                                                "createdAt" to Timestamp.now(),
                                                "updatedAt" to Timestamp.now()
                                            )
                                            db.collection("users").document(uid).set(userMap, SetOptions.merge()).addOnSuccessListener { 
                                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
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
                            Text(
                                if (isSignUp) "GET STARTED" else "LOG IN", 
                                fontWeight = FontWeight.ExtraBold, 
                                color = Color.White, 
                                letterSpacing = 2.sp,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    // Premium Social and Toggle Row
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(
                            onClick = { 
                                Toast.makeText(context, "Google integration available soon", Toast.LENGTH_SHORT).show()
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
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onVisibilityChange: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
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

    var showImagePicker by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            showImagePicker = true
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
        Spacer(Modifier.height(24.dp))
        
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
        Text(currentUser.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = LinkText)
        Text(if (currentUser.customUid.isNotEmpty()) "@${currentUser.customUid}" else "No User ID set", color = LinkMuted, fontSize = 16.sp)

        Spacer(Modifier.height(40.dp))

        ProfileInfoCard(label = "User ID", value = currentUser.customUid.ifEmpty { "Not set" }, icon = Icons.Outlined.AccountCircle)
        ProfileInfoCard(label = "Email Address", value = currentUser.email, icon = Icons.Outlined.AlternateEmail)
        ProfileInfoCard(label = "Mobile Number", value = currentUser.phone.ifEmpty { "Not linked" }, icon = Icons.Outlined.Phone)
        ProfileInfoCard(label = "App Version", value = "v$appVersion Premium", icon = Icons.Outlined.Verified)

        Spacer(Modifier.height(48.dp))

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
        Spacer(Modifier.height(32.dp))
    }
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
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offset += dragAmount
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    rotationZ = rotation,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                        
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 1.dp.toPx()
                            drawCircle(Color.White.copy(0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
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
                        
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 1f..4f,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(thumbColor = LinkCyan, activeTrackColor = LinkCyan)
                        )
                        
                        IconButton(
                            onClick = { rotation += 90f },
                            modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)
                        ) { Icon(Icons.AutoMirrored.Outlined.RotateRight, "Rotate Right", tint = Color.White) }
                    }
                    Text("Pinch to zoom • Drag to move", color = Color.White.copy(0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
                
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White) }
                        Button(
                            onClick = {
                                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
                                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        decoder.isMutableRequired = true
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                                }
                                onConfirm(bitmap)
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
@Composable private fun SmartChatDetailScreen(peerId: String, prefs: android.content.SharedPreferences, userId: String, allMessages: List<LocalChatMessage>, allRealUsers: List<RealUser>, onBack: () -> Unit, onOpenMedia: (LocalChatMessage) -> Unit) {
    val peer = allRealUsers.find { it.uid == peerId }
    val peerName = peer?.name ?: "Shyna User"
    var text by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(peerName, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (peer?.isOnline == true) "Online" else "Offline", fontSize = 12.sp, color = if (peer?.isOnline == true) LinkGreen else Color.Gray) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF006B5E), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = Color.White, modifier = Modifier.imePadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message") }, shape = CircleShape)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { if (text.isNotBlank()) { /* send logic */ text = "" } }, colors = IconButtonDefaults.iconButtonColors(containerColor = LinkChipSelectedText, contentColor = Color.White)) { Icon(Icons.AutoMirrored.Outlined.Send, null) }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFEFEAE2))) {
            Text("Chat with $peerName coming soon in this UI build.", Modifier.align(Alignment.Center), color = LinkMuted)
        }
    }
}

@Composable private fun FullScreenMediaViewer(media: LocalChatMessage, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        AsyncImage(model = media.metadata, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MessageInfoScreen(message: LocalChatMessage, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Message Info") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) { Text("Message: ${message.text}"); Text("Sent: ${formatDate(message.time)}") }
    }
}
