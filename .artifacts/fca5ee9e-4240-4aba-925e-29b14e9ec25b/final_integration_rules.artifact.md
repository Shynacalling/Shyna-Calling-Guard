# Shyna Master Integration Rules (Consolidated FINAL)

This document is the **Single Source of Truth** for the Shyna Caller Guard project. It contains all critical logic, backend configurations, and safety patterns required to restore the app to its perfect working state.

## 1. Backend Migration: Cloudinary (Primary Media)
Firebase Storage is **DISABLED**. All media (Profile DPs, Chat Photos, Chat Videos) now uses Cloudinary.

### Cloudinary Configuration
- **Cloud Name**: `shynacalling`
- **Upload Preset**: `shyna_chat_unsigned` (Unsigned Mode)
- **Initialization**: Handled in `ShynaApplication.kt` within a `try-catch` block.

### Firestore Security Rules
Copy these into **Firebase Console > Firestore > Rules**:
```javascript
service cloud.firestore {
  match /databases/{database}/documents {
    // Users: Publicly searchable for discovery, writable only by owner
    match /users/{userId} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    // Connections/Chats: Require authentication
    match /connections/{connId} {
      allow read, write: if request.auth != null;
    }
    match /chats/{chatId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 2. Stability & Crash Prevention Rules (Critical)

### A. Coil Image Loading (The "ImageVector" Rule)
**CRITICAL**: Never pass `ImageVector` (e.g., `Icons.Default.Person`) into `AsyncImage` models or error/fallback painters.
- **Correct Pattern**:
  ```kotlin
  if (!photoUrl.isNullOrBlank()) {
      AsyncImage(model = photoUrl, ...)
  } else {
      Icon(imageVector = Icons.Default.Person, ...)
  }
  ```

### B. Firestore Data Safety (The "Strict Type" Rule)
Legacy data may have inconsistent types. Always use safe getters.
- **Correct Pattern**:
  ```kotlin
  val v = doc.get("field")
  val safeVal = if (v is Number) v.toLong() else 0L
  ```

### C. LazyColumn List Stability
Every item in a list MUST have a unique, namespaced key to prevent `LazyLayout` identity crashes.
- **Example**: `key = { "msg_${it.id}" }` or `key = { "chat_${it.id}" }`.
- **Deduplication**: Always use `.distinctBy { it.id }` before passing data to a `LazyColumn`.

## 3. High-End Feature Logic

### A. Modern Camera (WhatsApp Style)
- **Implementation**: `ShynaCameraScreen.kt` using CameraX.
- **Logic**: Tap for high-res photo, Long-press for video recording with timer.
- **Auto-Send**: Media is automatically uploaded to Cloudinary and sent to the active chat on capture.

### B. Live Location (Android 14 Ready)
- **Service**: `LocationService.kt`
- **Type**: `FOREGROUND_SERVICE_TYPE_LOCATION` (Mandatory).
- **Safety**: Includes built-in permission checks and `try-catch` guards for foreground starts.

## 4. Restore Checklist
To restore the project from scratch:
1. Ensure `google-services.json` is present in `/app`.
2. Verify Cloudinary preset `shyna_chat_unsigned` exists in your Cloudinary Dashboard.
3. Apply the Firestore Rules from Section 1.
4. Run `clean assembleDebug`.

**FINAL STATUS: COMPLETED & STABLE**
