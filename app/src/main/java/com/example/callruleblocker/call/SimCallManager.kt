package com.example.callruleblocker.call

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile

data class SimChoice(
    val index: Int,
    val label: String,
    val handle: PhoneAccountHandle
)

object SimCallManager {
    const val PREFS = "dialer_preferences"
    const val KEY_DEFAULT_SIM = "default_sim_mode"
    const val MODE_ASK = "ASK"
    const val MODE_SIM_PREFIX = "SIM_"

    @SuppressLint("MissingPermission")
    fun getChoices(context: Context): List<SimChoice> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return runCatching {
            telecom.callCapablePhoneAccounts.mapIndexed { index, handle ->
                val account = telecom.getPhoneAccount(handle)
                val rawLabel = account?.label?.toString()?.trim().orEmpty()
                SimChoice(
                    index = index,
                    label = rawLabel.ifBlank { "SIM ${index + 1}" },
                    handle = handle
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun placeCall(context: Context, number: String, simIndex: Int? = null) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val extras = Bundle()
        if (simIndex != null) {
            // Hot path: do not enumerate PhoneAccount metadata/labels again just before dialing.
            // The UI already selected the SIM index; resolving only the handle avoids extra
            // Telecom binder work before placeCall().
            runCatching { telecom?.callCapablePhoneAccounts?.getOrNull(simIndex) }.getOrNull()?.let { handle ->
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }
        telecom?.placeCall(Uri.parse("tel:${Uri.encode(number)}"), extras)
    }
    @SuppressLint("MissingPermission")
    fun placeVideoCall(context: Context, number: String, simIndex: Int? = null) {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val extras = Bundle().apply {
            putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
            if (simIndex != null) {
                getChoices(context).getOrNull(simIndex)?.let { choice ->
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, choice.handle)
                }
            }
        }
        telecom.placeCall(Uri.parse("tel:${Uri.encode(number)}"), extras)
    }

}
