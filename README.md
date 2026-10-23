# MathMotor Video

MathMotor Video is a local-first Android video editor. The current project is focused on a clear, CapCut-inspired editing workflow rather than AI-based generation or analysis.

## Editing flow

1. Choose one video from the phone, or open an empty editor.
2. Use the timeline to add clips, select/reorder them, trim their start/end, split clips, and move clips earlier or later.
3. Add an optional text layer with timing and position controls.
4. Add background music and/or a sound effect; adjust volume and effect start time.
5. Preview the result, choose source resolution, 720p, or 1080p, and export an MP4 to Movies/MathMotorVideo.
6. Save and reopen an editable project as a JSON file.

The editor uses Android's document picker for user-selected media and AndroidX Media3 for playback and export. Video files are processed on-device; no AI service, paid API, account, or cloud upload is required for ordinary editing.

## Version and build policy

- Published baseline: **versionCode 384** (app-v8.3-44).
- Source code can be updated incrementally without changing versionCode or publishing a new APK.
- Both GitHub Actions workflows are manual-only. A source update does not automatically build or release an APK.
- Create a new APK/release only when it is explicitly requested.

To run the source build manually, open the repository's **Actions** tab and run **Build MathMotorVideo APK**. That workflow executes unit tests and creates a debug APK artifact; it does not publish a release. The separate **Build and publish APK** workflow creates a signed release and must also be started manually.

## Current caveats

VersionCode 384 is the last published APK. Newer source updates are not yet included in that APK unless a later build is explicitly requested. After source changes, run the manual build and test on a real Android device before treating the changes as verified.

This repository is a reconstruction from the recoverable project files, not an exact recovery of the old V7 source tree.