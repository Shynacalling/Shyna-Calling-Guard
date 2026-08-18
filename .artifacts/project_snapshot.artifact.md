# Final Project Snapshot: CallRuleBlocker (Shyna Caller Guard)
**Status: FINAL VERIFIED DATA (Aug 18, 2026)**

This document serves as the final record of the consolidated project data, incorporating all recent fixes for Authentication and LiveKit integration.

## Core Project Info
- **Project Name:** CallRuleBlocker (Shyna Caller Guard final 11 Aug 2026)
- **Current Version:** 4.14.6 (Code 79)
- **Target Platform:** Android (Target SDK 37, Min SDK 26)
- **Namespace:** `com.example.callruleblocker`

## Verified Production Endpoints
- **LiveKit Server (WSS):** `wss://shyna-calling-guard.onrender.com`
- **Token Server (HTTPS):** `https://shyna-calling-guard.onrender.com/token`
- **Request Format:** POST with `roomName` and `participantName`.
- **Response Format:** JSON with `token` field.

## Authentication System (Firebase)
- **Source of Truth:** `FirebaseAuth.getInstance().currentUser`
- **State Management:** Reactive `AuthStateListener` integrated with Compose.
- **Login Flow:** Direct Email/Password gate (no intermediate "Welcome" screen).
- **Features:** Login, Sign Up (Create Account), Forgot Password (Reset Email), and Logout.
- **Account Dialog:** Displays authenticated user's Email and UID; never shows "null".

## Core Functionality Matrix
- **Caller Guard:**
    - `InCallServiceImpl` (Telecom integration)
    - Independent SIM 1 / SIM 2 rules.
    - Incoming call blocking & recording.
    - Call logs and Contact CSV management.
- **Shyna Link:**
    - High-quality Video & Voice calling (LiveKit).
    - Encrypted Mesh/Offline messaging logic.
    - Location sharing and file attachments.

## Project Structure & History
The project includes a complete history of fixes documented in various README files:
- `README_V4_14_6_SOUND_ANIMATION_CAMERA_SAFE.md`
- `README_V4_14_5_RECENTS_SELECTION_BLOCK_PRIVACY_DIALER.md`
- ... (and all other versions mentioned in the root).

## Build Verification
- **Last Successful Build:** 18-08-2026 04:18 PM
- **Task:** `gradlew clean assembleDebug`
- **APK Location:** `app\build\outputs\apk\debug\app-debug.apk`

---
**Acknowledged by Assistant:** All coding links, configurations, and logic mentioned above are now part of the permanent "Final Data" snapshot. No further changes will be made to this state unless specifically requested.
