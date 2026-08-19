# Project Blueprint: Shyna Caller Guard (Updated v2)

**Last Updated**: August 19, 2026 (23:50)
This document captures the latest architecture and logic as of the complete "Smart Communication" redesign.

## Core Architecture

The project follows a modern Android architecture with **Jetpack Compose** for the UI and a clean separation between data management, call handling, and social features.

---

## 1. Premium Design System (`ShynaTheme`)
- **Universal Styling**: Centralized theme object for consistent colors (Deep Charcoal Primary, Slate Surface, Shyna Green accents).
- **Typography**: Optimized hierarchy for readability with premium off-white and gray text combinations.
- **Interactions**: Standardized ripple effects and rounded corners (16dp-24dp) for all interactive components.

---

## 2. Call Management & Rules Logic
- **Component**: `RuleRepository`
    - **Blocking Hierarchy**: Specific Number > Family Contacts > Unknown Numbers > Allow.
    - **Optimization**: Fast-path for specific numbers to avoid UI lag.
- **Dual SIM Support**: `SimCallManager` handles carrier selection for outgoing/incoming calls.

---

## 3. Shyna Link (Advanced Social Module)
The chat interface has been redesigned into a modern interaction pattern.

### Chat Interface (`SmartChatDetailScreen`)
- **Interaction Pattern**: WhatsApp-style fixed header, scrollable body, and fixed composer.
- **Dynamic Header**: Switches between *Standard*, *Selection* (long-press), and *Search* modes.
- **Messaging Bubbles**:
    - **Geometry**: Custom shapes with tail for incoming/outgoing distinction.
    - **Ticks**: Clock (Sending), Single (Sent), Double (Delivered), Blue Double (Read).
    - **Features**: Quoted Reply previews, Forwarding labels.
- **Composer**: Dynamic Mic-to-Send button transition with a premium 8-item Attachment Hub.

### Profile & DP Persistence
- **Dual Persistence**: DP updates are synced to **Firebase Auth Profile** and **Firestore** simultaneously.
- **Restore Logic**: Re-installs automatically fetch DP from Firebase Auth to prevent data loss.
- **Real-time Sync**: Firestore snapshots ensure that DP changes are visible to other users instantly.

---

## 4. Phone Dialer & Dashboard
- **Component**: `PhoneHomeScreen`
    - **Premium UI**: Integrated Pinch-to-Zoom (0.85x to 1.30x) scaling logic.
    - **Tabs**: Keypad, Recents (Categorized), and Contacts.

---

## 5. Logical Entities (Data Models)
- **`Rule`**: Blocking rule definition.
- **`LocalChatMessage`**: Rich social message metadata including status and media links.
- **`RealUser`**: Extended profile with demographics and dual-synced photo metadata.
- **`Connection`**: Relationship state machine (Pending, Accepted, Blocked).

---

## 6. Project Structure (Snapshot)

### Social & Auth (`/ui`)
- [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt) (Contains Chat, Profile, and Auth screens).

### Call Control (`/call`)
- `SimCallManager`, `CallRecorder`, `CallControlCenter`.

### Data Layer (`/data`)
- `AppDatabase`, `RuleRepository`, `SessionManager`.

---

## Restore Point Verification
To verify the system's integrity after a restore:
1.  **UI Consistency**: Open any chat and verify the `ShynaTheme.PrimaryBg` (0xFF0B141B).
2.  **DP Sync**: Change profile photo and check if it reflects in the `allRealUsers` list via Firestore.
3.  **Chat Logic**: Long-press a message to ensure "Selection Mode" header appears correctly.

> [!TIP]
> This blueprint represents a stable, premium state. All backend integrations (Firebase, LiveKit, Telecom) are verified as functional.
