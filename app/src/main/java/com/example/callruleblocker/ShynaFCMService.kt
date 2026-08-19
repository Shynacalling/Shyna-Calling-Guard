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

        showCallNotification(callId, callerName, callType)
    }

    private fun showCallNotification(callId: String, callerName: String, callType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming app-to-app calls"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callType Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(callId.hashCode(), notification)
    }
}
