package com.example.callruleblocker

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.example.callruleblocker.ui.SmartCommunicationScreen
import com.example.callruleblocker.data.RuleRepository
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.MainCallType
import com.example.callruleblocker.call.GlobalCallState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_SEARCH = "start_search"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        setContent {
            CallRuleBlockerTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val repository = remember { RuleRepository(context) }
                val rules by repository.observeAll().collectAsState(initial = emptyList())
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("AUTH_CHECK") }
                    var selectedReportType by remember { mutableStateOf("MONTHLY") }
                    val primaryFeature by CallStateController.primaryFeature.collectAsState()
                    val globalState by CallStateController.globalState.collectAsState()

                    // Back Button Rule during calling
                    BackHandler(enabled = globalState != GlobalCallState.IDLE && globalState != GlobalCallState.ENDED) {
                        // DO NOTHING - Prevent accidental exit or feature switch during active call
                        Log.d("ShynaCall", "Back blocked during active call state: $globalState")
                    }

                    when (currentScreen) {
                        "AUTH_CHECK" -> {
                            val prefs = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
                            val pinEnabled = prefs.getBoolean("app_lock_pin", false)
                            val biometricEnabled = prefs.getBoolean("app_lock_biometric", false)
                            
                            if (pinEnabled || biometricEnabled) {
                                com.example.callruleblocker.ui.AppLockScreen(
                                    onUnlocked = { currentScreen = "PHONE_HOME" }
                                )
                            } else {
                                LaunchedEffect(Unit) { currentScreen = "PHONE_HOME" }
                            }
                        }
                        "SHYNA_LINK" -> {
                            SmartCommunicationScreen(
                                initialOnline = true,
                                onBack = { currentScreen = "PHONE_HOME" }
                            )
                        }
                        "RULES" -> {
                            com.example.callruleblocker.ui.RuleListScreen(
                                rules = rules,
                                onAddRule = { currentScreen = "ADD_RULE" },
                                onDeleteRule = { scope.launch { repository.deleteRule(it) } },
                                onToggleRule = { scope.launch { repository.updateRule(it.copy(enabled = !it.enabled)) } },
                                onBack = { currentScreen = "PHONE_HOME" }
                            )
                        }
                        "ADD_RULE" -> {
                            com.example.callruleblocker.ui.AddRuleScreen(
                                onSave = { scope.launch { repository.addRule(it) }; currentScreen = "RULES" },
                                onCancel = { currentScreen = "RULES" }
                            )
                        }
                        "SETTINGS" -> {
                            com.example.callruleblocker.ui.RecordingSettingsScreen(
                                onBack = { currentScreen = "PHONE_HOME" },
                                onOpenCsv = { currentScreen = "CSV" },
                                onOpenFeatureHub = { currentScreen = "FEATURE_HUB" },
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenRingtone = { currentScreen = "RINGTONE" },
                                onOpenNotifications = { currentScreen = "NOTIFICATIONS" },
                                onOpenRecycleBin = { currentScreen = "RECYCLE_BIN" },
                                onOpenSupplementaryServices = { currentScreen = "SUPPLEMENTARY" },
                                onThemeChanged = {}
                            )
                        }
                        "CSV" -> {
                            com.example.callruleblocker.ui.ContactCsvScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "FEATURE_HUB" -> {
                            com.example.callruleblocker.ui.FeatureHubScreen(
                                onBack = { currentScreen = "SETTINGS" },
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenSettings = { currentScreen = "SETTINGS" },
                                onOpenReport = { type -> selectedReportType = type; currentScreen = "REPORT" },
                                onThemeChanged = {}
                            )
                        }
                        "RINGTONE" -> {
                            com.example.callruleblocker.ui.RingtoneSettingsScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "NOTIFICATIONS" -> {
                            com.example.callruleblocker.ui.NotificationSettingsScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "SUPPLEMENTARY" -> {
                            com.example.callruleblocker.ui.SupplementaryServicesScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "REPORT" -> {
                            com.example.callruleblocker.ui.ReportGeneratorScreen(
                                reportType = selectedReportType,
                                onBack = { currentScreen = "FEATURE_HUB" }
                            )
                        }
                        "PHONE_HOME" -> {
                            com.example.callruleblocker.ui.PhoneHomeScreen(
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenSettings = { currentScreen = "SETTINGS" },
                                onOpenRecycleBin = { currentScreen = "RECYCLE_BIN" },
                                onOpenOfflineCall = { 
                                    CallStateController.setPrimaryFeature(MainCallType.OFFLINE_CALL)
                                    currentScreen = "OFFLINE_CALL"
                                },
                                onOpenOnlineCall = { 
                                    CallStateController.setPrimaryFeature(MainCallType.SHYNA_LINK)
                                    currentScreen = "SHYNA_LINK"
                                },
                                onOpenRecordings = { currentScreen = "RECORDINGS" },
                                onCall = { number, sim -> com.example.callruleblocker.call.SimCallManager.placeCall(context, number, sim) }
                            )
                        }
                        "OFFLINE_CALL" -> {
                            com.example.callruleblocker.ui.OfflineCallScreen(
                                onBack = { 
                                    CallStateController.setPrimaryFeature(MainCallType.PHONE_DIALER)
                                    currentScreen = "PHONE_HOME" 
                                }
                            )
                        }
                        else -> {
                            // Default entry based on primary feature
                            LaunchedEffect(primaryFeature) {
                                currentScreen = when(primaryFeature) {
                                    MainCallType.PHONE_DIALER -> "PHONE_HOME"
                                    MainCallType.SHYNA_LINK -> "SHYNA_LINK"
                                    MainCallType.OFFLINE_CALL -> "OFFLINE_CALL"
                                }
                            }
                        }
                    }
                }
                
                // ASYNC LISTENERS & TOKEN SYNC (SAFE WRAPPER)
                LaunchedEffect(user) {
                    if (user != null) {
                        // SAVE FCM TOKEN FOR CALL NOTIFICATIONS
                        try {
                            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val token = task.result
                                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                        .set(mapOf("fcmToken" to token), SetOptions.merge())
                                        .addOnSuccessListener { Log.d("ShynaCall", "FCM_TOKEN_SYNCED") }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ShynaCall", "FCM Token sync failed on start", e)
                        }

                        // APP-TO-APP CALL LISTENER
                        try {
                            com.example.callruleblocker.call.CallSignalingManager.listenForIncomingCalls(user.uid) { call ->
                                val currentState = CallStateController.globalState.value
                                
                                if (currentState == GlobalCallState.ACTIVE) {
                                    // BUSY LOGIC: Reject incoming call if already in a call
                                    Log.d("ShynaCall", "User Busy: Rejecting incoming app call.")
                                    com.example.callruleblocker.call.CallSignalingManager.updateCallStatus(call.id, com.example.callruleblocker.call.AppCallStatus.REJECTED)
                                    // Optionally show a notification that you missed a call while busy
                                } else {
                                    val intent = Intent(this@MainActivity, AppCallActivity::class.java).apply {
                                        putExtra("callId", call.id)
                                        putExtra("isIncoming", true)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(intent)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ShynaCall", "Call listener failed on start", e)
                        }
                    }
                }
            }
        }
    }
}

private fun showInternetCallHeadsUp(context: android.content.Context, call: com.example.callruleblocker.call.AppCall) {
    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channelId = "shyna_calls"
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(channelId, "Incoming Calls", android.app.NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
    }

    val intent = android.content.Intent(context, AppCallActivity::class.java).apply {
        putExtra("callId", call.id)
        putExtra("isIncoming", true)
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)

    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.sym_call_incoming)
        .setContentTitle("Incoming Shyna Call")
        .setContentText(call.callerName)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
        .setFullScreenIntent(pendingIntent, true)
        .setAutoCancel(true)
        .build()

    nm.notify(101, notification)
}
