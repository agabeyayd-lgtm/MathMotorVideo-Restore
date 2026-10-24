# MathMotor Video

MathMotor Video is a local-first Android video editor with a CapCut-inspired workflow. Normal editing runs on-device; AI generation, AI scoring and paid cloud APIs are outside the current product scope.

## Editing flow

1. Choose one video from the phone, or open an empty editor.
2. Use the timeline to add clips, select/reorder them, trim their start/end, split clips and move clips earlier or later.
3. Add text layers with timing, position, size, colour and style controls.
4. Add background music and/or a sound effect; adjust levels and effect start time.
5. Preview the result, choose source resolution, 720p or 1080p, and export an MP4 to Movies/MathMotorVideo.
6. Save/reopen an editable JSON project; local draft autosave stores the playhead, zoom and timeline scroll for resume.

The editor uses Android's document picker and AndroidX Media3 for playback/export. Video files are processed locally; no account or cloud upload is required for normal editing.

## Version and build policy

- Latest published signed APK checked in the repository: **app-v8.3-60, versionCode 393**.
- Current source also declares versionCode 393, but source commits made after that APK was published are not present in the existing APK.
- Source verification and emulator screenshot workflows do not publish or update the installed APK.
- Debug APK builds and signed releases remain explicit/manual actions. Do not change the version code or publish a replacement APK until the release is deliberately requested and the changes have been verified.
- For a source build, run **Build MathMotorVideo APK** in GitHub Actions. For a signed release, the separate **Build and publish APK** workflow is required.

## Verification boundaries

- Unit tests cover timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- Emulator smoke checks verify app launch, sample-video import and screenshots. The latest trim/seek changes still need to be verified with interactive trim gestures and by exporting/reopening an MP4.
- A successful source build is not the same as a tested, published APK. Test the final release on a physical Android device.

This repository is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree.
