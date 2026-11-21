# MathMotorVideo

**MathMotorVideo** is an Android video editor built around a practical, timeline-based editing workflow. The project aims to provide a CapCut-style editing experience while keeping core editing operations independent of cloud AI services.

> **Project status:** Active development. The current main-branch source declares Android version `408` (`8.41-capcut-editor`). This is source metadata, not confirmation that a signed APK for that version has been published. [PR #45](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/45) is an open timeline/editor change and must be judged by its exact-head CI results.

## What the app is for

The goal is to make common video-editing tasks available directly on Android:

- Arrange and edit video clips on a timeline.
- Trim, extend, split, move and reorder clips.
- Add and adjust text and visual overlays.
- Work with music and sound effects.
- Adjust playback speed and visual filters.
- Preview edits, undo and redo project changes, and save drafts.
- Export edited video to MP4.

The feature list describes the project scope; individual workflows may still be under active development and are not all guaranteed to be fully validated on every device.

## Project details

| Item | Value |
| --- | --- |
| Platform | Android |
| Application ID | `com.mathmotor.video.next` |
| Android namespace | `com.mathmotor.video` |
| Minimum Android version | Android 8.0 (API 26) |
| Target Android version | API 35 |
| Compile SDK | API 36 |
| Java / Kotlin target | Java 17 |
| Main language | Kotlin |
| Media playback | AndroidX Media3 |

## Build

You need Android Studio or a compatible Android SDK installation, JDK 17, and the Android SDK platform/build tools required by the project.

Clone the repository:

```bash
git clone https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore.git
cd MathMotorVideo-Restore
```

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The debug APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For emulator validation, use the repository's GitHub Actions workflows. A successful local compile or unit-test run alone does not establish that the complete editor flow passes on an Android emulator or physical device.

## Verification and releases

- Check the [Pull Requests](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pulls) page for work in progress.
- Check [GitHub Actions](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions) for build, unit-test and emulator results.
- Treat a change as verified only when the required checks pass on the exact commit being reviewed.
- CI debug APKs are for testing; they are not signed production updates.
- Release signing material is supplied through protected CI configuration and must never be committed to this repository.
- App versioning, the in-app update mechanism and release publication are separate from ordinary editor changes. Do not infer a published release from a version number in source code.

## Development notes

The editor is intended to work without requiring an AI service or a paid AI API. Media editing, timeline interaction, preview and export are implemented in the Android application.

When investigating a regression, use the failing workflow's logs and the exact commit SHA. Do not weaken test thresholds merely to turn a failing check green.

## Further information

- [Open issues](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/issues)
- [Pull request #45 — shared timeline and filmstrip work](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/45)
- [Detailed restoration and engineering history](RESTORATION_STATUS.md)
- [Latest workflow runs](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions)
