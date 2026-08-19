# Implementation Plan: Modern WhatsApp-Style Chat Redesign

Redesign the one-to-one chat interface (`SmartChatDetailScreen`) in the Shyna Calling app to follow a modern interaction pattern (similar to WhatsApp) while maintaining Shyna branding and preserving all existing core logic.

## User Review Required

> [!IMPORTANT]
> - **Visual Identity**: The layout will mimic WhatsApp's interaction model (header, message bubbles, composer), but will use Shyna's existing color palette (LinkBlue, LinkGreen, LinkCyan, LinkBg).
> - **Backend Hooks**: Features like "Mute Notifications", "Wallpaper", and "Starred Messages" will be implemented as UI-ready components. If the current Firestore/Local schema doesn't support them, they will be marked as "BACKEND REQUIRED".
> - **Selective Deletion**: "Delete for Everyone" will be enabled only for messages sent by the current user within a 48-hour window (standard pattern).

## Proposed Changes

### 1. UI Architecture & State Management
- Introduce a `ChatSelectionState` to manage multi-select, reply-to, and forward-to states.
- Refactor `SmartChatDetailScreen` into a clear `Scaffold` structure.

### 2. Header Component (`ChatHeader`)
- **Left**: Back Button + Circular Profile Photo + Name/Status (Vertical Column).
- **Right**: Video Call Icon + Voice Call Icon + 3-dot Menu.
- **Dynamic Header**: Switch to "Selection Header" (X, Count, Reply, Star, Delete, Forward, More) when messages are long-pressed.

### 3. Message Body (`MessageList`)
- **Dividers**: Centered "Date Dividers" and "Unread Message" separators.
- **Bubbles**: Updated `ShynaMessageBubble` with:
    - WhatsApp-style tail/corners.
    - Status ticks (Clock, Single Tick, Double Tick, Blue Tick).
    - Quote/Reply preview inside the bubble.
    - Media templates (Image, Video with duration, File with icon/size, Location with map preview).

### 4. Bottom Composer (`ChatComposer`)
- **Layout**: [Emoji] [Message Field] [Attach] [Camera] [Mic/Send].
- **Interaction**:
    - Toggle between Mic and Send icon based on text input.
    - Attachment sheet with 8 standard options (Document, Camera, Gallery, Audio, Location, Contact, Poll, File).

### 5. Utility Screens & Dialogs
- **Message Info**: Screen showing Sent/Delivered/Read timestamps.
- **Forward Screen**: Recipient selection list.
- **Media Hub**: Tabbed view (Media, Links, Docs).
- **Clear/Delete Dialogs**: Confirmation modals as per requirements.

---

## Component Breakdown

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Massive refactor of `SmartChatDetailScreen` and its sub-composables.
- Implement new state variables for selection and reply.

---

## Verification Plan

### Automated Tests
- Run `app:assembleDebug` to ensure no breaking changes in existing `Telecom` or `LiveKit` logic.

### Manual Verification
1. Open a chat and verify the new 2-line header (Name + Online status).
2. Long press a message to enter selection mode and verify the header change.
3. Type text in the composer and verify the Mic icon switches to a Send icon.
4. Send an image and verify the rounded preview with timestamp overlay.
5. Tap the "Info" option in selection mode to see message delivery details.
