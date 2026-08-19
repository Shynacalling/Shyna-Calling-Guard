package com.example.callruleblocker.call

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.UUID

enum class AppCallType { VOICE, VIDEO }
enum class AppCallStatus { RINGING, ACCEPTED, CONNECTED, REJECTED, ENDED, MISSED, FAILED }

data class AppCall(
    val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhoto: String? = null,
    val receiverUid: String = "",
    val type: AppCallType = AppCallType.VOICE,
    val status: AppCallStatus = AppCallStatus.RINGING,
    val roomName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object CallSignalingManager {
    private const val TAG = "ShynaCall"
    private val db = FirebaseFirestore.getInstance()
    private var callListener: ListenerRegistration? = null

    fun startCall(
        callerUid: String,
        callerName: String,
        callerPhoto: String?,
        receiverUid: String,
        type: AppCallType,
        onCallCreated: (AppCall) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val callId = UUID.randomUUID().toString()
        val roomName = "room_$callId"
        val call = AppCall(
            id = callId,
            callerUid = callerUid,
            callerName = callerName,
            callerPhoto = callerPhoto,
            receiverUid = receiverUid,
            type = type,
            status = AppCallStatus.RINGING,
            roomName = roomName
        )

        db.collection("app_calls").document(callId)
            .set(call)
            .addOnSuccessListener {
                Log.d(TAG, "CALL_CREATED: $callId type=${type.name} room=$roomName")
                onCallCreated(call)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "CALL_FAILED: ${e.message}", e)
                onError(e)
            }
    }

    fun listenForIncomingCalls(userUid: String, onIncomingCall: (AppCall) -> Unit) {
        callListener?.remove()
        Log.d(TAG, "LISTENING_FOR_CALLS: uid=$userUid")
        callListener = db.collection("app_calls")
            .whereEqualTo("receiverUid", userUid)
            .whereEqualTo("status", AppCallStatus.RINGING.name)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "SIGNALING_LISTEN_FAILED", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val call = dc.document.toObject(AppCall::class.java)
                        // Verify it's a recent call (within 1 minute) and still ringing
                        if (call.status == AppCallStatus.RINGING && System.currentTimeMillis() - call.timestamp < 60000) {
                            Log.d(TAG, "INCOMING_CALL: id=${call.id} caller=${call.callerName}")
                            onIncomingCall(call)
                        }
                    }
                }
            }
    }

    fun updateCallStatus(callId: String, status: AppCallStatus) {
        db.collection("app_calls").document(callId)
            .update("status", status.name)
            .addOnSuccessListener {
                Log.d(TAG, "STATUS_UPDATED: $callId -> $status")
                if (status == AppCallStatus.ACCEPTED) {
                    Log.d(TAG, "CALL_ACCEPTED: $callId")
                } else if (status == AppCallStatus.REJECTED) {
                    Log.d(TAG, "CALL_REJECTED: $callId")
                } else if (status == AppCallStatus.ENDED) {
                    Log.d(TAG, "CALL_ENDED: $callId")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "STATUS_UPDATE_FAILED: id=$callId target=$status error=${e.message}")
            }
    }

    fun listenToCall(callId: String, onUpdate: (AppCall) -> Unit): ListenerRegistration {
        return db.collection("app_calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.toObject(AppCall::class.java)?.let { onUpdate(it) }
            }
    }

    fun cleanup() {
        callListener?.remove()
        callListener = null
    }
}
