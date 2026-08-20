# Final Restoration Baseline: Version 4.14.6-STABLE
**Date**: August 20, 2026 | **Build Status**: 100% Success ✅

This document marks the final accepted state of the project after a 1-step back restoration and calling system overhaul. This is now the permanent baseline for all future work.

## 1. Core State Verification
- **Baseline**: Restored to the stable August 18 baseline (Version 4.14.6).
- **Integrity**: All recent experimental UI changes (WhatsApp filters, capsule search) have been safely removed to return to the core stable logic.
- **Build**: Successfully verified with `gradlew assembleDebug`.

## 2. Integrated Fixes (Applied & Finalized)
The following critical calling fixes have been re-applied and merged into the restored baseline:

### A. High-Priority Wake-up Signal
- **File**: `CallSignalingManager.kt`
- **Logic**: Added `LiveKitCallManager.notifyReceiver` trigger. Calls now send a real-time FCM notification to wake the receiver's phone immediately.

### B. Chat Calling Interface
- **File**: `SmartCommunicationScreen.kt`
- **Logic**: Re-integrated **Video Call** and **Voice Call** icons in the top bar. They are fully hooked up to the `CallSignalingManager`.

### C. Dialer Stability
- **File**: `DialerActivity.kt` & `MainActivity.kt`
- **Logic**: Synchronized search constants to prevent compiler errors.

## 3. Persistent Configuration
- **LiveKit Server**: `wss://shyna-calling-guard-kn60m55l.livekit.cloud`
- **Token Server**: `https://shyna-calling-guard.onrender.com`
- **SharedPreferences**: Using `smart_communication_v2` for persistence.

---
**Status**: **ACCEPTED & COMMITTED**. No further automatic changes will be made to this state. Any new updates will require a fresh user request.
