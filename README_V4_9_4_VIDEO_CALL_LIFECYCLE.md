# v4.9.4 Video Call Lifecycle Fix

- Always-visible end call control for video sessions.
- Ongoing call notification with End call action when app is backgrounded or screen is off.
- Remote display and local preview surfaces are explicitly attached and detached.
- Camera, preview and display surfaces are released before disconnect.
- Conference parent/children are terminated together to prevent a surviving video leg.
- Audio control panel is hidden during video layout to avoid overlapping/off-screen end controls.
