# Shyna Caller Guard Premium v4.14.6

## Added
- Soft Android system click feedback for dial-pad keys, call button, settings rows and settings switches.
- New **Button press sound** ON/OFF switch in Call Settings. Default is ON.
- Settings cards animate size changes for smoother expansion and state updates.
- Existing dial-pad spring animation, call-button animation, screen crossfade and call controls retained.

## Caller photo safety
- Audio-call caller photo now uses `ContentScale.Fit` with centered alignment.
- The image is no longer stretched or aggressively center-cropped.
- Small inner padding protects faces and photo edges inside the circular frame.

## Notes
- The click uses Android's lightweight system click sound, so it remains subtle and follows device audio behavior.
