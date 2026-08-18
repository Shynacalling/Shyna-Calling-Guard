# Shyna Caller Guard Premium v4.12.0 - Smart Communication

## Added on the phone home screen
- Offline Call
- Online Call & Chat

## Offline Call
- Phone number entry
- Ask SIM / SIM 1 / SIM 2
- Direct cellular call through the existing SimCallManager
- Bluetooth device settings
- Carrier Wi-Fi Calling settings
- Mobile network / SIM / VoLTE controls
- Android Auto / car Bluetooth shortcut

## Online Call & Chat
- Internet connectivity status
- Shyna server configuration (HTTPS/WSS)
- Shyna user ID/account field
- Internet audio-call entry point
- Internet video-call entry point
- Local chat flow with persisted messages
- End-to-end encryption preference
- Wi-Fi-only video preference
- Low-data mode
- Read receipts
- Presence control
- Unknown-user blocking

## Backend boundary
The mobile application flow, controls, persistence, validation and service entry points are prepared. Real user-to-user audio/video signaling, authentication, message delivery, push notifications, TURN/STUN media routing and multi-device sync require the purchased backend server and credentials. Until a secure server URL is configured, online audio/video buttons remain disabled instead of pretending to work.

## Version
- versionCode: 75
- versionName: 4.12.0
