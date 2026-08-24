package com.example.callruleblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.VideoProfile
import com.example.callruleblocker.call.CallControlCenter
import com.example.callruleblocker.call.CallHolder

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            OngoingCallNotification.ACTION_ANSWER_CALL -> {
                val call = CallControlCenter.allCalls.value.firstOrNull { it.state == Call.STATE_RINGING }
                    ?: CallHolder.currentCall.value?.takeIf { it.state == Call.STATE_RINGING }
                OngoingCallNotification.cancel(context)
                call?.let {
                    CallHolder.set(it)
                    val answerState = if (VideoProfile.isVideo(it.details.videoState)) {
                        it.details.videoState
                    } else {
                        VideoProfile.STATE_AUDIO_ONLY
                    }
                    runCatching { it.answer(answerState) }
                }
            }

            OngoingCallNotification.ACTION_DECLINE_CALL -> {
                val call = CallControlCenter.allCalls.value.firstOrNull { it.state == Call.STATE_RINGING }
                    ?: CallHolder.currentCall.value?.takeIf { it.state == Call.STATE_RINGING }
                OngoingCallNotification.cancel(context)
                runCatching { call?.reject(false, null) }
            }

            OngoingCallNotification.ACTION_END_CALL -> {
                CallControlCenter.endAllActiveCalls()
                OngoingCallNotification.cancel(context)
            }

            "com.example.callruleblocker.action.ACCEPT_APP_CALL" -> {
                val callId = intent.getStringExtra("callId") ?: return
                val startIntent = Intent(context, AppCallActivity::class.java).apply {
                    putExtra("callId", callId)
                    putExtra("isIncoming", true)
                    putExtra("autoAccept", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(startIntent)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(callId.hashCode())
            }

            "com.example.callruleblocker.action.DECLINE_APP_CALL" -> {
                val callId = intent.getStringExtra("callId") ?: return
                // Fetch call info to save to history before updating status
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("app_calls").document(callId).get().addOnSuccessListener { snapshot ->
                    val call = snapshot.toObject(com.example.callruleblocker.call.AppCall::class.java)
                    if (call != null) {
                        val updatedCall = call.copy(status = com.example.callruleblocker.call.AppCallStatus.REJECTED)
                        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUid != null) {
                            com.example.callruleblocker.call.CallSignalingManager.saveCallHistory(updatedCall, currentUid)
                            com.example.callruleblocker.call.CallSignalingManager.saveCallMessageToChat(updatedCall)
                        }
                    }
                    com.example.callruleblocker.call.CallSignalingManager.updateCallStatus(callId, com.example.callruleblocker.call.AppCallStatus.REJECTED)
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(callId.hashCode())
            }
        }
    }
}
