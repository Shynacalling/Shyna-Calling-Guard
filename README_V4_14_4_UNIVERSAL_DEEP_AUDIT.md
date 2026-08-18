# Shyna Caller Guard v4.14.4 - Universal Deep Audit

Deep source-level audit covered:
- Audio/video call UI and duplicate contact-photo rendering
- Call lifecycle callbacks and cleanup
- Video upgrade request null-safety
- Dual-SIM PhoneAccount/subscription mapping fallbacks
- Recent calls filters, grouping, missed-call popup, photos and swipe actions
- Auto-rejected/blocked call separation
- Contacts photo rendering and CSV export failure handling
- Manifest permissions/components and app version consistency

Additional hardening in v4.14.4:
1. Removed unsafe missed-call popup force unwrap.
2. Removed unsafe contact bitmap force unwrap.
3. Removed unsafe pending video-profile force unwrap; pending state is cleared after accept/decline.
4. Made ICC-ID SIM matching null-safe.
5. Replaced generic export crash with a recoverable IOException.
6. Updated app versionCode/versionName to 78 / 4.14.4.
7. Confirmed audio-call screen now has only one CallerAvatar render site.

Build limitation:
The supplied archive still does not contain gradlew, gradlew.bat, or gradle-wrapper.jar. Therefore a full APK/Gradle compile could not be executed in this environment. Android Studio can regenerate/supply the wrapper and perform the final device/OEM validation.
