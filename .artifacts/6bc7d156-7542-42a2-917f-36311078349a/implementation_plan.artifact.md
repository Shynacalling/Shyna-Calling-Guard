# Dialer Keypad Improvements and Logout Feature

This plan addresses the performance issues of the dialer keypad, improves the number display scaling (supporting up to 3 lines and 16 digits per line), ensures special characters (*, #) are correctly handled, and adds a logout/sign-out feature for the Shyna Link account.

## Proposed Changes

### Dialer Performance and UI
The dialer keypad's "slowness" is likely caused by heavy contact suggestion calculations on the main thread and unnecessary recompositions. I will optimize this by moving the calculation to a background thread and memoizing the click handlers.

#### [MODIFY] [DialerState.kt](file:///D:/5     01 Aug Shyna_Caller_Guard_Premium/01 Aug 2026 Shyna_Caller_Guard_Premium/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/DialerState.kt)
- Increase the maximum character limit in `append()` from 30 to 100 to support longer dial strings and multiple lines.

#### [MODIFY] [PhoneHomeScreen.kt](file:///D:/5     01 Aug Shyna_Caller_Guard_Premium/01 Aug 2026 Shyna_Caller_Guard_Premium/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- **KeypadScreen Optimization**:
    - Move `suggestions` calculation to a `LaunchedEffect` using `Dispatchers.Default` to prevent UI jank during typing.
    - Use `remember` for the `onDigitClick` lambda to avoid unnecessary recompositions of the keypad themes.
- **Number Display Scaling**:
    - Update `dialNumberFontSize` to start shrinking after 12 digits and ensure 16 digits fit per line.
    - Set `maxLines = 3` in the `BasicTextField` for the dialer number.
    - Adjust `fixedNumberAreaHeight` to accommodate up to 3 lines.

---

### Account Management
Add the ability to "logout" or clear the account settings.

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/5     01 Aug Shyna_Caller_Guard_Premium/01 Aug 2026 Shyna_Caller_Guard_Premium/4     Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Add a "Logout / Clear Account" button in the `CallsPage` connection setup section.
- This button will clear the `user_id` and `server_url` from `SharedPreferences` and reset the local state.

## Verification Plan

### Automated Tests
- N/A (UI-centric changes)

### Manual Verification
- Open the Dialer and type more than 12 digits; verify the font shrinks.
- Type more than 16 digits; verify it wraps to a second line.
- Type more than 32 digits; verify it wraps to a third line.
- Verify that `*` and `#` can be typed.
- Observe typing performance (should be smoother).
- Go to "Shyna Link" -> "Calls" -> "Server & account settings" and test the new Logout/Clear button.
