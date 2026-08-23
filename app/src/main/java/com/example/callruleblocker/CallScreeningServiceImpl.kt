package com.example.callruleblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.sim.SimSlotResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Professional Call Screening Service for microsecond-fast blocking.
 * This service is called by the Android system BEFORE the phone rings.
 */
class CallScreeningServiceImpl : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ruleRepository by lazy { RuleRepository(applicationContext) }
    private val blockedCallStore by lazy { BlockedCallStore(applicationContext) }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (number == null) {
            respondToCall(callDetails, CallScreeningService.CallResponse.Builder().build())
            return
        }

        serviceScope.launch {
            val decision = runCatching {
                withTimeoutOrNull(500) {
                    @Suppress("MissingPermission")
                    val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)
                    ruleRepository.decide(number, simSlot)
                }
            }.getOrNull() ?: "ALLOW"

            val responseBuilder = CallResponse.Builder()
            if (decision == "BLOCK") {
                Log.d("ShynaCall", "[SCREENING] BLOCKING: $number")
                
                // Record the block in our local audit store
                @Suppress("MissingPermission")
                val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)
                blockedCallStore.record(number, simSlot)

                // High-speed system block (No ring, no system log entry if desired)
                responseBuilder.setDisallowCall(true)
                responseBuilder.setRejectCall(true)
                responseBuilder.setSkipCallLog(true) // We manage our own "Auto Blocked" history
                responseBuilder.setSkipNotification(true)
            } else {
                Log.d("ShynaCall", "[SCREENING] ALLOWING: $number")
            }

            respondToCall(callDetails, responseBuilder.build())
        }
    }
}
