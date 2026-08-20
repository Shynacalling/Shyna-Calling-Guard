package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.callruleblocker.AppCallActivity
import com.example.callruleblocker.call.*
import com.example.callruleblocker.data.AudioRecorder
import com.example.callruleblocker.data.SessionManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ShynaDiscovery"
private const val COMM_PREFS = "smart_communication_premium_v2"
enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }
private enum class MessageType { TEXT, LOCATION, FILE, VOICE, IMAGE, VIDEO, CONTACT }
private data class RealUser(val uid: String, val name: String, val email: String, val phone: String = "", val isOnline: Boolean = false, val lastSeen: Long? = null, val photoUrl: String? = null)
private data class LocalChatMessage(val id: String = UUID.randomUUID().toString(), val text: String, val mine: Boolean, val time: Long, val peerName: String = "", val type: MessageType = MessageType.TEXT, val metadata: String? = null, val senderId: String = "")

// --- PREMIUM UNIVERSAL THEME SYSTEM ---
data class ShynaColors(
    val PrimaryBg: Color, val SurfaceBg: Color, val HeaderBg: Color,
    val IncomingBubble: Color, val OutgoingBubble: Color,
    val TextPrimary: Color, val TextSecondary: Color,
    val BrandGreen: Color, val DividerColor: Color,
    val SelectionOverlay: Color, val isDark: Boolean
)

val ShynaDarkPalette = ShynaColors(
    PrimaryBg = Color(0xFF0F171E), SurfaceBg = Color(0xFF1B2730), HeaderBg = Color(0xFF1B2730),
    IncomingBubble = Color(0xFF1B2730), OutgoingBubble = Color(0xFF005C4B),
    TextPrimary = Color(0xFFE9EDEF), TextSecondary = Color(0xFF8696A0),
    BrandGreen = Color(0xFF00A884), DividerColor = Color(0xFF222D34),
    SelectionOverlay = Color(0xFF00A884).copy(alpha = 0.2f), isDark = true
)

val ShynaLightPalette = ShynaColors(
    PrimaryBg = Color(0xFFFFFFFF), SurfaceBg = Color(0xFFF0F2F5), HeaderBg = Color(0xFFFFFFFF),
    IncomingBubble = Color(0xFFFFFFFF), OutgoingBubble = Color(0xFFE7FFDB),
    TextPrimary = Color(0xFF111B21), TextSecondary = Color(0xFF667781),
    BrandGreen = Color(0xFF008069), DividerColor = Color(0xFFE9EDEF),
    SelectionOverlay = Color(0xFF008069).copy(alpha = 0.1f), isDark = false
)

val LocalShynaColors = staticCompositionLocalOf { ShynaDarkPalette }

@Composable
fun ShynaTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val darkTheme = if (mode == ThemeMode.SYSTEM) isSystemInDarkTheme() else mode == ThemeMode.DARK
    val colors = if (darkTheme) ShynaDarkPalette else ShynaLightPalette
    CompositionLocalProvider(LocalShynaColors provides colors) { content() }
}

object ShynaDesign {
    val colors: ShynaColors @Composable get() = LocalShynaColors.current
    @Composable fun premiumGradient() = Brush.verticalGradient(listOf(colors.HeaderBg, colors.PrimaryBg))
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    var themeMode by remember { 
        mutableStateOf(
            try { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) } 
            catch(e: Exception) { ThemeMode.DARK }
        ) 
    }

    ShynaTheme(mode = themeMode) {
        SmartCommunicationContent(
            onBack = onBack,
            themeMode = themeMode,
            onThemeChange = { themeMode = it; prefs.edit().putString("theme_mode", it.name).apply() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmartCommunicationContent(onBack: () -> Unit, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var currentUid by remember { mutableStateOf(auth.currentUser?.uid) }
    
    var allUsers by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(LinkTab.CHATS) }
    var search by remember { mutableStateOf("") }
    var archivedOpen by remember { mutableStateOf(false) }
    var showCameraByChatId by remember { mutableStateOf<String?>(null) }
    var showLocationByChatId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(currentUid) {
        val uid = currentUid ?: return@DisposableEffect onDispose {}
        db.collection("users").document(uid).update("isOnline", true, "lastSeen", FieldValue.serverTimestamp())
        val listener = db.collection("users").addSnapshotListener { s, _ ->
            allUsers = s?.documents?.mapNotNull { d ->
                RealUser(d.id, d.getString("name") ?: "", d.getString("email") ?: "", isOnline = d.getBoolean("isOnline") ?: false, lastSeen = d.getTimestamp("lastSeen")?.toDate()?.time, photoUrl = d.getString("photoUrl"))
            } ?: emptyList()
        }
        onDispose {
            db.collection("users").document(uid).update("isOnline", false, "lastSeen", FieldValue.serverTimestamp())
            listener.remove()
        }
    }

    BackHandler(selectedPeerId != null || archivedOpen || showCameraByChatId != null || showLocationByChatId != null) { 
        if (showCameraByChatId != null) showCameraByChatId = null
        else if (showLocationByChatId != null) showLocationByChatId = null
        else if (archivedOpen) archivedOpen = false 
        else selectedPeerId = null 
    }

    Box(Modifier.fillMaxSize()) {
        if (showCameraByChatId != null) {
            val targetId = showCameraByChatId!!
            ShynaCameraScreen(
                onBack = { showCameraByChatId = null },
                onMediaCaptured = { uri, isVideo ->
                    val type = if (isVideo) MessageType.VIDEO else MessageType.IMAGE
                    val label = if (isVideo) "📹 Video" else "📷 Photo"
                    val msg = mapOf("text" to label, "senderId" to currentUid, "timestamp" to Timestamp.now(), "type" to type.name, "metadata" to uri.toString())
                    db.collection("chats").document(targetId).collection("messages").add(msg)
                    db.collection("chats").document(targetId).set(mapOf("lastMessage" to label, "timestamp" to Timestamp.now()), SetOptions.merge())
                    showCameraByChatId = null
                }
            )
        } else if (showLocationByChatId != null) {
            val targetId = showLocationByChatId!!
            SendLocationScreen(
                onBack = { showLocationByChatId = null },
                onSendLocation = { loc ->
                    val msg = mapOf("text" to "📍 Location", "senderId" to currentUid, "timestamp" to Timestamp.now(), "type" to MessageType.LOCATION.name, "metadata" to loc)
                    db.collection("chats").document(targetId).collection("messages").add(msg)
                    db.collection("chats").document(targetId).set(mapOf("lastMessage" to "📍 Location", "timestamp" to Timestamp.now()), SetOptions.merge())
                    showLocationByChatId = null
                }
            )
        } else if (selectedPeerId != null) {
            SmartChatDetailScreen(
                peerId = selectedPeerId!!, 
                userId = currentUid!!, 
                allUsers = allUsers, 
                onBack = { selectedPeerId = null },
                onOpenCamera = { showCameraByChatId = it },
                onOpenLocation = { showLocationByChatId = it }
            )
        } else {
            Scaffold(
                containerColor = ShynaDesign.colors.PrimaryBg,
                topBar = {
                    Column(Modifier.background(ShynaDesign.colors.HeaderBg).shadow(4.dp)) {
                        TopAppBar(
                            title = { Text(if (archivedOpen) "Archived" else "Shyna Premium", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
                            navigationIcon = { if (archivedOpen) IconButton(onClick = { archivedOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                            actions = {
                                if (!archivedOpen) {
                                    IconButton(onClick = { archivedOpen = true }) { Icon(Icons.Outlined.Archive, null, tint = ShynaDesign.colors.TextPrimary) }
                                }
                                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        if (!archivedOpen) PremiumSearchBar(search, { search = it })
                    }
                },
                bottomBar = { PremiumBottomBar(selectedTab, { selectedTab = it }) }
            ) { p ->
                Box(Modifier.padding(p).fillMaxSize()) {
                    when (selectedTab) {
                        LinkTab.CHATS -> ChatsList(allUsers, currentUid!!, search, onOpen = { selectedPeerId = it })
                        LinkTab.UPDATES -> UpdatesPage(allUsers.find { it.uid == currentUid })
                        LinkTab.COMMUNITIES -> CommunitiesPage()
                        LinkTab.CALLS -> CallsPage()
                        LinkTab.YOU -> YouPage(allUsers.find { it.uid == currentUid }, themeMode, onThemeChange, { auth.signOut(); onBack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(16.dp, 8.dp), shape = RoundedCornerShape(24.dp), color = ShynaDesign.colors.DividerColor) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = ShynaDesign.colors.TextSecondary)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp),
                decorationBox = { if (query.isEmpty()) Text("Ask Shyna Search...", color = ShynaDesign.colors.TextSecondary); it() }
            )
        }
    }
}

@Composable
private fun PremiumBottomBar(selected: LinkTab, onSelect: (LinkTab) -> Unit) {
    NavigationBar(containerColor = ShynaDesign.colors.HeaderBg) {
        LinkTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(getTabIcon(tab, selected == tab), tab.name) },
                label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ShynaDesign.colors.BrandGreen, selectedTextColor = ShynaDesign.colors.BrandGreen, unselectedIconColor = ShynaDesign.colors.TextSecondary, unselectedTextColor = ShynaDesign.colors.TextSecondary, indicatorColor = ShynaDesign.colors.BrandGreen.copy(0.1f))
            )
        }
    }
}

private fun getTabIcon(tab: LinkTab, selected: Boolean) = when(tab) {
    LinkTab.CHATS -> if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
    LinkTab.UPDATES -> if (selected) Icons.Filled.DonutLarge else Icons.Outlined.DonutLarge
    LinkTab.COMMUNITIES -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
    LinkTab.CALLS -> if (selected) Icons.Filled.Call else Icons.Outlined.Call
    LinkTab.YOU -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, 
    userId: String, 
    allUsers: List<RealUser>, 
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenLocation: (String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val peer = allUsers.find { it.uid == peerId }
    val chatId = if (userId < peerId) "${userId}_${peerId}" else "${peerId}_${userId}"
    var text by remember { mutableStateOf("") }
    val msgs = remember { mutableStateListOf<LocalChatMessage>() }
    val listState = rememberLazyListState()
    val selectedMsgs = remember { mutableStateListOf<String>() }
    val isSelectionMode by remember { derivedStateOf { selectedMsgs.isNotEmpty() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var fullScreenMedia by remember { mutableStateOf<LocalChatMessage?>(null) }

    // Media Launchers
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val isVideo = context.contentResolver.getType(it)?.startsWith("video") == true
            val type = if (isVideo) MessageType.VIDEO else MessageType.IMAGE
            val label = if (isVideo) "📹 Video" else "📷 Photo"
            val msg = mapOf("text" to label, "senderId" to userId, "timestamp" to Timestamp.now(), "type" to type.name, "metadata" to it.toString())
            db.collection("chats").document(chatId).collection("messages").add(msg)
            db.collection("chats").document(chatId).set(mapOf("lastMessage" to label, "timestamp" to Timestamp.now()), SetOptions.merge())
        }
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val name = getFileName(context, it) ?: "Document"
            val msg = mapOf("text" to "📄 $name", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.FILE.name, "metadata" to it.toString())
            db.collection("chats").document(chatId).collection("messages").add(msg)
        }
    }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            val name = getContactName(context, it) ?: "Contact"
            val msg = mapOf("text" to "👤 $name", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.CONTACT.name, "metadata" to it.toString())
            db.collection("chats").document(chatId).collection("messages").add(msg)
        }
    }

    DisposableEffect(chatId) {
        val l = db.collection("chats").document(chatId).collection("messages").orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING).addSnapshotListener { s, _ ->
            msgs.clear()
            s?.documents?.forEach { d ->
                val typeStr = d.getString("type") ?: "TEXT"
                val mType = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT }
                msgs.add(LocalChatMessage(d.id, d.getString("text") ?: "", d.getString("senderId") == userId, d.getTimestamp("timestamp")?.toDate()?.time ?: 0L, type = mType, metadata = d.getString("metadata")))
            }
        }
        onDispose { l.remove() }
    }

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    if (fullScreenMedia != null) {
        FullScreenMediaViewer(media = fullScreenMedia!!, onDismiss = { fullScreenMedia = null })
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedMsgs.size}", color = ShynaDesign.colors.TextPrimary) },
                    navigationIcon = { IconButton(onClick = { selectedMsgs.clear() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Reply, null, tint = ShynaDesign.colors.TextPrimary) }
                        IconButton(onClick = {
                            selectedMsgs.forEach { id -> db.collection("chats").document(chatId).collection("messages").document(id).delete() }
                            selectedMsgs.clear()
                        }) { Icon(Icons.Default.Delete, null, tint = ShynaDesign.colors.TextPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                if (!peer?.photoUrl.isNullOrBlank()) AsyncImage(peer?.photoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(peer?.name ?: "Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                                val statusText = if (peer?.isOnline == true) "online" else formatLastSeen(peer?.lastSeen)
                                Text(statusText, fontSize = 12.sp, color = if(peer?.isOnline == true) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    actions = {
                        IconButton(onClick = { peer?.let { p -> CallSignalingManager.startCall(context, userId, "User", null, p.uid, p.name, p.photoUrl, AppCallType.VIDEO, { created -> context.startActivity(Intent(context, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, {}) } }) { Icon(Icons.Default.Videocam, null, tint = ShynaDesign.colors.TextPrimary) }
                        IconButton(onClick = { peer?.let { p -> CallSignalingManager.startCall(context, userId, "User", null, p.uid, p.name, p.photoUrl, AppCallType.VOICE, { created -> context.startActivity(Intent(context, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, {}) } }) { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.TextPrimary) }
                        IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            }
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
            Box(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                showEmojis = false
                showAttachments = false
                selectedMsgs.clear()
            }) {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp)) {
                    items(msgs) { m ->
                        PremiumMessageBubble(m, isSelected = selectedMsgs.contains(m.id), 
                            onLongClick = { if (selectedMsgs.contains(m.id)) selectedMsgs.remove(m.id) else selectedMsgs.add(m.id) },
                            onMediaClick = { fullScreenMedia = it }
                        )
                    }
                }
            }

            if (showAttachments) {
                AttachmentPanel(
                    onDismiss = { showAttachments = false },
                    onMediaClick = { type ->
                        showAttachments = false
                        when (type) {
                            "CAMERA" -> onOpenCamera(chatId)
                            "GALLERY" -> galleryLauncher.launch("image/* video/*")
                            "DOC" -> docLauncher.launch("*/*")
                            "CONTACT" -> contactLauncher.launch(null)
                            "LOCATION" -> onOpenLocation(chatId)
                            else -> Toast.makeText(context, "$type Feature Active", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            PremiumChatComposer(
                text = text, onTextChange = { text = it },
                onSend = {
                    if (text.isNotBlank()) {
                        val msg = mapOf("text" to text, "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.TEXT.name)
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf("lastMessage" to text, "timestamp" to Timestamp.now(), "user1" to (if (userId < peerId) userId else peerId), "user2" to (if (userId < peerId) peerId else userId)), SetOptions.merge())
                        text = ""
                        showEmojis = false
                    }
                },
                onVoiceComplete = { file ->
                    val msg = mapOf("text" to "🎤 Voice Note", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.VOICE.name, "metadata" to Uri.fromFile(file).toString())
                    db.collection("chats").document(chatId).collection("messages").add(msg)
                },
                onAttachClick = { 
                    showEmojis = false
                    showAttachments = !showAttachments 
                },
                onEmojiClick = { 
                    showAttachments = false
                    showEmojis = !showEmojis 
                },
                onCameraClick = {
                    showEmojis = false
                    showAttachments = false
                    onOpenCamera(chatId)
                },
                isEmojiVisible = showEmojis
            )

            if (showEmojis) {
                EmojiPicker(onEmojiSelected = { text += it })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumMessageBubble(
    m: LocalChatMessage, 
    isSelected: Boolean, 
    onLongClick: () -> Unit,
    onMediaClick: (LocalChatMessage) -> Unit
) {
    val align = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (isSelected) ShynaDesign.colors.SelectionOverlay else Color.Transparent
    val timeStr = remember(m.time) { 
        try { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.time)) }
        catch(e: Exception) { "" }
    }
    
    Box(Modifier.fillMaxWidth().background(color).combinedClickable(onLongClick = onLongClick, onClick = {}).padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = align) {
        Surface(
            color = if (m.mine) ShynaDesign.colors.OutgoingBubble else ShynaDesign.colors.IncomingBubble,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (m.mine) 16.dp else 2.dp, bottomEnd = if (m.mine) 2.dp else 16.dp),
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(10.dp)) {
                when (m.type) {
                    MessageType.IMAGE -> {
                        AsyncImage(
                            model = m.metadata,
                            contentDescription = null,
                            modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).clickable { onMediaClick(m) },
                            contentScale = ContentScale.Crop
                        )
                    }
                    MessageType.VIDEO -> {
                        Box(Modifier.size(200.dp).background(Color.Black, RoundedCornerShape(8.dp)).clickable { onMediaClick(m) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                    MessageType.VOICE -> {
                        VoiceWavePlayer(m.metadata)
                    }
                    MessageType.FILE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = ShynaDesign.colors.TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(m.text, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                    else -> {
                        Text(m.text, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
                    }
                }
                Text(
                    timeStr,
                    fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary, modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceWavePlayer(metadata: String?) {
    var isPlaying by remember { mutableStateOf(false) }
    val waveColor = ShynaDesign.colors.BrandGreen

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(200.dp)) {
        IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(40.dp).background(ShynaDesign.colors.BrandGreen, CircleShape)) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.weight(1f).height(30.dp)) {
            val bars = 25
            val spacing = size.width / bars
            for (i in 0 until bars) {
                val h = if (isPlaying) size.height * (0.2f + (Math.random().toFloat() * 0.8f)) else size.height * 0.3f
                drawLine(waveColor, Offset(i * spacing, size.height / 2 - h / 2), Offset(i * spacing, size.height / 2 + h / 2), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PremiumChatComposer(
    text: String, 
    onTextChange: (String) -> Unit, 
    onSend: () -> Unit, 
    onVoiceComplete: (File) -> Unit,
    onAttachClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onCameraClick: () -> Unit,
    isEmojiVisible: Boolean
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                amplitudes.add((recorder.getAmplitude() / 32767f).coerceIn(0.1f, 1f))
                if (amplitudes.size > 50) amplitudes.removeAt(0)
                delay(100)
            }
        } else amplitudes.clear()
    }

    Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Surface(Modifier.weight(1f), shape = RoundedCornerShape(28.dp), color = ShynaDesign.colors.HeaderBg) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEmojiClick) {
                    Icon(if (isEmojiVisible) Icons.Default.Keyboard else Icons.Default.EmojiEmotions, null, tint = ShynaDesign.colors.TextSecondary)
                }
                if (isRecording) {
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(40.dp)) {
                        RecordingWaveform(amplitudes)
                    }
                } else {
                    BasicTextField(
                        value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f).padding(8.dp),
                        textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 17.sp),
                        decorationBox = { if (text.isEmpty()) Text("Message", color = ShynaDesign.colors.TextSecondary); it() }
                    )
                }
                IconButton(onClick = onAttachClick) {
                    Icon(Icons.Filled.AttachFile, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.graphicsLayer(rotationZ = -45f))
                }
                if (text.isEmpty() && !isRecording) {
                    IconButton(onClick = onCameraClick) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = ShynaDesign.colors.TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(50.dp).clip(CircleShape).background(ShynaDesign.colors.BrandGreen).pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (text.isEmpty()) {
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.mp4")
                            recorder.start(file)
                            isRecording = true
                            try { awaitRelease(); recorder.stop(); onVoiceComplete(file) } finally { isRecording = false }
                        } else onSend()
                    }
                )
            }, contentAlignment = Alignment.Center
        ) {
            Icon(if (text.isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, null, tint = Color.White)
        }
    }
}

@Composable
private fun RecordingWaveform(amplitudes: List<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val spacing = 6f
        val centerY = size.height / 2
        amplitudes.forEachIndexed { i, amp ->
            val x = size.width - (amplitudes.size - i) * spacing
            val h = amp * size.height
            if (x > 0) drawLine(Color.Red, Offset(x, centerY - h / 2), Offset(x, centerY + h / 2), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AttachmentPanel(onDismiss: () -> Unit, onMediaClick: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(24.dp), color = ShynaDesign.colors.HeaderBg, shadowElevation = 8.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Document", Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF7F66FF)) { onMediaClick("DOC") }
                AttachmentItem("Camera", Icons.Filled.PhotoCamera, Color(0xFFFF2E74)) { onMediaClick("CAMERA") }
                AttachmentItem("Gallery", Icons.Filled.Image, Color(0xFFC059FF)) { onMediaClick("GALLERY") }
                AttachmentItem("Audio", Icons.Filled.Headphones, Color(0xFFFF8E2D)) { onMediaClick("AUDIO") }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Location", Icons.Filled.LocationOn, Color(0xFF00C659)) { onMediaClick("LOCATION") }
                AttachmentItem("Contact", Icons.Filled.Person, Color(0xFF00A5F4)) { onMediaClick("CONTACT") }
                AttachmentItem("Poll", Icons.Filled.BarChart, Color(0xFFFFBC38)) { onMediaClick("POLL") }
                AttachmentItem("Event", Icons.Filled.Event, Color(0xFF00D1B2)) { onMediaClick("EVENT") }
            }
        }
    }
}

@Composable
private fun AttachmentItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(Modifier.size(54.dp), shape = CircleShape, color = color.copy(0.1f)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
    }
}

@Composable
private fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖")
    Surface(Modifier.fillMaxWidth().height(250.dp), color = ShynaDesign.colors.HeaderBg) {
        LazyVerticalGrid(columns = GridCells.Adaptive(45.dp), contentPadding = PaddingValues(8.dp)) {
            items(emojis) { e -> 
                Box(Modifier.size(45.dp).clickable { onEmojiSelected(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun FullScreenMediaViewer(media: LocalChatMessage, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (media.type == MessageType.VIDEO) {
            AndroidView(factory = { context -> VideoView(context).apply { setVideoURI(Uri.parse(media.metadata)); val mc = MediaController(context); mc.setAnchorView(this); setMediaController(mc); start() } }, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(model = media.metadata, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
    }
}

@Composable
private fun ChatsList(users: List<RealUser>, currentUid: String, query: String, onOpen: (String) -> Unit) {
    val filtered = users.filter { it.uid != currentUid && (query.isEmpty() || it.name.contains(query, true)) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(filtered) { u ->
            ListItem(
                headlineContent = { Text(u.name, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
                supportingContent = { Text("Tap to chat", color = ShynaDesign.colors.TextSecondary) },
                leadingContent = {
                    Surface(Modifier.size(52.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                        if (!u.photoUrl.isNullOrBlank()) AsyncImage(u.photoUrl, null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(12.dp), tint = ShynaDesign.colors.TextSecondary)
                    }
                },
                trailingContent = { if (u.isOnline) Box(Modifier.size(10.dp).background(ShynaDesign.colors.BrandGreen, CircleShape)) },
                modifier = Modifier.clickable { onOpen(u.uid) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun UpdatesPage(currentUser: RealUser?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Status", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 20.sp)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Surface(Modifier.size(64.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                        if (!currentUser?.photoUrl.isNullOrBlank()) AsyncImage(currentUser?.photoUrl, null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp), tint = ShynaDesign.colors.TextSecondary)
                    }
                    Box(Modifier.size(24.dp).align(Alignment.BottomEnd).background(ShynaDesign.colors.BrandGreen, CircleShape).border(2.dp, ShynaDesign.colors.PrimaryBg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Text("My Status", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ShynaDesign.colors.TextPrimary)
            }
            repeat(5) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(64.dp), shape = CircleShape, border = BorderStroke(2.dp, ShynaDesign.colors.BrandGreen), color = ShynaDesign.colors.DividerColor) {
                        Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(16.dp))
                    }
                    Text("User ${it+1}", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ShynaDesign.colors.TextPrimary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Divider(color = ShynaDesign.colors.DividerColor)
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Channels", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ShynaDesign.colors.TextPrimary)
            Icon(Icons.Default.Add, null, tint = ShynaDesign.colors.BrandGreen)
        }
        repeat(3) {
            ListItem(
                headlineContent = { Text("Channel Name ${it+1}", color = ShynaDesign.colors.TextPrimary) },
                supportingContent = { Text("Recent update from the channel...", color = ShynaDesign.colors.TextSecondary) },
                leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) { Icon(Icons.Default.Public, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp)) } },
                trailingContent = { Text("12:30 PM", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun CommunitiesPage() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Groups, null, Modifier.size(100.dp), tint = ShynaDesign.colors.DividerColor)
        Spacer(Modifier.height(16.dp))
        Text("Stay connected with communities", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text("Communities bring members together in topic-based groups.", color = ShynaDesign.colors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
            Text("Start your community")
        }
    }
}

@Composable
private fun CallsPage() {
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("Create call link", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            supportingContent = { Text("Share a link for your Shyna call", color = ShynaDesign.colors.TextSecondary) },
            leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.BrandGreen) { Icon(Icons.Default.Link, null, tint = Color.White, modifier = Modifier.padding(12.dp)) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        Text("Recent", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
        repeat(5) {
            ListItem(
                headlineContent = { Text("User ${it+1}", color = ShynaDesign.colors.TextPrimary) },
                supportingContent = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CallMade, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Today, 10:30 AM", color = ShynaDesign.colors.TextSecondary)
                    }
                },
                leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) { Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp)) } },
                trailingContent = { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun YouPage(user: RealUser?, mode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(ShynaDesign.premiumGradient()), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(90.dp), shape = CircleShape, border = BorderStroke(3.dp, ShynaDesign.colors.BrandGreen)) {
                    if (!user?.photoUrl.isNullOrBlank()) AsyncImage(user?.photoUrl, null, contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = ShynaDesign.colors.TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.name ?: "User", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(user?.email ?: "", color = Color.White.copy(0.7f), fontSize = 14.sp)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        ProfileItem("Account", "Privacy, security, change number", Icons.Outlined.Key)
        ProfileItem("Privacy", "Block contacts, disappearing messages", Icons.Outlined.Lock)
        ProfileItem("Chats", "Theme, wallpapers, chat history", Icons.Outlined.Chat)
        
        ListItem(
            headlineContent = { Text("Dark Mode", color = ShynaDesign.colors.TextPrimary) },
            supportingContent = { Text(if(mode == ThemeMode.DARK) "On" else "Off", color = ShynaDesign.colors.TextSecondary) },
            leadingContent = { Icon(Icons.Default.DarkMode, null, tint = ShynaDesign.colors.TextSecondary) },
            trailingContent = { Switch(mode == ThemeMode.DARK, { onThemeChange(if (it) ThemeMode.DARK else ThemeMode.LIGHT) }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Logout", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileItem(title: String, subtitle: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(title, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary) },
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.TextSecondary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { }
    )
}

private fun formatLastSeen(time: Long?): String {
    if (time == null) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "last seen today at ${sdf.format(Date(time))}"
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

private fun getContactName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return name
}
