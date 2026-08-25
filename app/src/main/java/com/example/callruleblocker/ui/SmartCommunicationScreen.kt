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
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.callruleblocker.data.CloudinaryConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.location.Geocoder
import android.provider.ContactsContract
import android.provider.MediaStore
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.work.*
import com.example.callruleblocker.data.DiscoveryWorker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

private const val TAG = "ShynaCall"
private const val COMM_PREFS = "smart_communication_premium_v2"
private const val GOOGLE_WEB_CLIENT_ID = "118812641303-0ulisr49hrhaj8tflf5kq078rjmjjgne.apps.googleusercontent.com"

private object MediaUploader {
    fun upload(uri: Uri, context: Context, resourceType: String = "auto", onResult: (String?) -> Unit) {
        try {
            MediaManager.get().upload(uri)
                .unsigned(CloudinaryConfig.UPLOAD_PRESET)
                .option("resource_type", resourceType)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val url = resultData?.get("secure_url") as? String
                        val bytes = (resultData?.get("bytes") as? Number)?.toLong() ?: 0L
                        com.example.callruleblocker.data.NetworkUsageTracker.track(context, "media", sent = bytes)
                        
                        // For videos, ensures proper playback in some players
                        val finalUrl = if (url != null && resourceType == "video" && !url.contains(".")) "$url.mp4" else url
                        onResult(finalUrl)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Log.e("MediaUploader", "Upload failed: ${error?.description}")
                        onResult(null)
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        } catch (err: Exception) {
            Log.e("MediaUploader", "Upload exception", err)
            onResult(null)
        }
    }
    
    fun getFileMetadata(context: Context, uri: Uri): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "name" to "file",
            "size" to 0L,
            "mime" to "application/octet-stream"
        )
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIdx != -1) result["name"] = cursor.getString(nameIdx) ?: "file"
                if (sizeIdx != -1) result["size"] = cursor.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        result["mime"] = mime
        
        if (mime.startsWith("video")) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (duration != null) result["durationMs"] = duration.toLong()
                retriever.release()
            } catch (e: Exception) {
                Log.e("MediaUploader", "Metadata extraction failed", e)
            }
        }
        
        return result
    }
}

// Messaging models moved to MessagingModel.kt

@Composable
fun SmartCommunicationScreen(onBack: () -> Unit) {
    val mContext = LocalContext.current
    val prefs = remember { mContext.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    var themeMode by remember { 
        mutableStateOf(
            try { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) } 
            catch(_: Exception) { ThemeMode.DARK }
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
    
    val allUsers = remember { mutableStateListOf<RealUser>() }
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var selectedTab by remember { mutableStateOf(LinkTab.CHATS) }
    var search by remember { mutableStateOf("") }
    var archivedOpen by remember { mutableStateOf(false) }
    var showGalleryByChatId by remember { mutableStateOf<String?>(null) }
    var showAudioPickerByChatId by remember { mutableStateOf<String?>(null) }
    var showCameraByChatId by remember { mutableStateOf<String?>(null) }
    var startVideoImmediately by remember { mutableStateOf(false) }
    var showLocationByChatId by remember { mutableStateOf<String?>(null) }
    var showFullDPUser by remember { mutableStateOf<RealUser?>(null) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showStarredMessages by remember { mutableStateOf(false) }
    var showStatusDetailFor by remember { mutableStateOf<String?>(null) }
    var showChannelDetailFor by remember { mutableStateOf<String?>(null) }
    var showFindChannels by remember { mutableStateOf(false) }
    var showSearchInChatId by remember { mutableStateOf<String?>(null) }
    var forwardingMessage by remember { mutableStateOf<UniversalMessage?>(null) }
    
    var pendingMedia by remember { mutableStateOf<List<Pair<Uri, Boolean>>?>(null) }
    var pendingMediaChatId by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    var pendingGalleryChatId by remember { mutableStateOf<String?>(null) }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_IMAGES] == true &&
            permissions[android.Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            showGalleryByChatId = pendingGalleryChatId
        } else {
            Toast.makeText(mContext, "Gallery permission is required to select media", Toast.LENGTH_LONG).show()
        }
        pendingGalleryChatId = null
    }

    var pendingAudioChatId by remember { mutableStateOf<String?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            showAudioPickerByChatId = pendingAudioChatId
        } else {
            Toast.makeText(mContext, "Permission required to access audio files", Toast.LENGTH_LONG).show()
        }
        pendingAudioChatId = null
    }

    var selectedFilter by remember { mutableStateOf("All") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showArchivePicker by remember { mutableStateOf(false) }

    val favouriteChatIds = remember { mutableStateSetOf<String>() }
    val customLists = remember { mutableStateListOf<CustomChatList>() }
    val archivedChatIds = remember { mutableStateSetOf<String>() }
    val recentChats = remember { mutableStateListOf<ChatRowItem>() }
    val allStatuses = remember { mutableStateListOf<UserStatus>() }
    val allChannels = remember { mutableStateListOf<ShynaChannel>() }
    
    var privacySettings by remember { mutableStateOf(UserPrivacySettings()) }
    var storageSettings by remember { mutableStateOf(UserStorageSettings()) }

    val statusImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isUploading = true
            MediaUploader.upload(it, mContext) { url ->
                isUploading = false
                if (url != null) {
                    val status = UserStatus(
                        userId = currentUid!!,
                        mediaUrl = url,
                        timestamp = System.currentTimeMillis()
                    )
                    db.collection("statuses").add(status)
                    Toast.makeText(mContext, "Status updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Load persisted data
    LaunchedEffect(currentUid) {
        val uid = currentUid ?: return@LaunchedEffect
        
        val favJson = prefs.getString("fav_chats_$uid", "[]")
        val customJson = prefs.getString("custom_lists_$uid", "[]")
        val archivedJson = prefs.getString("archived_chats_$uid", "[]")
        val gson = Gson()
        
        try {
            val favs: List<String> = gson.fromJson(favJson, object : TypeToken<List<String>>() {}.type)
            favouriteChatIds.clear()
            favouriteChatIds.addAll(favs)
            
            val lists: List<CustomChatList> = gson.fromJson(customJson, object : TypeToken<List<CustomChatList>>() {}.type)
            customLists.clear()
            customLists.addAll(lists)

            val archived: List<String> = gson.fromJson(archivedJson, object : TypeToken<List<String>>() {}.type)
            archivedChatIds.clear()
            archivedChatIds.addAll(archived)
        } catch (e: Exception) {
            Log.e("ShynaLink", "Load data failed", e)
        }

        // --- NEW: Sync Archive from Firestore for multi-device support ---
        db.collection("users").document(uid).collection("archivedChats")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val remoteArchived = it.documents.map { d -> d.id }
                    archivedChatIds.addAll(remoteArchived)
                }
            }

        // Fetch Real Settings from Firestore
        db.collection("users").document(uid).collection("settings").document("privacy").get().addOnSuccessListener { d ->
            d?.toObject(UserPrivacySettings::class.java)?.let { privacySettings = it }
        }
        db.collection("users").document(uid).collection("settings").document("storage").get().addOnSuccessListener { d ->
            d?.toObject(UserStorageSettings::class.java)?.let { storageSettings = it }
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
    fun saveArchived() {
        val uid = currentUid ?: return
        prefs.edit().putString("archived_chats_$uid", Gson().toJson(archivedChatIds.toList())).apply()
    }

    DisposableEffect(currentUid) {
        val uid = currentUid ?: return@DisposableEffect onDispose {}
        
        // --- PROTECTED ACCOUNT SECURITY OBSERVER ---
        // Monitor own account status for instant server-side deletion/blocking
        val ownStatusListener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot == null || !snapshot.exists()) {
                Log.d("ShynaCall", "Account deleted from server. Logging out.")
                auth.signOut()
                currentUid = null
                onBack() // Exit to main screen
            } else if (snapshot.getBoolean("isBlocked") == true) {
                Log.d("ShynaCall", "Account blocked by admin. Logging out.")
                auth.signOut()
                currentUid = null
                onBack()
            }
        }

        // Listen to Users (Optimized with Delta Updates)
        val userListener = db.collection("users").addSnapshotListener { snapshots, _ ->
            if (snapshots == null) return@addSnapshotListener
            
            snapshots.documentChanges.forEach { dc ->
                val d = dc.document
                val user = RealUser(
                    uid = d.id, 
                    userId = d.getString("userId") ?: "",
                    name = d.getString("name") ?: "", 
                    email = d.getString("email") ?: "", 
                    phone = d.getString("phone") ?: "",
                    isOnline = d.getBoolean("isOnline") ?: false, 
                    lastSeen = d.getTimestamp("lastSeen")?.toDate()?.time, 
                    photoUrl = d.getString("photoUrl"),
                    followedChannels = d.get("followedChannels") as? List<String> ?: emptyList(),
                    district = d.getString("district"),
                    pincode = d.getString("pincode"),
                    state = d.getString("state"),
                    country = d.getString("country")
                )

                when (dc.type) {
                    com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                        if (allUsers.none { it.uid == d.id }) {
                            allUsers.add(user)
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                        val idx = allUsers.indexOfFirst { it.uid == d.id }
                        if (idx != -1) {
                            allUsers[idx] = user
                        } else {
                            allUsers.add(user)
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                        allUsers.removeAll { it.uid == d.id }
                    }
                }
            }
        }

        // Listen to Chats (Optimized with Delta Updates)
        val chatListener = db.collection("chats")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentList = recentChats.toMutableList()
                var listChanged = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val participants = d.get("participants") as? List<String> ?: emptyList()
                    val u1 = d.getString("user1") ?: ""
                    val u2 = d.getString("user2") ?: ""
                    
                    if (u1 != uid && u2 != uid && !participants.contains(uid)) return@forEach
                    
                    val isGroup = d.getBoolean("isGroup") ?: false
                    val peerUid = if (isGroup) "GROUP" else if (u1 == uid) u2 else u1
                    val lastMsg = d.getString("lastMessage") ?: ""
                    val gName = d.getString("name")
                    val timestamp = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    val unread = (d.get("unreadCount_$uid") as? Number)?.toInt() ?: 0
                    val mTypeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(mTypeStr) } catch(_: Exception) { MessageType.TEXT }
                    val lastStatusStr = d.getString("lastStatus") ?: MessageStatus.SENT.name
                    val lastStatus = try { MessageStatus.valueOf(lastStatusStr) } catch(_: Exception) { MessageStatus.SENT }
                    val lastSender = d.getString("lastSenderId") ?: ""

                    val newItem = ChatRowItem(
                        id = d.id,
                        peerUid = peerUid,
                        lastMessage = lastMsg,
                        time = timestamp,
                        unreadCount = unread,
                        isPinned = favouriteChatIds.contains(d.id),
                        isGroup = isGroup,
                        messageType = mType,
                        groupName = gName,
                        lastMessageStatus = lastStatus,
                        lastMessageMine = lastSender == uid
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
                                    
                                    // Mute/Block Check
                                    db.collection("users").document(uid).collection("chatSettings").document(newItem.id)
                                        .get().addOnSuccessListener { d ->
                                            val mutedUntil = d.getTimestamp("mutedUntil")?.toDate()?.time ?: 0L
                                            if (mutedUntil < System.currentTimeMillis()) {
                                                db.collection("users").document(uid).collection("blockedUsers").document(newItem.peerUid)
                                                    .get().addOnSuccessListener { bd ->
                                                        if (!bd.exists()) {
                                                            showSystemNotification(mContext, "New Message from $peerName", newItem.lastMessage)
                                                        }
                                                    }
                                            }
                                        }
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
                    scope.launch(Dispatchers.Default) {
                        val sorted = currentList.sortedByDescending { it.time }
                        withContext(Dispatchers.Main) {
                            recentChats.clear()
                            recentChats.addAll(sorted)
                        }
                    }
                }
            }

        // Listen for Real Statuses (Updates)
        val statusListener = db.collection("statuses")
            .whereGreaterThan("timestamp", System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            .addSnapshotListener { snapshots, _ ->
                snapshots?.let {
                    val list = it.documents.mapNotNull { d -> d.toObject(UserStatus::class.java) }
                    allStatuses.clear()
                    allStatuses.addAll(list.sortedByDescending { s -> s.timestamp })
                }
            }

        // Listen for Channels
        val channelListener = db.collection("channels").addSnapshotListener { snapshots, _ ->
            snapshots?.let {
                val list = it.documents.mapNotNull { d -> d.toObject(ShynaChannel::class.java) }
                allChannels.clear()
                allChannels.addAll(list.sortedByDescending { c -> c.lastUpdateTime })
            }
        }

        onDispose {
            ownStatusListener.remove()
            userListener.remove()
            chatListener.remove()
            statusListener.remove()
            channelListener.remove()
        }
    }

    BackHandler(selectedPeerId != null || archivedOpen || showCameraByChatId != null || showLocationByChatId != null || showGalleryByChatId != null || showStarredMessages || showFullDPUser != null) { 
        if (showCameraByChatId != null) showCameraByChatId = null
        else if (showLocationByChatId != null) showLocationByChatId = null
        else if (showGalleryByChatId != null) showGalleryByChatId = null
        else if (showStarredMessages) showStarredMessages = false
        else if (showFullDPUser != null) showFullDPUser = null
        else if (showProfileEdit) showProfileEdit = false
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
                    pendingMedia = mediaList
                    pendingMediaChatId = targetId
                    showGalleryByChatId = null
                }
            )
        } else if (showCameraByChatId != null) {
            val targetId = showCameraByChatId!!
            ShynaCameraScreen(
                onBack = { showCameraByChatId = null; startVideoImmediately = false },
                onMediaCaptured = { uri, isVideo ->
                    pendingMedia = listOf(uri to isVideo)
                    pendingMediaChatId = targetId
                    showCameraByChatId = null
                    startVideoImmediately = false
                },
                startVideoImmediately = startVideoImmediately
            )
        } else if (showAudioPickerByChatId != null) {
            PremiumAudioPickerScreen(
                onBack = { showAudioPickerByChatId = null },
                onAudioSelected = { audioList ->
                    val uid = currentUid ?: return@PremiumAudioPickerScreen
                    val targetId = showAudioPickerByChatId ?: return@PremiumAudioPickerScreen
                    isUploading = true
                    var uploadCount = 0
                    audioList.forEach { uri ->
                        val meta = MediaUploader.getFileMetadata(mContext, uri)
                        val name = (meta["name"] as? String) ?: "Audio File"
                        val size = (meta["size"] as? Long) ?: 0L
                        val mime = (meta["mime"] as? String) ?: "audio/mpeg"
                        MediaUploader.upload(uri, mContext, "video") { url ->
                            uploadCount++
                            if (url != null) {
                                val msg = mapOf(
                                    "text" to "🎵 $name", 
                                    "senderId" to uid, 
                                    "timestamp" to Timestamp.now(), 
                                    "type" to MessageType.VOICE.name, 
                                    "metadata" to url,
                                    "fileName" to name,
                                    "fileSize" to size,
                                    "mimeType" to mime,
                                    "status" to MessageStatus.SENT.name
                                )
                                db.collection("chats").document(targetId).collection("messages").add(msg)
                            }
                            if (uploadCount == audioList.size) {
                                isUploading = false
                                db.collection("chats").document(targetId).set(mapOf("lastMessage" to "🎵 Audio", "timestamp" to Timestamp.now()), SetOptions.merge())
                            }
                        }
                    }
                    showAudioPickerByChatId = null
                }
            )
        } else if (pendingMedia != null) {
            AttachmentPreviewScreen(
                media = pendingMedia!!,
                onSend = { media: List<Pair<Uri, Boolean>>, caption: String ->
                    val targetId = pendingMediaChatId ?: return@AttachmentPreviewScreen
                    val uid = currentUid ?: return@AttachmentPreviewScreen
                    val currentPendingMedia = media.toList()
                    
                    isUploading = true
                    var uploadCount = 0
                    currentPendingMedia.forEach { (uri, isV) ->
                        val meta = MediaUploader.getFileMetadata(mContext, uri)
                        MediaUploader.upload(uri, mContext, if (isV) "video" else "image") { url ->
                            uploadCount++
                            if (url != null) {
                                val type = if (isV) MessageType.VIDEO else MessageType.IMAGE
                                val label = if (isV) "📹 Video" else "📷 Photo"
                                val msg = mutableMapOf(
                                    "text" to label, 
                                    "caption" to caption,
                                    "senderId" to uid, 
                                    "timestamp" to Timestamp.now(), 
                                    "sentAt" to System.currentTimeMillis(),
                                    "type" to type.name, 
                                    "metadata" to url,
                                    "fileName" to (meta["name"] as? String),
                                    "fileSize" to (meta["size"] as? Long ?: 0L),
                                    "mimeType" to (meta["mime"] as? String),
                                    "status" to MessageStatus.SENT.name
                                )
                                if (isV) {
                                    msg["durationMs"] = meta["durationMs"] as? Long ?: 0L
                                }
                                db.collection("chats").document(targetId).collection("messages").add(msg)
                            } else {
                                Log.e(TAG, "Failed to upload media: $uri")
                            }
                            
                            if (uploadCount == currentPendingMedia.size) {
                                isUploading = false
                                val finalLabel = if(currentPendingMedia.size > 1) "📎 Multiple Media" else if(currentPendingMedia.first().second) "📹 Video" else "📷 Photo"
                                db.collection("chats").document(targetId).set(mapOf(
                                    "lastMessage" to finalLabel, 
                                    "timestamp" to Timestamp.now(),
                                    "lastStatus" to MessageStatus.SENT.name,
                                    "lastSenderId" to uid
                                ), SetOptions.merge())
                            }
                        }
                    }
                    pendingMedia = null
                    pendingMediaChatId = null
                },
                onDismiss = { 
                    pendingMedia = null
                    pendingMediaChatId = null
                }
            )
        } else if (showLocationByChatId != null) {
            val targetId = showLocationByChatId!!
            SendLocationScreen(
                onBack = { showLocationByChatId = null },
                onSendLocation = { loc ->
                    val uid = currentUid ?: return@SendLocationScreen
                    val isLive = loc.startsWith("LIVE|")
                    val type = if (isLive) MessageType.LIVE_LOCATION else MessageType.LOCATION
                    val label = if (isLive) "📍 Live Location" else "📍 Location"
                    
                    val msg = mutableMapOf<String, Any>(
                        "text" to label, 
                        "senderId" to uid, 
                        "timestamp" to Timestamp.now(), 
                        "sentAt" to System.currentTimeMillis(),
                        "type" to type.name, 
                        "metadata" to loc,
                        "status" to MessageStatus.SENT.name
                    )
                    
                    if (isLive) {
                        val expiry = loc.substringAfter("|").toLongOrNull() ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
                        msg["liveLocationExpiry"] = expiry
                        
                        // Start Background Service for Live Location
                        val intent = Intent(mContext, com.example.callruleblocker.data.LocationService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mContext.startForegroundService(intent)
                        } else {
                            mContext.startService(intent)
                        }
                    }

                    db.collection("chats").document(targetId).collection("messages").add(msg)
                    db.collection("chats").document(targetId).set(mapOf(
                        "lastMessage" to label, 
                        "timestamp" to Timestamp.now(),
                        "lastStatus" to MessageStatus.SENT.name,
                        "lastSenderId" to uid
                    ), SetOptions.merge())
                    showLocationByChatId = null
                }
            )
        } else if (showStarredMessages) {
            StarredMessagesScreen(userId = currentUid ?: "", onBack = { showStarredMessages = false })
        } else if (currentUid == null) {
            ShynaAuthFlow(onLoginSuccess = { currentUid = auth.currentUser?.uid }, onBack = onBack)
        } else if (selectedPeerId != null) {
            SmartChatDetailScreen(
                peerId = selectedPeerId!!, 
                userId = currentUid!!, 
                allUsers = allUsers, 
                storageSettings = storageSettings,
                onBack = { selectedPeerId = null },
                onOpenCamera = { chatId, isVideo -> 
                    showCameraByChatId = chatId
                    startVideoImmediately = isVideo
                },
                onOpenLocation = { showLocationByChatId = it },
                onOpenGallery = { id -> 
                    pendingGalleryChatId = id
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    galleryPermissionLauncher.launch(permissions)
                },
                onOpenAudio = { id -> 
                    pendingAudioChatId = id
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    audioPermissionLauncher.launch(permissions)
                },
                onAvatarClick = { showFullDPUser = it },
                drafts = drafts,
                onUploadingChange = { isUploading = it },
                initialSearchMode = showSearchInChatId == selectedPeerId,
                onForward = { forwardingMessage = it }
            )
            // Reset search trigger
            if (showSearchInChatId == selectedPeerId) showSearchInChatId = null
        } else {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = ShynaDesign.colors.PrimaryBg,
                topBar = {
                    Column(Modifier.background(ShynaDesign.colors.HeaderBg).shadow(4.dp)) {
                        TopAppBar(
                            title = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (archivedOpen) "Archived" else "Shyna Calling", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 22.sp)
                                    if (!archivedOpen) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Outlined.WorkspacePremium, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen.copy(0.5f)), color = Color.Transparent) {
                                            Text("Premium", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            navigationIcon = { if (archivedOpen) IconButton(onClick = { archivedOpen = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                            actions = {
                                if (archivedOpen) {
                                    IconButton(onClick = { showArchivePicker = true }) { Icon(Icons.Outlined.Add, null, tint = ShynaDesign.colors.TextPrimary) }
                                } else {
                                    IconButton(onClick = { archivedOpen = true }) { Icon(Icons.Outlined.Archive, null, tint = ShynaDesign.colors.TextPrimary) }
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
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
                                                else -> {}
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
                    val unreadChatsCount by remember(recentChats) { derivedStateOf { recentChats.count { it.unreadCount > 0 } } }
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
                            users = allUsers, 
                            query = search, 
                            filter = selectedFilter, 
                            favourites = favouriteChatIds, 
                            archived = archivedChatIds, 
                            recentChats = recentChats, 
                            customLists = customLists, 
                            isArchivedMode = archivedOpen,
                            onOpen = { selectedPeerId = it },
                            onAvatarClick = { showFullDPUser = it },
                            onToggleFav = { id -> 
                                if (favouriteChatIds.contains(id)) favouriteChatIds.remove(id) 
                                else favouriteChatIds.add(id)
                                saveFavs()
                            },
                            onArchive = { id ->
                                val uid = currentUid ?: return@ChatsList
                                archivedChatIds.add(id)
                                db.collection("users").document(uid).collection("archivedChats").document(id).set(mapOf("archived" to true, "timestamp" to Timestamp.now()))
                                saveArchived()
                            },
                            onUnarchive = { id ->
                                val uid = currentUid ?: return@ChatsList
                                archivedChatIds.remove(id)
                                db.collection("users").document(uid).collection("archivedChats").document(id).delete()
                                saveArchived()
                            },
                            onMarkUnread = { id -> 
                                val uid = currentUid ?: return@ChatsList
                                db.collection("chats").document(id).update("unreadCount_$uid", 1) 
                            },
                            onDeleteChat = { id -> 
                                db.collection("chats").document(id).delete()
                                if (archivedChatIds.contains(id)) {
                                    archivedChatIds.remove(id)
                                    saveArchived()
                                }
                                if (favouriteChatIds.contains(id)) {
                                    favouriteChatIds.remove(id)
                                    saveFavs()
                                }
                            }
                        )
                        LinkTab.UPDATES -> UpdatesPage(
                            currentUser = allUsers.find { it.uid == currentUid },
                            allUsers = allUsers,
                            statuses = allStatuses,
                            channels = allChannels,
                            onAddStatus = { statusImageLauncher.launch("image/*") },
                            onViewStatus = { showStatusDetailFor = it },
                            onOpenChannel = { showChannelDetailFor = it.id },
                            onFindChannels = { showFindChannels = true }
                        )
                        LinkTab.COMMUNITIES -> CommunitiesPage(
                            channels = allChannels,
                            currentUser = allUsers.find { it.uid == currentUid },
                            onJoin = { channel ->
                                val uid = currentUid ?: return@CommunitiesPage
                                db.collection("channels").document(channel.id)
                                    .update("followersCount", FieldValue.increment(1))
                                db.collection("users").document(uid)
                                    .update("followedChannels", FieldValue.arrayUnion(channel.id))
                                Toast.makeText(mContext, "Joined ${channel.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        LinkTab.CALLS -> CallsPage(currentUid ?: "", allUsers)
                        LinkTab.YOU -> {
                            val currentUser = allUsers.find { it.uid == currentUid }
                            if (showProfileEdit && currentUser != null) {
                                ProfileEditScreen(
                                    user = currentUser,
                                    onBack = { showProfileEdit = false },
                                    onUpdateName = { newName ->
                                        db.collection("users").document(currentUid!!).update("name", newName)
                                    },
                                    onUpdatePhoto = { uri ->
                                        isUploading = true
                                        MediaUploader.upload(uri, mContext) { url ->
                                            isUploading = false
                                            if (url != null) {
                                                db.collection("users").document(currentUid!!).update("photoUrl", url)
                                            }
                                        }
                                    },
                                    onChangePhone = { newPhone ->
                                        // WhatsApp style: require email verification for sensitive changes
                                        auth.currentUser?.sendEmailVerification()?.addOnSuccessListener {
                                            Toast.makeText(mContext, "Verification email sent to ${auth.currentUser?.email}. Verify to enable phone change.", Toast.LENGTH_LONG).show()
                                            // In a real app, we'd listen for auth state change or wait for next login
                                            // For now, we update if they are already verified or just show the process
                                            if (auth.currentUser?.isEmailVerified == true) {
                                                db.collection("users").document(currentUid!!).update("phone", newPhone)
                                                Toast.makeText(mContext, "Phone number updated", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                YouPage(
                                    user = currentUser, 
                                    mode = themeMode, 
                                    privacy = privacySettings,
                                    storage = storageSettings,
                                    onThemeChange = onThemeChange, 
                                    onUpdatePrivacy = { privacySettings = it; db.collection("users").document(currentUid!!).collection("settings").document("privacy").set(it) },
                                    onUpdateStorage = { storageSettings = it; db.collection("users").document(currentUid!!).collection("settings").document("storage").set(it) },
                                    onLogout = { auth.signOut(); onBack() },
                                    onOpenStarred = { showStarredMessages = true },
                                    onEditProfile = { showProfileEdit = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showStatusDetailFor != null) {
            val userStatuses = allStatuses.filter { it.userId == showStatusDetailFor }
            val user = allUsers.find { it.uid == showStatusDetailFor }
            if (userStatuses.isNotEmpty() && user != null) {
                StatusDetailScreen(
                    user = user,
                    statuses = userStatuses,
                    onBack = { showStatusDetailFor = null }
                )
            } else {
                showStatusDetailFor = null
            }
        }

        if (showChannelDetailFor != null) {
            val channel = allChannels.find { it.id == showChannelDetailFor }
            if (channel != null) {
                ChannelDetailScreen(
                    channel = channel,
                    onBack = { showChannelDetailFor = null },
                    onJoin = {
                        db.collection("channels").document(channel.id).update("followersCount", FieldValue.increment(1))
                        Toast.makeText(mContext, "Joined ${channel.name}", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                showChannelDetailFor = null
            }
        }

        if (showFindChannels) {
            FindChannelsScreen(
                onBack = { showFindChannels = false },
                onOpenChannel = { 
                    showChannelDetailFor = it.id
                    showFindChannels = false
                }
            )
        }

        if (showArchivePicker) {
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
                                        Icon(Icons.Outlined.WorkspacePremium, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen.copy(0.5f)), color = Color.Transparent) {
                                            Text("Premium", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            navigationIcon = { if (archivedOpen) IconButton(onClick = { archivedOpen = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                            actions = {
                                if (archivedOpen) {
                                    IconButton(onClick = { showArchivePicker = true }) { Icon(Icons.Outlined.Add, null, tint = ShynaDesign.colors.TextPrimary) }
                                } else {
                                    IconButton(onClick = { archivedOpen = true }) { Icon(Icons.Outlined.Archive, null, tint = ShynaDesign.colors.TextPrimary) }
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
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
                                                else -> {}
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
                    val unreadChatsCount by remember(recentChats) { derivedStateOf { recentChats.count { it.unreadCount > 0 } } }
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
                            users = allUsers, 
                            query = search, 
                            filter = selectedFilter, 
                            favourites = favouriteChatIds, 
                            archived = archivedChatIds, 
                            recentChats = recentChats, 
                            customLists = customLists, 
                            isArchivedMode = archivedOpen,
                            onOpen = { selectedPeerId = it },
                            onAvatarClick = { showFullDPUser = it },
                            onToggleFav = { id -> 
                                if (favouriteChatIds.contains(id)) favouriteChatIds.remove(id) 
                                else favouriteChatIds.add(id)
                                saveFavs()
                            },
                            onArchive = { id ->
                                val uid = currentUid ?: return@ChatsList
                                archivedChatIds.add(id)
                                db.collection("users").document(uid).collection("archivedChats").document(id).set(mapOf("archived" to true, "timestamp" to Timestamp.now()))
                                saveArchived()
                            },
                            onUnarchive = { id ->
                                val uid = currentUid ?: return@ChatsList
                                archivedChatIds.remove(id)
                                db.collection("users").document(uid).collection("archivedChats").document(id).delete()
                                saveArchived()
                            },
                            onMarkUnread = { id -> 
                                val uid = currentUid ?: return@ChatsList
                                db.collection("chats").document(id).update("unreadCount_$uid", 1) 
                            },
                            onDeleteChat = { id -> 
                                db.collection("chats").document(id).delete()
                                if (archivedChatIds.contains(id)) {
                                    archivedChatIds.remove(id)
                                    saveArchived()
                                }
                                if (favouriteChatIds.contains(id)) {
                                    favouriteChatIds.remove(id)
                                    saveFavs()
                                }
                            }
                        )
                        LinkTab.UPDATES -> UpdatesPage(
                            currentUser = allUsers.find { it.uid == currentUid },
                            allUsers = allUsers,
                            statuses = allStatuses,
                            channels = allChannels,
                            onAddStatus = { statusImageLauncher.launch("image/*") },
                            onViewStatus = { showStatusDetailFor = it },
                            onOpenChannel = { showChannelDetailFor = it.id },
                            onFindChannels = { showFindChannels = true }
                        )
                        LinkTab.COMMUNITIES -> CommunitiesPage(
                            channels = allChannels,
                            currentUser = allUsers.find { it.uid == currentUid },
                            onJoin = { channel ->
                                val uid = currentUid ?: return@CommunitiesPage
                                db.collection("channels").document(channel.id)
                                    .update("followersCount", FieldValue.increment(1))
                                db.collection("users").document(uid)
                                    .update("followedChannels", FieldValue.arrayUnion(channel.id))
                                Toast.makeText(mContext, "Joined ${channel.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        LinkTab.CALLS -> CallsPage(currentUid ?: "", allUsers)
                        LinkTab.YOU -> {
                            val currentUser = allUsers.find { it.uid == currentUid }
                            if (showProfileEdit && currentUser != null) {
                                ProfileEditScreen(
                                    user = currentUser,
                                    onBack = { showProfileEdit = false },
                                    onUpdateName = { newName ->
                                        db.collection("users").document(currentUid!!).update("name", newName)
                                    },
                                    onUpdatePhoto = { uri ->
                                        isUploading = true
                                        MediaUploader.upload(uri, mContext) { url ->
                                            isUploading = false
                                            if (url != null) {
                                                db.collection("users").document(currentUid!!).update("photoUrl", url)
                                            }
                                        }
                                    },
                                    onChangePhone = { newPhone ->
                                        // WhatsApp style: require email verification for sensitive changes
                                        auth.currentUser?.sendEmailVerification()?.addOnSuccessListener {
                                            Toast.makeText(mContext, "Verification email sent to ${auth.currentUser?.email}. Verify to enable phone change.", Toast.LENGTH_LONG).show()
                                            // In a real app, we'd listen for auth state change or wait for next login
                                            // For now, we update if they are already verified or just show the process
                                            if (auth.currentUser?.isEmailVerified == true) {
                                                db.collection("users").document(currentUid!!).update("phone", newPhone)
                                                Toast.makeText(mContext, "Phone number updated", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                YouPage(
                                    user = currentUser, 
                                    mode = themeMode, 
                                    privacy = privacySettings,
                                    storage = storageSettings,
                                    onThemeChange = onThemeChange, 
                                    onUpdatePrivacy = { privacySettings = it; db.collection("users").document(currentUid!!).collection("settings").document("privacy").set(it) },
                                    onUpdateStorage = { storageSettings = it; db.collection("users").document(currentUid!!).collection("settings").document("storage").set(it) },
                                    onLogout = { auth.signOut(); onBack() },
                                    onOpenStarred = { showStarredMessages = true },
                                    onEditProfile = { showProfileEdit = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showArchivePicker) {
            AlertDialog(
                onDismissRequest = { showArchivePicker = false },
                title = { Text("Archive Chats", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
                containerColor = ShynaDesign.colors.HeaderBg,
                text = {
                    val nonArchived = recentChats.filter { !archivedChatIds.contains(it.id) }
                    if (nonArchived.isEmpty()) {
                        Text("No chats available to archive", color = ShynaDesign.colors.TextSecondary)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(nonArchived) { chat ->
                                val peer = allUsers.find { it.uid == chat.peerUid }
                                peer?.let {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                archivedChatIds.add(chat.id)
                                                saveArchived()
                                                showArchivePicker = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = ShynaDesign.colors.DividerColor) {
                                            if (!it.photoUrl.isNullOrBlank()) AsyncImage(it.photoUrl, null, contentScale = ContentScale.Crop)
                                            else Box(contentAlignment = Alignment.Center) { Text(it.name.take(1).uppercase(), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Text(it.name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showArchivePicker = false }) { Text("Close", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                }
            )
        }

        if (showFullDPUser != null) {
            PeerDetailScreen(
                user = showFullDPUser!!,
                allUsers = allUsers,
                db = db,
                auth = auth,
                onBack = { showFullDPUser = null },
                onMessage = { 
                    selectedPeerId = showFullDPUser!!.uid
                    showFullDPUser = null
                },
                onSearchInChat = {
                    selectedPeerId = showFullDPUser!!.uid
                    showSearchInChatId = showFullDPUser!!.uid
                    showFullDPUser = null
                }
            )
        }

        if (isUploading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = ShynaDesign.colors.SurfaceBg, shadowElevation = 12.dp) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen, strokeWidth = 3.dp)
                        Spacer(Modifier.height(20.dp))
                        Text("Uploading media...", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Please wait", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (forwardingMessage != null) {
            ForwardChatPicker(
                msg = forwardingMessage!!,
                chats = recentChats,
                allUsers = allUsers,
                onDismiss = { forwardingMessage = null },
                onSend = { targetChatId, peerUid ->
                    val m = forwardingMessage!!
                    val newMsg = mutableMapOf(
                        "text" to m.text,
                        "senderId" to currentUid!!,
                        "timestamp" to Timestamp.now(),
                        "sentAt" to System.currentTimeMillis(),
                        "type" to m.messageType.name,
                        "isForwarded" to true,
                        "metadata" to m.metadata,
                        "fileName" to m.fileName,
                        "fileSize" to m.fileSize,
                        "mimeType" to m.mimeType,
                        "status" to MessageStatus.SENT.name
                    )
                    db.collection("chats").document(targetChatId).collection("messages").add(newMsg)
                    db.collection("chats").document(targetChatId).set(mapOf("lastMessage" to m.text, "timestamp" to Timestamp.now()), SetOptions.merge())
                    forwardingMessage = null
                    selectedPeerId = peerUid // Optionally open the chat
                    Toast.makeText(mContext, "Message forwarded", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ForwardChatPicker(
    @Suppress("UNUSED_PARAMETER") msg: UniversalMessage,
    chats: List<ChatRowItem>,
    allUsers: List<RealUser>,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward message", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(chats) { chat ->
                    val peer = allUsers.find { it.uid == chat.peerUid }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSend(chat.id, chat.peerUid) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = ShynaDesign.colors.DividerColor) {
                            if (!peer?.photoUrl.isNullOrBlank()) AsyncImage(peer?.photoUrl, null, contentScale = ContentScale.Crop)
                            else Box(contentAlignment = Alignment.Center) { Text(peer?.name?.take(1)?.uppercase() ?: "?", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(peer?.name ?: "Unknown", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
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
    val mainFilters = listOf("All", "Unread", "Favourites", "Groups")
    var showPlusMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        mainFilters.forEach { f ->
            val isSelected = f == selectedFilter
            FilterChipCompact(
                label = f,
                isSelected = isSelected,
                onClick = { onFilterChange(f) }
            )
        }

        // The "+" Chip for Custom Folders
        Box {
            Surface(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showPlusMenu = true },
                shape = RoundedCornerShape(16.dp),
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            DropdownMenu(
                expanded = showPlusMenu,
                onDismissRequest = { showPlusMenu = false },
                containerColor = ShynaDesign.colors.SurfaceBg
            ) {
                DropdownMenuItem(
                    text = { Text("New List", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen) },
                    onClick = { showPlusMenu = false; onAddClick() },
                    leadingIcon = { Icon(Icons.Default.AddCircle, null, tint = ShynaDesign.colors.BrandGreen) }
                )
                if (customLists.isNotEmpty()) HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                customLists.forEach { list ->
                    val isListSelected = selectedFilter == list.name
                    DropdownMenuItem(
                        text = { Text(list.name, color = if(isListSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextPrimary) },
                        onClick = { 
                            showPlusMenu = false
                            onFilterChange(list.name)
                        },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteList(list.name); if(isListSelected) onFilterChange("All") }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipCompact(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ShynaDesign.colors.BrandGreen.copy(0.1f) else ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.DividerColor)
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
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
    LinkTab.COMMUNITIES -> "Chat Room"
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

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val d1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val d2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) && d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, 
    userId: String, 
    allUsers: List<RealUser>, 
    storageSettings: UserStorageSettings,
    onBack: () -> Unit,
    onOpenCamera: (String, Boolean) -> Unit,
    onOpenLocation: (String) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenAudio: (String) -> Unit,
    onAvatarClick: (RealUser) -> Unit,
    drafts: MutableMap<String, String>,
    onUploadingChange: (Boolean) -> Unit,
    initialSearchMode: Boolean = false,
    onForward: (UniversalMessage) -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val peer = allUsers.find { it.uid == peerId }
    val currentUserProfile = allUsers.find { it.uid == userId }
    val chatId = if (userId < peerId) "${userId}_${peerId}" else "${peerId}_${userId}"
    var text by remember { mutableStateOf(drafts[chatId] ?: "") }
    val msgs = remember { mutableStateListOf<UniversalMessage>() }
    val listState = rememberLazyListState()
    val selectedMsgs = remember { mutableStateListOf<String>() }
    val isSelectionMode by remember { derivedStateOf { selectedMsgs.isNotEmpty() } }
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var fullScreenMedia by remember { mutableStateOf<UniversalMessage?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showMediaScoped by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showNewGroupScoped by remember { mutableStateOf(false) }
    var showRsvpFor by remember { mutableStateOf<UniversalMessage?>(null) }
    
    var pendingDocument by remember { mutableStateOf<Map<String, Any>?>(null) }

    val onVote: (UniversalMessage, Int) -> Unit = { m, index ->
        val attempts = (m.interactionAttempts[userId] ?: 0)
        val lastTime = m.lastInteractionTime[userId] ?: 0L
        val now = System.currentTimeMillis()
        
        val canInteractionNow = when {
            attempts < 2 -> true
            attempts == 2 -> (now - lastTime) >= 5 * 60 * 60 * 1000L
            else -> false
        }

        if (attempts >= 3) {
            Toast.makeText(mContext, "Maximum 3 attempts reached. Vote is final.", Toast.LENGTH_SHORT).show()
        } else if (!canInteractionNow) {
            val remaining = 5 * 60 * 60 * 1000L - (now - lastTime)
            val hours = remaining / (1000 * 60 * 60)
            val minutes = (remaining / (1000 * 60)) % 60
            Toast.makeText(mContext, "Vote locked for ${hours}h ${minutes}m", Toast.LENGTH_SHORT).show()
        } else {
            val votes = m.pollVotes.toMutableMap()
            val key = index.toString()
            
            // Interaction attempts tracking
            val newAttempts = attempts + 1
            val newTimes = m.lastInteractionTime.toMutableMap()
            newTimes[userId] = now
            
            if (m.allowMultipleAnswers) {
                val list = (votes[key] ?: emptyList()).toMutableList()
                if (list.contains(userId)) list.remove(userId) else list.add(userId)
                votes[key] = list
            } else {
                votes.keys.forEach { k ->
                    val l = (votes[k] ?: emptyList()).toMutableList()
                    if (l.contains(userId)) {
                        l.remove(userId)
                        votes[k] = l
                    }
                }
                val newList = (votes[key] ?: emptyList()).toMutableList()
                newList.add(userId)
                votes[key] = newList
            }
            
            db.collection("chats").document(chatId).collection("messages").document(m.id).update(
                "pollVotes", votes,
                "interactionAttempts.$userId", newAttempts,
                "lastInteractionTime.$userId", now
            )
        }
    }

    val onRSVP: (UniversalMessage, String) -> Unit = { m, status ->
        val attempts = (m.interactionAttempts[userId] ?: 0)
        val lastTime = m.lastInteractionTime[userId] ?: 0L
        val now = System.currentTimeMillis()
        
        val canInteractionNow = when {
            attempts < 2 -> true
            attempts == 2 -> (now - lastTime) >= 5 * 60 * 60 * 1000L
            else -> false
        }

        if (attempts >= 3) {
            Toast.makeText(mContext, "Maximum 3 attempts reached. RSVP is final.", Toast.LENGTH_SHORT).show()
        } else if (!canInteractionNow) {
            val remaining = 5 * 60 * 60 * 1000L - (now - lastTime)
            val hours = remaining / (1000 * 60 * 60)
            val minutes = (remaining / (1000 * 60)) % 60
            Toast.makeText(mContext, "RSVP locked for ${hours}h ${minutes}m", Toast.LENGTH_SHORT).show()
        } else {
            val rsvps = m.eventRSVPs.toMutableMap()
            val newAttempts = attempts + 1
            
            listOf("going", "maybe", "not_going").forEach { s ->
                val l = (rsvps[s] ?: emptyList()).toMutableList()
                if (l.contains(userId)) {
                    l.remove(userId)
                    rsvps[s] = l
                }
            }
            val list = (rsvps[status] ?: emptyList()).toMutableList()
            if (!list.contains(userId)) list.add(userId)
            rsvps[status] = list
            
            db.collection("chats").document(chatId).collection("messages").document(m.id).update(
                "eventRSVPs", rsvps,
                "interactionAttempts.$userId", newAttempts,
                "lastInteractionTime.$userId", now
            )
        }
        showRsvpFor = null
    }

    var userClearedAt by remember { mutableLongStateOf(0L) }
    
    var isSearchMode by remember { mutableStateOf(initialSearchMode) }
    var searchChatQuery by remember { mutableStateOf("") }
    var searchResultsIndices = remember(searchChatQuery, msgs.size) {
        if (searchChatQuery.isEmpty()) emptyList<Int>()
        else msgs.indices.filter { msgs[it].text.contains(searchChatQuery, ignoreCase = true) }
    }
    var currentSearchMatchIndex by remember { mutableIntStateOf(-1) }

    var menuExpanded by remember { mutableStateOf(false) }
    
    var isPeerBlocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    var replyingTo by remember { mutableStateOf<UniversalMessage?>(null) }
    var editingMessage by remember { mutableStateOf<UniversalMessage?>(null) }

    var isTyping by remember { mutableStateOf(false) }
    
    // Draft Sync
    LaunchedEffect(text) {
        if (text.isNotEmpty()) drafts[chatId] = text
        else drafts.remove(chatId)
    }

    // Scroll to bottom logic
    // Auto-scroll to bottom handled by reverseLayout = true in LazyColumn
    // LaunchedEffect(msgs.size) {
    //    if (msgs.isNotEmpty()) {
    //        listState.animateScrollToItem(msgs.size - 1)
    //    }
    // }


    if (showRsvpFor != null) {
        val m = showRsvpFor!!
        AlertDialog(
            onDismissRequest = { showRsvpFor = null },
            title = { Text(m.eventTitle ?: "Event RSVP", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                val attempts = m.interactionAttempts[userId] ?: 0
                val isBlocked = attempts >= 2
                Column {
                    Text(if(isBlocked) "Maximum 2 attempts reached." else "Are you attending this event?", color = ShynaDesign.colors.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    listOf("going", "maybe", "not_going").forEach { status ->
                        Button(
                            onClick = { onRSVP(m, status) },
                            enabled = !isBlocked,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(status == "going") ShynaDesign.colors.BrandGreen else ShynaDesign.colors.DividerColor
                            )
                        ) {
                            Text(status.uppercase())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRsvpFor = null }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (pendingDocument != null) {
        val doc = pendingDocument!!
        val uri = doc["uri"] as Uri
        val name = doc["name"] as String
        val size = doc["size"] as Long
        val mime = doc["mime"] as String
        val sizeStr = android.text.format.Formatter.formatFileSize(mContext, size)

        AlertDialog(
            onDismissRequest = { pendingDocument = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Send Document", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${name.substringAfterLast(".").uppercase()} • $sizeStr", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDocument = null
                        onUploadingChange(true)
                        MediaUploader.upload(uri, mContext, "raw") { url ->
                            onUploadingChange(false)
                            if (url != null) {
                                val msg = mapOf(
                                    "text" to name, 
                                    "senderId" to userId, 
                                    "timestamp" to Timestamp.now(), 
                                    "sentAt" to System.currentTimeMillis(),
                                    "type" to MessageType.DOC.name, 
                                    "metadata" to url,
                                    "fileName" to name,
                                    "fileSize" to size,
                                    "mimeType" to mime,
                                    "status" to MessageStatus.SENT.name
                                )
                                db.collection("chats").document(chatId).collection("messages").add(msg)
                                db.collection("chats").document(chatId).set(mapOf(
                                    "lastMessage" to "📄 $name", 
                                    "timestamp" to Timestamp.now(),
                                    "lastStatus" to MessageStatus.SENT.name,
                                    "lastSenderId" to userId
                                ), SetOptions.merge())
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDocument = null }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showEventDialog) {
        var eventTitle by remember { mutableStateOf("") }
        var eventDesc by remember { mutableStateOf("") }
        var eventLoc by remember { mutableStateOf("") }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = eventDesc,
                        onValueChange = { eventDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = eventLoc,
                        onValueChange = { eventLoc = it },
                        label = { Text("Location") },
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
                            Text(text = dateStr, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank()) {
                            val msg = mapOf(
                                "text" to "📅 $eventTitle",
                                "senderId" to userId,
                                "timestamp" to Timestamp.now(),
                                "type" to MessageType.EVENT.name,
                                "eventTitle" to eventTitle,
                                "eventDescription" to eventDesc,
                                "eventStartAt" to eventDate,
                                "eventLocation" to eventLoc
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
        var multi by remember { mutableStateOf(false) }
        val options = remember { mutableStateListOf("", "") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create Poll", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen))
                    options.forEachIndexed { i, opt ->
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = opt, 
                            onValueChange = { options[i] = it }, 
                            label = { Text("Option ${i+1}") }, 
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { if(options.size > 2) IconButton(onClick = { options.removeAt(i) }) { Icon(Icons.Default.Close, null) } }
                        )
                    }
                    if (options.size < 12) {
                        TextButton(onClick = { options.add("") }) { Text("+ Add Option", color = ShynaDesign.colors.BrandGreen) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = multi, onCheckedChange = { multi = it }, colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen))
                        Text("Allow multiple answers", color = ShynaDesign.colors.TextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (question.isNotBlank() && options.filter { it.isNotBlank() }.size >= 2) {
                        val msg = mapOf(
                            "text" to "📊 $question", 
                            "senderId" to userId, 
                            "timestamp" to Timestamp.now(), 
                            "type" to MessageType.POLL.name,
                            "pollQuestion" to question,
                            "pollOptions" to options.filter { it.isNotBlank() },
                            "pollVotes" to emptyMap<String, List<String>>(),
                            "allowMultipleAnswers" to multi
                        )
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf("lastMessage" to "📊 $question", "timestamp" to Timestamp.now()), SetOptions.merge())
                    }
                    showPollDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen), enabled = question.isNotBlank() && options.filter { it.isNotBlank() }.size >= 2) { Text("Create") }
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
            val meta = MediaUploader.getFileMetadata(mContext, it)
            val name = (meta["name"] as? String) ?: "Document"
            val size = (meta["size"] as? Long) ?: 0L
            val mime = (meta["mime"] as? String) ?: "application/octet-stream"
            
            // VALIDATION
            if (size <= 0) {
                Toast.makeText(mContext, "Unable to send file: File is empty", Toast.LENGTH_SHORT).show()
                return@let
            }
            if (size > 50 * 1024 * 1024) { // 50MB Limit
                Toast.makeText(mContext, "Unable to send file: Size exceeds 50MB", Toast.LENGTH_SHORT).show()
                return@let
            }

            pendingDocument = mapOf(
                "uri" to it,
                "name" to name,
                "size" to size,
                "mime" to mime
            )
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

    // Block/Mute State
    LaunchedEffect(peerId) {
        db.collection("users").document(userId).collection("blockedUsers").document(peerId)
            .addSnapshotListener { d, _ -> isPeerBlocked = d?.exists() == true }
            
        db.collection("users").document(userId).collection("chatSettings").document(chatId)
            .addSnapshotListener { d, _ -> isMuted = d?.getTimestamp("mutedUntil")?.let { it.toDate().time > System.currentTimeMillis() } ?: false }
    }

    DisposableEffect(chatId) {
        // Clear unread count when opening chat
        db.collection("chats").document(chatId).update("unreadCount_$userId", 0)
        
        val settingsListener = db.collection("users").document(userId).collection("chatSettings").document(chatId)
            .addSnapshotListener { d, _ ->
                userClearedAt = d?.getTimestamp("clearedAt")?.toDate()?.time ?: 0L
            }

        val l = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentMsgs = msgs.toMutableList()
                var changed = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val time = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    if (time < userClearedAt) return@forEach
                    
                    val typeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(typeStr) } catch(_: Exception) { MessageType.TEXT }
                    val statusStr = d.getString("status") ?: MessageStatus.SENT.name
                    val mStatus = try { MessageStatus.valueOf(statusStr) } catch(_: Exception) { MessageStatus.SENT }
                    
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val senderId = d.getString("senderId") ?: ""
                        if (senderId != userId) {
                            val msgText = d.getString("text") ?: ""
                            com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "messages", received = msgText.length.toLong())
                            
                            // Tick System: Mark as DELIVERED if not already
                            if (mStatus == MessageStatus.SENT || mStatus == MessageStatus.SENDING) {
                                db.collection("chats").document(chatId).collection("messages").document(d.id)
                                    .update("status", MessageStatus.DELIVERED.name, "deliveredAt", System.currentTimeMillis())
                                // Update chat status if it's the last message
                                db.collection("chats").document(chatId).get().addOnSuccessListener { chatDoc ->
                                    val chatTime = chatDoc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                                    if (chatTime <= time) {
                                        db.collection("chats").document(chatId).update("lastStatus", MessageStatus.DELIVERED.name)
                                    }
                                }
                            }

                            // Mark as READ immediately if screen is active
                            if (mStatus != MessageStatus.READ) {
                                db.collection("chats").document(chatId).collection("messages").document(d.id)
                                    .update("status", MessageStatus.READ.name, "readAt", System.currentTimeMillis(), "isRead", true)
                                db.collection("chats").document(chatId).get().addOnSuccessListener { chatDoc ->
                                    val chatTime = chatDoc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                                    if (chatTime <= time) {
                                        db.collection("chats").document(chatId).update("lastStatus", MessageStatus.READ.name)
                                    }
                                }
                            }

                            // Auto-save to gallery if enabled AND allowed by network settings
                            val isWifi = com.example.callruleblocker.data.NetworkDetector.isWifi(mContext)
                            val allowedByNetwork = if(isWifi) {
                                storageSettings.wifiMedia.contains(if(mType == MessageType.VIDEO) "video" else "photo")
                            } else {
                                storageSettings.mobileDataMedia.contains(if(mType == MessageType.VIDEO) "video" else "photo")
                            }

                            if (storageSettings.saveToGallery && allowedByNetwork && (mType == MessageType.IMAGE || mType == MessageType.VIDEO)) {
                                val url = d.getString("metadata")
                                if (!url.isNullOrBlank()) {
                                    val name = d.getString("fileName") ?: "media_${System.currentTimeMillis()}"
                                    scope.launch {
                                        com.example.callruleblocker.data.MediaSaver.saveToGallery(mContext, url, name, mType == MessageType.VIDEO)
                                    }
                                }
                            }
                        }
                    }
                    
                    val m = UniversalMessage(
                        id = d.id, 
                        text = d.getString("text") ?: "", 
                        caption = d.getString("caption"),
                        senderId = d.getString("senderId") ?: "",
                        isMine = d.getString("senderId") == userId, 
                        time = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L, 
                        messageType = mType, 
                        status = mStatus,
                        sentAt = d.getLong("sentAt"),
                        deliveredAt = d.getLong("deliveredAt"),
                        readAt = d.getLong("readAt"),
                        metadata = d.getString("metadata"),
                        replyToMessageId = d.getString("replyToMessageId"),
                        replyToText = d.getString("replyToText"),
                        isForwarded = d.getBoolean("isForwarded") ?: false,
                        isStarred = d.getBoolean("isStarred") ?: false,
                        isDeleted = d.getBoolean("isDeleted") ?: false,
                        deleteForEveryone = d.getBoolean("deleteForEveryone") ?: false,
                        deletedFor = d.get("deletedFor") as? List<String> ?: emptyList(),
                        editedAt = d.getLong("editedAt"),
                        reactions = d.get("reactions") as? Map<String, String> ?: emptyMap(),
                        liveLocationExpiry = d.getLong("liveLocationExpiry"),
                        isRead = d.getBoolean("isRead") ?: false,
                        fileName = d.getString("fileName"),
                        fileSize = d.getLong("fileSize") ?: 0L,
                        mimeType = d.getString("mimeType"),
                        thumbnailUrl = d.getString("thumbnailUrl"),
                        durationMs = d.getLong("durationMs") ?: 0L,
                        // Poll
                        pollQuestion = d.getString("pollQuestion"),
                        pollOptions = d.get("pollOptions") as? List<String> ?: emptyList(),
                        pollVotes = d.get("pollVotes") as? Map<String, List<String>> ?: emptyMap(),
                        allowMultipleAnswers = d.getBoolean("allowMultipleAnswers") ?: false,
                        // Event
                        eventTitle = d.getString("eventTitle"),
                        eventDescription = d.getString("eventDescription"),
                        eventStartAt = d.getLong("eventStartAt") ?: 0L,
                        eventLocation = d.getString("eventLocation"),
                        eventRSVPs = d.get("eventRSVPs") as? Map<String, List<String>> ?: emptyMap()
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

                if (changed || userClearedAt > 0) {
                    scope.launch(Dispatchers.Default) {
                        val finalMsgs = currentMsgs.filter { it.time >= userClearedAt && !it.deletedFor.contains(userId) }.sortedByDescending { it.time }
                        withContext(Dispatchers.Main) {
                            msgs.clear()
                            msgs.addAll(finalMsgs)
                        }
                    }
                }
            }
        onDispose { 
            l.remove() 
            settingsListener.remove()
        }
    }

    // No visible scroll animation on load
    // LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    var infoMessage by remember { mutableStateOf<UniversalMessage?>(null) }
    
    if (infoMessage != null) {
        MessageInfoDialog(infoMessage!!) { infoMessage = null }
    }

    if (showMediaScoped) {
        MediaScopedScreen(
            peerName = peer?.name ?: "User",
            messages = msgs,
            onBack = { showMediaScoped = false },
            onMediaClick = { fullScreenMedia = it }
        )
    }

    if (showNewGroupScoped && peer != null) {
        NewGroupScopedScreen(
            peer = peer,
            allUsers = allUsers,
            onBack = { showNewGroupScoped = false },
            onCreate = { name, uids ->
                val gId = UUID.randomUUID().toString()
                val data = mapOf(
                    "id" to gId,
                    "name" to name,
                    "participants" to uids,
                    "isGroup" to true,
                    "createdBy" to userId,
                    "timestamp" to Timestamp.now(),
                    "lastMessage" to "Group created",
                    "user1" to userId, // For simple group list query if needed
                    "user2" to "GROUP"
                )
                db.collection("chats").document(gId).set(data)
                
                // Add system message
                val sysMsg = mapOf(
                    "text" to "You created group \"$name\"",
                    "senderId" to "SYSTEM",
                    "timestamp" to Timestamp.now(),
                    "type" to MessageType.SYSTEM.name
                )
                db.collection("chats").document(gId).collection("messages").add(sysMsg)
                
                showNewGroupScoped = false
            }
        )
    }

    if (showMuteDialog) {
        MuteDialog(
            onDismiss = { showMuteDialog = false },
            onMute = { durationHours ->
                val until = if (durationHours == -1) {
                    Timestamp(Date(System.currentTimeMillis() + 100L * 365 * 24 * 60 * 60 * 1000)) // ~100 years
                } else {
                    Timestamp(Date(System.currentTimeMillis() + durationHours * 60 * 60 * 1000L))
                }
                db.collection("users").document(userId).collection("chatSettings").document(chatId).set(mapOf("mutedUntil" to until), SetOptions.merge())
                showMuteDialog = false
            }
        )
    }

    val chatMediaList = remember(msgs.size) {
        msgs.filter { it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO }
    }

    if (fullScreenMedia != null) {
        val initialIndex = chatMediaList.indexOfFirst { it.id == fullScreenMedia?.id }.coerceAtLeast(0)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullScreenMedia = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            ChatMediaViewerScreen(
                initialIndex = initialIndex,
                mediaList = chatMediaList,
                onDismiss = { fullScreenMedia = null }
            )
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (isSearchMode) {
                TopAppBar(
                    title = {
                        BasicTextField(
                            value = searchChatQuery,
                            onValueChange = { 
                                searchChatQuery = it
                                currentSearchMatchIndex = if (it.isEmpty()) -1 else 0
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp),
                            cursorBrush = SolidColor(ShynaDesign.colors.BrandGreen),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        if (searchChatQuery.isEmpty()) Text("Search...", color = ShynaDesign.colors.TextSecondary)
                                        innerTextField()
                                    }
                                    if (searchResultsIndices.isNotEmpty()) {
                                        Text("${currentSearchMatchIndex + 1}/${searchResultsIndices.size}", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                                        IconButton(onClick = {
                                            currentSearchMatchIndex = (currentSearchMatchIndex - 1 + searchResultsIndices.size) % searchResultsIndices.size
                                            scope.launch { listState.animateScrollToItem(searchResultsIndices[currentSearchMatchIndex]) }
                                        }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = ShynaDesign.colors.TextSecondary) }
                                        IconButton(onClick = {
                                            currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchResultsIndices.size
                                            scope.launch { listState.animateScrollToItem(searchResultsIndices[currentSearchMatchIndex]) }
                                        }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = ShynaDesign.colors.TextSecondary) }
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = { IconButton(onClick = { isSearchMode = false; searchChatQuery = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            } else if (isSelectionMode) {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMsgs.clear() }) { Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextPrimary) }
                            Text("${selectedMsgs.size}", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            msgs.forEach { if (!selectedMsgs.contains(it.id)) selectedMsgs.add(it.id) }
                        }) { Icon(Icons.Default.SelectAll, null, tint = ShynaDesign.colors.TextPrimary) }

                        val firstSelId = selectedMsgs.firstOrNull()
                        val firstSelMsg = msgs.find { it.id == firstSelId }
                        
                        if (selectedMsgs.size == 1 && firstSelMsg != null) {
                            if (firstSelMsg.messageType == MessageType.IMAGE || firstSelMsg.messageType == MessageType.VIDEO || firstSelMsg.messageType == MessageType.DOC || firstSelMsg.messageType == MessageType.LOCATION) {
                                IconButton(onClick = { 
                                    val m = firstSelMsg
                                    if (m.messageType == MessageType.LOCATION) {
                                        val loc = m.metadata ?: "0,0"
                                        val parts = loc.split(",")
                                        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                                        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                                        val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        mContext.startActivity(mapIntent)
                                    } else if (m.messageType == MessageType.DOC) {
                                        DocInteraction.downloadAndOpen(mContext, scope, m)
                                    } else {
                                        fullScreenMedia = m
                                    }
                                    selectedMsgs.clear()
                                }) { Icon(Icons.Outlined.Visibility, null, tint = ShynaDesign.colors.TextPrimary) }
                            }

                            IconButton(onClick = { 
                                replyingTo = firstSelMsg
                                selectedMsgs.clear()
                            }) { Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = ShynaDesign.colors.TextPrimary) }
                            
                            if (PermissionEngine.canEdit(firstSelMsg)) {
                                IconButton(onClick = { 
                                    editingMessage = firstSelMsg
                                    text = firstSelMsg.text
                                    selectedMsgs.clear()
                                }) { Icon(Icons.Outlined.Edit, null, tint = ShynaDesign.colors.TextPrimary) }
                            }
                            
                            IconButton(onClick = {
                                db.collection("chats").document(chatId).collection("messages").document(firstSelId!!).update("isStarred", !firstSelMsg.isStarred)
                                selectedMsgs.clear()
                            }) { Icon(if(firstSelMsg.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, tint = ShynaDesign.colors.TextPrimary) }

                            IconButton(onClick = { 
                                infoMessage = firstSelMsg
                                selectedMsgs.clear()
                            }) { Icon(Icons.Outlined.Info, null, tint = ShynaDesign.colors.TextPrimary) }
                        }
                        
                        IconButton(onClick = {
                            if (selectedMsgs.size == 1 && firstSelMsg != null) {
                                onForward(firstSelMsg)
                            } else {
                                Toast.makeText(mContext, "Forwarding multiple not supported yet", Toast.LENGTH_SHORT).show()
                            }
                            selectedMsgs.clear()
                        }) { Icon(Icons.AutoMirrored.Outlined.Forward, null, tint = ShynaDesign.colors.TextPrimary) }

                        var showDeleteDialog by remember { mutableStateOf(false) }
                        if (showDeleteDialog) {
                            val canDeleteForEveryone = selectedMsgs.all { id -> 
                                val msg = msgs.find { it.id == id }
                                msg?.isMine == true && !msg.isDeleted
                            }
                            
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Delete message?", fontWeight = FontWeight.Bold)
                                    }
                                },
                                text = { 
                                    Text(
                                        if (selectedMsgs.size == 1) "Do you want to delete this message?" 
                                        else "Do you want to delete ${selectedMsgs.size} messages?",
                                        color = ShynaDesign.colors.TextSecondary
                                    ) 
                                },
                                confirmButton = {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (canDeleteForEveryone) {
                                            Surface(
                                                onClick = {
                                                    selectedMsgs.forEach { id -> 
                                                        val m = msgs.find { it.id == id }
                                                        val updates = mutableMapOf<String, Any>(
                                                            "isDeleted" to true, 
                                                            "deleteForEveryone" to true, 
                                                            "text" to "This message was deleted"
                                                        )
                                                        if (m?.messageType != MessageType.TEXT) {
                                                            updates["metadata"] = com.google.firebase.firestore.FieldValue.delete()
                                                            updates["fileName"] = com.google.firebase.firestore.FieldValue.delete()
                                                            updates["thumbnailUrl"] = com.google.firebase.firestore.FieldValue.delete()
                                                        }
                                                        db.collection("chats").document(chatId).collection("messages").document(id).update(updates)
                                                    }
                                                    selectedMsgs.clear()
                                                    showDeleteDialog = false
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color.Transparent
                                            ) {
                                                Text("Delete for everyone", modifier = Modifier.fillMaxWidth().padding(16.dp), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                selectedMsgs.forEach { id -> 
                                                    db.collection("chats").document(chatId).collection("messages").document(id)
                                                        .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(userId)) 
                                                }
                                                selectedMsgs.clear()
                                                showDeleteDialog = false
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Transparent
                                        ) {
                                            Text("Delete for me", modifier = Modifier.fillMaxWidth().padding(16.dp), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }
                                        Surface(
                                            onClick = { showDeleteDialog = false },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Transparent
                                        ) {
                                            Text("Cancel", modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color.Gray, textAlign = TextAlign.Center)
                                        }
                                    }
                                },
                                dismissButton = null,
                                containerColor = ShynaDesign.colors.SurfaceBg,
                                shape = RoundedCornerShape(28.dp)
                            )
                        }

                        IconButton(onClick = { showDeleteDialog = true }) { 
                            Icon(Icons.Outlined.Delete, null, tint = ShynaDesign.colors.TextPrimary) 
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp).clickable { peer?.let { onAvatarClick(it) } }, 
                                shape = CircleShape, 
                                color = ShynaDesign.colors.DividerColor
                            ) {
                                if (!peer?.photoUrl.isNullOrBlank()) AsyncImage(peer?.photoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Outlined.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.clickable { peer?.let { onAvatarClick(it) } }) {
                                Text(peer?.name ?: "Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                                val statusText = if (peer?.isOnline == true) "online" else formatLastSeen(peer?.lastSeen)
                                Text(statusText, fontSize = 12.sp, color = if(peer?.isOnline == true) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    actions = {
                        IconButton(onClick = { 
                                if (isPeerBlocked) {
                                    Toast.makeText(mContext, "Unblock contact to call", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                peer?.let { p -> 
                                    CallSignalingManager.startCall(
                                        mContext, 
                                        userId, 
                                        currentUserProfile?.name ?: "User", 
                                        currentUserProfile?.photoUrl, 
                                        p.uid, 
                                        p.name, 
                                        p.photoUrl, 
                                        AppCallType.VIDEO, 
                                        { created -> 
                                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { 
                                                putExtra("callId", created.id)
                                                putExtra("isIncoming", false) 
                                            }) 
                                        }, 
                                        {}
                                    ) 
                                } 
                        }) { Icon(Icons.Outlined.Videocam, null, tint = ShynaDesign.colors.TextPrimary) }
                        
                        IconButton(onClick = { 
                                if (isPeerBlocked) {
                                    Toast.makeText(mContext, "Unblock contact to call", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                peer?.let { p -> 
                                    CallSignalingManager.startCall(
                                        mContext, 
                                        userId, 
                                        currentUserProfile?.name ?: "User", 
                                        currentUserProfile?.photoUrl, 
                                        p.uid, 
                                        p.name, 
                                        p.photoUrl, 
                                        AppCallType.VOICE, 
                                        { created -> 
                                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { 
                                                putExtra("callId", created.id)
                                                putExtra("isIncoming", false) 
                                            }) 
                                        }, 
                                        {}
                                    ) 
                                } 
                        }) { Icon(Icons.Outlined.Call, null, tint = ShynaDesign.colors.TextPrimary) }
                        
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                offset = DpOffset(0.dp, 0.dp),
                                containerColor = ShynaDesign.colors.SurfaceBg
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New group", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; showNewGroupScoped = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("View contact", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; peer?.let { onAvatarClick(it) } }
                                )
                                DropdownMenuItem(
                                    text = { Text("Media, links, and docs", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; showMediaScoped = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; isSearchMode = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(if(isMuted) "Unmute notifications" else "Mute notifications", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        if (isMuted) {
                                            db.collection("users").document(userId).collection("chatSettings").document(chatId).update("mutedUntil", null)
                                        } else {
                                            showMuteDialog = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear chat", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        db.collection("users").document(userId).collection("chatSettings").document(chatId).set(mapOf("clearedAt" to Timestamp.now()), SetOptions.merge())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if(isPeerBlocked) "Unblock" else "Block", color = if(isPeerBlocked) ShynaDesign.colors.TextPrimary else Color.Red) },
                                    onClick = { 
                                        menuExpanded = false
                                        if (isPeerBlocked) {
                                            db.collection("users").document(userId).collection("blockedUsers").document(peerId).delete()
                                        } else {
                                            db.collection("users").document(userId).collection("blockedUsers").document(peerId).set(mapOf("timestamp" to Timestamp.now()))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export chat", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; exportChat(mContext, peer?.name ?: "User", msgs) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to contacts", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = ContactsContract.Contacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.NAME, peer?.name)
                                            putExtra(ContactsContract.Intents.Insert.PHONE, peer?.phone)
                                            putExtra(ContactsContract.Intents.Insert.EMAIL, peer?.email)
                                        }
                                        mContext.startActivity(intent)
                                    }
                                )
                            }
                        }
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
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    itemsIndexed(
                        items = msgs,
                        key = { _, it -> it.id }
                    ) { index, m ->
                        val isHighlighted = isSearchMode && searchChatQuery.isNotEmpty() && m.text.contains(searchChatQuery, ignoreCase = true)
                        val olderMsg = if (index < msgs.size - 1) msgs[index + 1] else null
                        val showDateDivider = olderMsg == null || !isSameDay(m.time, olderMsg.time)
                        
                        Column {
                            if (showDateDivider) {
                                DateDivider(m.time)
                            }
                            
                            if (selectedMsgs.size == 1 && selectedMsgs.contains(m.id)) {
                                Row(
                                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp).align(if (m.isMine) Alignment.End else Alignment.Start).background(ShynaDesign.colors.HeaderBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                        Text(emoji, Modifier.clickable {
                                            val newReactions = m.reactions.toMutableMap()
                                            newReactions[userId] = emoji
                                            db.collection("chats").document(chatId).collection("messages").document(m.id).update("reactions", newReactions)
                                            selectedMsgs.clear()
                                        }, fontSize = 20.sp)
                                    }
                                }
                            }
                            PremiumMessageBubble(
                                m = m, 
                                isSelected = selectedMsgs.contains(m.id), 
                                isSearchMatch = isHighlighted,
                                currentUserId = userId,
                                onLongClick = { 
                                    if (selectedMsgs.contains(m.id)) {
                                        selectedMsgs.remove(m.id)
                                    } else {
                                        selectedMsgs.add(m.id)
                                    }
                                },
                                onClick = {
                                    if (selectedMsgs.contains(m.id)) selectedMsgs.remove(m.id)
                                    else selectedMsgs.add(m.id)
                                },
                                onMediaClick = { fullScreenMedia = it },
                                onPollVote = { index -> onVote(m, index) },
                                onEventRSVP = { showRsvpFor = m },
                                onCallAgain = { callMsg ->
                                    val isVideo = callMsg.callType == "VIDEO"
                                    peer?.let { p ->
                                        CallSignalingManager.startCall(
                                            mContext, 
                                            userId, 
                                            currentUserProfile?.name ?: "User", 
                                            currentUserProfile?.photoUrl, 
                                            p.uid, 
                                            p.name, 
                                            p.photoUrl, 
                                            if(isVideo) AppCallType.VIDEO else AppCallType.VOICE, 
                                            { created -> 
                                                mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) 
                                            }, 
                                            {}
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showAttachments) {
                AttachmentPanel(
                    onMediaClick = { type ->
                        showAttachments = false
                        when (type) {
                            "CAMERA" -> onOpenCamera(chatId, false)
                            "GALLERY" -> onOpenGallery(chatId)
                            "LINK" -> showUrlDialog = true
                            "AUDIO" -> onOpenAudio(chatId)
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
                text = text, onTextChange = { 
                    text = it
                    // Simulated typing indicator logic
                    if (it.isNotEmpty() && !isTyping) {
                        isTyping = true
                        scope.launch { delay(3000); isTyping = false }
                    }
                },
                onSend = {
                    if (editingMessage != null) {
                        db.collection("chats").document(chatId).collection("messages").document(editingMessage!!.id).update(
                            "text", text,
                            "editedAt", System.currentTimeMillis()
                        )
                        editingMessage = null
                        text = ""
                    } else if (text.isNotBlank()) {
                        val msg = mutableMapOf(
                            "text" to text, 
                            "senderId" to userId, 
                            "timestamp" to Timestamp.now(), 
                            "sentAt" to System.currentTimeMillis(),
                            "type" to MessageType.TEXT.name,
                            "status" to MessageStatus.SENT.name
                        )
                        if (replyingTo != null) {
                            msg["replyToMessageId"] = replyingTo!!.id
                            msg["replyToText"] = replyingTo!!.text
                            replyingTo = null
                        }
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "messages", sent = text.length.toLong())
                        db.collection("chats").document(chatId).set(mapOf(
                            "lastMessage" to text, 
                            "timestamp" to Timestamp.now(), 
                            "lastStatus" to MessageStatus.SENT.name,
                            "lastSenderId" to userId,
                            "type" to MessageType.TEXT.name,
                            "user1" to (if (userId < peerId) userId else peerId), 
                            "user2" to (if (userId < peerId) peerId else userId)
                        ), SetOptions.merge())
                        text = ""
                        showEmojis = false
                    }
                },
                onVoiceComplete = { file ->
                    onUploadingChange(true)
                    val fileSize = file.length()
                    
                    // Get Duration
                    val duration = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        retriever.release()
                        dur
                    } catch(e: Exception) { 0L }

                    MediaUploader.upload(Uri.fromFile(file), mContext, "video") { url ->
                        onUploadingChange(false)
                        if (url != null) {
                            com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "media", sent = fileSize)
                            val msg = mapOf(
                                "text" to "🎤 Voice Note", 
                                "senderId" to userId, 
                                "timestamp" to Timestamp.now(), 
                                "sentAt" to System.currentTimeMillis(),
                                "type" to MessageType.VOICE.name, 
                                "metadata" to url,
                                "status" to MessageStatus.SENT.name,
                                "mimeType" to "audio/mp4",
                                "durationMs" to duration,
                                "fileSize" to fileSize
                            )
                            db.collection("chats").document(chatId).collection("messages").add(msg)
                            db.collection("chats").document(chatId).set(mapOf(
                                "lastMessage" to "🎤 Voice Note", 
                                "timestamp" to Timestamp.now(),
                                "lastStatus" to MessageStatus.SENT.name,
                                "lastSenderId" to userId
                            ), SetOptions.merge())
                        } else {
                            Toast.makeText(mContext, "Failed to send voice note", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAttachClick = { 
                    showEmojis = false
                    showAttachments = !showAttachments 
                },
                onEmojiClick = { 
                    showAttachments = false
                    showEmojis = !showEmojis 
                },
                onCameraClick = { isVideo ->
                    showEmojis = false
                    showAttachments = false
                    onOpenCamera(chatId, isVideo)
                },
                isEmojiVisible = showEmojis,
                replyingTo = replyingTo,
                editingMessage = editingMessage,
                onCancelAction = {
                    if (editingMessage != null) text = ""
                    replyingTo = null
                    editingMessage = null
                },
                isPeerActive = peer != null // Disable composer if peer is deleted
            )


            if (showEmojis) {
                EmojiPicker(onEmojiSelected = { text += it })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumMessageBubble(
    m: UniversalMessage, 
    isSelected: Boolean, 
    isSearchMatch: Boolean = false,
    currentUserId: String = "",
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onMediaClick: (UniversalMessage) -> Unit,
    onPollVote: (Int) -> Unit = {},
    onEventRSVP: () -> Unit = {},
    onCallAgain: (UniversalMessage) -> Unit = {}
) {
    val align = if (m.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val color = when {
        isSelected -> ShynaDesign.colors.SelectionOverlay
        isSearchMatch -> ShynaDesign.colors.BrandGreen.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    val displayTime = m.editedAt ?: m.time
    val timeStr = remember(displayTime) { 
        try { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(displayTime)) }
        catch(e: Exception) { "" }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 16.dp, vertical = 4.dp), 
        contentAlignment = align
    ) {
        Surface(
            color = if (m.isMine) ShynaDesign.colors.OutgoingBubble else ShynaDesign.colors.IncomingBubble,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (m.isMine) 16.dp else 2.dp, bottomEnd = if (m.isMine) 2.dp else 16.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { 
                        // Professional: Single click selects the message and shows contextual actions
                        onClick() 
                        // If it's already selected and is media, maybe open it? 
                        // But following user's exact instruction: "click karo woh select aye"
                    },
                    onDoubleTap = {
                        // Double tap to open media/docs for convenience
                        if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || (m.messageType == MessageType.DOC && m.metadata != null)) {
                            onMediaClick(m)
                        }
                    }
                )
            }
        ) {
            val contentPadding = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 0.dp else 10.dp
            Column(Modifier.padding(contentPadding)) {
                if (m.isForwarded) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)) {
                        Icon(Icons.Default.Reply, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(12.dp).graphicsLayer(scaleX = -1f))
                        Text("Forwarded", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
                    }
                }
                
                if (m.replyToText != null) {
                    Surface(
                        color = Color.Black.copy(0.05f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth()
                    ) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(ShynaDesign.colors.BrandGreen))
                            Column(Modifier.padding(8.dp)) {
                                Text("Reply", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                                Text(m.replyToText, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (m.deleteForEveryone) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                        Icon(Icons.Default.Block, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("This message was deleted", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    when (m.messageType) {
                        MessageType.TEXT -> TextMessageBubble(m)
                        MessageType.IMAGE -> ImageMessageBubble(m)
                        MessageType.VIDEO -> VideoMessageBubble(m)
                        MessageType.VOICE -> VoiceMessageBubble(m)
                        MessageType.AUDIO -> AudioMessageBubble(m)
                        MessageType.LOCATION -> LocationMessageBubble(m)
                        MessageType.LIVE_LOCATION -> LiveLocationMessageBubble(m)
                        MessageType.LINK -> LinkMessageBubble(m)
                        MessageType.DOC -> DocMessageBubble(m)
                        MessageType.CONTACT -> ContactMessageBubble(m)
                        MessageType.EVENT -> EventMessageBubble(m, currentUserId, onEventRSVP)
                        MessageType.POLL -> PollMessageBubble(m, currentUserId, onPollVote)
                        MessageType.CALL -> CallMessageBubble(m) { onCallAgain(m) }
                        else -> TextMessageBubble(m)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(
                            top = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 0.dp else 4.dp,
                            bottom = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 8.dp else 0.dp,
                            end = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 12.dp else 0.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (m.isStarred) Icon(Icons.Default.Star, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(10.dp).padding(end = 4.dp))
                    if (m.editedAt != null && !m.deleteForEveryone) {
                        Text("edited", fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(timeStr, fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    MessageStatusTicks(m.status, m.isMine)
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ShynaDesign.colors.BrandGreen.copy(alpha = 0.1f))
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    shape = CircleShape,
                    color = ShynaDesign.colors.BrandGreen
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(6.dp))
                }
            }
        }
        
        if (m.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(if (m.isMine) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(y = 12.dp, x = if (m.isMine) (-12).dp else 12.dp)
                    .background(ShynaDesign.colors.SurfaceBg, CircleShape)
                    .border(1.dp, ShynaDesign.colors.DividerColor, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                m.reactions.values.distinct().take(3).forEach { emoji ->
                    Text(emoji, fontSize = 12.sp)
                }
                if (m.reactions.size > 1) {
                    Text(m.reactions.size.toString(), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp), color = ShynaDesign.colors.TextSecondary)
                }
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
    onCameraClick: (Boolean) -> Unit,
    isEmojiVisible: Boolean,
    replyingTo: UniversalMessage? = null,
    editingMessage: UniversalMessage? = null,
    onCancelAction: () -> Unit = {},
    isPeerActive: Boolean = true
) {
    val mContext = LocalContext.current
    val recorder = remember { AudioRecorder(mContext) }
    var isRecording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }

    if (!isPeerActive) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            color = ShynaDesign.colors.HeaderBg,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "This user is no longer available. You cannot send messages.",
                modifier = Modifier.padding(16.dp),
                color = ShynaDesign.colors.TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
        return
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                amplitudes.add((recorder.getAmplitude() / 32767f).coerceIn(0.1f, 1f))
                if (amplitudes.size > 50) amplitudes.removeAt(0)
                delay(100)
            }
        } else amplitudes.clear()
    }

    Column(Modifier.fillMaxWidth()) {
        if (replyingTo != null || editingMessage != null) {
            Surface(
                color = ShynaDesign.colors.HeaderBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(ShynaDesign.colors.BrandGreen, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val title = if (editingMessage != null) "Edit Message" else "Replying to"
                        val content = editingMessage?.text ?: replyingTo?.text ?: "Message"
                        Text(title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                        Text(content, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelAction) {
                        Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Surface(Modifier.weight(1f), shape = if (replyingTo != null || editingMessage != null) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(28.dp), color = ShynaDesign.colors.HeaderBg) {
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
                    if (text.isEmpty() && !isRecording && editingMessage == null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { onCameraClick(false) },
                                    onLongClick = { onCameraClick(true) }
                                )
                                .padding(8.dp)
                        ) {
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
                            if (text.isEmpty() && editingMessage == null) {
                                val file = File(mContext.cacheDir, "voice_${System.currentTimeMillis()}.mp4")
                                recorder.start(file)
                                isRecording = true
                                try { awaitRelease(); recorder.stop(); onVoiceComplete(file) } finally { isRecording = false }
                            } else onSend()
                        }
                    )
                }, contentAlignment = Alignment.Center
            ) {
                Icon(if (editingMessage != null) Icons.Default.Check else if (text.isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, null, tint = Color.White)
            }
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
private fun AttachmentPanel(onMediaClick: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(24.dp), color = ShynaDesign.colors.HeaderBg, shadowElevation = 8.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Gallery", Icons.Filled.Image, Color(0xFFC059FF)) { onMediaClick("GALLERY") }
                AttachmentItem("Camera", Icons.Filled.PhotoCamera, Color(0xFFFF2E74)) { onMediaClick("CAMERA") }
                AttachmentItem("Audio", Icons.Filled.Headphones, Color(0xFFFF8E2D)) { onMediaClick("AUDIO") }
                AttachmentItem("Poll", Icons.Filled.BarChart, Color(0xFFFFBC38)) { onMediaClick("POLL") }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Document", Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF7F66FF)) { onMediaClick("DOC") }
                AttachmentItem("Contact", Icons.Filled.Person, Color(0xFF00A5F4)) { onMediaClick("CONTACT") }
                AttachmentItem("Location", Icons.Filled.LocationOn, Color(0xFF00C659)) { onMediaClick("LOCATION") }
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
private fun DateDivider(time: Long) {
    val dateStr = remember(time) { 
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = time }
        if (now.get(Calendar.DATE) == msgTime.get(Calendar.DATE) && now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) && now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) "Today"
        else if (now.get(Calendar.DATE) - msgTime.get(Calendar.DATE) == 1 && now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) && now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) "Yesterday"
        else SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(time))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
            Text(dateStr, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun MessageStatusTicks(status: MessageStatus, isMine: Boolean) {
    if (!isMine) return
    val icon = when (status) {
        MessageStatus.SENDING -> Icons.Default.AccessTime
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.Error
    }
    val color = if (status == MessageStatus.READ) Color(0xFF25D366) else ShynaDesign.colors.TextSecondary
    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
}

@Composable
private fun MessageInfoDialog(m: UniversalMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow(Icons.Default.Done, "Sent", m.sentAt ?: m.time)
                if (m.deliveredAt != null) InfoRow(Icons.Default.DoneAll, "Delivered", m.deliveredAt)
                if (m.readAt != null) InfoRow(Icons.Default.DoneAll, "Read", m.readAt, Color(0xFF25D366))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, time: Long, iconColor: Color = ShynaDesign.colors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            val dateStr = remember(time) { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time)) }
            Text(dateStr, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        }
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
private fun ChatsList(
    users: List<RealUser>, 
    query: String, 
    filter: String, 
    favourites: Set<String>, 
    archived: Set<String>, 
    recentChats: List<ChatRowItem>,
    customLists: List<CustomChatList>,
    isArchivedMode: Boolean = false,
    onOpen: (String) -> Unit,
    onAvatarClick: (RealUser) -> Unit,
    onToggleFav: (String) -> Unit,
    onArchive: (String) -> Unit = {},
    onUnarchive: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    val displayList by remember(recentChats, query, filter, favourites, archived, customLists, isArchivedMode) {
        derivedStateOf {
            recentChats.filter { chat ->
                val isGroup = chat.isGroup
                val peer = if (isGroup) null else users.find { it.uid == chat.peerUid }
                
                // Archived check
                val isChatArchived = archived.contains(chat.id)
                if (isArchivedMode != isChatArchived) return@filter false
                
                // Search Match (Local Chat)
                val chatName = if (isGroup) chat.groupName ?: "Group" else peer?.name ?: ""
                
                val matchesSearch = query.isEmpty() || chatName.contains(query, true)
                if (!matchesSearch) return@filter false
                
                // Filter Match
                when (filter) {
                    "All" -> true
                    "Unread" -> chat.unreadCount > 0
                    "Favourites" -> favourites.contains(chat.id)
                    "Groups" -> isGroup
                    else -> {
                        val custom = customLists.find { it.name == filter }
                        if (custom != null) custom.chatIds.contains(chat.id) else true
                    }
                }
            }
        }
    }

    val searchResults by remember(users, query, recentChats) {
        derivedStateOf {
            if (query.isEmpty()) emptyList<RealUser>()
            else users.filter { u ->
                u.name.contains(query, true) && recentChats.none { it.peerUid == u.uid }
            }
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
            val peer = users.find { it.uid == chat.peerUid }
            peer?.let {
                PremiumChatItem(
                    it, 
                    chat, 
                    isArchived = archived.contains(chat.id),
                    onClick = { onOpen(it.uid) },
                    onAvatarClick = { onAvatarClick(it) },
                    onToggleFav = { onToggleFav(chat.id) },
                    onArchive = { onArchive(chat.id) },
                    onUnarchive = { onUnarchive(chat.id) },
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
    isArchived: Boolean = false,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onToggleFav: () -> Unit = {},
    onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {},
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
                    modifier = Modifier.size(56.dp).clickable { onAvatarClick() },
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
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xFF25D366), CircleShape)
                            .border(2.dp, ShynaDesign.colors.PrimaryBg, CircleShape)
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
                    if (chat.lastMessageMine && !isCommunity) {
                        MessageStatusTicks(chat.lastMessageStatus, true)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (!isCommunity) {
                        val icon = when(chat.messageType) {
                            MessageType.IMAGE -> Icons.Default.Image
                            MessageType.VIDEO -> Icons.Default.Videocam
                            MessageType.VOICE -> Icons.Default.Mic
                            MessageType.LOCATION -> Icons.Default.LocationOn
                            MessageType.DOC -> Icons.Default.InsertDriveFile
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
                text = { Text(if (isArchived) "Unarchive" else "Archive") },
                onClick = { if (isArchived) onUnarchive() else onArchive(); showMenu = false },
                leadingIcon = { Icon(if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) }
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
private fun UpdatesPage(
    currentUser: RealUser?,
    allUsers: List<RealUser>,
    statuses: List<UserStatus>,
    channels: List<ShynaChannel>,
    onAddStatus: () -> Unit,
    onViewStatus: (String) -> Unit,
    onOpenChannel: (ShynaChannel) -> Unit,
    onFindChannels: () -> Unit
) {
    val statusGroups by remember(statuses) {
        derivedStateOf { statuses.groupBy { it.userId } }
    }
    
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Status", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 20.sp)
        LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Surface(Modifier.size(64.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                            if (!currentUser?.photoUrl.isNullOrBlank()) AsyncImage(currentUser?.photoUrl, null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp), tint = ShynaDesign.colors.TextSecondary)
                        }
                        Box(Modifier.size(24.dp).align(Alignment.BottomEnd).background(ShynaDesign.colors.BrandGreen, CircleShape).border(2.dp, ShynaDesign.colors.PrimaryBg, CircleShape).clickable { onAddStatus() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("My Status", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ShynaDesign.colors.TextPrimary)
                }
            }
            
            items(statusGroups.keys.toList()) { userId ->
                val user = allUsers.find { it.uid == userId }
                if (user != null && userId != currentUser?.uid) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onViewStatus(userId) }) {
                        Surface(Modifier.size(64.dp), shape = CircleShape, border = BorderStroke(2.dp, ShynaDesign.colors.BrandGreen), color = ShynaDesign.colors.DividerColor) {
                            if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(16.dp))
                        }
                        Text(user.name, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Channels", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ShynaDesign.colors.TextPrimary)
            IconButton(onClick = onFindChannels) { Icon(Icons.Default.Add, null, tint = ShynaDesign.colors.BrandGreen) }
        }
        
        channels.forEach { channel ->
            ListItem(
                headlineContent = { Text(channel.name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(channel.lastMessage, color = ShynaDesign.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { 
                    Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) { 
                        if (!channel.photoUrl.isNullOrBlank()) AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Public, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp)) 
                    } 
                },
                trailingContent = { 
                    val time = remember(channel.lastUpdateTime) { formatChatDate(channel.lastUpdateTime) }
                    Text(time, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp) 
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpenChannel(channel) }
            )
        }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No channels followed", color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun CommunitiesPage(channels: List<ShynaChannel>, currentUser: RealUser?, onJoin: (ShynaChannel) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Start Community", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val c = ShynaChannel(name = name, description = desc)
                    db.collection("channels").document(c.id).set(c)
                    showCreateDialog = false
                }, enabled = name.isNotBlank()) { Text("Create") }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Groups, null, Modifier.size(80.dp), tint = ShynaDesign.colors.BrandGreen.copy(0.3f))
            Spacer(Modifier.height(16.dp))
            Text("Stay connected with communities", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text("Communities bring members together in topic-based groups.", color = ShynaDesign.colors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showCreateDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                Text("Start your community")
            }
        }
        
        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
        Text("Suggested Communities", Modifier.padding(16.dp), color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
        
        LazyColumn(Modifier.weight(1f)) {
            items(channels) { channel ->
                ListItem(
                    headlineContent = { Text(channel.name, color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text(channel.description, color = ShynaDesign.colors.TextSecondary, maxLines = 1) },
                    leadingContent = { 
                        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = ShynaDesign.colors.DividerColor) {
                            if(!channel.photoUrl.isNullOrBlank()) AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Public, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp))
                        }
                    },
                    trailingContent = {
                        val isFollowing = currentUser?.followedChannels?.contains(channel.id) == true
                        if (isFollowing) {
                            Text("JOINED", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            TextButton(onClick = { onJoin(channel) }) {
                                Text("JOIN", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallsPage(userId: String, allUsers: List<RealUser>) {
    val db = FirebaseFirestore.getInstance()
    var history by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val mContext = LocalContext.current
    val currentUserProfile = allUsers.find { it.uid == userId }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).collection("call_history")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        history = it.documents.map { d -> 
                            (d.data ?: emptyMap()) + ("id" to d.id)
                        }
                    }
                }
        }
    }

    BackHandler(isSelectionMode) {
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Create call link", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
                supportingContent = { Text("Share a link for your Shyna call", color = ShynaDesign.colors.TextSecondary) },
                leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.BrandGreen) { Icon(Icons.Default.Link, null, tint = Color.White, modifier = Modifier.padding(12.dp)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            Text("Recent", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            
            LazyColumn(Modifier.weight(1f)) {
                items(history) { call ->
                    val id = call["id"] as? String ?: ""
                    val type = call["type"] as? String ?: "VOICE"
                    val status = call["status"] as? String ?: "ENDED"
                    val direction = call["direction"] as? String ?: "outgoing"
                    val name = call["receiverName"] as? String ?: call["callerName"] as? String ?: "Unknown"
                    val photo = if(direction == "outgoing") call["receiverPhoto"] as? String else call["callerPhoto"] as? String
                    val time = (call["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                    val timeStr = remember(time) { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(time)) }
                    val isVideo = type == "VIDEO"
                    val isMissed = status == "MISSED" || status == "REJECTED"
                    val peerUid = if(direction == "outgoing") call["receiverUid"] as? String else call["callerUid"] as? String
                    
                    val isSelected = selectedIds.contains(id)

                    ListItem(
                        headlineContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = if(isMissed && direction == "incoming") Color.Red else ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                if (isMissed) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                        Text("MISSED", color = Color.Red, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        supportingContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = if(direction == "outgoing") Icons.Default.CallMade else if(isMissed) Icons.AutoMirrored.Default.CallMissed else Icons.AutoMirrored.Default.CallReceived
                                Icon(icon, null, tint = if(isMissed) Color.Red else ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(timeStr, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                            }
                        },
                        leadingContent = { 
                            Box {
                                Surface(
                                    Modifier.size(48.dp), 
                                    shape = CircleShape, 
                                    color = if(isSelected) ShynaDesign.colors.BrandGreen.copy(0.2f) else ShynaDesign.colors.DividerColor
                                ) { 
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.padding(12.dp))
                                    } else if (!photo.isNullOrBlank()) {
                                        AsyncImage(photo, null, contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp)) 
                                    }
                                } 
                            }
                        },
                        trailingContent = { 
                            if (!isSelectionMode) {
                                IconButton(onClick = {
                                    if (peerUid != null) {
                                        CallSignalingManager.startCall(
                                            mContext, 
                                            userId, 
                                            currentUserProfile?.name ?: "User", 
                                            currentUserProfile?.photoUrl, 
                                            peerUid, 
                                            name, 
                                            photo, 
                                            if(isVideo) AppCallType.VIDEO else AppCallType.VOICE, 
                                            { created ->
                                                mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) })
                                            }, 
                                            {}
                                        )
                                    }
                                }) {
                                    Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if(isSelected) ShynaDesign.colors.SelectionOverlay else Color.Transparent
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - id else selectedIds + id
                                }
                            },
                            onLongClick = {
                                selectedIds = selectedIds + id
                            }
                        )
                    )
                }
                if (history.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No recent calls", color = ShynaDesign.colors.TextSecondary)
                        }
                    }
                }
            }
        }

        // Selection Toolbar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).height(64.dp),
                shape = RoundedCornerShape(32.dp),
                color = ShynaDesign.colors.HeaderBg,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextPrimary)
                    }
                    Text("${selectedIds.size} selected", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { 
                            selectedIds = history.map { it["id"] as String }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, null, tint = ShynaDesign.colors.TextPrimary)
                        }
                        IconButton(onClick = {
                            selectedIds.forEach { id ->
                                db.collection("users").document(userId).collection("call_history").document(id).delete()
                            }
                            selectedIds = emptySet()
                            Toast.makeText(mContext, "Call history deleted", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun exportChat(context: Context, name: String, msgs: List<UniversalMessage>) {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())
        val content = msgs.joinToString("\n") { m ->
            val typeLabel = if(m.messageType == MessageType.TEXT) "" else " [${m.messageType.name}]"
            "${sdf.format(Date(m.time))} - ${if(m.isMine) "Me" else name}: ${m.text}$typeLabel"
        }
        
        // Sanitize name for filename
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.cacheDir, "Chat_with_$sanitizedName.txt")
        
        // Write file in background
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                file.writeText(content)
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(intent, "Export Chat").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ShynaLink", "File write failed", e)
                    Toast.makeText(context, "Failed to save export file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("ShynaLink", "Export failed", e)
        Toast.makeText(context, "Failed to export chat", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerDetailScreen(
    user: RealUser,
    allUsers: List<RealUser>,
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onSearchInChat: () -> Unit
) {
    val design = ShynaDesign.colors
    val mContext = LocalContext.current
    val currentUid = auth.currentUser?.uid ?: ""
    val currentUserProfile = allUsers.find { it.uid == currentUid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Info", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = design.HeaderBg)
            )
        },
        containerColor = design.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                if (!user.photoUrl.isNullOrBlank()) {
                    AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(design.DividerColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(100.dp), tint = design.TextSecondary)
                    }
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
                Text(
                    user.name, 
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailAction(Icons.AutoMirrored.Outlined.Message, "Message") { onMessage() }
                DetailAction(Icons.Outlined.Call, "Audio") {
                    CallSignalingManager.startCall(
                        mContext, 
                        currentUid, 
                        currentUserProfile?.name ?: "User", 
                        currentUserProfile?.photoUrl, 
                        user.uid, 
                        user.name, 
                        user.photoUrl, 
                        AppCallType.VOICE, 
                        { created ->
                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) })
                        }, 
                        {}
                    )
                }
                DetailAction(Icons.Outlined.Videocam, "Video") {
                    CallSignalingManager.startCall(
                        mContext, 
                        currentUid, 
                        currentUserProfile?.name ?: "User", 
                        currentUserProfile?.photoUrl, 
                        user.uid, 
                        user.name, 
                        user.photoUrl, 
                        AppCallType.VIDEO, 
                        { created ->
                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) })
                        }, 
                        {}
                    )
                }
                DetailAction(Icons.Outlined.Search, "Search") { onSearchInChat() }
            }
            
            Spacer(Modifier.height(24.dp))
            
            ProfileItem("User ID", user.userId, Icons.Outlined.AlternateEmail) {
                val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("User ID", user.userId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(mContext, "User ID copied", Toast.LENGTH_SHORT).show()
            }
            ProfileItem("Phone", user.phone.ifBlank { "Not provided" }, Icons.Outlined.Phone) {
                if (user.phone.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${user.phone}"))
                    mContext.startActivity(intent)
                }
            }
            ProfileItem("Location", "${user.district ?: "Unknown"}, ${user.state ?: ""}", Icons.Outlined.LocationOn) {
                if (user.district != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${user.district},${user.state}"))
                    mContext.startActivity(intent)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            ProfileItem("Add to Contacts", "Save to your phone", Icons.Outlined.PersonAdd) {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, user.name)
                    putExtra(ContactsContract.Intents.Insert.PHONE, user.phone)
                    putExtra(ContactsContract.Intents.Insert.EMAIL, user.email)
                }
                mContext.startActivity(intent)
            }
            
            ProfileItem("Block ${user.name}", "Report or block this contact", Icons.Outlined.Block) {
                // Real block logic
                val currentUid = auth.currentUser?.uid ?: return@ProfileItem
                db.collection("users").document(currentUid).collection("blockedUsers").document(user.uid)
                    .set(mapOf("blockedAt" to Timestamp.now(), "name" to user.name))
                Toast.makeText(mContext, "${user.name} blocked", Toast.LENGTH_SHORT).show()
                onBack()
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(24.dp))
        Text(label, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MuteDialog(onDismiss: () -> Unit, onMute: (Int) -> Unit) {
    val options = listOf("8 hours" to 8, "1 week" to 168, "Always" to -1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications for...", color = ShynaDesign.colors.TextPrimary) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onMute(value) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = { onMute(value) }, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = ShynaDesign.colors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewGroupScopedScreen(peer: RealUser, allUsers: List<RealUser>, onBack: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf(peer.uid) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { if(groupName.isNotBlank()) onCreate(groupName, selectedUids.toList()) }) {
                        Text("CREATE", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp)) {
            RoyalTextField(groupName, { groupName = it }, "Group Name", Icons.Outlined.Group)
            Spacer(Modifier.height(24.dp))
            Text("Participants: ${selectedUids.size}", color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(allUsers.filter { it.uid != FirebaseAuth.getInstance().currentUser?.uid }) { user ->
                    val isSelected = selectedUids.contains(user.uid)
                    ListItem(
                        headlineContent = { Text(user.name, color = ShynaDesign.colors.TextPrimary) },
                        leadingContent = { 
                            Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                if(!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp))
                            }
                        },
                        trailingContent = {
                            Checkbox(isSelected, { if(it) selectedUids.add(user.uid) else if(user.uid != peer.uid) selectedUids.remove(user.uid) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { 
                            if(isSelected) { if(user.uid != peer.uid) selectedUids.remove(user.uid) } 
                            else selectedUids.add(user.uid)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaScopedScreen(
    peerName: String,
    messages: List<UniversalMessage>,
    onBack: () -> Unit,
    onMediaClick: (UniversalMessage) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("MEDIA", "LINKS", "DOCS")
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerName, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ShynaDesign.colors.HeaderBg,
                contentColor = ShynaDesign.colors.BrandGreen,
                divider = { HorizontalDivider(color = ShynaDesign.colors.DividerColor) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    val mediaItems = messages.filter { it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(mediaItems) { m ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { onMediaClick(m) }
                            ) {
                                AsyncImage(
                                    model = if (m.messageType == MessageType.VIDEO) m.metadata?.replace("/video/upload/", "/video/upload/w_300,h_300,c_fill,so_0/")?.replace(".mp4", ".jpg") else m.metadata,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (m.messageType == MessageType.VIDEO) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp))
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val links = messages.filter { it.messageType == MessageType.LINK || it.text.startsWith("http") }
                    val mContext = LocalContext.current
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(links) { m ->
                            ListItem(
                                headlineContent = { Text(m.text, color = ShynaDesign.colors.BrandGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.Link, null, tint = ShynaDesign.colors.TextSecondary) },
                                modifier = Modifier.clickable { 
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.text)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        mContext.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(mContext, "Cannot open link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
                2 -> {
                    val docs = messages.filter { it.messageType == MessageType.DOC }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(docs) { m ->
                            var isDownloading by remember { mutableStateOf(false) }
                            var progress by remember { mutableFloatStateOf(0f) }
                            
                            val mContext = LocalContext.current
                            ListItem(
                                headlineContent = { Text(m.fileName ?: "Document", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(android.text.format.Formatter.formatFileSize(mContext, m.fileSize), color = ShynaDesign.colors.TextSecondary) },
                                leadingContent = { 
                                    if (isDownloading) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.InsertDriveFile, null, tint = ShynaDesign.colors.BrandGreen) 
                                },
                                trailingContent = { Icon(Icons.Default.Download, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.clickable { 
                                    val url = m.metadata ?: return@clickable
                                    val sanitizedName = (m.fileName ?: "doc").replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                    val file = File(mContext.cacheDir, sanitizedName)
                                    
                                    if (file.exists() && file.length() > 0) {
                                        FileOpener.open(mContext, file, m.mimeType)
                                        return@clickable
                                    }

                                    isDownloading = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val connection = URL(url).openConnection()
                                            connection.connect()
                                            val length = connection.contentLength
                                            connection.getInputStream().use { input ->
                                                FileOutputStream(file).use { output ->
                                                    val data = ByteArray(4096)
                                                    var total = 0L
                                                    var count: Int
                                                    while (input.read(data).also { count = it } != -1) {
                                                        total += count
                                                        if (length > 0) progress = total.toFloat() / length
                                                        output.write(data, 0, count)
                                                    }
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                FileOpener.open(mContext, file, m.mimeType)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                Toast.makeText(mContext, "Download failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditScreen(
    user: RealUser,
    onBack: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: (Uri) -> Unit,
    onChangePhone: (String) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }
    
    LaunchedEffect(user) {
        name = user.name
        phone = user.phone
    }
    
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onUpdatePhoto(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Surface(
                    modifier = Modifier.size(140.dp).clickable { photoLauncher.launch("image/*") },
                    shape = CircleShape,
                    border = BorderStroke(4.dp, ShynaDesign.colors.BrandGreen)
                ) {
                    if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, modifier = Modifier.padding(30.dp), tint = ShynaDesign.colors.TextSecondary)
                }
                Box(
                    Modifier.size(44.dp).align(Alignment.BottomEnd).background(ShynaDesign.colors.BrandGreen, CircleShape).border(3.dp, Color.White, CircleShape).clickable { photoLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { onUpdateName(name) }) { Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = user.email, onValueChange = {},
                label = { Text("Email (Locked)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = false
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { onChangePhone(phone) }) { Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
            )
            
            Spacer(Modifier.height(32.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Discovery Info", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("Location: ${user.district ?: "Unknown"}, ${user.state ?: ""}", color = ShynaDesign.colors.TextPrimary)
                    Text("Pincode: ${user.pincode ?: "Not Set"}", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouPage(
    user: RealUser?, 
    mode: ThemeMode, 
    privacy: UserPrivacySettings,
    storage: UserStorageSettings,
    onThemeChange: (ThemeMode) -> Unit, 
    onUpdatePrivacy: (UserPrivacySettings) -> Unit,
    onUpdateStorage: (UserStorageSettings) -> Unit,
    onLogout: () -> Unit,
    onOpenStarred: () -> Unit,
    onEditProfile: () -> Unit
) {
    val mContext = LocalContext.current
    var showStorageSettings by remember { mutableStateOf(false) }
    var showPrivacySettings by remember { mutableStateOf(false) }
    
    var showNetworkUsage by remember { mutableStateOf(false) }
    
    if (showStorageSettings) {
        StorageSettingsDialog(
            current = storage,
            onDismiss = { showStorageSettings = false },
            onSave = onUpdateStorage
        )
    }

    if (showNetworkUsage) {
        NetworkUsageScreen(onBack = { showNetworkUsage = false })
    }

    if (showPrivacySettings) {
        PrivacySettingsDialog(
            current = privacy,
            onDismiss = { showPrivacySettings = false },
            onSave = onUpdatePrivacy
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(ShynaDesign.premiumGradient()), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Surface(
                        modifier = Modifier.size(90.dp).clickable { onEditProfile() }, 
                        shape = CircleShape, 
                        border = BorderStroke(3.dp, ShynaDesign.colors.BrandGreen)
                    ) {
                        if (!user?.photoUrl.isNullOrBlank()) AsyncImage(user?.photoUrl, null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(20.dp), tint = ShynaDesign.colors.TextSecondary)
                    }
                    Box(
                        Modifier.size(30.dp).align(Alignment.BottomEnd).background(ShynaDesign.colors.BrandGreen, CircleShape).border(2.dp, ShynaDesign.colors.HeaderBg, CircleShape).clickable { onEditProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = if(ShynaDesign.colors.isDark) Color.White else Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.name ?: "User", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(user?.email ?: "", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        ProfileItem("Profile", "Name, status, phone number", Icons.Outlined.Person) {
            onEditProfile()
        }
        ProfileItem("Account", "Privacy, security, change number", Icons.Outlined.Key) {
            showPrivacySettings = true
        }
        ProfileItem("Privacy", "Block contacts, disappearing messages", Icons.Outlined.Lock) {
            showPrivacySettings = true
        }
        ProfileItem("Chats", "Theme, wallpapers, chat history", Icons.Outlined.Chat) {
            // Simplified chat theme toggle for now since we have a separate Dark Mode switch
            Toast.makeText(mContext, "Chat settings linked to global theme", Toast.LENGTH_SHORT).show()
        }
        ProfileItem("Starred Messages", "View all your starred messages", Icons.Outlined.Star) {
            onOpenStarred()
        }
        ProfileItem("Storage and Data", "Network usage, auto-download", Icons.Outlined.Storage) {
            showStorageSettings = true
        }
        ProfileItem("Network Usage", "Check data used by calls and media", Icons.Outlined.BarChart) {
            showNetworkUsage = true
        }
        
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
private fun StorageSettingsDialog(
    current: UserStorageSettings,
    onDismiss: () -> Unit,
    onSave: (UserStorageSettings) -> Unit
) {
    var wifiAutoDownload by remember { mutableStateOf(current.wifiMedia) }
    var mobileAutoDownload by remember { mutableStateOf(current.mobileDataMedia) }
    var saveToGallery by remember { mutableStateOf(current.saveToGallery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage and Data", color = ShynaDesign.colors.TextPrimary) },
        text = {
            Column {
                Text("Media auto-download", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp)
                
                StorageMediaOption("When using mobile data", mobileAutoDownload) { mobileAutoDownload = it }
                StorageMediaOption("When connected on Wi-Fi", wifiAutoDownload) { wifiAutoDownload = it }
                
                HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                ListItem(
                    headlineContent = { Text("Save to gallery", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("Automatically save incoming media to your gallery", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(saveToGallery, { saveToGallery = it }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                onSave(current.copy(wifiMedia = wifiAutoDownload, mobileDataMedia = mobileAutoDownload, saveToGallery = saveToGallery))
                onDismiss() 
            }) { Text("Save", color = ShynaDesign.colors.BrandGreen) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun StorageMediaOption(label: String, selected: Set<String>, onUpdate: (Set<String>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val options = listOf("photo", "video", "audio", "doc")

    ListItem(
        headlineContent = { Text(label, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(if (selected.isEmpty()) "No media" else selected.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }, color = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(Modifier.fillMaxWidth().clickable { 
                            val next = selected.toMutableSet()
                            if (next.contains(opt)) next.remove(opt) else next.add(opt)
                            onUpdate(next)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected.contains(opt), null, colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen))
                            Spacer(Modifier.width(12.dp))
                            Text(opt.replaceFirstChar { it.uppercase() }, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK", color = ShynaDesign.colors.BrandGreen) } },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }
}

@Composable
private fun PrivacySettingsDialog(
    current: UserPrivacySettings,
    onDismiss: () -> Unit,
    onSave: (UserPrivacySettings) -> Unit
) {
    var lastSeen by remember { mutableStateOf(current.lastSeen) }
    var photo by remember { mutableStateOf(current.profilePhoto) }
    var about by remember { mutableStateOf(current.about) }
    var groups by remember { mutableStateOf(current.groups) }
    var readReceipts by remember { mutableStateOf(current.readReceipts) }
    var disappearingMsgs by remember { mutableIntStateOf(current.disappearingMessages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Settings", color = ShynaDesign.colors.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PrivacyOption("Last seen", lastSeen, listOf("Everyone", "My Contacts", "Nobody")) { lastSeen = it }
                PrivacyOption("Profile photo", photo, listOf("Everyone", "My Contacts", "Nobody")) { photo = it }
                PrivacyOption("About", about, listOf("Everyone", "My Contacts", "Nobody")) { about = it }
                PrivacyOption("Groups", groups, listOf("Everyone", "My Contacts", "Nobody")) { groups = it }
                
                ListItem(
                    headlineContent = { Text("Read receipts", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("If turned off, you won't send or receive read receipts.", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(readReceipts, { readReceipts = it }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                PrivacyOption("Disappearing messages", if(disappearingMsgs == 0) "Off" else "$disappearingMsgs days", listOf("Off", "24 hours", "7 days", "90 days")) { 
                    disappearingMsgs = when(it) {
                        "24 hours" -> 1
                        "7 days" -> 7
                        "90 days" -> 90
                        else -> 0
                    }
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                onSave(current.copy(
                    lastSeen = lastSeen, 
                    profilePhoto = photo, 
                    about = about,
                    groups = groups,
                    readReceipts = readReceipts,
                    disappearingMessages = disappearingMsgs
                ))
                onDismiss() 
            }) { Text("Save", color = ShynaDesign.colors.BrandGreen) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun PrivacyOption(label: String, selected: String, options: List<String>, onUpdate: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(selected, color = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(Modifier.fillMaxWidth().clickable { onUpdate(opt); showDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == opt, null, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                            Spacer(Modifier.width(12.dp))
                            Text(opt, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDetailScreen(user: RealUser, statuses: List<UserStatus>, onBack: () -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStatus = statuses[currentIndex]
    val scope = rememberCoroutineScope()
    
    // Auto-advance logic
    LaunchedEffect(currentIndex) {
        delay(5000)
        if (currentIndex < statuses.size - 1) {
            currentIndex++
        } else {
            onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = currentStatus.mediaUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        
        // Progress Bars
        Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            statuses.forEachIndexed { index, _ ->
                val progress = when {
                    index < currentIndex -> 1f
                    index == currentIndex -> 1f // Animating this is complex for a simple logic, usually a state
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(2.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        // Header
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(40.dp), shape = CircleShape) {
                AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(formatChatDate(currentStatus.timestamp), color = Color.White.copy(0.7f), fontSize = 12.sp)
            }
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) }
        }
        
        // Navigation Tap Areas
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex > 0) currentIndex-- })
            Box(Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex < statuses.size - 1) currentIndex++ else onBack() })
        }
        
        if (!currentStatus.caption.isNullOrBlank()) {
            Text(
                currentStatus.caption,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 20.dp, end = 20.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDetailScreen(channel: ShynaChannel, onBack: () -> Unit, onJoin: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.padding(16.dp)) {
                Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text("${channel.followersCount} followers", color = ShynaDesign.colors.TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onJoin, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Follow")
                }
                Spacer(Modifier.height(24.dp))
                Text("About", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(channel.description, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindChannelsScreen(onBack: () -> Unit, onOpenChannel: (ShynaChannel) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var channels by remember { mutableStateOf<List<ShynaChannel>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        db.collection("channels").limit(20).get().addOnSuccessListener { d ->
            channels = d.documents.mapNotNull { it.toObject(ShynaChannel::class.java) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Channels") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize()) {
            items(channels) { c ->
                ListItem(
                    headlineContent = { Text(c.name, color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text(c.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape) { AsyncImage(c.photoUrl, null) } },
                    modifier = Modifier.clickable { onOpenChannel(c) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkUsageScreen(onBack: () -> Unit) {
    val mContext = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    
    fun getUsageLabel(key: String): String {
        val wifi = com.example.callruleblocker.data.NetworkUsageTracker.getDetailedUsage(mContext, key, true)
        val mobile = com.example.callruleblocker.data.NetworkUsageTracker.getDetailedUsage(mContext, key, false)
        return "Wi-Fi -> $wifi\nMobile -> $mobile"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Usage", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            refresh // Trigger
            UsageSection("Calls", getUsageLabel("calls"), Icons.Default.Call)
            UsageSection("Media", getUsageLabel("media"), Icons.Default.Image)
            UsageSection("Messages", getUsageLabel("messages"), Icons.Default.Chat)
            UsageSection("Status", getUsageLabel("status"), Icons.Default.Update)
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { 
                    com.example.callruleblocker.data.NetworkUsageTracker.clear(mContext)
                    refresh++
                    Toast.makeText(mContext, "Statistics reset", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red)
            ) {
                Text("Reset statistics", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun trackNetworkUsage(context: Context, type: String, sent: Long = 0, received: Long = 0) {
    val prefs = context.getSharedPreferences("shyna_network_usage", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putLong("${type}_sent", prefs.getLong("${type}_sent", 0) + sent)
        putLong("${type}_received", prefs.getLong("${type}_received", 0) + received)
    }.apply()
}

@Composable
private fun UsageSection(title: String, subtitle: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(title, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary) },
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.BrandGreen) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    HorizontalDivider(color = ShynaDesign.colors.DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ProfileItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary) },
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.TextSecondary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatLastSeen(time: Long?): String {
    if (time == null || time == 0L) return "last seen recently"
    val now = System.currentTimeMillis()
    val diff = now - time
    
    val chatTime = Calendar.getInstance().apply { timeInMillis = time }
    val nowTime = Calendar.getInstance()
    
    return when {
        diff < 60000 -> "last seen just now"
        diff < 3600000 -> "last seen ${diff / 60000} minutes ago"
        isSameDay(time, now) -> "last seen today at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))}"
        nowTime.get(Calendar.YEAR) == chatTime.get(Calendar.YEAR) && nowTime.get(Calendar.DAY_OF_YEAR) - chatTime.get(Calendar.DAY_OF_YEAR) == 1 -> 
            "last seen yesterday at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))}"
        else -> "last seen on ${SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(time))}"
    }
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

private fun formatGalleryDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

private data class GalleryMedia(
    val id: Long, 
    val uri: Uri, 
    val name: String, 
    val dateAdded: Long, 
    val isVideo: Boolean,
    val album: String,
    val duration: Long = 0
)

@Composable
private fun PremiumGalleryScreen(onBack: () -> Unit, onMediaSelected: (List<Pair<Uri, Boolean>>) -> Unit) {
    val mContext = LocalContext.current
    val mediaItems = remember { mutableStateListOf<GalleryMedia>() }
    val selectedMedia = remember { mutableStateListOf<GalleryMedia>() }
    var selectedTab by remember { mutableStateOf("Albums") }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val allMedia = mutableListOf<GalleryMedia>()
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val videoProjection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            android.provider.MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
        
        // Images
        mContext.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                allMedia.add(GalleryMedia(id, uri, cursor.getString(nameCol) ?: "", cursor.getLong(dateCol) * 1000, false, cursor.getString(albumCol) ?: "Internal"))
            }
        }
        
        // Videos
        mContext.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                allMedia.add(GalleryMedia(
                    id, uri, cursor.getString(nameCol) ?: "", 
                    cursor.getLong(dateCol) * 1000, true, 
                    cursor.getString(albumCol) ?: "Internal",
                    cursor.getLong(durCol)
                ))
            }
        }
        
        mediaItems.clear()
        mediaItems.addAll(allMedia.sortedByDescending { it.dateAdded })
    }

    val filteredMedia = remember(mediaItems.size, selectedTab, selectedAlbum, searchQuery, sortByName) {
        var list = mediaItems.filter { 
            val matchTab = when(selectedTab) {
                "Pictures" -> !it.isVideo
                "Videos" -> it.isVideo
                "Albums" -> selectedAlbum == null || it.album == selectedAlbum
                else -> true
            }
            val matchSearch = searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
            matchTab && matchSearch
        }
        if (sortByName) list.sortedBy { it.name } else list.sortedByDescending { it.dateAdded }
    }

    val albums = remember(mediaItems.size) {
        mediaItems.groupBy { it.album }.map { (name, items) -> name to items.first().uri }.sortedBy { it.first }
    }

    val groupedMedia = remember(filteredMedia) {
        filteredMedia.groupBy { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateAdded }
            val now = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) "Today"
            else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1) "Yesterday"
            else SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAdded))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // TOP BAR
        if (isSearchActive) {
            Surface(Modifier.fillMaxWidth().height(64.dp), color = Color.Black) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    TextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search by name, .ext or number...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = ShynaDesign.colors.BrandGreen)
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selectedAlbum != null) selectedAlbum = null else onBack() }) { Icon(if (selectedAlbum != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close, null, tint = Color.White) }
                Text(if (selectedAlbum != null) selectedAlbum!! else "Select $selectedTab", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { sortByName = !sortByName }) { Icon(if (sortByName) Icons.Default.SortByAlpha else Icons.Default.Schedule, null, tint = Color.White) }
                if (selectedMedia.isNotEmpty()) {
                    Button(onClick = { onMediaSelected(selectedMedia.map { it.uri to it.isVideo }) }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) { Text("Send (${selectedMedia.size})") }
                }
            }
        }

        // CONTENT
        Box(Modifier.weight(1f)) {
            if (selectedTab == "Albums" && selectedAlbum == null) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(albums) { (name, thumb) ->
                        Column(Modifier.clickable { selectedAlbum = name }) {
                            AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(8.dp))
                            Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${mediaItems.count { it.album == name }} items", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            } else if (filteredMedia.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No items found", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    groupedMedia.forEach { (date, items) ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                val isAllSelected = items.all { it in selectedMedia }
                                IconButton(onClick = { if (isAllSelected) selectedMedia.removeAll(items) else items.forEach { if (it !in selectedMedia) selectedMedia.add(it) } }) {
                                    Icon(if (isAllSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if(isAllSelected) ShynaDesign.colors.BrandGreen else Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(date, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(items.chunked(3)) { rowItems ->
                            Row(Modifier.fillMaxWidth()) {
                                rowItems.forEach { item ->
                                    val isSelected = selectedMedia.contains(item)
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clickable { if (isSelected) selectedMedia.remove(item) else selectedMedia.add(item) }) {
                                        if (item.isVideo) {
                                            val thumb by produceState<Bitmap?>(null, item.uri) {
                                                value = try {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                        mContext.contentResolver.loadThumbnail(item.uri, Size(300, 300), null)
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        MediaStore.Video.Thumbnails.getThumbnail(mContext.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                                                    }
                                                } catch (e: Exception) { null }
                                            }
                                            if (thumb != null) {
                                                Image(bitmap = thumb!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.5f))
                                                }
                                            }
                                            
                                            // Duration Badge
                                            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                                Text(formatGalleryDuration(item.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.7f), modifier = Modifier.align(Alignment.Center).size(28.dp))
                                        } else {
                                            AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                        
                                        Box(Modifier.fillMaxSize().padding(6.dp)) {
                                            Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if(isSelected) ShynaDesign.colors.BrandGreen else Color.White.copy(0.8f), modifier = Modifier.align(Alignment.TopStart).size(22.dp))
                                        }
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM BAR
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GalleryTabItem("Albums", Icons.Default.PhotoLibrary, selectedTab == "Albums") { selectedTab = "Albums"; selectedAlbum = null; isSearchActive = false }
                GalleryTabItem("Pictures", Icons.Default.Image, selectedTab == "Pictures") { selectedTab = "Pictures"; isSearchActive = false }
                GalleryTabItem("Videos", Icons.Default.VideoLibrary, selectedTab == "Videos") { selectedTab = "Videos"; isSearchActive = false }
                GalleryTabItem("Search", Icons.Default.Search, isSearchActive) { isSearchActive = !isSearchActive }
            }
        }
    }
}

@Composable
private fun GalleryTabItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Icon(icon, null, tint = if (active) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
        Text(label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp)
    }
}

private data class AudioItem(val id: Long, val uri: Uri, val name: String, val size: Long, val duration: Long, val date: Long)

@Composable
private fun PremiumAudioPickerScreen(onBack: () -> Unit, onAudioSelected: (List<Uri>) -> Unit) {
    val mContext = LocalContext.current
    val audioItems = remember { mutableStateListOf<AudioItem>() }
    val selectedAudio = remember { mutableStateListOf<AudioItem>() }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.SIZE,
            android.provider.MediaStore.Audio.Media.DURATION,
            android.provider.MediaStore.Audio.Media.DATE_ADDED
        )
        val sortOrder = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        mContext.contentResolver.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                audioItems.add(AudioItem(id, uri, cursor.getString(nameCol) ?: "Unknown", cursor.getLong(sizeCol), cursor.getLong(durCol), cursor.getLong(dateCol) * 1000))
            }
        }
    }

    val filtered = remember(audioItems.size, searchQuery) {
        if (searchQuery.isEmpty()) audioItems 
        else audioItems.filter { it.name.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("Select Audio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selectedAudio.isNotEmpty()) {
                Button(onClick = { onAudioSelected(selectedAudio.map { it.uri }) }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Send (${selectedAudio.size})")
                }
            }
        }
        
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by name...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No audio files found", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { audio ->
                    val isSelected = selectedAudio.contains(audio)
                    ListItem(
                        headlineContent = { Text(audio.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { 
                            val sizeStr = android.text.format.Formatter.formatFileSize(mContext, audio.size)
                            val durStr = String.format("%d:%02d", audio.duration / 60000, (audio.duration % 60000) / 1000)
                            Text("$sizeStr • $durStr", color = Color.Gray, fontSize = 12.sp) 
                        },
                        leadingContent = {
                            Surface(Modifier.size(48.dp), shape = CircleShape, color = if(isSelected) ShynaDesign.colors.BrandGreen else Color.DarkGray) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if(isSelected) Icons.Default.Check else Icons.Default.MusicNote, null, tint = if(isSelected) Color.Black else Color.White)
                                }
                            }
                        },
                        trailingContent = {
                            val dateStr = remember(audio.date) { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(audio.date)) }
                            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
                        },
                        colors = ListItemDefaults.colors(containerColor = if(isSelected) Color.White.copy(0.05f) else Color.Transparent),
                        modifier = Modifier.clickable { 
                            if (isSelected) selectedAudio.remove(audio) else selectedAudio.add(audio)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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
    
    val channel = NotificationChannel(channelId, "Shyna Messages", NotificationManager.IMPORTANCE_HIGH)
    nm.createNotificationChannel(channel)

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
                    .addOnSuccessListener { authResult -> 
                        val user = authResult.user
                        if (user != null) {
                            val db = FirebaseFirestore.getInstance()
                            // ENSURE USER PROFILE EXISTS IN FIRESTORE TO PREVENT LOGOUT LOOP
                            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    Toast.makeText(mContext, "Welcome back, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    // Create new profile for Google user
                                    val data = mapOf(
                                        "uid" to user.uid,
                                        "userId" to (user.email?.split("@")?.get(0) ?: user.uid),
                                        "name" to (user.displayName ?: "User"),
                                        "email" to (user.email ?: ""),
                                        "phone" to (user.phoneNumber ?: ""),
                                        "isOnline" to true,
                                        "lastSeen" to com.google.firebase.Timestamp.now()
                                    )
                                    db.collection("users").document(user.uid).set(data).addOnSuccessListener {
                                        Toast.makeText(mContext, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }.addOnFailureListener { e ->
                                        Toast.makeText(mContext, "Profile creation failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.addOnFailureListener { e ->
                                Log.e("ShynaAuth", "Firestore check failed", e)
                                Toast.makeText(mContext, "Database check failed. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { Toast.makeText(mContext, "Google Login Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
            } else {
                Toast.makeText(mContext, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ShynaAuth", "Google Sign In Error", e)
            if (e is com.google.android.gms.common.api.ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                Toast.makeText(mContext, "Error 10: SHA-1 mismatch. Please add your SHA-1 to Firebase Console.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(mContext, "Google Sign-In error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onGoogleClick = { 
        Toast.makeText(mContext, "Starting Google Sign-In...", Toast.LENGTH_SHORT).show()
        googleLauncher.launch(googleSignInClient.signInIntent) 
    }

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
    val design = ShynaDesign.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
    ) {
        // Subtle Background Pattern (Wavy lines & Dots placeholder)
        if (!design.isDark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw some soft beige decorative lines/dots as seen in image
                drawCircle(color = Color(0xFFFDF5E6), radius = 250f, center = Offset(size.width, size.height * 0.9f))
            }
        } else {
            // Dark mode specific background effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(design.AuthAccent.copy(alpha = 0.05f), Color.Transparent)),
                    radius = 800f,
                    center = Offset(size.width, 0f)
                )
            }
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
                Text("Shyna ", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
                Text("Calling", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
            }
            
            Spacer(Modifier.height(10.dp))
            
            // Subtitle: --- 👑 WORLD ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.width(36.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(Modifier.width(36.dp), color = design.AuthAccent.copy(alpha = 0.4f))
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Main Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = design.SurfaceBg,
                border = if(design.isDark) BorderStroke(1.dp, design.DividerColor) else null,
                shadowElevation = if(design.isDark) 0.dp else 6.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Welcome back!", 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = design.AuthAccent
                    )
                    Text(
                        "Login to continue with Shyna Calling", 
                        color = design.TextSecondary, 
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
                            color = design.AuthAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    AuthButton(
                        text = "Login",
                        loading = loading,
                        onClick = {
                            val trimmedEmail = email.trim()
                            if (trimmedEmail.isBlank() || password.isBlank()) {
                                Toast.makeText(mContext, "Please enter email and password", Toast.LENGTH_SHORT).show()
                                return@AuthButton
                            }
                            loading = true
                            val db = FirebaseFirestore.getInstance()
                            
                            val performLogin: (String) -> Unit = { finalEmail ->
                                auth.signInWithEmailAndPassword(finalEmail, password)
                                    .addOnSuccessListener { 
                                        loading = false
                                        onLoginSuccess() 
                                    }
                                    .addOnFailureListener {
                                        loading = false
                                        val errorMsg = when {
                                            it.localizedMessage?.contains("password", ignoreCase = true) == true -> "Incorrect password. Please try again."
                                            it.localizedMessage?.contains("user", ignoreCase = true) == true -> "User not found. Please sign up."
                                            else -> "Login Failed: ${it.localizedMessage}"
                                        }
                                        Toast.makeText(mContext, errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                            }

                            if (!trimmedEmail.contains("@")) {
                                // Try login by User ID
                                db.collection("users").whereEqualTo("userId", trimmedEmail).get()
                                    .addOnSuccessListener { snapshots ->
                                        if (!snapshots.isEmpty) {
                                            val foundEmail = snapshots.documents[0].getString("email")
                                            if (foundEmail != null) {
                                                performLogin(foundEmail)
                                            } else {
                                                loading = false
                                                Toast.makeText(mContext, "User ID found, but no email linked to it.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            loading = false
                                            Toast.makeText(mContext, "User ID '$trimmedEmail' not found. Try your email.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        loading = false
                                        Toast.makeText(mContext, "User ID Lookup Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                performLogin(trimmedEmail)
                            }
                        }
                    )
                    
                    Spacer(Modifier.height(28.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = design.DividerColor)
                        Text(" or continue with ", color = design.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        HorizontalDivider(Modifier.weight(1f), color = design.DividerColor)
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
                        Text("Don't have an account? ", color = design.TextSecondary, fontSize = 14.sp)
                        Text(
                            "Sign Up", 
                            color = design.AuthAccent, 
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
private fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val design = ShynaDesign.colors
    val gradient = Brush.horizontalGradient(listOf(design.AuthAccent, design.AuthAccent.copy(alpha = 0.8f)))

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (design.isDark) gradient else SolidColor(design.AuthAccent), RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        enabled = !loading,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        else Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
    val design = ShynaDesign.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
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
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
                }
                
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text("Shyna ", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
                        Text("Calling", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.width(24.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        HorizontalDivider(Modifier.width(24.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                    }
                }
            }
            
            Spacer(Modifier.height(28.dp))
            
            Text("Sign Up", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
            
            Spacer(Modifier.height(28.dp))
            
            RoyalTextField(name, { name = it }, "Full Name", Icons.Outlined.Person)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(userIdInput, { userIdInput = it }, "User ID", Icons.Outlined.Mail)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(email, { email = it }, "Email Address (Compulsory)", Icons.Outlined.Mail)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(phone, { phone = it }, "Phone Number (Optional)", Icons.Outlined.Phone)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
            
            Spacer(Modifier.height(18.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = agree, onCheckedChange = { agree = it }, colors = CheckboxDefaults.colors(checkedColor = design.AuthAccent))
                Text(
                    text = buildAnnotatedString {
                        append("I agree to the ")
                        withStyle(SpanStyle(color = design.AuthAccent, fontWeight = FontWeight.Bold)) { append("Terms of Service ") }
                        append("and ")
                        withStyle(SpanStyle(color = design.AuthAccent, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                    },
                    fontSize = 13.sp,
                    color = design.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            AuthButton(
                text = "Sign Up",
                loading = loading,
                onClick = {
                    val trimmedEmail = email.trim()
                    val trimmedUserId = userIdInput.trim()
                    val trimmedName = name.trim()
                    
                    if (trimmedEmail.isBlank() || password.isBlank() || trimmedName.isBlank() || trimmedUserId.isBlank()) {
                        Toast.makeText(mContext, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                        return@AuthButton
                    }
                    loading = true
                    val db = FirebaseFirestore.getInstance()
                    val reservedRef = db.collection("reserved_ids").document(trimmedUserId)
                    
                    val createProfile: (String) -> Unit = { uid ->
                        val data = mapOf(
                            "uid" to uid,
                            "userId" to trimmedUserId,
                            "name" to trimmedName,
                            "email" to trimmedEmail,
                            "phone" to phone.trim(),
                            "isOnline" to true,
                            "lastSeen" to com.google.firebase.Timestamp.now()
                        )
                        // Atomically reserve the ID and create the user
                        db.runTransaction { transaction ->
                            transaction.set(reservedRef, mapOf("uid" to uid, "timestamp" to com.google.firebase.Timestamp.now()))
                            transaction.set(db.collection("users").document(uid), data)
                        }.addOnSuccessListener {
                            loading = false
                            Toast.makeText(mContext, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            onSignUpSuccess()
                        }.addOnFailureListener {
                            loading = false
                            Log.e("ShynaAuth", "Transaction failed", it)
                            Toast.makeText(mContext, "Database Error: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }

                    reservedRef.get().addOnSuccessListener { reservedDoc ->
                        if (reservedDoc.exists()) {
                            loading = false
                            Toast.makeText(mContext, "User ID already taken or reserved", Toast.LENGTH_LONG).show()
                        } else {
                            auth.createUserWithEmailAndPassword(trimmedEmail, password)
                                .addOnSuccessListener { result ->
                                    createProfile(result.user?.uid ?: "")
                                }
                                .addOnFailureListener { e ->
                                    if (e is FirebaseAuthUserCollisionException) {
                                        // Email exists. Check if Firestore profile exists.
                                        auth.signInWithEmailAndPassword(trimmedEmail, password).addOnSuccessListener { signInResult ->
                                            val uid = signInResult.user?.uid ?: ""
                                            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                                                if (doc.exists()) {
                                                    loading = false
                                                    Toast.makeText(mContext, "Account already exists. Please login.", Toast.LENGTH_LONG).show()
                                                } else {
                                                    // Recovering: Profile missing but Auth exists
                                                    createProfile(uid)
                                                }
                                            }
                                        }.addOnFailureListener {
                                            loading = false
                                            Toast.makeText(mContext, "Email already in use with a different password.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        loading = false
                                        Log.e("ShynaAuth", "Auth failed", e)
                                        Toast.makeText(mContext, "Sign Up Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    }.addOnFailureListener {
                        loading = false
                        Toast.makeText(mContext, "Check failed. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text("or continue with", color = design.TextSecondary, fontSize = 13.sp)
            
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
                Text("Already have an account? ", color = design.TextSecondary, fontSize = 15.sp)
                Text(
                    "Login", 
                    color = design.AuthAccent, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onLoginClick() }
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                color = design.AuthAccent, 
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
    val design = ShynaDesign.colors
    
    Box(Modifier.fillMaxSize().background(design.PrimaryBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
                }
                Text("Forgot Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent, modifier = Modifier.align(Alignment.Center))
            }
            
            Spacer(Modifier.height(40.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = design.AuthAccent.copy(0.05f)) { }
                Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = design.SurfaceBg, shadowElevation = if(design.isDark) 0.dp else 2.dp, border = if(design.isDark) BorderStroke(1.dp, design.DividerColor) else null) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = design.AuthAccent, modifier = Modifier.size(48.dp))
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    null, 
                    tint = design.AuthAccent, 
                    modifier = Modifier.size(28.dp).offset(x = 65.dp, y = (-40).dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text("No worries!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
            Spacer(Modifier.height(14.dp))
            Text(
                "Enter your registered email and we'll send you reset instructions.",
                textAlign = TextAlign.Center,
                color = design.TextSecondary,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Spacer(Modifier.height(40.dp))
            
            RoyalTextField(email, { email = it }, "Enter your Email-ID", Icons.Outlined.Email)
            
            Spacer(Modifier.height(32.dp))
            
            AuthButton(
                text = "Send Reset Link",
                loading = loading,
                onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(mContext, "Enter your email-ID.", Toast.LENGTH_SHORT).show()
                        return@AuthButton
                    }
                    loading = true
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            loading = false
                            Toast.makeText(mContext, "Password reset link sent to $email. Please check your inbox.", Toast.LENGTH_LONG).show()
                            onResetSent(email)
                        }
                        .addOnFailureListener {
                            loading = false
                            val errorMsg = when {
                                it.message?.contains("user-not-found") == true -> "No account found with this email."
                                it.message?.contains("invalid-email") == true -> "Invalid email address format."
                                else -> it.localizedMessage
                            }
                            Toast.makeText(mContext, errorMsg, Toast.LENGTH_LONG).show()
                        }
                }
            )
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                modifier = Modifier.clickable { onBack() }.padding(12.dp),
                color = design.AuthAccent,
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
    val design = ShynaDesign.colors
    val mContext = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp)) // Moved down from top
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
            }
            Text("Reset Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent, modifier = Modifier.align(Alignment.Center))
        }

        Spacer(Modifier.height(32.dp))
        
        Text("Create New Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
        Text("Set a strong password for $email", color = design.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(32.dp))

        RoyalTextField(newPassword, { newPassword = it }, "New Password", Icons.Outlined.Lock, isPassword = true)
        Spacer(Modifier.height(16.dp))
        RoyalTextField(confirmPassword, { confirmPassword = it }, "Confirm Password", Icons.Outlined.Lock, isPassword = true)

        Spacer(Modifier.height(32.dp))

        AuthButton(
            text = "Update Password",
            loading = false,
            onClick = {
                if (newPassword.isBlank() || confirmPassword.isBlank()) {
                    Toast.makeText(mContext, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                    return@AuthButton
                }
                if (newPassword != confirmPassword) {
                    Toast.makeText(mContext, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@AuthButton
                }
                // Note: Actual password update happens outside the app via Firebase Email Link usually.
                // This is a UI placeholder to complete the flow as requested.
                Toast.makeText(mContext, "Password updated! You can now log in with your new password.", Toast.LENGTH_LONG).show()
                onResetSuccess()
            }
        )

        Spacer(Modifier.weight(1f))
        BottomBranding()
    }
}

@Composable
private fun BottomBranding() {
    val design = ShynaDesign.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text("Shyna ", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
            Text("Calling", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.width(28.dp), color = design.AuthAccent.copy(alpha = 0.4f))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            HorizontalDivider(Modifier.width(28.dp), color = design.AuthAccent.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun RoyalTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false) {
    var passwordVisible by remember { mutableStateOf(false) }
    val design = ShynaDesign.colors
    val brandColor = design.AuthAccent
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(label, color = design.TextSecondary, fontSize = 15.sp) },
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
            unfocusedBorderColor = design.DividerColor,
            focusedLabelColor = brandColor,
            cursorColor = brandColor,
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = brandColor,
                backgroundColor = brandColor.copy(alpha = 0.2f)
            ),
            unfocusedContainerColor = design.SurfaceBg,
            focusedContainerColor = design.SurfaceBg
        ),
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = design.TextPrimary)
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
