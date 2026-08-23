package com.example.callruleblocker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.GlobalCallState
import com.example.callruleblocker.call.MainCallType
import com.example.callruleblocker.call.ShynaHandshake
import kotlinx.coroutines.delay

private enum class OfflineMode { HOME, BLUETOOTH, WIFI_DIRECT, RADIO, NEARBY_USERS, USER_DETAILS, CHAT, CALL }

data class OfflineUser(
    val id: String,
    val name: String,
    val status: String,
    val transport: List<String>,
    val handshake: ShynaHandshake? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineCallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(OfflineMode.HOME) }
    var selectedUser by remember { mutableStateOf<OfflineUser?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val nearbyUsers = remember { mutableStateListOf<OfflineUser>() }

    // REAL DISCOVERY SIMULATION (Triggers on screen enter)
    LaunchedEffect(mode) {
        if (mode == OfflineMode.BLUETOOTH || mode == OfflineMode.WIFI_DIRECT || mode == OfflineMode.NEARBY_USERS) {
            nearbyUsers.clear()
            // In a production build, this would register real BroadcastReceivers
            // for Bluetooth and WifiP2pManager peers.
            delay(1500)
            nearbyUsers.add(OfflineUser("dev_1", "Real Nearby User A", "Available", listOf("Bluetooth")))
            nearbyUsers.add(OfflineUser("dev_2", "Real Nearby User B", "Available", listOf("Wi-Fi Direct")))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getModeTitle(mode), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == OfflineMode.HOME) onBack()
                        else mode = OfflineMode.HOME
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary)
                    }
                },
                actions = {
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
                                        DropdownMenuItem(
                                            text = { Text("Shyna Link", color = ShynaDesign.colors.TextPrimary) },
                                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, null, tint = ShynaDesign.colors.BrandGreen) },
                                            onClick = { 
                                                menuExpanded = false
                                                CallStateController.setPrimaryFeature(MainCallType.SHYNA_LINK)
                                                onBack() 
                                            }
                                        )
                                    }
                                    MainCallType.OFFLINE_CALL -> {}
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (mode) {
                OfflineMode.HOME -> OfflineHomeUI { mode = it }
                OfflineMode.BLUETOOTH -> ActiveUsersScreen("Bluetooth", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.WIFI_DIRECT -> ActiveUsersScreen("Wi-Fi Direct", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.RADIO -> RadioScreenUI()
                OfflineMode.NEARBY_USERS -> ActiveUsersScreen("All Nearby Users", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.USER_DETAILS -> UserSelectionScreen(selectedUser!!, onChat = { mode = OfflineMode.CHAT }, onCall = { mode = OfflineMode.CALL }, onCancel = { mode = OfflineMode.HOME })
                OfflineMode.CHAT -> OfflineChatUI(selectedUser!!)
                OfflineMode.CALL -> OfflineCallingUI(selectedUser!!)
            }
        }
    }
}

@Composable
private fun OfflineHomeUI(onModeSelect: (OfflineMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(ShynaDesign.colors.HeaderBg).padding(16.dp)) {
            Column {
                Text("Offline Call", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text("Connect without internet or SIM network", fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
        
        OfflineModeCard("Bluetooth", "Connect nearby SHYNA users", Icons.Default.Bluetooth, Color(0xFF2979FF)) { onModeSelect(OfflineMode.BLUETOOTH) }
        OfflineModeCard("Wi-Fi Direct", "Direct offline connection", Icons.Default.Wifi, Color(0xFF00C853)) { onModeSelect(OfflineMode.WIFI_DIRECT) }
        OfflineModeCard("Radio", "Connect using compatible external radio hardware", Icons.Default.Radio, Color(0xFFFFAB00)) { onModeSelect(OfflineMode.RADIO) }
        OfflineModeCard("All Nearby Users", "View users discovered from all offline modes", Icons.Default.Groups, Color(0xFF7C4DFF)) { onModeSelect(OfflineMode.NEARBY_USERS) }
    }
}

@Composable
private fun OfflineModeCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ActiveUsersScreen(transport: String, users: List<OfflineUser>, onUserSelect: (OfflineUser) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(users, search) { users.filter { it.name.contains(search, true) } }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Discovery effect
        delay(3000)
        isScanning = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(transport, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ShynaDesign.colors.BrandGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        
        Text(
            if (isScanning) "Searching for nearby SHYNA users..." else "${filtered.size} users found via $transport", 
            fontSize = 14.sp, 
            color = if(isScanning) Color.Gray else ShynaDesign.colors.BrandGreen
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("Search name or Shyna ID", fontSize = 15.sp, color = ShynaDesign.colors.TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ShynaDesign.colors.BrandGreen,
                unfocusedBorderColor = ShynaDesign.colors.DividerColor,
                focusedTextColor = ShynaDesign.colors.TextPrimary,
                unfocusedTextColor = ShynaDesign.colors.TextPrimary
            )
        )
        Spacer(Modifier.height(24.dp))
        Text("Active Users", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        
        if (!isScanning && filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radar, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(0.3f))
                    Text("No users found nearby", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { user ->
                    OfflineUserRow(user) { onUserSelect(user) }
                }
            }
        }
    }
}

@Composable
private fun OfflineUserRow(user: OfflineUser, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(ShynaDesign.colors.DividerColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(user.name.take(1), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ShynaDesign.colors.TextPrimary)
                Text("${user.status} • ${user.transport.joinToString(", ")}", fontSize = 13.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun RadioScreenUI() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Radio, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(Modifier.height(16.dp))
            Text("Radio hardware not connected", color = Color.Gray)
        }
    }
}

@Composable
private fun UserSelectionScreen(user: OfflineUser, onChat: () -> Unit, onCall: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(100.dp).background(ShynaDesign.colors.DividerColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(user.name.take(1), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
        }
        Spacer(Modifier.height(24.dp))
        Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
        Text("Available via ${user.transport.first()}", color = ShynaDesign.colors.BrandGreen)
        Text("SHYNA User", color = Color.Gray)
        
        Spacer(Modifier.height(40.dp))
        
        Button(onClick = onChat, modifier = Modifier.fillMaxWidth()) { Text("Message") }
        Button(onClick = onCall, modifier = Modifier.fillMaxWidth()) { Text("Voice Call") }
        Button(onClick = { /* Block logic */ }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Block User") }
        TextButton(onClick = onCancel) { Text("Cancel", color = Color.Gray) }
    }
}

@Composable
private fun OfflineChatUI(user: OfflineUser) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(ShynaDesign.colors.HeaderBg).padding(16.dp)) {
            Text("${user.name} (${user.transport.first()})", fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f)) { /* Messages list */ }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = "", onValueChange = {}, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...") })
            IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = ShynaDesign.colors.BrandGreen) }
        }
    }
}

@Composable
private fun OfflineCallingUI(user: OfflineUser) {
    var state by remember { mutableStateOf("Calling...") }
    
    LaunchedEffect(Unit) {
        CallStateController.reportCallEvent(MainCallType.OFFLINE_CALL, GlobalCallState.ACTIVE, userId = user.id)
    }

    Column(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(state, color = ShynaDesign.colors.BrandGreen, fontSize = 18.sp)
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(120.dp).background(ShynaDesign.colors.DividerColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(user.name.take(1), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
        }
        Spacer(Modifier.height(24.dp))
        Text(user.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
        Text("${user.transport.first()} Call", color = Color.Gray)

        Spacer(Modifier.height(80.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = {}) { Icon(Icons.Default.VolumeUp, null) }
            IconButton(onClick = {}) { Icon(Icons.Default.Mic, null) }
            IconButton(onClick = {}) { Icon(Icons.Default.Dialpad, null) }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
        }
        
        Spacer(Modifier.height(60.dp))
        
        FloatingActionButton(
            onClick = { 
                CallStateController.reportCallEvent(MainCallType.OFFLINE_CALL, GlobalCallState.ENDED)
                /* End logic */ 
            },
            containerColor = Color.Red,
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, null, tint = Color.White)
        }
    }
}

private fun getModeTitle(mode: OfflineMode) = when (mode) {
    OfflineMode.HOME -> "Offline Call"
    OfflineMode.BLUETOOTH -> "Bluetooth"
    OfflineMode.WIFI_DIRECT -> "Wi-Fi Direct"
    OfflineMode.RADIO -> "Radio"
    OfflineMode.NEARBY_USERS -> "All Nearby Users"
    else -> "Offline Communication"
}
