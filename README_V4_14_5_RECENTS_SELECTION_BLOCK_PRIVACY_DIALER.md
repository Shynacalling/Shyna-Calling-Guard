# Shyna Caller Guard Premium v4.14.5

## Recents selection and delete
- Long-press any Recent row to enter selection mode.
- Tap additional rows to multi-select or deselect.
- Added Select all, exit-selection, delete action, and confirmation dialog.
- Deletes every Android call-log entry belonging to the selected numbers.
- Existing recycle-bin snapshot logic remains active through deleteCallHistory().
- Swipe-to-call is disabled while selection mode is active to prevent accidental calls.

## Blocked-number privacy rule
- Enabled SPECIFIC_NUMBER + BLOCK rules are loaded before Recents are built.
- Such numbers are completely excluded from Recents.
- New rejected/blocked attempts are also excluded, even if Android keeps writing them into CallLog.
- The number remains available in the app's Block & SIM rules list.

## Dialer editing rules
- Dialed number is now a native editable/selectable Compose text field.
- Tap allows cursor placement and text selection.
- Long-press exposes Android Copy/Paste/Select all actions.
- Phone keyboard input is supported alongside the custom keypad.
- Input is sanitized to digits, +, * and # and limited to 30 characters.
- Existing T9 matching, SIM selection, video call, backspace repeat and call flow are preserved.

## Validation note
The uploaded project did not contain gradlew or gradle-wrapper.jar, so an isolated Gradle compile could not be executed in this environment. Source-level checks and targeted consistency inspection were completed.
