# MathMotorVideo — restoration and video editor

This repository is a clean reconstruction of the recoverable MathMotorVideo project, not the original V7 source. It contains both the local video-generation/physics-test path and a native Android video editor.

## Current capabilities

- Generate local deterministic motion clips, render vertical H.264 MP4 files, and keep a local video history.
- Open videos in the in-app player and inspect selected videos with frame/metadata checks.
- Edit a multi-clip timeline with a live preview, frame-aware trim and split, clip reordering, and undo/redo.
- Add independent timed text layers, multiple music beds, and timed sound effects with volume and fade controls.
- Automatically back up projects privately, restore a backup, and save/open project JSON (format v3; v1/v2 import supported).
- Export edited videos as MP4 into `Movies/MathMotorVideo`, with output aspect-ratio and resolution controls.

## Build and validation

The GitHub Actions build workflow runs JVM unit tests, builds a debug APK, and runs Android-emulator checks for the editor UI, video-player first-frame status, clip splitting, and MP4 export. A successful run uploads the debug APK artifact.

Emulator checks are useful regression evidence, but **they do not replace a test on a physical Android phone**. The original V7 source tree was not recovered, so this is not a byte-for-byte restoration of the old app.

To run the same local build tasks:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

## Release safety

Pushing or merging code does not publish a public APK. The signed release workflow is manual and should only be run for an explicitly approved release. Before publishing an update, increase `versionCode`; the in-app updater compares that number with the latest release, so re-publishing version code 372 will not be offered as a newer version to users already on 372.

The release workflow requires the persistent signing-key secrets. The same signing identity is required to update an already-installed copy of the app without uninstalling it.

## Recovery limits

The original V7 learning/connector modules and exact original UI/media pipeline were not available in the recovered source files. The current implementation is a source reconstruction and should be validated on the target physical device before treating it as a fully verified replacement.
