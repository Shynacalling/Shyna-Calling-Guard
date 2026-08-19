# Current Project State - Shyna Caller Guard (Smart Communication)

This document serves as a "save point" for all the logic, data structures, and UI components implemented in the `SmartCommunicationScreen.kt` and related modules as of 2026-08-20.

## 1. Core Logic & Features

### Authentication & Profile
- **Firebase Auth Integration**: Supports Email/Password and Google Sign-In.
- **Profile Setup**: Comprehensive profile creation including First/Last Name, DOB, Pincode (with auto-lookup for District/State), Phone, and Custom User ID.
- **Profile Synchronization**: Automatic syncing of Firebase Auth data with Firestore `users` collection.
- **Profile Image**: Integrated image picker, editor (rotation/scale), and upload to Firebase Storage with Firestore URL update.

### Messaging System (Real-time)
- **Firestore Backend**: Messages are stored in `chats/{chatId}/messages`.
- **Message Types**: Supports `TEXT`, `LOCATION`, `FILE`, `VOICE`, `IMAGE`, `VIDEO`, `EVENT`, `POLL`, `CONTACT`, and `LIVE_LOCATION`.
- **Message Status**: Tracks `SENDING`, `SENT`, `DELIVERED`, and `READ`.
- **Reactions**: Multi-emoji reaction support per message.
- **Replies**: UI support for replying to specific messages.
- **Selection Mode**: Multiple message selection for deletion, info, or reply.

### Connection Management
- **Status Tracking**: `NONE`, `PENDING`, `ACCEPTED`, `BLOCKED`, `IGNORED`.
- **Request Flow**: Users can send/accept/ignore connection requests. Includes a cooldown logic for "Ignored" requests.
- **Blocking**: Ability to block users, which prevents communication and filters them from the discovery list.

### UI & UX (Jetpack Compose)
- **Advanced Theme Engine**: Light/Dark/System modes with custom `ShynaColors` palette.
- **Discovery Mode**: Searchable directory of registered users.
- **Active Status**: Real-time "Active Now" stories row for online users.
- **Multimedia Hub**: Premium attachment menu for sharing location, documents, contacts, etc.
- **Voice Recording**: Integrated `AudioRecorder` with waveform visualization and playback speed control.
- **Live Location**: Foreground service integration for real-time location sharing.

## 2. Key Data Models

```kotlin
private data class Connection(
    val id: String, val user1: String, val user2: String,
    val status: ConnectionStatus, val initiator: String, ...
)

private data class LocalChatMessage(
    val id: String, val chatId: String, val text: String,
    val mine: Boolean, val time: Long, val type: MessageType, ...
)

private data class RealUser(
    val uid: String, val name: String, val email: String,
    val phone: String, val customUid: String, val photoUrl: String?, ...
)
```

## 3. Storage & Infrastructure
- **Firebase Firestore**: Main database for users, connections, and chats.
- **Firebase Storage**: For profile pictures and chat media (Images/Videos/Voice).
- **Google Maps API**: Used for static map previews and location sharing.
- **LiveKit**: (Referenced) Used for Voice/Video calling signaling and management.

## 4. Pending/Future Tasks
- [ ] Implement actual ExoPlayer for full-screen video playback.
- [ ] Finalize the "Updates", "Groups", and "Calls" tabs logic.
- [ ] Add end-to-end encryption layer (optional).
- [ ] Integrate "Fast Login Link" logic.

---
**Note**: This summary ensures that all current progress is documented and can be used as a reference if any code is lost or needs restoration.
