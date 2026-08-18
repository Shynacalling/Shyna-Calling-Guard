# Implementation Plan - Performance Optimization and Feature Fixes

This plan addresses UI jank, call blocking latency, recording issues, and overall app smoothness to match a "Samsung-like" experience.

## User Review Required

> [!IMPORTANT]
> - I will be removing the "Enable Speaker" notice for recording as requested. Note that recording quality on some devices may decrease without the speakerphone on, as third-party apps have limited access to internal call audio.
> - The "1 ring" blocking delay will be reduced by optimizing database and contact queries.

## Proposed Changes

### [Core Logic & Performance]

#### [MODIFY] [RuleRepository.kt](file:///D:/Shyna_Caller_Guard_Premium_Poco_Final_Checked/app/src/main/java/com/example/callruleblocker/data/RuleRepository.kt)
- Optimize `isInFamilyGroup` to use a single query for group membership instead of nested queries.
- Ensure `decide` is as fast as possible by caching or pre-calculating common values.

#### [MODIFY] [InCallServiceImpl.kt](file:///D:/Shyna_Caller_Guard_Premium_Poco_Final_Checked/app/src/main/java/com/example/callruleblocker/InCallServiceImpl.kt)
- Reduce the timeout for rule decision and ensure it's executed on a high-priority dispatcher if possible.

### [UI Smoothness]

#### [MODIFY] [PhoneHomeScreen.kt](file:///D:/Shyna_Caller_Guard_Premium_Poco_Final_Checked/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- Optimize `SwipeRecentGroupRow` and `SwipeContactRow` by using raw offsets during dragging and only animating on release.
- Refine the `pinchModifier` to prevent it from interfering with list scrolling and item dragging.
- Apply `remember` to expensive calculations in the list items.

#### [MODIFY] [CallActivity.kt](file:///D:/Shyna_Caller_Guard_Premium_Poco_Final_Checked/app/src/main/java/com/example/callruleblocker/CallActivity.kt)
- Remove the "Speaker on karo" Toast message.
- Update `RecordingIndicator` to a more modern, subtle red status pill.
- Optimize the `MaterialYouCallTheme` drag logic for better responsiveness.

### [Recording Fixes]

#### [MODIFY] [CallRecorder.kt](file:///D:/Shyna_Caller_Guard_Premium_Poco_Final_Checked/app/src/main/java/com/example/callruleblocker/call/CallRecorder.kt)
- Lower the minimum amplitude threshold for recording validation to prevent false "blocked" detections.

## Verification Plan

### Automated Tests
- N/A (UI and timing-specific fixes).

### Manual Verification
1. **UI Smoothness**: Drag recents and contacts; check for any frame drops or "stickiness".
2. **Call Blocking**: Test with a blocked number and verify it cuts the call quickly (ideally within 1 ring).
3. **Recording**: Start a recording, verify no "Speaker" notice appears, and check that the recording file is saved even with low volume.
4. **Overall Experience**: Navigate through the app and check that all transitions and actions feel "Samsung smooth".
