# Implementation Plan - Contacts Upgrade: Multi-Selection & Block Option

The user wants to bring the same powerful features from the Recents list to the Contacts tab. This includes the ability to select multiple contacts for batch actions and a quick way to block saved numbers.

## User Review Required

> [!IMPORTANT]
> I will be adding **Multi-Selection** to the Contacts tab. You can long-press a contact to enter selection mode, then tap others to select them. A count will appear at the top, just like in Recents.
>
> Additionally, I will add a **Left Swipe** gesture to contacts to quickly reveal a **Block** action. An **Info icon** will also be added to each row to view local history and block settings for that contact.

## Proposed Changes

### Contacts Tab Selection Logic

#### [MODIFY] [ContactsScreen](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- **Selection State:** Add `selectedContactIds: Set<Long>` to track selected contacts.
- **Selection Bar:** Implement a top bar that appears in selection mode showing the count, "Select all", "Close", and "Delete" actions.
- **Delete Action:** Batch delete selected contacts (with confirmation).

#### [MODIFY] [SwipeContactRow](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- **Selection Support:** Show a checkbox/icon when in selection mode. Handle long-press to enter mode and single-tap to toggle.
- **Left Swipe to Block:** Enable horizontal dragging to the left. Reveal a red "Block" background and icon.
- **Info Icon:** Add a right-side Info button that opens the local detail screen (history + block settings).

### Feature Upgrades & Consistency

#### [NEW] [BlockSettingsDialog](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- Extract the blocking UI into a reusable `@Composable` to be used from both Recents and Contacts tabs.

#### [MODIFY] [SwipeRecentGroupRow](file:///C:/Users/admin/Documents/Softwear/Shyna_Caller_Guard_Premium_v4.14.6_Sound_Animation_Camera_Safe/1       final total data backup video call samrt aap Shyna_Caller_Guard_Premium_v4.14.1_Compile_Fixed/app/src/main/java/com/example/callruleblocker/ui/PhoneHomeScreen.kt)
- Standardize the Info button to always open the local details screen, even if the number is already in contacts.

## Verification Plan

### Manual Verification
- **Multi-Selection:** Select 3 contacts, verify the count shows "3 selected". Verify "Select all" works for the current list.
- **Left Swipe Block:** Swipe left on a contact, verify the block dialog appears and works correctly.
- **Info Button:** Click the Info icon on a contact and a recent call; both should open the same local history screen.
- **Batch Deletion:** Delete multiple selected contacts and verify they are removed from the system address book.
