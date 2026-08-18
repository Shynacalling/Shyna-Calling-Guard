# Implementation Plan - Send Location Interface

Implement a "Send Location" screen that matches the provided screenshot, triggered from the location link in the attachments menu.

## Proposed Changes

### Build Configuration
#### [MODIFY] [build.gradle.kts](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/build.gradle.kts)
- Add Google Maps Compose and Play Services Maps dependencies.
- Add Google Places SDK dependency.

### Manifest and Permissions
#### [MODIFY] [AndroidManifest.xml](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/src/main/AndroidManifest.xml)
- Add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions without version restrictions.
- Add Google Maps API key meta-data placeholder.

### UI Components
#### [NEW] [SendLocationScreen.kt](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/src/main/java/com/example/callruleblocker/ui/SendLocationScreen.kt)
- Create a Composable that implements the "Send Location" UI:
    - Custom Top Bar with search and refresh actions.
    - Google Map integration with dark theme styling.
    - Map control overlays (Focus/Full screen and My Location).
    - List view for "Share live location" and "Nearby places".
    - Place items with icons and addresses.

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard%20final%2011%20Aug%202026%20(4)/Shyna_Caller_Guard%20final%2011%20Aug%202026/Shyna_Caller_Guard%20final%2011%20Aug%202026/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Add `LOCATION` to `ChatTool` enum.
- Update `AttachmentSheet` to trigger `ChatTool.LOCATION`.
- Integrate `SendLocationScreen` into the `when (activeTool)` block.

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Navigate to a chat detail screen.
- Open the attachment menu.
- Click on "Location".
- Verify that the "Send Location" screen appears and matches the screenshot.
- Verify that the map is displayed and the list of places is shown.
