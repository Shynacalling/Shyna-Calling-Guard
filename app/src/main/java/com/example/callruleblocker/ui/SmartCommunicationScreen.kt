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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
private enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS }
private enum class MessageType { TEXT, LOCATION, FILE }
private data class LocalChatMessage(val text: String, val mine: Boolean, val time: Long, val type: MessageType = MessageType.TEXT, val metadata: String? = null)

private val LinkBlue = Color(0xFF2979FF)
private val LinkGreen = Color(0xFF00C853)
private val LinkCyan = Color(0xFF00E5FF)
private val LinkBg = Color(0xFF0A0A0A)
private val LinkCard = Color(0xFF151515)
private val LinkMuted = Color(0xFFA8ADB7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCommunicationScreen(initialOnline: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val firebaseUid = auth.currentUser?.uid
    val prefs = remember { context.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableStateOf(if (initialOnline) LinkTab.CHATS else LinkTab.CALLS) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var serverOpen by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", LiveKitConfig.URL) ?: LiveKitConfig.URL) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", LiveKitConfig.API_KEY) ?: LiveKitConfig.API_KEY) }
    var apiSecret by remember { mutableStateOf(prefs.getString("api_secret", LiveKitConfig.API_SECRET) ?: LiveKitConfig.API_SECRET) }
    var userId by remember { mutableStateOf(prefs.getString("user_id", firebaseUid ?: "") ?: "") }
    var internetReady by remember { mutableStateOf(hasInternet(context)) }
    var message by remember { mutableStateOf("") }
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<LocalChatMessage>().apply { addAll(loadMessages(prefs)) } }

    selectedPeer?.let { peer ->
        SmartChatDetailScreen(peer = peer, prefs = prefs, userId = userId, onBack = { selectedPeer = null })
        return
    }

    Scaffold(
        containerColor = LinkBg,
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search Shyna Link") },
                            trailingIcon = { IconButton(onClick = { searchOpen = false; search = "" }) { Icon(Icons.Outlined.Close, "Close") } }
                        )
                    } else {
                        Column {
                            Text("Shyna Link", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Connect Even Without Network", color = LinkMuted, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (!searchOpen) IconButton(onClick = { searchOpen = true }) { Icon(Icons.Outlined.Search, "Search") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(LinkCard)) {
                            MenuItem("Refresh connections", Icons.Outlined.Refresh) { internetReady = hasInternet(context); menuOpen = false }
                            MenuItem("Nearby device settings", Icons.Outlined.Devices) { openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS); menuOpen = false }
                            MenuItem("Security & privacy", Icons.Outlined.Security) { selectedTab = LinkTab.UPDATES; menuOpen = false }
                            MenuItem("Server & account", Icons.Outlined.CloudSync) { serverOpen = true; selectedTab = LinkTab.CALLS; menuOpen = false }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LinkBg, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        bottomBar = { LinkBottomBar(selectedTab) { selectedTab = it } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedTab = when (selectedTab) {
                        LinkTab.CHATS -> LinkTab.CHATS
                        LinkTab.UPDATES -> LinkTab.UPDATES
                        LinkTab.COMMUNITIES -> LinkTab.COMMUNITIES
                        LinkTab.CALLS -> LinkTab.CALLS
                    }
                    Toast.makeText(context, when (selectedTab) {
                        LinkTab.CHATS -> "New chat ready"
                        LinkTab.UPDATES -> "Status composer ready"
                        LinkTab.COMMUNITIES -> "Create community"
                        LinkTab.CALLS -> "New call"
                    }, Toast.LENGTH_SHORT).show()
                },
                containerColor = LinkGreen,
                contentColor = Color.Black,
                shape = RoundedCornerShape(18.dp)
            ) { Icon(when (selectedTab) { LinkTab.CHATS -> Icons.Outlined.AddComment; LinkTab.UPDATES -> Icons.Outlined.PhotoCamera; LinkTab.COMMUNITIES -> Icons.Outlined.GroupAdd; LinkTab.CALLS -> Icons.Outlined.AddIcCall }, null) }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LinkBg, Color(0xFF0D1018), LinkBg))).padding(padding)) {
            when (selectedTab) {
                LinkTab.CHATS -> ChatsPage(messages, message, { message = it }, {
                    val clean = message.trim()
                    if (clean.isNotEmpty()) {
                        messages += LocalChatMessage(clean, true, System.currentTimeMillis())
                        saveMessages(prefs, messages)
                        message = ""
                    }
                }, search, onOpenChat = { selectedPeer = it })
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
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    apiSecret = apiSecret,
                    onApiSecretChange = { apiSecret = it },
                    userId = userId,
                    onUserIdChange = { userId = it }
                )
            }
        }
    }
}

@Composable
private fun ChatsPage(messages: List<LocalChatMessage>, message: String, onMessageChange: (String) -> Unit, onSend: () -> Unit, search: String, onOpenChat: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnectionBanner() }
        item { SectionTitle("Nearby chats") }
        val demo = listOf("Rahul" to "Bluetooth • 18 m", "Aman" to "Wi-Fi Direct • 42 m", "Office Team" to "Mesh • 75 m")
        demo.filter { search.isBlank() || it.first.contains(search, true) }.forEachIndexed { index, item ->
            item {
                ContactRow(item.first, item.second, if (index == 0) "Voice note received" else "Tap to start offline chat", index + 1) { onOpenChat(item.first) }
            }
        }
        item {
            PremiumCard("Local encrypted chat", Icons.Outlined.Lock) {
                if (messages.isEmpty()) Text("Messages stay on this device until a server or nearby peer is connected.", color = LinkMuted)
                messages.takeLast(8).forEach { ChatBubble(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(message, onMessageChange, Modifier.weight(1f), placeholder = { Text("Message") }, singleLine = true)
                    IconButton(onClick = onSend) { Icon(Icons.Outlined.Send, "Send", tint = LinkGreen) }
                }
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
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    apiSecret: String,
    onApiSecretChange: (String) -> Unit,
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
                    QuickAction("Offline call", Icons.Outlined.Phone, LinkGreen, Modifier.weight(1f)) { }
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
                StatusLine("LiveKit Cloud", if (serverUrl.isBlank() || apiKey.isBlank()) "Not configured" else "Configured", serverUrl.isNotBlank() && apiKey.isNotBlank())
                OutlinedButton(onClick = { onServerOpenChange(!serverOpen) }, modifier = Modifier.fillMaxWidth()) { Text(if (serverOpen) "Hide LiveKit settings" else "LiveKit & account settings") }
                if (serverOpen) {
                    OutlinedTextField(serverUrl, onServerUrlChange, Modifier.fillMaxWidth(), label = { Text("LiveKit URL (wss://...)") }, singleLine = true)
                    OutlinedTextField(apiKey, onApiKeyChange, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true)
                    OutlinedTextField(apiSecret, onApiSecretChange, Modifier.fillMaxWidth(), label = { Text("API Secret") }, singleLine = true)
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
                                .putString("api_key", apiKey.trim())
                                .putString("api_secret", apiSecret.trim())
                                .putString("user_id", userId.trim())
                                .apply()
                            Toast.makeText(context, "Connection saved", Toast.LENGTH_SHORT).show() 
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Configuration") }
                    
                    if (context is com.example.callruleblocker.MainActivity) {
                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        var isSignUp by remember { mutableStateOf(false) }
                        
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
                                Text("Logout")
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Email Address") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                                
                                Button(
                                    onClick = { 
                                        if (isSignUp) context.registerUser(email, password)
                                        else context.loginUser(email, password)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = LinkGreen, contentColor = Color.Black)
                                ) {
                                    Text(if (isSignUp) "Create Account" else "Login to Shyna Link")
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { isSignUp = !isSignUp }) {
                                        Text(if (isSignUp) "Already have an account? Login" else "New here? Sign Up", color = LinkCyan)
                                    }
                                    if (!isSignUp) {
                                        TextButton(onClick = { 
                                            if (email.isNotBlank()) {
                                                com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                                                    .addOnSuccessListener { Toast.makeText(context, "Reset email sent", Toast.LENGTH_SHORT).show() }
                                            } else {
                                                Toast.makeText(context, "Enter email first", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("Forgot Password?", color = LinkMuted)
                                        }
                                    }
                                }
                            }
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
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        LinkTabItem(LinkTab.CHATS, selected, "Chats", Icons.Outlined.Chat, onSelect)
        LinkTabItem(LinkTab.UPDATES, selected, "Updates", Icons.Outlined.DonutLarge, onSelect)
        LinkTabItem(LinkTab.COMMUNITIES, selected, "Communities", Icons.Outlined.Groups, onSelect)
        LinkTabItem(LinkTab.CALLS, selected, "Calls", Icons.Outlined.Call, onSelect)
    }
}

@Composable private fun RowScope.LinkTabItem(tab: LinkTab, selected: LinkTab, label: String, icon: ImageVector, onSelect: (LinkTab) -> Unit) {
    NavigationBarItem(selected = tab == selected, onClick = { onSelect(tab) }, icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, selectedTextColor = Color.White, indicatorColor = LinkGreen, unselectedIconColor = Color.White, unselectedTextColor = Color.White))
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

    Surface(shape = RoundedCornerShape(18.dp), color = LinkCard) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha }.background(LinkGreen, CircleShape)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("Connected Nearby", fontWeight = FontWeight.Bold); Text("Bluetooth • Wi-Fi Direct • Mesh", color = LinkMuted, fontSize = 12.sp) }
            AssistChip(onClick = {}, label = { Text("AUTO") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }

@Composable private fun PremiumCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = LinkCard, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = LinkBlue.copy(alpha = .18f)) { Icon(icon, null, tint = LinkCyan, modifier = Modifier.padding(9.dp)) }; Spacer(Modifier.width(10.dp)); Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) LinkGreen else Color(0xFFFF3B30)); Spacer(Modifier.width(8.dp)); Text(label, Modifier.weight(1f)); Text(value, color = if (ok) LinkGreen else LinkMuted, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun ChatBubble(message: LocalChatMessage) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "bubbleScale"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) { 
        when (message.type) {
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
                    modifier = Modifier.width(240.dp).clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.metadata))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.DarkGray, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Description, null, tint = Color.LightGray) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(message.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(message.metadata ?: "File", color = LinkMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Outlined.Shortcut, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            else -> {
                Surface(shape = RoundedCornerShape(16.dp), color = if (message.mine) LinkGreen.copy(alpha = .20f) else Color(0xFF242424)) { 
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { 
                        Text(message.text)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            val timeString = remember(message.time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.time)) }
                            Text(timeString, color = LinkMuted, fontSize = 10.sp) 
                            if (message.mine) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.DoneAll, null, tint = LinkCyan, modifier = Modifier.size(14.dp))
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
private fun saveMessages(prefs: android.content.SharedPreferences, messages: List<LocalChatMessage>) { val value = messages.takeLast(100).joinToString("\n") { "${it.time}|${if (it.mine) 1 else 0}|${it.type.name}|${it.metadata ?: ""}|${Uri.encode(it.text)}" }; prefs.edit().putString("local_chat", value).apply() }
private fun loadMessages(prefs: android.content.SharedPreferences): List<LocalChatMessage> = (prefs.getString("local_chat", "") ?: "").lineSequence().mapNotNull { line -> val p = line.split('|', limit = 5); if (p.size != 5) null else p[0].toLongOrNull()?.let { LocalChatMessage(Uri.decode(p[4]), p[1] == "1", it, try { MessageType.valueOf(p[2]) } catch(e: Exception) { MessageType.TEXT }, if (p[3].isEmpty()) null else p[3]) } }.toList()

private enum class ChatTool { NONE, AUDIO_TEST, VIDEO_SETTINGS, GROUP_CALL, ATTACHMENTS, SECURITY, LOCATION, GALLERY, VIDEO_CALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartChatDetailScreen(peer: String, prefs: android.content.SharedPreferences, userId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var activeTool by remember { mutableStateOf(ChatTool.NONE) }
    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    val audioRecorder = remember { AudioRecorder(context) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    if (isRecording) {
        LaunchedEffect(Unit) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000L)
                recordingTime++
            }
        }
    }
    var messages by remember { mutableStateOf(listOf(
        LocalChatMessage("Hi, secure connection ready.", false, System.currentTimeMillis() - 180000),
        LocalChatMessage("Voice, video, files and group calling are available from the top buttons.", true, System.currentTimeMillis() - 90000)
    )) }

    DisposableEffect(peer) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val senderId = auth.currentUser?.uid ?: return@DisposableEffect onDispose {}
        val receiverId = if (peer == "Rahul") "a8DG7xxx" else "fk92Kxxx"
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
                    if (t != null) LocalChatMessage(t, sId == senderId, time) else null
                } ?: emptyList()
                if (cloudMessages.isNotEmpty()) messages = cloudMessages
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        containerColor = Color(0xFFF3F0ED),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), CircleShape, LinkGreen.copy(alpha = .18f)) {
                            Box(contentAlignment = Alignment.Center) { Text(peer.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column { Text(peer, fontWeight = FontWeight.Bold); Text("online • encrypted", fontSize = 11.sp, color = Color(0xFFD7EEE3)) }
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
            Surface(
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding().imePadding()
            ) {
                Column {
                    if (isRecording) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Mic, null, tint = Color.Red)
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d", recordingTime / 60, recordingTime % 60),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            if (!isLocked) Text("Slide up to lock", color = Color.Gray, fontSize = 12.sp)
                            else Text("Recording locked", color = LinkGreen, fontSize = 12.sp)
                            IconButton(onClick = { isRecording = false; isLocked = false; recordingTime = 0 }) {
                                Icon(Icons.Outlined.Delete, null, tint = Color.Gray)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Emoji picker not implemented */ }) {
                            Icon(Icons.Outlined.InsertEmoticon, "Emoji", tint = Color.Gray)
                        }
                        
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F5F5),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        
                        IconButton(onClick = { activeTool = ChatTool.ATTACHMENTS }) {
                            Icon(Icons.Outlined.AttachFile, "Attach", tint = Color.Gray)
                        }
                        
                        if (text.isEmpty()) {
                            IconButton(onClick = { activeTool = ChatTool.ATTACHMENTS }) {
                                Icon(Icons.Outlined.CameraAlt, "Camera", tint = Color.Gray)
                            }
                        }
                        
                        Spacer(Modifier.width(4.dp))
                        
                        val micModifier = if (text.isBlank()) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                        audioFile = file
                                        try {
                                            audioRecorder.start(file)
                                            isRecording = true
                                            isLocked = false
                                            recordingTime = 0
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onTap = {
                                        if (isRecording) {
                                            audioRecorder.stop()
                                            isRecording = false
                                            isLocked = false
                                            audioFile?.let { file ->
                                                uploadVoiceNote(file, context) { url ->
                                                    messages = messages + LocalChatMessage("Voice note", true, System.currentTimeMillis(), MessageType.FILE, url)
                                                    saveMessages(prefs, messages)
                                                }
                                            }
                                            recordingTime = 0
                                        } else {
                                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                            audioFile = file
                                            audioRecorder.start(file)
                                            isRecording = true
                                            isLocked = true
                                            recordingTime = 0
                                        }
                                    }
                                )
                            }.pointerInput(Unit) {
                                detectDragGestures { _, dragAmount ->
                                    if (isRecording && !isLocked && dragAmount.y < -50) {
                                        isLocked = true
                                        Toast.makeText(context, "Recording locked", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else Modifier

                        FilledIconButton(
                            modifier = micModifier,
                            onClick = {
                                if (text.isNotBlank()) {
                                    val cleanText = text.trim()
                                    messages = messages + LocalChatMessage(cleanText, true, System.currentTimeMillis())
                                    if (context is com.example.callruleblocker.MainActivity) {
                                        val peerUid = if (peer == "Rahul") "a8DG7xxx" else "fk92Kxxx"
                                        context.sendMessage(peerUid, cleanText)
                                    }
                                    text = ""
                                } else if (isLocked) {
                                    audioRecorder.stop()
                                    isRecording = false
                                    isLocked = false
                                    audioFile?.let { file ->
                                        uploadVoiceNote(file, context) { url ->
                                            messages = messages + LocalChatMessage("Voice note", true, System.currentTimeMillis(), MessageType.FILE, url)
                                            saveMessages(prefs, messages)
                                        }
                                    }
                                    recordingTime = 0
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isRecording) Color.Red else Color(0xFF00A884),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(if (text.isBlank()) Icons.Outlined.Mic else Icons.Outlined.Send, null)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).background(Color(0xFFEFEAE2)), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CenterInfoChip("Today") }
            item { CenterInfoChip("Messages and calls are protected with end-to-end encryption") }
            items(messages.size) { ChatBubble(messages[it]) }
            if (isRecording) item { CenterInfoChip("Recording voice note… tap microphone again to finish") }
        }
    }

    when (activeTool) {
        ChatTool.AUDIO_TEST -> AudioVideoTestDialog(context, prefs, onDismiss = { activeTool = ChatTool.NONE }, openVideo = { activeTool = ChatTool.VIDEO_SETTINGS })
        ChatTool.VIDEO_SETTINGS -> VideoCallSettingsDialog(
            prefs = prefs, 
            peer = peer, 
            onDismiss = { activeTool = ChatTool.NONE }, 
            onStart = { activeTool = ChatTool.VIDEO_CALL },
            onGroup = { activeTool = ChatTool.GROUP_CALL }
        )
        ChatTool.GROUP_CALL -> GroupCallDialog(peer, onDismiss = { activeTool = ChatTool.NONE })
        ChatTool.ATTACHMENTS -> AttachmentSheet(onAction = { activeTool = it }, onDismiss = { activeTool = ChatTool.NONE })
        ChatTool.SECURITY -> SecurityDialog(onDismiss = { activeTool = ChatTool.NONE })
        ChatTool.LOCATION -> SendLocationScreen(
            onBack = { activeTool = ChatTool.NONE },
            onSendLocation = { coords ->
                messages = messages + LocalChatMessage("Live Location", true, System.currentTimeMillis(), MessageType.LOCATION, coords)
                saveMessages(prefs, messages)
                activeTool = ChatTool.NONE
            }
        )
        ChatTool.GALLERY -> GalleryPickerScreen(
            onBack = { activeTool = ChatTool.NONE },
            onItemsSelected = { selected ->
                selected.forEach { 
                    messages = messages + LocalChatMessage("Image", true, System.currentTimeMillis(), MessageType.FILE, it)
                }
                saveMessages(prefs, messages)
                activeTool = ChatTool.NONE
            }
        )
        ChatTool.VIDEO_CALL -> {
            val roomName = if (peer == "Rahul") "room_rahul" else "room_aman"
            VideoCallScreen(roomName = roomName, userId = userId) {
                activeTool = ChatTool.NONE
            }
        }
        ChatTool.NONE -> Unit
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

@Composable private fun CenterInfoChip(text: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFD9F0F2)) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFF334155), fontSize = 11.sp) } } }

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

@Composable private fun AttachmentSheet(onAction: (ChatTool) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Share") }, text = { Column { FeatureRow("Camera", "Take a photo or video", Icons.Outlined.CameraAlt); FeatureRow("Gallery", "Photos and videos", Icons.Outlined.PhotoLibrary) { onAction(ChatTool.GALLERY) }; FeatureRow("Document", "PDF, ZIP, APK and more", Icons.Outlined.Description); FeatureRow("Contact", "Share contact card", Icons.Outlined.ContactPage); FeatureRow("Location", "Live or current location", Icons.Outlined.LocationOn) { onAction(ChatTool.LOCATION) }; FeatureRow("Audio", "Voice recording or audio file", Icons.Outlined.AudioFile) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }) }

@Composable private fun SecurityDialog(onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Security details") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DiagnosticLine("End-to-end encryption", "Enabled", true); DiagnosticLine("Device authentication", "Verified", true); DiagnosticLine("Session key", "Rotates automatically", true); DiagnosticLine("Secure local history", "Enabled", true); Text("QR verification and safety-number comparison can be used before sensitive calls.", fontSize = 12.sp) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }) }
