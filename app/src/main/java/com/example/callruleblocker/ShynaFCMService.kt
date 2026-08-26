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
import android.app.Notification
import androidx.core.content.ContextCompat

class ShynaFCMService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "ShynaCall"
        const val CHANNEL_ID = "shyna_app_calls_v2" // Versioned channel for clean start
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM_TOKEN_RECEIVED: $token")
        
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "FCM_TOKEN_UPDATED_ON_REFRESH") }
                .addOnFailureListener { e -> Log.e(TAG, "FCM_TOKEN_UPDATE_FAILED", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM_MESSAGE_RECEIVED")
        Log.d(TAG, "FCM_DATA=${message.data}")

        val data = message.data
        val callId = data["callId"] ?: return
        val callerName = data["callerName"] ?: "Shyna User"
        val callType = data["callType"] ?: "VOICE"
        val callerUid = data["callerUid"] ?: ""

        Log.d(TAG, "FCM_CALL_ID=$callId FCM_CALLER_UID=$callerUid FCM_CALLER_NAME=$callerName FCM_CALL_TYPE=$callType")

        val receiverUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "FCM_RECEIVED_BUT_NO_USER_LOGGED_IN")
            return
        }

        // BLOCK CHECK STARTED
        Log.d(TAG, "BLOCK_CHECK_STARTED for $callerUid")
        if (callerUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(receiverUid).collection("blockedUsers").document(callerUid)
                .get()
                .addOnSuccessListener { d ->
                    val isBlocked = d.exists()
                    Log.d(TAG, "BLOCK_CHECK_RESULT=${if(isBlocked) "BLOCKED" else "ALLOWED"}")
                    
                    if (isBlocked) {
                        Log.d(TAG, "Caller is blocked. Rejecting call.")
                        CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED, "caller_blocked")
                    } else {
                        handleIncomingFcm(callId, callerName, callType)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "BLOCK_CHECK_FAILED", e)
                    // Safe fallback: Allow call if block check fails to avoid missing important calls
                    handleIncomingFcm(callId, callerName, callType)
                }
        } else {
            handleIncomingFcm(callId, callerName, callType)
        }
    }

    private fun handleIncomingFcm(callId: String, callerName: String, callType: String) {
        // ACTIVE_SESSION_CHECK
        val currentActiveSession = CallStateController.activeSession.value
        Log.d(TAG, "ACTIVE_SESSION_CHECK ACTIVE_CALL_ID=${currentActiveSession?.callId} ACTIVE_STATE=${currentActiveSession?.state}")

        if (currentActiveSession != null && currentActiveSession.state != GlobalCallState.IDLE && currentActiveSession.callId != callId) {
            Log.d(TAG, "User Truly Busy: Auto-rejecting DIFFERENT FCM call (ID: $callId, Current: ${currentActiveSession.callId})")
            CallSignalingManager.updateCallStatus(callId, AppCallStatus.REJECTED, "user_busy_fcm")
            return
        }

        showCallNotification(callId, callerName, callType)
    }

    private fun showCallNotification(callId: String, callerName: String, callType: String) {
        Log.d(TAG, "SHOW_CALL_NOTIFICATION_START callId=$callId")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Permission & Status Logs
        val notificationsEnabled = notificationManager.areNotificationsEnabled()
        Log.d(TAG, "NOTIFICATIONS_ENABLED=$notificationsEnabled")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS_GRANTED=$postGranted")
        }

        val channel = NotificationChannel(CHANNEL_ID, "App Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming app-to-app calls"
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            setSound(ringtoneUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "CHANNEL_ID=$CHANNEL_ID CHANNEL_IMPORTANCE=${notificationManager.getNotificationChannel(CHANNEL_ID)?.importance}")

        // FULLSCREEN_INTENT_CREATED
        val intent = Intent(this, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncoming", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val baseRequestCode = callId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, baseRequestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.d(TAG, "FULLSCREEN_INTENT_CREATED requestCode=$baseRequestCode")

        // ACCEPT ACTION
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.ACCEPT_APP_CALL"
            putExtra("callId", callId)
        }
        val acceptPending = PendingIntent.getBroadcast(this, baseRequestCode + 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // DECLINE ACTION
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.DECLINE_APP_CALL"
            putExtra("callId", callId)
        }
        val declinePending = PendingIntent.getBroadcast(this, baseRequestCode + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else true
        Log.d(TAG, "CAN_USE_FULL_SCREEN_INTENT=$canUseFullScreen")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callType Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
            .setOngoing(true) // Prevent accidental swipe away during ring
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        Log.d(TAG, "CALL_NOTIFICATION_POSTING hash=${callId.hashCode()}")
        notificationManager.notify(callId.hashCode(), notification)
        Log.d(TAG, "CALL_NOTIFICATION_POSTED")
    }
}
