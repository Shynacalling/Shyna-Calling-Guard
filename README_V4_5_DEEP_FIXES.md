# Shyna Caller Guard v4.5

Deep fixes in this maintenance build:

- Dark native activity theme removes white flashes around the internal dialer/call UI.
- Bluetooth routing validates supported call audio routes and opens Bluetooth settings when no call device is connected.
- Video-upgrade button now reports the carrier/Telecom result instead of silently doing nothing.
- Incoming answer labels are tappable in addition to the swipe gesture; end-call uses state-aware disconnect/reject handling.
- Tapping a Recents row opens complete number history and linked recording playback.
- New recordings include the normalized number in their filename for number-history matching.
- Block settings now open a blocked-number-first screen instead of looking like SIM settings.
- Removed the tag/rules icon from the keypad top bar.
- Contact list deduplicates repeated phone numbers and offers Samsung Contacts duplicate review/merge management.
- Automatic recording exclusions and selected-number controls are nested under Automatic recording and its Selected scope.
- Call-background selection is now an explicit dialog and applies on the next call screen.
- Wi-Fi Calling uses Samsung/Android-compatible routes with a safe settings fallback.
- Recents large header follows drag distance smoothly instead of snapping after a threshold.

Carrier/OEM limits still apply to two-way recording, active-call video upgrades, conference merge, and Bluetooth routing.
