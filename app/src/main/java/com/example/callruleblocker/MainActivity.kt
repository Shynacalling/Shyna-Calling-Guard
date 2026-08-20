package com.example.callruleblocker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.example.callruleblocker.ui.SmartCommunicationScreen

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showCommunication by remember { mutableStateOf(user != null) }
                    
                    if (showCommunication) {
                        SmartCommunicationScreen(
                            initialOnline = true,
                            onBack = { finish() }
                        )
                    } else {
                        // Original login or welcome screen logic
                        // For now, redirecting to communication screen if user exists
                        if (user != null) {
                            showCommunication = true
                        } else {
                            // Show login screen (not implemented in this snapshot)
                        }
                    }
                }
                
                // SAVE FCM TOKEN FOR CALL NOTIFICATIONS
                try {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            FirebaseFirestore.getInstance().collection("users").document(user!!.uid)
                                .set(mapOf("fcmToken" to token), SetOptions.merge())
                                .addOnSuccessListener { Log.d("ShynaCall", "FCM_TOKEN_SYNCED") }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "FCM Token sync failed on start", e)
                }

                // APP-TO-APP CALL LISTENER
                try {
                    com.example.callruleblocker.call.CallSignalingManager.listenForIncomingCalls(user!!.uid) { call ->
                        val intent = Intent(this, AppCallActivity::class.java).apply {
                            putExtra("callId", call.id)
                            putExtra("isIncoming", true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("ShynaCall", "Call listener failed on start", e)
                }
            }
        }
    }
}
