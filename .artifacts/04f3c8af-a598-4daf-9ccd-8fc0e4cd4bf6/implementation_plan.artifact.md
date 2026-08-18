# Implementation Plan - Bluetooth Automation and Dialer Selection Improvements

Enhance the Dialer UI to support professional text selection (copy/paste) and implement smart Bluetooth audio routing during calls.

## User Review Required

> [!IMPORTANT]
> The Dialer UI will switch from an animation-driven `FlowRow` to a `BasicTextField`. This enables standard Android text selection, cursor movement, and copy/paste/cut functionality.
> The system keyboard will be suppressed, ensuring the custom Dialer keypad remains the primary input method.

> [!NOTE]
> Bluetooth automation will automatically route call audio to Bluetooth if a device is connected/active during a call, adhering to the user's request for "automatic connection logic."

## Proposed Changes

### Dialer Enhancements

#### [MODIFY] [DialerState.kt](file:///C:/Users/admin/Desktop/01 Aug 2026 Shyna_Caller_Guard_Premium/4%20%20%20%20%20Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1%20%20%20%20%20%20%20final%20total%20data%20backup%20video%20call%20samrt%20aap%20Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/DialerState.kt)
- Update `number` to be backed by `TextFieldValue` to support selection and cursor position.
- Add `insert(digit: String)` and `deleteAtCursor()` to support professional editing.

#### [MODIFY] [PhoneHomeScreen.kt](file:///C:/Users/admin/Desktop/01 Aug 2026 Shyna_Caller_Guard_Premium/4%20%20%20%20%20Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1%20%20%20%20%20%20%20final%20total%20data%20backup%20video%20call%20samrt%20aap%20Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- Replace `FlowRow` in `KeypadScreen` with `BasicTextField`.
- Configure `BasicTextField` with `showSoftInputOnFocus = false`.
- Implement visual formatting for phone numbers (e.g., spaces/dashes).
- Add support for Paste action via context menu.

### Bluetooth Automation

#### [MODIFY] [CallControlCenter.kt](file:///C:/Users/admin/Desktop/01 Aug 2026 Shyna_Caller_Guard_Premium/4%20%20%20%20%20Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1%20%20%20%20%20%20%20final%20total%20data%20backup%20video%20call%20samrt%20aap%20Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/call/CallControlCenter.kt)
- Add logic to automatically switch audio route to `ROUTE_BLUETOOTH` if it becomes available in the `supportedRouteMask` while a call is active, unless the user has manually overridden it recently.

#### [MODIFY] [InCallServiceImpl.kt](file:///C:/Users/admin/Desktop/01 Aug 2026 Shyna_Caller_Guard_Premium/4%20%20%20%20%20Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1%20%20%20%20%20%20%20final%20total%20data%20backup%20video%20call%20samrt%20aap%20Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/InCallServiceImpl.kt)
- Ensure `onCallAudioStateChanged` triggers the auto-route logic in `CallControlCenter`.

## Verification Plan

### Automated Tests
- N/A (UI and Telephony behavior verification requires manual/device testing).

### Manual Verification
- **Dialer:** Verify that tapping the number field allows cursor movement, selection, and shows copy/paste options. Confirm the system keyboard does NOT appear.
- **Bluetooth:** During an active call, connect a Bluetooth headset. The app should automatically route audio to the headset. Disconnect it, and it should revert to earpiece/speaker.
