package com.example.callruleblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.RemoteMessage
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.GlobalCallState
import com.example.callruleblocker.call.CallSignalingManager
import com.example.callruleblocker.call.AppCallStatus
import android.media.RingtoneManager
import android.media.AudioAttributes

class ShynaFCMService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "ShynaCall"
        const val CHANNEL_ID = "shyna_app_calls"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM_TOKEN_RECEIVED: $token")
        
        // UPDATE TOKEN IN FIRESTORE IF USER IS LOGGED IN
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "FCM_TOKEN_UPDATED_ON_REFRESH") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM_MESSAGE_RECEIVED")

        val data = message.data
        val callId = data["callId"] ?: return
        val callerName = data["callerName"] ?: "Shyna User"
        val callType = data["callType"] ?: "VOICE"
        val callerUid = data["callerUid"] ?: ""

        val receiverUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // BLOCK CHECK
        if (callerUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(receiverUid).collection("blockedUsers").document(callerUid)
                .get().addOnSuccessListener { d ->
                    if (d.exists()) {
                        Log.d(TAG, "Caller is blocked. Rejecting call.")
                        CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED)
                    } else {
                        // BUSY LOGIC
                        if (CallStateController.globalState.value == GlobalCallState.ACTIVE) {
                            Log.d(TAG, "User Busy: Auto-rejecting FCM call.")
                            CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED)
                            return@addOnSuccessListener
                        }
                        showCallNotification(callId, callerName, callType)
                    }
                }
        } else {
            showCallNotification(callId, callerName, callType)
        }
    }

    private fun showCallNotification(callId: String, callerName: String, callType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(CHANNEL_ID, "App Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming app-to-app calls"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
            )
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ACCEPT ACTION
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.ACCEPT_APP_CALL"
            putExtra("callId", callId)
        }
        val acceptPending = PendingIntent.getBroadcast(this, 101, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // DECLINE ACTION
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.DECLINE_APP_CALL"
            putExtra("callId", callId)
        }
        val declinePending = PendingIntent.getBroadcast(this, 102, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callType Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(callId.hashCode(), notification)
    }
}
