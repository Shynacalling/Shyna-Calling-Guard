package com.example.callruleblocker.call

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppCallType { VOICE, VIDEO }
enum class AppCallStatus { RINGING, ACCEPTED, CONNECTED, REJECTED, ENDED, MISSED, FAILED }

data class AppCall(
    val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhoto: String? = null,
    val receiverUid: String = "",
    val receiverName: String = "",
    val receiverPhoto: String? = null,
    val type: AppCallType = AppCallType.VOICE,
    val status: AppCallStatus = AppCallStatus.RINGING,
    val roomName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val duration: Long = 0,
    val endReason: String? = null
)

object CallSignalingManager {
    private const val TAG = "ShynaCall"
    private val db = FirebaseFirestore.getInstance()
    private var callListener: ListenerRegistration? = null

    fun startCall(
        context: android.content.Context,
        callerUid: String,
        callerName: String,
        callerPhoto: String?,
        receiverUid: String,
        receiverName: String,
        receiverPhoto: String?,
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
            receiverName = receiverName,
            receiverPhoto = receiverPhoto,
            type = type,
            status = AppCallStatus.RINGING,
            roomName = roomName
        )

        db.collection("app_calls").document(callId)
            .set(call)
            .addOnSuccessListener {
                Log.d(TAG, "CALL_CREATED: $callId type=${type.name} room=$roomName")
                onCallCreated(call)
                
                // NOTIFY RECEIVER VIA FCM (Safe Background Call)
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
                scope.launch {
                    LiveKitCallManager(context).notifyReceiver(call)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "CALL_FAILED: ${e.message}", e)
                onError(e)
            }
    }

    fun listenForIncomingCalls(userUid: String, onIncomingCall: (AppCall) -> Unit) {
        callListener?.remove()
        Log.d(TAG, "LISTENING_FOR_CALLS_START: uid=$userUid")
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
                        // Verify it's a recent call (within 2 minutes) and still ringing
                        // Using absolute difference to handle slight clock skews between devices
                        val now = System.currentTimeMillis()
                        val diff = Math.abs(now - call.timestamp)
                        Log.d(TAG, "FIRESTORE_SIGNAL_RECEIVED: id=${call.id} status=${call.status} skew=${now - call.timestamp}ms")
                        
                        if (call.status == AppCallStatus.RINGING && diff < 120000) {
                            Log.d(TAG, "INCOMING_CALL_VALIDATED: id=${call.id} caller=${call.callerName}")
                            
                            // Report to Central Controller
                            CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.INCOMING, call.id)
                            
                            onIncomingCall(call)
                        } else {
                            Log.d(TAG, "INCOMING_CALL_IGNORED: id=${call.id} status=${call.status} diff=${diff}ms")
                        }
                    }
                }
            }
    }

    fun updateCallStatus(callId: String, status: AppCallStatus, reason: String? = null) {
        Log.d(TAG, "UPDATING_CALL_STATUS: id=$callId target=$status reason=$reason")
        val updates = mutableMapOf<String, Any>("status" to status.name)
        reason?.let { updates["endReason"] = it }
        
        db.collection("app_calls").document(callId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "STATUS_UPDATED_SUCCESS: $callId -> $status")
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

    fun saveCallHistory(call: AppCall, currentUid: String) {
        val historyEntry = hashMapOf(
            "callId" to call.id,
            "callerUid" to call.callerUid,
            "callerName" to call.callerName,
            "callerPhoto" to call.callerPhoto,
            "receiverUid" to call.receiverUid,
            "receiverName" to call.receiverName,
            "receiverPhoto" to call.receiverPhoto,
            "type" to call.type.name,
            "status" to call.status.name,
            "duration" to call.duration,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "direction" to if (call.callerUid == currentUid) "outgoing" else "incoming"
        )

        db.collection("users").document(currentUid)
            .collection("call_history").document(call.id)
            .set(historyEntry, SetOptions.merge())
    }

    fun saveCallMessageToChat(call: AppCall) {
        val chatId = if (call.callerUid < call.receiverUid) "${call.callerUid}_${call.receiverUid}" else "${call.receiverUid}_${call.callerUid}"
        
        val msg = mapOf(
            "text" to if(call.type == AppCallType.VIDEO) "Video call" else "Audio call",
            "senderId" to call.callerUid,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "type" to "CALL",
            "callId" to call.id,
            "callType" to call.type.name,
            "callStatus" to call.status.name,
            "callDuration" to call.duration
        )
        
        db.collection("chats").document(chatId).collection("messages").add(msg)
        
        val statusLabel = when(call.status) {
            AppCallStatus.MISSED -> "Missed ${call.type.name.lowercase()} call"
            AppCallStatus.REJECTED -> "Rejected ${call.type.name.lowercase()} call"
            else -> "${if(call.type == AppCallType.VIDEO) "📹" else "📞"} ${call.type.name.lowercase()} call"
        }
        db.collection("chats").document(chatId).set(mapOf("lastMessage" to statusLabel, "timestamp" to com.google.firebase.Timestamp.now()), SetOptions.merge())
    }
}
