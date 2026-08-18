# v4.14.2 Recents / SIM / Swipe Fix

Implemented:
- Replaced fragile `accountId contains "2"` SIM detection with active subscription and Telecom phone-account mapping.
- Recent rows now carry a resolved physical SIM slot and use consistent SIM badges/filtering.
- Calls automatically rejected by block rules are recorded locally and reclassified as `BLOCKED_TYPE` when the system call log appears.
- Auto-blocked calls remain hidden from the default All Calls view and appear under the Blocked Calls filter.
- Recent rows fetch and display the same contact photo URI used by Contacts.
- Contact list rows now support right-swipe to call and left-swipe to message, with matching animation and haptic threshold feedback.
- Existing full-screen caller photo background behavior is retained in `CallActivity`.

Build note:
The supplied archive contains `gradle/wrapper/gradle-wrapper.properties` but does not contain `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`. Open in Android Studio and sync/build, or restore the Gradle wrapper files before command-line compilation.
