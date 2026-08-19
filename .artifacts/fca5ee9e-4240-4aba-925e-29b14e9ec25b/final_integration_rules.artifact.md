# Shyna Master Integration Rules (Final)

This document contains the consolidated rules and logic for the Shyna Caller Guard project to ensure seamless operation of DP uploads, calling, and data searching.

## 1. Firebase Backend Rules (The Foundation)

### Storage Rules
Copy these into **Firebase Console > Storage > Rules**:
```javascript
service firebase.storage {
  match /b/{bucket}/o {
    // Rule: Profile pictures are public to read but private to write
    match /profiles/{userId} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    // Rule: Chat media can be accessed by authenticated users
    match /chat_media/{chatId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Firestore Rules
Copy these into **Firebase Console > Firestore > Rules**:
```javascript
service cloud.firestore {
  match /databases/{database}/documents {
    // Rule: Users directory must be readable for the Search/Discovery feature
    match /users/{userId} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    // Rule: Connections and Chats
    match /connections/{connId} {
      allow read, write: if request.auth != null;
    }
    match /chats/{chatId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 2. Core Logic Implementation Rules

### A. The "Safe Upload" Rule (Fixes "Object not found")
Always read file data into memory immediately after selection to prevent URI permission loss.
```kotlin
// Rule: Use putBytes() instead of putFile() for media to be 100% reliable
val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
fileRef.putBytes(bytes).continueWithTask { ... }.addOnSuccessListener { ... }
```

### B. The "Smart Search" Rule
To make searching (Calling Data Search) work perfectly, always normalize inputs:
- Convert emails to lowercase.
- Strip non-numeric characters from phone numbers.
- Filter `allRealUsers` in memory for small datasets (<1000 users) or use Firestore indexing for larger ones.

### C. The "Call Blocking" Rule
Before allowing a call (Voice/Video):
1. Check the `Connection` status between `userId` and `peerId`.
2. If status is `BLOCKED` or `IGNORED`, disable calling buttons.

## 3. Verified Synchronization
All fixes for DP uploads, specialized sanitization of file paths, and real-time messaging updates have been synchronized in `SmartCommunicationScreen.kt`.

**Status: READY FOR DEPLOYMENT**
