# Shyna Caller Guard Premium v4.13.0 - Shyna Link Super UI

## Phone screen
- Removed the two large Offline/Online buttons from above the dialer.
- Added **Offline Call** and **Shyna Link** inside the top-right three-dot menu.
- Existing dialer, recents, contacts, rules, settings and recycle bin remain available.

## Shyna Link redesign
- WhatsApp-inspired bottom navigation: Chats, Updates, Communities, Calls.
- AMOLED dark premium theme with Electric Blue, Emerald Green and Cyan accents.
- Consistent contrast rule: dark surface uses light text; bright action surface uses dark text.
- Nearby chat list, connection status, communities, mesh relay, SOS and call pages.
- Functional local message persistence.
- Functional Enable/Disable/Auto settings saved in SharedPreferences.
- Offline SIM calling remains linked to SimCallManager.
- Bluetooth, Wi-Fi and SIM settings open the correct Android settings pages.
- Secure server and user account configuration is persisted.

## Added control groups
- AI noise removal and AI spam detection
- Auto reply and cloud backup preferences
- PIN/biometric/secure logs
- Mesh relay, discovery mode and battery saver
- Video quality, auto quality, echo cancellation and picture-in-picture
- Nearby device calls, file sharing entry points and emergency features

## Backend boundary
Real peer-to-peer Bluetooth/Wi-Fi Direct voice/video streaming, mesh packet relay, authentication, push notifications, cloud backup, TURN/STUN and multi-device sync require dedicated native services and/or a secure backend. This build exposes honest working controls and persists settings without pretending those external services are already connected.
