# Shyna Caller Guard Premium v2.1

## New in v2.1
- Standard Android voice-call entry activity for assistant/deep-link requests.
- Supports `tel:` call intents and `shyna://call?name=Ravi` / `shyna://call?number=9876543210`.
- Resolves a spoken contact name against Google/Samsung system contacts.
- Confirmation screen prevents accidental voice-dial calls.
- CSV contact import with preview, quoted-column parsing, invalid-row filtering and duplicate-number skipping.
- CSV export in `Name,Phone,Email` format using Android Storage Access Framework.
- Contact CSV controls are available in Call settings > Import / export contacts.
- Version updated to 2.1 (versionCode 21).

## Bixby setup
When Shyna Caller Guard is the default Phone app, normal Bixby commands such as “Call Ravi” are handled through Android Telecom and Shyna provides the call UI. For an app-specific Bixby Quick Command or Routine, configure it to open a URI such as `shyna://call?name=Ravi`. A fully published custom Bixby capsule is a separate Samsung Developer/Marketplace package and cannot be embedded only inside the Android APK.

## CSV format
```csv
Name,Phone,Email
Ravi Kumar,+919876543210,ravi@example.com
```
Quoted commas and double quotes are supported. Import requires Contacts permission. New contacts are written to the device’s local contacts provider and can subsequently sync according to the user’s Google/Samsung Contacts settings.

## Device limitations
Two-way call recording, video upgrade, Bluetooth routing and conference merge remain dependent on Android version, Samsung firmware, carrier and call capabilities.
