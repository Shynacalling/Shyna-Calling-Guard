# Shyna Caller Guard Premium v4.14.3

## Single caller photo fix

- Cross-checked the incoming and outgoing audio-call Compose layout.
- Found that `CallerAvatar(callerData.photoBytes)` was rendered twice:
  1. Once below the call-state and SIM information.
  2. Again inside the audio branch of `AnimatedContent`.
- Removed the second avatar rendering.
- Incoming, outgoing, ringing, and active audio calls now show only one circular contact photo.
- Video-call layout remains unchanged.
