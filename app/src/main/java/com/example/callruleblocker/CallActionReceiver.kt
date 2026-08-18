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
        }
    }
}
