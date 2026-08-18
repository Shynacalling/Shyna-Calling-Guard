# Fix Build and Manifest Errors

The project currently faces a critical build error (errno 206) due to an excessively long file path on Windows. Additionally, the `AndroidManifest.xml` contains several unresolved class references and attribute warnings that may be causing confusion in the IDE.

## User Review Required

> [!IMPORTANT]
> The error **"could not set current directory (errno 206)"** is caused by the Windows `MAX_PATH` limit (260 characters). The current project path is approximately 247 characters long, and Gradle's temporary build files quickly exceed the limit.
>
> **Recommendation:** Please move the project folder to a shorter path, such as `C:\Projects\CallRuleBlocker`, to permanently resolve this build issue.

## Proposed Changes

### Build Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Answer_Bar_Up_Final/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/build.gradle.kts)
- I will verify if any additional dependencies are missing or if the `compileSdk` version causes conflicts with the manifest attributes.

### Android Manifest
#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Answer_Bar_Up_Final/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/AndroidManifest.xml)
- Add `package="com.example.callruleblocker"` to the `<manifest>` tag to help the IDE resolve relative class names (e.g., `.MainActivity`).
- Ensure all attributes like `android:usesPermissionFlags` and `android:maxSdkVersion` are correctly recognized by confirming the `compileSdk` is at least 34.

### Code Organization
#### [NEW] [CallActionReceiver.kt](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Answer_Bar_Up_Final/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/CallActionReceiver.kt)
- Move the `CallActionReceiver` class from `OngoingCallNotification.kt` to its own file. This improves code organization and ensures the manifest can reliably resolve the class.

#### [MODIFY] [OngoingCallNotification.kt](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Answer_Bar_Up_Final/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/OngoingCallNotification.kt)
- Remove the `CallActionReceiver` class definition as it will be moved to its own file.

## Verification Plan

### Automated Tests
- I will attempt to run `gradle assembleDebug` again after applying the manifest fixes.
- *Note:* If the path length remains an issue, the build may still fail with errno 206 until the project is moved.

### Manual Verification
- Verify that the IDE no longer shows "Unresolved class" errors in `AndroidManifest.xml`.
- Confirm that the app's notification actions (Answer/Decline/End) still work correctly with the moved `CallActionReceiver`.
