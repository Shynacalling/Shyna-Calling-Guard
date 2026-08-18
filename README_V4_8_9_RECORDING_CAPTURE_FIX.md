# v4.8.9 Recording Capture Fix

- Replaced unreliable VOICE_COMMUNICATION capture with microphone capture.
- Speaker-assisted recording is enabled while recording so both sides may be captured acoustically.
- Previous call audio route is restored when recording stops.
- Recording only becomes active after MediaRecorder starts successfully.
- Empty, too-short, or silent-size files are rejected and deleted.
- Mono AAC/M4A output at 44.1 kHz and 96 kbps for broad playback compatibility.
- Manual and automatic recording now use the same start/stop path.
