package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.content.pm.PackageManager
import android.widget.Toast
import android.util.Log
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.data.SessionManager
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.ui.*
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var repository: RuleRepository
    private lateinit var auth: FirebaseAuth

    var firebaseUser by mutableStateOf<com.google.firebase.auth.FirebaseUser?>(null)

    private var permissionsGranted by mutableStateOf(false)
    private var isDefaultApp by mutableStateOf(false)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsGranted = hasAllPermissions() }

    private val requestRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDefaultApp = isDefaultDialerApp() }

    private val essentialPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private val firstRunPermissions = essentialPermissions

    private fun hasAllPermissions() = essentialPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDefaultDialerApp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val roleManager = getSystemService(RoleManager::class.java) ?: return true
        return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    companion object {
        const val EXTRA_START_SEARCH = "com.example.callruleblocker.START_SEARCH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firebaseUser = auth.currentUser
        
        auth.addAuthStateListener {
            val user = it.currentUser
            firebaseUser = user
            if (user != null) {
                // ENSURE PROFILE SYNC ON AUTH STATE CHANGE
                syncCurrentUserToFirestore()
                
                // SAVE FCM TOKEN FOR CALL NOTIFICATIONS
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        FirebaseFirestore.getInstance().collection("users").document(user.uid)
                            .set(mapOf("fcmToken" to token), SetOptions.merge())
                            .addOnSuccessListener { Log.d("ShynaCall", "FCM_TOKEN_SYNCED") }
                    }
                }

                // APP-TO-APP CALL LISTENER
                com.example.callruleblocker.call.CallSignalingManager.listenForIncomingCalls(user.uid) { call ->
                    val intent = Intent(this, AppCallActivity::class.java).apply {
                        putExtra("callId", call.id)
                        putExtra("isIncoming", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
            } else {
                com.example.callruleblocker.call.CallSignalingManager.cleanup()
            }
        }

        // REDIRECT IF CALL IS ACTIVE: Ensures clicking the app icon always returns to the live call.
        if (com.example.callruleblocker.call.CallHolder.currentCall.value != null) {
            startActivity(Intent(this, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        runCatching {
            repository = RuleRepository(applicationContext)
            NotificationSupport.createChannels(this)
            permissionsGranted = hasAllPermissions()
            isDefaultApp = isDefaultDialerApp()
        }.onFailure { it.printStackTrace() }

        setContent {
            val context = LocalContext.current
            var appearanceSettings by remember { mutableStateOf(PersonalizationManager.getSettings(context)) }
            val onThemeChanged = { appearanceSettings = PersonalizationManager.getSettings(context) }

            CompositionLocalProvider(LocalAppearance provides appearanceSettings) {
                val currentUid = firebaseUser?.uid
                var sessionExpired by remember { mutableStateOf(false) }

                LaunchedEffect(currentUid) {
                    if (currentUid != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val localSessionId = SessionManager.getLocalSessionId(context)
                        
                        db.collection("users").document(currentUid).addSnapshotListener { snapshot, _ ->
                            val remoteSessionId = snapshot?.getString("activeSessionId")
                            if (remoteSessionId != null && localSessionId != null && remoteSessionId != localSessionId) {
                                sessionExpired = true
                            }
                        }
                    }
                }

                if (sessionExpired) {
                    AlertDialog(
                        onDismissRequest = { /* Force logout */ },
                        title = { Text("Session Expired") },
                        text = { Text("Your account has been logged in on another device. Please log in again.") },
                        confirmButton = {
                            Button(onClick = {
                                SessionManager.clearLocalSession(context)
                                auth.signOut()
                                sessionExpired = false
                                firebaseUser = null
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                CallRuleBlockerTheme {
                    val prefs = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
                    var isLocked by remember { 
                        mutableStateOf(prefs.getBoolean("app_lock_pin", false) || prefs.getBoolean("app_lock_biometric", false)) 
                    }

                    if (isLocked) {
                        AppLockScreen(onUnlocked = { isLocked = false })
                    } else {
                        var screen by remember { mutableStateOf("phone") }
                        var searchInitiallyVisible by remember { 
                            mutableStateOf(intent?.getBooleanExtra(EXTRA_START_SEARCH, false) ?: false)
                        }
                        var reportType by remember { mutableStateOf("") }
                        val rules by repository.observeAll().collectAsState(initial = emptyList())
                        val scope = rememberCoroutineScope()

                        if (!permissionsGranted || !isDefaultApp) {
                            SetupScreen(
                                permissionsGranted = permissionsGranted,
                                isDefaultApp = isDefaultApp,
                                onGrantPermissions = { requestPermissions.launch(firstRunPermissions) },
                                onSetDefaultApp = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val rm = getSystemService(RoleManager::class.java)
                                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                                            requestRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                                            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                                        startActivity(intent)
                                    }
                                },
                                onRecheck = {
                                    permissionsGranted = hasAllPermissions()
                                    isDefaultApp = isDefaultDialerApp()
                                }
                            )
                        } else {
                            Crossfade(
                                targetState = screen,
                                animationSpec = tween(durationMillis = 220),
                                label = "premium-screen-transition"
                            ) { activeScreen ->
                                when (activeScreen) {
                                    "phone" -> PhoneHomeScreen(
                                        initialSearchVisible = searchInitiallyVisible,
                                        onOpenRules = { screen = "list" },
                                        onOpenSettings = { screen = "settings" },
                                        onOpenRecycleBin = { screen = "recycle_bin" },
                                        onOpenOfflineCall = { screen = "offline_call" },
                                        onOpenOnlineCall = { screen = "online_call" },
                                        onOpenRecordings = { screen = "recordings" },
                                        onFontScaleChanged = { newScale ->
                                            appearanceSettings = appearanceSettings.copy(uiScale = newScale)
                                        },
                                        onCall = { number, simIndex ->
                                            SimCallManager.placeCall(this, number, simIndex)
                                        }
                                    )
                                    "settings" -> RecordingSettingsScreen(
                                        onBack = { screen = "phone" },
                                        onOpenCsv = { screen = "csv" },
                                        onOpenFeatureHub = { screen = "feature_hub" },
                                        onOpenRules = { screen = "list" },
                                        onOpenRingtone = { screen = "ringtone" },
                                        onOpenNotifications = { screen = "notifications" },
                                        onOpenRecycleBin = { screen = "recycle_bin" },
                                        onOpenSupplementaryServices = { screen = "supplementary_services" },
                                        onThemeChanged = onThemeChanged
                                    )
                                    "csv" -> ContactCsvScreen(onBack = { screen = "settings" })
                                    "offline_call" -> SmartCommunicationScreen(initialOnline = false, onBack = { screen = "phone" })
                                    "online_call" -> SmartCommunicationScreen(initialOnline = true, onBack = { screen = "phone" })
                                    "feature_hub" -> FeatureHubScreen(
                                        onBack = { screen = "settings" },
                                        onOpenRules = { screen = "list" },
                                        onOpenSettings = { screen = "settings" },
                                        onOpenReport = { type ->
                                            reportType = type
                                            screen = "report_generator"
                                        },
                                        onThemeChanged = onThemeChanged
                                    )
                                    "report_generator" -> ReportGeneratorScreen(
                                        reportType = reportType,
                                        onBack = { screen = "feature_hub" }
                                    )
                                    "ringtone" -> RingtoneSettingsScreen(onBack = { screen = "settings" })
                                    "notifications" -> NotificationSettingsScreen(onBack = { screen = "settings" })
                                    "recycle_bin" -> RecycleBinScreen(repository = repository, onBack = { screen = "settings" })
                                    "recordings" -> CallRecordingsScreen(onBack = { screen = "phone" })
                                    "supplementary_services" -> SupplementaryServicesScreen(onBack = { screen = "settings" })
                                    "list" -> RuleListScreen(
                                        rules = rules,
                                        onAddRule = { screen = "add" },
                                        onDeleteRule = { rule -> scope.launch { repository.deleteRule(rule) } },
                                        onToggleRule = { rule ->
                                            scope.launch { repository.updateRule(rule.copy(enabled = !rule.enabled)) }
                                        },
                                        onBack = { screen = "phone" }
                                    )
                                    "add" -> AddRuleScreen(
                                        onSave = { rule ->
                                            scope.launch {
                                                repository.addRule(rule)
                                                screen = "list"
                                            }
                                        },
                                        onCancel = { screen = "list" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            permissionsGranted = hasAllPermissions()
            isDefaultApp = isDefaultDialerApp()
        }.onFailure { it.printStackTrace() }
    }

    fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                firebaseUser = auth.currentUser
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        syncCurrentUserToFirestore()
                    }
                    Toast.makeText(this, "Signup Successful", Toast.LENGTH_SHORT).show()
                }
 else {
                    Toast.makeText(this, task.exception?.message ?: "Signup Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun syncCurrentUserToFirestore() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: run {
            Log.e("ShynaDiscovery", "PROFILE_SYNC_ABORTED: No current user")
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        Log.d("ShynaDiscovery", "PROFILE_SYNC_START")
        Log.d("ShynaDiscovery", "FIREBASE_PROJECT_ID=${db.app.options.projectId}")
        Log.d("ShynaDiscovery", "AUTH_UID=${user.uid}")
        Log.d("ShynaDiscovery", "AUTH_EMAIL=${user.email}")

        val email = user.email ?: ""
        val normalizedEmail = email.trim().lowercase()
        val displayName = user.displayName ?: ""
        val parts = displayName.split(" ", limit = 2)
        val fName = parts.getOrNull(0) ?: ""
        val lName = parts.getOrNull(1) ?: ""
        val name = if (displayName.isNotBlank()) displayName else email.substringBefore("@")
        
        val sessionId = SessionManager.getLocalSessionId(this) 
                        ?: SessionManager.startNewSession(this)
        val deviceId = SessionManager.getDeviceId(this)

        val syncData = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "email" to email,
            "normalizedEmail" to normalizedEmail,
            "displayName" to displayName,
            "firstName" to fName,
            "lastName" to lName,
            "name" to name,
            "phone" to (user.phoneNumber ?: ""),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "isOnline" to true,
            "activeSessionId" to sessionId,
            "deviceId" to deviceId,
            "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("users").document(user.uid)
            .set(syncData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("ShynaDiscovery", "PROFILE_SYNC_SUCCESS uid=${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e("ShynaDiscovery", "PROFILE_SYNC_FAILED uid=${user.uid}", e)
            }
    }

    fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                firebaseUser = auth.currentUser
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        // CRITICAL: Sync profile to Firestore on EVERY login to ensure searchability
                        syncCurrentUserToFirestore()
                    }
                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, task.exception?.message ?: "Login Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun logoutUser() {
        auth.signOut()
        firebaseUser = null
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
    }

    fun sendMessage(receiverId: String, messageText: String) {
        val senderId = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Create a unique chatId by sorting UIDs
        val chatId = if (senderId < receiverId) {
            "${senderId}_${receiverId}"
        } else {
            "${receiverId}_${senderId}"
        }

        val message = hashMapOf(
            "senderId" to senderId,
            "receiverId" to receiverId,
            "text" to messageText,
            "timestamp" to Timestamp.now()
        )

        val chatUpdate = hashMapOf(
            "user1" to if (senderId < receiverId) senderId else receiverId,
            "user2" to if (senderId < receiverId) receiverId else senderId,
            "lastMessage" to messageText,
            "timestamp" to Timestamp.now()
        )

        // Update chat metadata and add the message to the sub-collection
        db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
        db.collection("chats").document(chatId).collection("messages").add(message)
            .addOnFailureListener { it.printStackTrace() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    permissionsGranted: Boolean,
    isDefaultApp: Boolean,
    onGrantPermissions: () -> Unit,
    onSetDefaultApp: () -> Unit,
    onRecheck: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Shyna Caller Guard Setup") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "This app now needs to become your phone's default Calling app " +
                    "so it can reliably tell SIM 1 apart from SIM 2:",
                style = MaterialTheme.typography.bodyLarge
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Phone & contacts permission", style = MaterialTheme.typography.titleMedium)
                    Text("Needed to read the caller's number, place calls, and check your contacts/SIM.")
                    Button(onClick = onGrantPermissions, enabled = !permissionsGranted) {
                        Text(if (permissionsGranted) "Granted ✓" else "Grant permission")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Set as default Phone app", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android will show a dialog asking to make Shyna Caller Guard your " +
                            "default Phone app, replacing the system's own dialer for handling calls. " +
                            "This is the only way third-party apps can reliably see which SIM a call arrived on."
                    )
                    Button(onClick = onSetDefaultApp, enabled = !isDefaultApp) {
                        Text(if (isDefaultApp) "Set ✓" else "Set as default")
                    }
                }
            }

            OutlinedButton(onClick = onRecheck) { Text("I've done this — continue") }
        }
    }
}
