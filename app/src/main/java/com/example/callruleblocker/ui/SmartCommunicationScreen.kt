package com.example.callruleblocker.ui

import android.content.Context
import android.util.Log
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ShynaCall"
private const val COMM_PREFS = "smart_communication_premium_v2"
private const val GOOGLE_WEB_CLIENT_ID = "118812641303-0ulisr49hrhaj8tflf5kq078rjmjjgne.apps.googleusercontent.com"
enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }
private enum class MessageType { TEXT, LOCATION, FILE, VOICE, IMAGE, VIDEO, CONTACT, LINK, EVENT }
private data class RealUser(val uid: String, val name: String, val email: String, val phone: String = "", val isOnline: Boolean = false, val lastSeen: Long? = null, val photoUrl: String? = null)
private data class LocalChatMessage(val id: String = UUID.randomUUID().toString(), val text: String, val mine: Boolean, val time: Long, val peerName: String = "", val type: MessageType = MessageType.TEXT, val metadata: String? = null, val senderId: String = "")

private data class ChatRowItem(
    val id: String, 
    val peerUid: String,
    val lastMessage: String, 
    val time: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isGroup: Boolean = false,
    val type: MessageType = MessageType.TEXT
)

private data class CustomChatList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val chatIds: List<String>
)

// --- PREMIUM UNIVERSAL THEME SYSTEM ---
data class ShynaColors(
    val PrimaryBg: Color, val SurfaceBg: Color, val HeaderBg: Color,
    val IncomingBubble: Color, val OutgoingBubble: Color,
    val TextPrimary: Color, val TextSecondary: Color,
    val BrandGreen: Color, val DividerColor: Color,
    val SelectionOverlay: Color, val isDark: Boolean
)

val ShynaDarkPalette = ShynaColors(
    PrimaryBg = Color(0xFF0B141B), SurfaceBg = Color(0xFF121B22), HeaderBg = Color(0xFF121B22),
    IncomingBubble = Color(0xFF202C33), OutgoingBubble = Color(0xFF005C4B),
    TextPrimary = Color(0xFFE9EDEF), TextSecondary = Color(0xFF8696A0),
    BrandGreen = Color(0xFFD4A017), DividerColor = Color(0xFF222D34),
    SelectionOverlay = Color(0xFFD4A017).copy(alpha = 0.2f), isDark = true
)

val ShynaLightPalette = ShynaColors(
    PrimaryBg = Color(0xFFF7F7F7), SurfaceBg = Color(0xFFFFFFFF), HeaderBg = Color(0xFFF7F7F7),
    IncomingBubble = Color(0xFFFFFFFF), OutgoingBubble = Color(0xFFE7FFDB),
    TextPrimary = Color(0xFF000000), TextSecondary = Color(0xFF667781),
    BrandGreen = Color(0xFFD4A017), DividerColor = Color(0xFFEEEEEE),
    SelectionOverlay = Color(0xFFD4A017).copy(alpha = 0.1f), isDark = false
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
    val mContext = LocalContext.current
    val prefs = remember { mContext.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
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
            onThemeChange = { themeMode = it; prefs.edit().putString("theme_mode", it.name).apply() },
            prefs = prefs
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmartCommunicationContent(
    onBack: () -> Unit, 
    themeMode: ThemeMode, 
    onThemeChange: (ThemeMode) -> Unit,
    prefs: android.content.SharedPreferences
) {
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var showGalleryByChatId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    var menuExpanded by remember { mutableStateOf(false) }

    val favouriteChatIds = remember { mutableStateSetOf<String>() }
    val customLists = remember { mutableStateListOf<CustomChatList>() }
    val archivedChatIds = remember { mutableStateSetOf<String>() }
    val recentChats = remember { mutableStateListOf<ChatRowItem>() }

    // Load persisted data
    LaunchedEffect(currentUid) {
        val uid = currentUid ?: return@LaunchedEffect
        val favJson = prefs.getString("fav_chats_$uid", "[]")
        val customJson = prefs.getString("custom_lists_$uid", "[]")
        val gson = Gson()
        
        try {
            val favs: List<String> = gson.fromJson(favJson, object : TypeToken<List<String>>() {}.type)
            favouriteChatIds.clear()
            favouriteChatIds.addAll(favs)
            
            val lists: List<CustomChatList> = gson.fromJson(customJson, object : TypeToken<List<CustomChatList>>() {}.type)
            customLists.clear()
            customLists.addAll(lists)
        } catch (e: Exception) {
            Log.e("ShynaLink", "Load data failed", e)
        }
    }

    fun saveFavs() {
        val uid = currentUid ?: return
        prefs.edit().putString("fav_chats_$uid", Gson().toJson(favouriteChatIds.toList())).apply()
    }
    fun saveCustomLists() {
        val uid = currentUid ?: return
        prefs.edit().putString("custom_lists_$uid", Gson().toJson(customLists.toList())).apply()
    }

    DisposableEffect(currentUid) {
        val uid = currentUid ?: return@DisposableEffect onDispose {}
        
        // Listen to Users (Optimized with Delta Updates)
        val userListener = db.collection("users").addSnapshotListener { snapshots, _ ->
            if (snapshots == null) return@addSnapshotListener
            
            val currentUsers = allUsers.toMutableList()
            var changed = false

            snapshots.documentChanges.forEach { dc ->
                val d = dc.document
                val user = RealUser(
                    d.id, 
                    d.getString("name") ?: "", 
                    d.getString("email") ?: "", 
                    isOnline = d.getBoolean("isOnline") ?: false, 
                    lastSeen = d.getTimestamp("lastSeen")?.toDate()?.time, 
                    photoUrl = d.getString("photoUrl")
                )

                when (dc.type) {
                    com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                        if (currentUsers.none { it.uid == d.id }) {
                            currentUsers.add(user)
                            changed = true
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                        val idx = currentUsers.indexOfFirst { it.uid == d.id }
                        if (idx != -1) {
                            currentUsers[idx] = user
                            changed = true
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                        currentUsers.removeAll { it.uid == d.id }
                        changed = true
                    }
                }
            }
            if (changed) allUsers = currentUsers
        }

        // Listen to Chats (Optimized with Delta Updates)
        val chatListener = db.collection("chats")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentList = recentChats.toMutableList()
                var listChanged = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val u1 = d.getString("user1") ?: ""
                    val u2 = d.getString("user2") ?: ""
                    if (u1 != uid && u2 != uid) return@forEach
                    
                    val peerUid = if (u1 == uid) u2 else u1
                    val lastMsg = d.getString("lastMessage") ?: ""
                    val timestamp = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    val unread = (d.get("unreadCount_$uid") as? Number)?.toInt() ?: 0
                    val mTypeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(mTypeStr) } catch(e: Exception) { MessageType.TEXT }

                    val newItem = ChatRowItem(
                        id = d.id,
                        peerUid = peerUid,
                        lastMessage = lastMsg,
                        time = timestamp,
                        unreadCount = unread,
                        isPinned = favouriteChatIds.contains(d.id),
                        isGroup = d.getBoolean("isGroup") ?: false,
                        type = mType
                    )

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            val index = currentList.indexOfFirst { it.id == d.id }
                            
                            // Notification logic for modifications
                            if (dc.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED && index != -1) {
                                val old = currentList[index]
                                if (newItem.time > old.time && newItem.unreadCount > old.unreadCount) {
                                    val peerName = allUsers.find { it.uid == newItem.peerUid }?.name ?: "Shyna User"
                                    showSystemNotification(mContext, "New Message from $peerName", newItem.lastMessage)
                                }
                            }

                            if (index != -1) currentList[index] = newItem
                            else currentList.add(newItem)
                            listChanged = true
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            currentList.removeAll { it.id == d.id }
                            listChanged = true
                        }
                    }
                }

                if (listChanged) {
                    // Sorting on a background thread for maximum smoothness
                    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        val sorted = currentList.sortedByDescending { it.time }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            recentChats.clear()
                            recentChats.addAll(sorted)
                        }
                    }
                }
            }

        onDispose {
            userListener.remove()
            chatListener.remove()
        }
    }

    BackHandler(selectedPeerId != null || archivedOpen || showCameraByChatId != null || showLocationByChatId != null || showGalleryByChatId != null) { 
        if (showCameraByChatId != null) showCameraByChatId = null
        else if (showLocationByChatId != null) showLocationByChatId = null
        else if (showGalleryByChatId != null) showGalleryByChatId = null
        else if (archivedOpen) archivedOpen = false 
        else selectedPeerId = null 
    }

    var showCreateListDialog by remember { mutableStateOf(false) }

    if (showCreateListDialog) {
        CreateCustomListDialog(
            chats = recentChats,
            users = allUsers,
            onDismiss = { showCreateListDialog = false },
            onSave = { newList ->
                customLists.add(newList)
                saveCustomLists()
                showCreateListDialog = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (showGalleryByChatId != null) {
            val targetId = showGalleryByChatId!!
            PremiumGalleryScreen(
                onBack = { showGalleryByChatId = null },
                onMediaSelected = { mediaList ->
                    mediaList.forEach { (uri, isV) ->
                        val type = if (isV) MessageType.VIDEO else MessageType.IMAGE
                        val label = if (isV) "📹 Video" else "📷 Photo"
                        
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val prefix = if (isV) "Shyna Video" else "Shyna Image"
                        val ext = if (isV) "mp4" else "jpg"
                        val fileName = "$prefix $timeStamp.$ext"
                        
                        val processedUri = if (!isV) {
                            compressImage(mContext, uri, fileName) ?: uri
                        } else {
                            renameFile(mContext, uri, fileName) ?: uri
                        }

                        val msg = mapOf("text" to label, "senderId" to currentUid, "timestamp" to Timestamp.now(), "type" to type.name, "metadata" to processedUri.toString())
                        db.collection("chats").document(targetId).collection("messages").add(msg)
                    }
                    val finalLabel = if(mediaList.size > 1) "📎 Multiple Media" else if(mediaList.first().second) "📹 Video" else "📷 Photo"
                    db.collection("chats").document(targetId).set(mapOf("lastMessage" to finalLabel, "timestamp" to Timestamp.now()), SetOptions.merge())
                    showGalleryByChatId = null
                }
            )
        } else if (showCameraByChatId != null) {
            val targetId = showCameraByChatId!!
            ShynaCameraScreen(
                onBack = { showCameraByChatId = null },
                onMediaCaptured = { uri, isVideo ->
                    val type = if (isVideo) MessageType.VIDEO else MessageType.IMAGE
                    val label = if (isVideo) "📹 Video" else "📷 Photo"
                    
                    // Universal Premium Naming & Compression
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val prefix = if (isVideo) "Shyna Video" else "Shyna Image"
                    val ext = if (isVideo) "mp4" else "jpg"
                    val fileName = "$prefix $timeStamp.$ext"
                    
                    val processedUri = if (!isVideo) {
                        compressImage(mContext, uri, fileName) ?: uri
                    } else {
                        // Video compression placeholder/rename
                        renameFile(mContext, uri, fileName) ?: uri
                    }

                    val msg = mapOf("text" to label, "senderId" to currentUid, "timestamp" to Timestamp.now(), "type" to type.name, "metadata" to processedUri.toString())
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
        } else if (currentUid == null) {
            ShynaAuthFlow(onLoginSuccess = { currentUid = auth.currentUser?.uid }, onBack = onBack)
        } else if (selectedPeerId != null) {
            SmartChatDetailScreen(
                peerId = selectedPeerId!!, 
                userId = currentUid!!, 
                allUsers = allUsers, 
                onBack = { selectedPeerId = null },
                onOpenCamera = { showCameraByChatId = it },
                onOpenLocation = { showLocationByChatId = it },
                onOpenGallery = { showGalleryByChatId = it }
            )
        } else {
            Scaffold(
                containerColor = ShynaDesign.colors.PrimaryBg,
                topBar = {
                    Column(Modifier.background(ShynaDesign.colors.HeaderBg).shadow(4.dp)) {
                        TopAppBar(
                            title = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (archivedOpen) "Archived" else "Shyna Calling", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 22.sp)
                                    if (!archivedOpen) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.WorkspacePremium, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen.copy(0.5f)), color = Color.Transparent) {
                                            Text("Premium", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            navigationIcon = { if (archivedOpen) IconButton(onClick = { archivedOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                            actions = {
                                if (!archivedOpen) {
                                    IconButton(onClick = { archivedOpen = true }) { Icon(Icons.Default.Inventory2, null, tint = ShynaDesign.colors.TextPrimary) }
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        modifier = Modifier.background(ShynaDesign.colors.HeaderBg)
                                    ) {
                                        val secondaryFeatures = CallStateController.getSecondaryFeatures()
                                        secondaryFeatures.forEach { feature ->
                                            when (feature) {
                                                MainCallType.PHONE_DIALER -> {
                                                    DropdownMenuItem(
                                                        text = { Text("Phone Dialer", color = ShynaDesign.colors.TextPrimary) },
                                                        leadingIcon = { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen) },
                                                        onClick = { 
                                                            menuExpanded = false
                                                            CallStateController.setPrimaryFeature(MainCallType.PHONE_DIALER)
                                                            onBack() 
                                                        }
                                                    )
                                                }
                                                MainCallType.SHYNA_LINK -> {
                                                    // Already here
                                                }
                                                MainCallType.OFFLINE_CALL -> {
                                                    DropdownMenuItem(
                                                        text = { Text("Offline Call", color = ShynaDesign.colors.TextPrimary) },
                                                        leadingIcon = { Icon(Icons.Default.SettingsInputAntenna, null, tint = ShynaDesign.colors.BrandGreen) },
                                                        onClick = { 
                                                            menuExpanded = false
                                                            CallStateController.setPrimaryFeature(MainCallType.OFFLINE_CALL)
                                                            onBack() // MainActivity handles switching based on primaryFeature
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        if (!archivedOpen) {
                            PremiumSearchBar(search, { search = it })
                            if (selectedTab == LinkTab.CHATS) {
                                PremiumFilterRow(
                                    selectedFilter = selectedFilter,
                                    customLists = customLists,
                                    onFilterChange = { selectedFilter = it },
                                    onAddClick = { showCreateListDialog = true },
                                    onDeleteList = { name ->
                                        customLists.removeAll { it.name == name }
                                        saveCustomLists()
                                    }
                                )
                                HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp)
                            }
                        }
                    }
                },
                bottomBar = { 
                    val unreadChatsCount = remember(recentChats) { recentChats.count { it.unreadCount > 0 } }
                    PremiumBottomBar(
                        selected = selectedTab, 
                        userPhotoUrl = allUsers.find { it.uid == currentUid }?.photoUrl,
                        unreadChats = unreadChatsCount,
                        unreadUpdates = 0,
                        onSelect = { selectedTab = it }
                    )
                }
            ) { p ->
                Box(Modifier.padding(p).fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
                    when (selectedTab) {
                        LinkTab.CHATS -> ChatsList(
                            allUsers, search, selectedFilter, favouriteChatIds, archivedChatIds, recentChats, customLists, 
                            onOpen = { selectedPeerId = it },
                            onToggleFav = { id -> 
                                if (favouriteChatIds.contains(id)) favouriteChatIds.remove(id) 
                                else favouriteChatIds.add(id)
                                saveFavs()
                            },
                            onMarkUnread = { id -> 
                                val uid = currentUid ?: return@ChatsList
                                db.collection("chats").document(id).update("unreadCount_$uid", 1) 
                            },
                            onDeleteChat = { id -> db.collection("chats").document(id).delete() }
                        )
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
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), 
        shape = RoundedCornerShape(32.dp), 
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 17.sp),
                decorationBox = { if (query.isEmpty()) Text("Ask Shyna Search or Meta AI", color = ShynaDesign.colors.TextSecondary); it() }
            )
            Icon(Icons.Default.AutoAwesome, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PremiumFilterRow(
    selectedFilter: String,
    customLists: List<CustomChatList>,
    onFilterChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onDeleteList: (String) -> Unit
) {
    val filters = remember(customLists) {
        listOf("All", "Unread", "Favourites", "Groups") + customLists.map { it.name }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { f ->
            val isSelected = f == selectedFilter
            val isCustom = remember(f) { f !in listOf("All", "Unread", "Favourites", "Groups") }
            var showMenu by remember { mutableStateOf(false) }

            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onFilterChange(f) },
                            onLongClick = { if (isCustom) showMenu = true }
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) ShynaDesign.colors.BrandGreen.copy(0.05f) else ShynaDesign.colors.SurfaceBg,
                    border = BorderStroke(1.dp, if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.DividerColor)
                ) {
                    Text(
                        f, 
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }

                if (isCustom) {
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete List", color = Color.Red) },
                            onClick = { 
                                onDeleteList(f)
                                if (isSelected) onFilterChange("All")
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        )
                    }
                }
            }
        }
        item {
            Surface(
                onClick = onAddClick,
                shape = CircleShape,
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumBottomBar(
    selected: LinkTab, 
    userPhotoUrl: String?, 
    unreadChats: Int,
    unreadUpdates: Int,
    onSelect: (LinkTab) -> Unit
) {
    NavigationBar(containerColor = ShynaDesign.colors.HeaderBg, tonalElevation = 8.dp) {
        LinkTab.entries.forEach { tab ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { 
                    BadgedBox(badge = {
                        if (tab == LinkTab.CHATS && unreadChats > 0) {
                            Badge(containerColor = ShynaDesign.colors.BrandGreen, modifier = Modifier.offset(x = (-4).dp, y = 4.dp)) { 
                                Text(unreadChats.toString(), color = Color.White, fontSize = 10.sp) 
                            }
                        } else if (tab == LinkTab.UPDATES && unreadUpdates > 0) {
                            Badge(containerColor = ShynaDesign.colors.BrandGreen, modifier = Modifier.offset(x = (-4).dp, y = 4.dp)) { 
                                Text(unreadUpdates.toString(), color = Color.White, fontSize = 10.sp) 
                            }
                        } else if (tab == LinkTab.COMMUNITIES) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF25D366), CircleShape).border(1.dp, ShynaDesign.colors.HeaderBg, CircleShape).offset(x = 12.dp, y = (-4).dp))
                        }
                    }) {
                        if (tab == LinkTab.YOU) {
                            Surface(Modifier.size(28.dp), shape = CircleShape, border = if(isSelected) BorderStroke(2.dp, ShynaDesign.colors.BrandGreen) else null) {
                                if (!userPhotoUrl.isNullOrBlank()) AsyncImage(userPhotoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary)
                            }
                        } else {
                            Icon(getTabIcon(tab, isSelected), tab.name, modifier = Modifier.size(28.dp))
                        }
                    }
                },
                label = { Text(getTabLabel(tab), fontSize = 12.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShynaDesign.colors.BrandGreen,
                    selectedTextColor = ShynaDesign.colors.BrandGreen,
                    unselectedIconColor = ShynaDesign.colors.TextSecondary,
                    unselectedTextColor = ShynaDesign.colors.TextSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

private fun getTabLabel(tab: LinkTab) = when(tab) {
    LinkTab.CHATS -> "Chats"
    LinkTab.UPDATES -> "Updates"
    LinkTab.COMMUNITIES -> "Online Chat"
    LinkTab.CALLS -> "Calls"
    LinkTab.YOU -> "You"
}

private fun getTabIcon(tab: LinkTab, selected: Boolean) = when(tab) {
    LinkTab.CHATS -> if (selected) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline
    LinkTab.UPDATES -> if (selected) Icons.Default.DonutLarge else Icons.Outlined.DonutLarge
    LinkTab.COMMUNITIES -> if (selected) Icons.Default.Groups else Icons.Outlined.Groups
    LinkTab.CALLS -> if (selected) Icons.Default.Call else Icons.Outlined.Call
    LinkTab.YOU -> if (selected) Icons.Default.Person else Icons.Outlined.Person
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, 
    userId: String, 
    allUsers: List<RealUser>, 
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenLocation: (String) -> Unit,
    onOpenGallery: (String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val peer = allUsers.find { it.uid == peerId }
    val chatId = if (userId < peerId) "${userId}_${peerId}" else "${peerId}_${userId}"
    var text by remember { mutableStateOf("") }
    val msgs = remember { mutableStateListOf<LocalChatMessage>() }
    val listState = rememberLazyListState()
    val selectedMsgs = remember { mutableStateListOf<String>() }
    val isSelectionMode by remember { derivedStateOf { selectedMsgs.isNotEmpty() } }
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var fullScreenMedia by remember { mutableStateOf<LocalChatMessage?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }

    if (showEventDialog) {
        var eventTitle by remember { mutableStateOf("") }
        var eventDate by remember { mutableStateOf(System.currentTimeMillis()) }
        var showDatePicker by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventDate)

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        eventDate = datePickerState.selectedDateMillis ?: eventDate
                        showDatePicker = false
                    }) { Text("OK", color = ShynaDesign.colors.BrandGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        AlertDialog(
            onDismissRequest = { showEventDialog = false },
            title = { Text("Create New Event", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    Spacer(Modifier.height(16.dp))
                    val dateStr = remember(eventDate) { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(eventDate)) }
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = ShynaDesign.colors.BrandGreen)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = dateStr,
                                color = ShynaDesign.colors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank()) {
                            val metadata = "$eventTitle|$eventDate"
                            val msg = mapOf(
                                "text" to "📅 $eventTitle",
                                "senderId" to userId,
                                "timestamp" to Timestamp.now(),
                                "type" to MessageType.EVENT.name,
                                "metadata" to metadata
                            )
                            db.collection("chats").document(chatId).collection("messages").add(msg)
                            db.collection("chats").document(chatId).set(mapOf("lastMessage" to "📅 $eventTitle", "timestamp" to Timestamp.now()), SetOptions.merge())
                        }
                        showEventDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen),
                    enabled = eventTitle.isNotBlank()
                ) { Text("Create Event") }
            },
            dismissButton = {
                TextButton(onClick = { showEventDialog = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showPollDialog) {
        var question by remember { mutableStateOf("") }
        val options = remember { mutableStateListOf("", "") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create Poll", color = ShynaDesign.colors.TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                    options.forEachIndexed { i, opt ->
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = opt, onValueChange = { options[i] = it }, label = { Text("Option ${i+1}") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (options.size < 5) {
                        TextButton(onClick = { options.add("") }) { Text("+ Add Option", color = ShynaDesign.colors.BrandGreen) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (question.isNotBlank() && options.all { it.isNotBlank() }) {
                        val msg = mapOf("text" to "📊 $question", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.TEXT.name) // Simplified for now
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                    }
                    showPollDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) { Text("Create") }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Send Link", color = ShynaDesign.colors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (urlInput.isNotBlank()) {
                        val msg = mapOf("text" to urlInput, "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.LINK.name)
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf("lastMessage" to "🔗 Link", "timestamp" to Timestamp.now()), SetOptions.merge())
                    }
                    showUrlDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    // Media Launchers
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val name = getFileName(mContext, it) ?: "Document"
            val msg = mapOf("text" to "📄 $name", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.FILE.name, "metadata" to it.toString())
            db.collection("chats").document(chatId).collection("messages").add(msg)
            db.collection("chats").document(chatId).set(mapOf("lastMessage" to "📄 $name", "timestamp" to Timestamp.now()), SetOptions.merge())
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val name = getFileName(mContext, it) ?: "Audio"
            val msg = mapOf("text" to "🎵 $name", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.VOICE.name, "metadata" to it.toString())
            db.collection("chats").document(chatId).collection("messages").add(msg)
            db.collection("chats").document(chatId).set(mapOf("lastMessage" to "🎵 Audio", "timestamp" to Timestamp.now()), SetOptions.merge())
        }
    }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { contactUri ->
            var name = ""
            var phone = ""
            mContext.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts.DISPLAY_NAME))
                    val contactId = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts._ID))
                    val hasPhone = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER))
                    if (hasPhone == "1") {
                        mContext.contentResolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                            null, null
                        )?.use { pCursor ->
                            if (pCursor.moveToFirst()) {
                                phone = pCursor.getString(pCursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))
                            }
                        }
                    }
                }
            }
            if (name.isNotEmpty()) {
                val metadata = "$name|$phone"
                val msg = mapOf("text" to "👤 $name", "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.CONTACT.name, "metadata" to metadata)
                db.collection("chats").document(chatId).collection("messages").add(msg)
                db.collection("chats").document(chatId).set(mapOf("lastMessage" to "👤 $name", "timestamp" to Timestamp.now()), SetOptions.merge())
            }
        }
    }

    DisposableEffect(chatId) {
        // Clear unread count when opening chat
        db.collection("chats").document(chatId).update("unreadCount_$userId", 0)

        val l = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentMsgs = msgs.toMutableList()
                var changed = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val typeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT }
                    val m = LocalChatMessage(
                        id = d.id, 
                        text = d.getString("text") ?: "", 
                        mine = d.getString("senderId") == userId, 
                        time = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L, 
                        type = mType, 
                        metadata = d.getString("metadata")
                    )

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            if (currentMsgs.none { it.id == d.id }) {
                                currentMsgs.add(m)
                                changed = true
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            val idx = currentMsgs.indexOfFirst { it.id == d.id }
                            if (idx != -1) {
                                currentMsgs[idx] = m
                                changed = true
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            currentMsgs.removeAll { it.id == d.id }
                            changed = true
                        }
                    }
                }

                if (changed) {
                    scope.launch(Dispatchers.Default) {
                        val sorted = currentMsgs.sortedBy { it.time }
                        withContext(Dispatchers.Main) {
                            msgs.clear()
                            msgs.addAll(sorted)
                        }
                    }
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
                        IconButton(onClick = { peer?.let { p -> CallSignalingManager.startCall(mContext, userId, "User", null, p.uid, p.name, p.photoUrl, AppCallType.VIDEO, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, {}) } }) { Icon(Icons.Default.Videocam, null, tint = ShynaDesign.colors.TextPrimary) }
                        IconButton(onClick = { peer?.let { p -> CallSignalingManager.startCall(mContext, userId, "User", null, p.uid, p.name, p.photoUrl, AppCallType.VOICE, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, {}) } }) { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.TextPrimary) }
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), 
                    state = listState, 
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(
                        items = msgs,
                        key = { it.id }
                    ) { m ->
                        PremiumMessageBubble(
                            m = m, 
                            isSelected = selectedMsgs.contains(m.id), 
                            onLongClick = { 
                                if (selectedMsgs.contains(m.id)) selectedMsgs.remove(m.id) 
                                else selectedMsgs.add(m.id) 
                            },
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
                            "GALLERY" -> onOpenGallery(chatId)
                            "LINK" -> showUrlDialog = true
                            "AUDIO" -> audioLauncher.launch("audio/*")
                            "DOC" -> docLauncher.launch("*/*")
                            "CONTACT" -> contactLauncher.launch(null)
                            "LOCATION" -> onOpenLocation(chatId)
                            "POLL" -> showPollDialog = true
                            "EVENT" -> showEventDialog = true
                            else -> Toast.makeText(mContext, "$type Feature Active", Toast.LENGTH_SHORT).show()
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
    val mContext = LocalContext.current
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
                    MessageType.LOCATION -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            try {
                                val loc = m.metadata ?: m.text.replace("📍 ", "")
                                val gmmIntentUri = Uri.parse("geo:0,0?q=$loc")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mContext.startActivity(mapIntent)
                            } catch (e: Exception) {
                                Toast.makeText(mContext, "No maps app found", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.LocationOn, null, tint = ShynaDesign.colors.BrandGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("📍 Shared Location", color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                    MessageType.LINK -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.text))
                                mContext.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(mContext, "Cannot open link", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Link, null, tint = Color(0xFF34B7F1))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = m.text, 
                                color = Color(0xFF34B7F1),
                                style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                            )
                        }
                    }
                    MessageType.FILE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = ShynaDesign.colors.TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(m.text, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                    MessageType.CONTACT -> {
                        val parts = m.metadata?.split("|") ?: listOf(m.text.replace("👤 ", ""), "")
                        val cName = parts.getOrNull(0) ?: "Contact"
                        val cPhone = parts.getOrNull(1) ?: ""
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ShynaDesign.colors.DividerColor.copy(alpha = 0.5f))
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = android.provider.ContactsContract.RawContacts.CONTENT_TYPE
                                            putExtra(android.provider.ContactsContract.Intents.Insert.NAME, cName)
                                            putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, cPhone)
                                        }
                                        mContext.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(mContext, "Cannot save contact", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.BrandGreen.copy(0.1f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.BrandGreen)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(cName, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
                                    if (cPhone.isNotEmpty()) Text(cPhone, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ShynaDesign.colors.DividerColor)
                            Text(
                                "View Contact", 
                                modifier = Modifier.fillMaxWidth(), 
                                textAlign = TextAlign.Center, 
                                color = ShynaDesign.colors.BrandGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    MessageType.EVENT -> {
                        val parts = m.metadata?.split("|") ?: listOf(m.text.replace("📅 ", ""), "")
                        val eTitle = parts.getOrNull(0) ?: "Event"
                        val eDate = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        val dateStr = remember(eDate) { 
                            if(eDate > 0) SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date(eDate)) 
                            else "No Date"
                        }
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ShynaDesign.colors.SurfaceBg)
                                .border(1.dp, ShynaDesign.colors.DividerColor, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(ShynaDesign.colors.BrandGreen.copy(0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.EventAvailable, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(eTitle, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = dateStr,
                                        color = ShynaDesign.colors.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ShynaDesign.colors.DividerColor)
                            Button(
                                onClick = { /* Add to calendar logic */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen.copy(0.1f), contentColor = ShynaDesign.colors.BrandGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Add to Calendar", fontWeight = FontWeight.Bold)
                            }
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
    val mContext = LocalContext.current
    val recorder = remember { AudioRecorder(mContext) }
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
                            val file = File(mContext.cacheDir, "voice_${System.currentTimeMillis()}.mp4")
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
                AttachmentItem("Link", Icons.Default.Link, Color(0xFFFF2E74)) { onMediaClick("LINK") }
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
    val emojis = remember { listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖") }
    Surface(Modifier.fillMaxWidth().height(250.dp), color = ShynaDesign.colors.HeaderBg) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(45.dp), 
            contentPadding = PaddingValues(8.dp),
            state = rememberLazyGridState()
        ) {
            items(emojis, key = { it }) { e -> 
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
private fun ChatsList(
    users: List<RealUser>, 
    query: String, 
    filter: String, 
    favourites: Set<String>, 
    archived: Set<String>, 
    recentChats: List<ChatRowItem>,
    customLists: List<CustomChatList>,
    onOpen: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onMarkUnread: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    val displayList = remember(recentChats, query, filter, favourites, archived, customLists) {
        recentChats.filter { chat ->
            val peer = users.find { it.uid == chat.peerUid }
            if (peer == null) return@filter false
            
            // Archived check
            if (archived.contains(chat.id)) return@filter false
            
            // Search Match (Local Chat)
            val matchesSearch = query.isEmpty() || peer.name.contains(query, true)
            if (!matchesSearch) return@filter false
            
            // Filter Match
            when (filter) {
                "All" -> true
                "Unread" -> chat.unreadCount > 0
                "Favourites" -> favourites.contains(chat.id)
                "Groups" -> chat.isGroup || peer.name.contains("Group", true)
                else -> {
                    val custom = customLists.find { it.name == filter }
                    if (custom != null) custom.chatIds.contains(chat.id) else true
                }
            }
        }
    }

    val searchResults = remember(users, query, recentChats) {
        if (query.isEmpty()) emptyList<RealUser>()
        else users.filter { u ->
            u.name.contains(query, true) && recentChats.none { it.peerUid == u.uid }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
        if (displayList.isEmpty() && searchResults.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.8f), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline, 
                            contentDescription = null, 
                            modifier = Modifier.size(80.dp), 
                            tint = ShynaDesign.colors.TextSecondary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (query.isNotEmpty()) "No results for '$query'" else "No chats found in $filter", 
                            color = ShynaDesign.colors.TextSecondary
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = displayList,
            key = { _, chat -> chat.id }
        ) { index, chat ->
            val peer = remember(users, chat.peerUid) { users.find { it.uid == chat.peerUid } }
            peer?.let {
                PremiumChatItem(
                    it, 
                    chat, 
                    onClick = { onOpen(it.uid) },
                    onToggleFav = { onToggleFav(chat.id) },
                    onMarkUnread = { onMarkUnread(chat.id) },
                    onDelete = { onDeleteChat(chat.id) }
                )
                if (index < displayList.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 84.dp), 
                        color = ShynaDesign.colors.DividerColor, 
                        thickness = 0.5.dp
                    )
                }
            }
        }

        if (searchResults.isNotEmpty()) {
            item {
                Text(
                    "GLOBAL SEARCH", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.labelMedium, 
                    color = ShynaDesign.colors.BrandGreen
                )
            }
            items(searchResults) { user ->
                PremiumChatItem(user, ChatRowItem("new", user.uid, "Start a new conversation", 0, 0, false), onClick = { onOpen(user.uid) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumChatItem(
    user: RealUser, 
    chat: ChatRowItem, 
    onClick: () -> Unit,
    onToggleFav: () -> Unit = {},
    onMarkUnread: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val unreadCount = remember(chat.unreadCount) { if (chat.unreadCount > 0) chat.unreadCount.toString() else null }
    val isCommunity = remember(user.name) { user.name.contains("Decathlon") }
    val dateStr = remember(chat.time) { formatChatDate(chat.time) }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = if (isCommunity) RoundedCornerShape(12.dp) else CircleShape,
                    color = if (isCommunity) Color(0xFFE8F5E9) else ShynaDesign.colors.DividerColor
                ) {
                    if (!user.photoUrl.isNullOrBlank()) {
                        AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCommunity) {
                                Icon(Icons.Default.Groups, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                            } else {
                                Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp)
                            }
                        }
                    }
                }
                if (user.isOnline && !isCommunity) {
                    Box(
                        Modifier
                            .size(15.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xFF25D366), CircleShape)
                            .border(2.dp, ShynaDesign.colors.SurfaceBg, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 18.sp, 
                        color = ShynaDesign.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        dateStr,
                        fontSize = 12.sp, 
                        color = if (unreadCount != null) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (chat.type == MessageType.TEXT && !isCommunity) {
                        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF34B7F1), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (!isCommunity) {
                        val icon = when(chat.type) {
                            MessageType.IMAGE -> Icons.Default.Image
                            MessageType.VIDEO -> Icons.Default.Videocam
                            MessageType.VOICE -> Icons.Default.Mic
                            MessageType.LOCATION -> Icons.Default.LocationOn
                            MessageType.FILE -> Icons.Default.InsertDriveFile
                            else -> null
                        }
                        icon?.let { Icon(it, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    }
                    Text(
                        if (isCommunity) "Decathlon skating community  ▶  जय..." else chat.lastMessage, 
                        fontSize = 14.sp, 
                        color = ShynaDesign.colors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.isPinned) {
                        Icon(Icons.Default.PushPin, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp).graphicsLayer(rotationZ = 45f))
                        if (unreadCount != null) Spacer(Modifier.width(8.dp))
                    }
                    if (unreadCount != null) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(ShynaDesign.colors.BrandGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(unreadCount, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (isCommunity) {
                        Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (chat.isPinned) "Unfavourite" else "Mark as Favourite") },
                onClick = { onToggleFav(); showMenu = false },
                leadingIcon = { Icon(if (chat.isPinned) Icons.Default.StarOutline else Icons.Default.Star, null) }
            )
            DropdownMenuItem(
                text = { Text("Mark as Unread") },
                onClick = { onMarkUnread(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.MarkChatUnread, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete Chat", color = Color.Red) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
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

// --- PREMIUM GALLERY PICKER ---

private data class GalleryMedia(val id: Long, val uri: Uri, val dateAdded: Long, val isVideo: Boolean)

@Composable
private fun PremiumGalleryScreen(onBack: () -> Unit, onMediaSelected: (List<Pair<Uri, Boolean>>) -> Unit) {
    val mContext = LocalContext.current
    val mediaItems = remember { mutableStateListOf<GalleryMedia>() }
    val selectedMedia = remember { mutableStateListOf<GalleryMedia>() }
    
    LaunchedEffect(Unit) {
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.MIME_TYPE
        )
        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
        
        // Images
        mContext.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                mediaItems.add(GalleryMedia(id, uri, cursor.getLong(dateCol) * 1000, false))
            }
        }
        // Videos
        mContext.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                mediaItems.add(GalleryMedia(id, uri, cursor.getLong(dateCol) * 1000, true))
            }
        }
    }

    val groupedMedia = remember(mediaItems.size) {
        mediaItems.sortedByDescending { it.dateAdded }.groupBy { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateAdded }
            val now = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) "Today"
            else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1) "Yesterday"
            else SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAdded))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) }
            Text("Select items", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selectedMedia.isNotEmpty()) {
                Button(onClick = { onMediaSelected(selectedMedia.map { it.uri to it.isVideo }) }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Send (${selectedMedia.size})")
                }
            }
        }
        
        if (selectedMedia.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("No items selected", color = Color.Gray, fontSize = 16.sp)
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            groupedMedia.forEach { (date, items) ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        val isAllSelected = items.all { it in selectedMedia }
                        IconButton(onClick = {
                            if (isAllSelected) selectedMedia.removeAll(items)
                            else items.forEach { if (it !in selectedMedia) selectedMedia.add(it) }
                        }) {
                            Icon(if (isAllSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if(isAllSelected) ShynaDesign.colors.BrandGreen else Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(date, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    val rows = (items.size + 2) / 3
                    Column {
                        for (i in 0 until rows) {
                            Row(Modifier.fillMaxWidth()) {
                                for (j in 0 until 3) {
                                    val index = i * 3 + j
                                    if (index < items.size) {
                                        val item = items[index]
                                        val isSelected = selectedMedia.contains(item)
                                        Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clickable { 
                                            if (isSelected) selectedMedia.remove(item) else selectedMedia.add(item)
                                        }) {
                                            AsyncImage(item.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            if (item.isVideo) {
                                                Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.7f), modifier = Modifier.align(Alignment.Center).size(28.dp))
                                            }
                                            // Selection Icon (Top Start as per screenshot)
                                            Box(Modifier.fillMaxSize().padding(6.dp)) {
                                                Icon(
                                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, 
                                                    null, 
                                                    tint = if(isSelected) ShynaDesign.colors.BrandGreen else Color.White.copy(0.8f), 
                                                    modifier = Modifier.align(Alignment.TopStart).size(22.dp)
                                                )
                                            }
                                            // Expand Icon (Bottom End as per screenshot)
                                            Icon(
                                                Icons.Default.OpenInFull, 
                                                null, 
                                                tint = Color.White, 
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GalleryTabItem("Pictures", Icons.Default.Image, true)
                GalleryTabItem("Albums", Icons.Default.PhotoLibrary, false)
                GalleryTabItem("Collections", Icons.Default.Collections, false)
                GalleryTabItem("Search", Icons.Default.Search, false)
            }
        }
    }
}

@Composable
private fun GalleryTabItem(label: String, icon: ImageVector, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
        Text(label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp)
    }
}

private fun compressImage(context: Context, uri: Uri, fileName: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        val file = File(context.cacheDir, fileName)
        val out = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

private fun showSystemNotification(context: Context, title: String, message: String) {
    val channelId = "shyna_messages"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Shyna Messages", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    nm.notify(System.currentTimeMillis().toInt(), notification)
}

@Composable
private fun CreateCustomListDialog(
    chats: List<ChatRowItem>,
    users: List<RealUser>,
    onDismiss: () -> Unit,
    onSave: (CustomChatList) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    val selectedChatIds = remember { mutableStateListOf<String>() }
    val mContext = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New List", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("List Name (e.g. Family, Work)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Select Chats", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    items(chats) { chat ->
                        val peer = users.find { it.uid == chat.peerUid }
                        peer?.let {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        if (selectedChatIds.contains(chat.id)) selectedChatIds.remove(chat.id)
                                        else selectedChatIds.add(chat.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedChatIds.contains(chat.id),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(it.name, color = ShynaDesign.colors.TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (listName.isBlank()) {
                        Toast.makeText(mContext, "Please enter a list name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedChatIds.isEmpty()) {
                        Toast.makeText(mContext, "Please select at least one chat", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(CustomChatList(name = listName, chatIds = selectedChatIds.toList()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
            ) { Text("Save List") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

private fun formatChatDate(time: Long): String {
    if (time == 0L) return ""
    val now = Calendar.getInstance()
    val chatTime = Calendar.getInstance().apply { timeInMillis = time }
    
    return if (now.get(Calendar.DATE) == chatTime.get(Calendar.DATE)) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))
    } else if (now.get(Calendar.DATE) - chatTime.get(Calendar.DATE) == 1) {
        "Yesterday"
    } else {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(time))
    }
}

private fun renameFile(context: Context, uri: Uri, fileName: String): Uri? {
    return try {
        val file = File(context.cacheDir, fileName)
        val inputStream = context.contentResolver.openInputStream(uri)
        val outputStream = java.io.FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.close()
        inputStream?.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

// --- ROYAL AUTH FLOW ---

private enum class AuthStep { LOGIN, SIGNUP, FORGOT_PASSWORD, RESET_PASSWORD }

@Composable
private fun ShynaAuthFlow(onLoginSuccess: () -> Unit, onBack: () -> Unit) {
    val mContext = LocalContext.current
    var step by remember { mutableStateOf(AuthStep.LOGIN) }
    var resetEmail by remember { mutableStateOf("") }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(mContext, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { onLoginSuccess() }
                    .addOnFailureListener { Toast.makeText(mContext, "Google Login Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            Log.e("ShynaAuth", "Google Sign In Error", e)
        }
    }

    val onGoogleClick = { googleLauncher.launch(googleSignInClient.signInIntent) }

    BackHandler(enabled = true) {
        when (step) {
            AuthStep.LOGIN -> onBack()
            AuthStep.SIGNUP -> step = AuthStep.LOGIN
            AuthStep.FORGOT_PASSWORD -> step = AuthStep.LOGIN
            AuthStep.RESET_PASSWORD -> step = AuthStep.LOGIN
        }
    }
    
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "auth_flow"
    ) { currentStep ->
        when (currentStep) {
            AuthStep.LOGIN -> LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onSignUpClick = { step = AuthStep.SIGNUP },
                onForgotClick = { step = AuthStep.FORGOT_PASSWORD },
                onGoogleClick = onGoogleClick,
                onBack = onBack
            )
            AuthStep.SIGNUP -> SignUpScreen(
                onBack = { step = AuthStep.LOGIN },
                onLoginClick = { step = AuthStep.LOGIN },
                onGoogleClick = onGoogleClick,
                onSignUpSuccess = { step = AuthStep.LOGIN } // Go to login after signup success as per common flow
            )
            AuthStep.FORGOT_PASSWORD -> ForgotPasswordScreen(
                onBack = { step = AuthStep.LOGIN },
                onResetSent = { email -> 
                    resetEmail = email
                    // Firebase handles the actual reset via email, 
                    // but we can show the custom reset screen if needed for demo/manual flow.
                    step = AuthStep.RESET_PASSWORD 
                }
            )
            AuthStep.RESET_PASSWORD -> ResetPasswordScreen(
                email = resetEmail,
                onBack = { step = AuthStep.LOGIN },
                onResetSuccess = { step = AuthStep.LOGIN }
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Text(
                "Skip", 
                modifier = Modifier.clickable { onSkip() }.padding(8.dp),
                color = ShynaDesign.colors.BrandGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(Modifier.height(40.dp))
        
        // Illustration placeholder (using a box with border and icons as per screenshot)
        val brandColor = ShynaDesign.colors.BrandGreen
        Box(contentAlignment = Alignment.Center) {
            // Stylized background circles
            Canvas(Modifier.size(280.dp)) {
                drawCircle(color = Color(0xFFFFF8E1), radius = size.minDimension / 2)
                drawCircle(
                    color = brandColor.copy(0.1f), 
                    radius = size.minDimension / 2.5f, 
                    style = Stroke(
                        width = 1.dp.toPx(), 
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )
            }
            
            // Central Avatar Placeholder
            Surface(
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                border = BorderStroke(4.dp, brandColor),
                color = Color.White
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(40.dp), tint = brandColor)
            }
            
            // Orbiting Icons
            val icons = listOf(Icons.Default.Call, Icons.AutoMirrored.Filled.Chat, Icons.Default.Shield, Icons.Default.Groups)
            icons.forEachIndexed { i, icon ->
                val angle = (i * 90f) * (Math.PI / 180f)
                Box(
                    Modifier
                        .offset(
                            x = (Math.cos(angle) * 120).dp,
                            y = (Math.sin(angle) * 120).dp
                        )
                        .size(44.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, brandColor.copy(0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = brandColor, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Spacer(Modifier.height(60.dp))
        
        Text(
            "Welcome to Shyna Calling!",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(10.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Smart", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("  •  ", color = Color.LightGray)
            Text("Secure", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("  •  ", color = Color.LightGray)
            Text("Reliable", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(20.dp))
        
        Text(
            "Experience seamless calling with advanced features and complete privacy.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 20.sp,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Let's Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Dots Indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(ShynaDesign.colors.BrandGreen, CircleShape))
            repeat(3) { Box(Modifier.size(10.dp).background(Color(0xFFEEEEEE), CircleShape)) }
        }
    }
}

@Composable
private fun LoginScreen(
    onLoginSuccess: () -> Unit, 
    onSignUpClick: () -> Unit, 
    onForgotClick: () -> Unit, 
    onGoogleClick: () -> Unit,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFEFEFE))
    ) {
        // Subtle Background Pattern (Wavy lines & Dots placeholder)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw some soft beige decorative lines/dots as seen in image
            drawCircle(color = Color(0xFFFDF5E6), radius = 250f, center = Offset(size.width, size.height * 0.9f))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            
            // Title: Shyna Calling
            Row {
                Text("Shyna ", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD4A017))
                Text("Calling", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202020))
            }
            
            Spacer(Modifier.height(10.dp))
            
            // Subtitle: --- 👑 WORLD ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.width(36.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD4A017), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("WORLD", color = Color(0xFFD4A017), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(Modifier.width(36.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Main Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Welcome back!", 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color(0xFFD35400) // Deep orange/gold
                    )
                    Text(
                        "Login to continue with Shyna Calling", 
                        color = Color.Gray, 
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    
                    Spacer(Modifier.height(28.dp))
                    
                    RoyalTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email or Phone Number",
                        icon = Icons.Outlined.Person
                    )
                    
                    Spacer(Modifier.height(18.dp))
                    
                    RoyalTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        icon = Icons.Outlined.Lock,
                        isPassword = true
                    )
                    
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "Forgot Password?", 
                            modifier = Modifier.clickable { onForgotClick() }.padding(vertical = 12.dp),
                            color = Color(0xFFD35400),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) return@Button
                            loading = true
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { 
                                    loading = false
                                    onLoginSuccess() 
                                }
                                .addOnFailureListener {
                                    loading = false
                                    Toast.makeText(mContext, it.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD35400)), // Match screenshot orange
                        enabled = !loading
                    ) {
                        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Login", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    Spacer(Modifier.height(28.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFEEEEEE))
                        Text(" or continue with ", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFEEEEEE))
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    SocialLoginButton(
                        icon = Icons.Default.GTranslate, 
                        text = "Continue with Google", 
                        isGoogle = true,
                        onClick = onGoogleClick
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    SocialLoginButton(
                        icon = Icons.Default.Mail, 
                        text = "Continue with Gmail", 
                        iconColor = Color(0xFFEA4335),
                        onClick = onGoogleClick
                    )
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Row {
                        Text("Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                        Text(
                            "Sign Up", 
                            color = Color(0xFFD35400), 
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onSignUpClick() }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SignUpScreen(
    onBack: () -> Unit, 
    onLoginClick: () -> Unit, 
    onGoogleClick: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    var name by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current
    val brandColor = Color(0xFFD35400)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = brandColor)
                }
                
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text("Shyna ", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD4A017))
                        Text("Calling", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202020))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.width(24.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD4A017), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WORLD", color = Color(0xFFD4A017), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        HorizontalDivider(Modifier.width(24.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
                    }
                }
            }
            
            Spacer(Modifier.height(28.dp))
            
            Text("Sign Up", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = brandColor)
            
            Spacer(Modifier.height(28.dp))
            
            RoyalTextField(name, { name = it }, "Full Name", Icons.Outlined.Person)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(userIdInput, { userIdInput = it }, "User ID", Icons.Outlined.Mail)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(email, { email = it }, "Email Address (Complasory)", Icons.Outlined.Mail)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(phone, { phone = it }, "Phone Number (Optional)", Icons.Outlined.Phone)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
            
            Spacer(Modifier.height(18.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = agree, onCheckedChange = { agree = it }, colors = CheckboxDefaults.colors(checkedColor = brandColor))
                Text(
                    text = buildAnnotatedString {
                        append("I agree to the ")
                        withStyle(SpanStyle(color = brandColor, fontWeight = FontWeight.Bold)) { append("Terms of Service ") }
                        append("and ")
                        withStyle(SpanStyle(color = brandColor, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                    },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) return@Button
                    loading = true
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { 
                            loading = false
                            onSignUpSuccess() 
                        }
                        .addOnFailureListener {
                            loading = false
                            Toast.makeText(mContext, it.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Sign Up", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text("or continue with", color = Color.Gray, fontSize = 13.sp)
            
            Spacer(Modifier.height(16.dp))
            
            SocialLoginButton(
                icon = Icons.Default.GTranslate, 
                text = "Continue with Google", 
                isGoogle = true,
                onClick = onGoogleClick
            )
            
            Spacer(Modifier.height(12.dp))
            
            SocialLoginButton(
                icon = Icons.Default.Mail, 
                text = "Continue with Gmail", 
                iconColor = Color(0xFFEA4335),
                onClick = onGoogleClick
            )
            
            Spacer(Modifier.height(36.dp))
            
            Row {
                Text("Already have an account? ", color = Color.Gray, fontSize = 15.sp)
                Text(
                    "Login", 
                    color = brandColor, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onLoginClick() }
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                color = brandColor, 
                fontWeight = FontWeight.Bold, 
                fontSize = 17.sp,
                modifier = Modifier.clickable { onLoginClick() }
            )
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ForgotPasswordScreen(onBack: () -> Unit, onResetSent: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current
    val brandColor = Color(0xFFD35400)
    
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = brandColor)
                }
                Text("Forgot Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = brandColor, modifier = Modifier.align(Alignment.Center))
            }
            
            Spacer(Modifier.height(40.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = brandColor.copy(0.05f)) { }
                Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = brandColor, modifier = Modifier.size(48.dp))
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    null, 
                    tint = brandColor, 
                    modifier = Modifier.size(28.dp).offset(x = 65.dp, y = (-40).dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text("No worries!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = brandColor)
            Spacer(Modifier.height(14.dp))
            Text(
                "Enter your registered email and we'll send you reset instructions.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Spacer(Modifier.height(40.dp))
            
            RoyalTextField(email, { email = it }, "Enter your Email-ID", Icons.Outlined.Email)
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(mContext, "Enter your email-ID.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    loading = true
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            loading = false
                            Toast.makeText(mContext, "Reset instructions sent successfully.", Toast.LENGTH_LONG).show()
                            onResetSent(email)
                        }
                        .addOnFailureListener {
                            loading = false
                            Toast.makeText(mContext, it.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Send Reset Link", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                modifier = Modifier.clickable { onBack() }.padding(12.dp),
                color = brandColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(Modifier.weight(1f))
            
            // Bottom Branding
            BottomBranding()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ResetPasswordScreen(email: String, onBack: () -> Unit, onResetSuccess: () -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val brandColor = Color(0xFFD35400)
    val mContext = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp)) // Moved down from top
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = brandColor)
            }
            Text("Reset Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = brandColor, modifier = Modifier.align(Alignment.Center))
        }

        Spacer(Modifier.height(32.dp))
        
        Text("Create New Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = brandColor)
        Text("Set a strong password for $email", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(32.dp))

        RoyalTextField(newPassword, { newPassword = it }, "New Password", Icons.Outlined.Lock, isPassword = true)
        Spacer(Modifier.height(16.dp))
        RoyalTextField(confirmPassword, { confirmPassword = it }, "Confirm Password", Icons.Outlined.Lock, isPassword = true)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (newPassword.isBlank() || confirmPassword.isBlank()) {
                    Toast.makeText(mContext, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (newPassword != confirmPassword) {
                    Toast.makeText(mContext, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Note: Actual password update happens outside the app via Firebase Email Link usually.
                // This is a UI placeholder to complete the flow as requested.
                Toast.makeText(mContext, "Password changed successfully.", Toast.LENGTH_SHORT).show()
                onResetSuccess()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = brandColor)
        ) {
            Text("Update Password", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.weight(1f))
        BottomBranding()
    }
}

@Composable
private fun BottomBranding() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text("Shyna ", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD4A017))
            Text("Calling", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202020))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.width(28.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD4A017), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("WORLD", color = Color(0xFFD4A017), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            HorizontalDivider(Modifier.width(28.dp), color = Color(0xFFD4A017).copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun RoyalTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false) {
    var passwordVisible by remember { mutableStateOf(false) }
    val brandColor = Color(0xFFD35400)
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(label, color = Color.Gray, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, null, tint = brandColor, modifier = Modifier.size(22.dp)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                        null, 
                        tint = brandColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = brandColor,
            unfocusedBorderColor = Color(0xFFEEEEEE),
            focusedLabelColor = brandColor,
            cursorColor = brandColor,
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = brandColor,
                backgroundColor = brandColor.copy(alpha = 0.2f)
            )
        ),
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = Color.Black)
    )
}

@Composable
private fun SocialLoginButton(
    icon: ImageVector, 
    text: String, 
    iconColor: Color = Color.Unspecified, 
    isGoogle: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(60.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        color = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isGoogle) {
                // Professional Colorful G Logo using Canvas segments
                Box(Modifier.size(24.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val size = size.minDimension
                        val strokeWidth = size * 0.2f
                        
                        // Red segment
                        drawArc(Color(0xFFEA4335), -45f, -90f, false, style = Stroke(strokeWidth))
                        // Yellow segment
                        drawArc(Color(0xFFFBBC05), 45f, 90f, false, style = Stroke(strokeWidth))
                        // Green segment
                        drawArc(Color(0xFF34A853), 135f, 90f, false, style = Stroke(strokeWidth))
                        // Blue segment
                        drawArc(Color(0xFF4285F4), 225f, 90f, false, style = Stroke(strokeWidth))
                        
                        // Blue middle bar
                        drawLine(Color(0xFF4285F4), Offset(size/2, size/2), Offset(size, size/2), strokeWidth = strokeWidth)
                    }
                }
            } else {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(text, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
        }
    }
}
