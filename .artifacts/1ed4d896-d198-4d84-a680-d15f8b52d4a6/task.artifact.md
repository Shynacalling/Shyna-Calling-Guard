# Tasks - Refine Selection and Cleanup UI

- `[x]` Refine Selection Logic
    - `[x]` Add `id` to `RecentCall` data class
    - `[x]` Fetch `_ID` in `loadRecentCalls`
    - `[x]` Update `RecentsScreen` to use `selectedCallIds: Set<Long>`
    - `[x]` Update selection count and key logic
    - `[x]` Ensure deletion still clears full history for selected numbers
- `[x]` Cleanup UI Features
    - `[x]` Remove `onMessage` from `PhoneHomeScreen` and `RecentsScreen`
    - `[x]` Remove message icon and left-swipe from `SwipeRecentGroupRow`
- `[x]` Final Verification
    - `[x]` Verify selection count is accurate for multiple entries of the same number
    - `[x]` Verify deletion behavior
    - `[x]` Verify build stability
