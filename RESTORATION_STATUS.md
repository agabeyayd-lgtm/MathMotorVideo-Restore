# MathMotor Video — project status

## Current product direction

MathMotor Video is now a **local Android video editor** with an easy, CapCut-inspired workflow. AI generation, AI scoring, social-media data learning, and paid API integration are outside the current scope. Normal editing should work on-device without a cloud account or AI service.

## Published baseline and release policy

- Working source currently declares versionCode = 393 and versionName = 8.27-timeline-text-controls.
- Last documented published baseline: app-v8.3-44 — MathMotor Video signed APK build 44 (older than the current source).
- The source is being improved incrementally on the main branch.
- The debug-build workflow and signed-release workflow are manual-only. Pushing source changes must not automatically produce an APK artifact or release.
- Do not change the version code or publish a replacement APK until explicitly requested.

## What the current source contains

- Android video editor screen with preview, a multi-lane timeline, seek/playhead controls and timeline zoom.
- Import and order multiple clips; select, trim, split, move and remove clips.
- Thumbnail filmstrips for clips.
- Text overlay with adjustable visibility interval, position and size.
- Audio controls for original audio, looping music, and a timed sound effect.
- MP4 export using AndroidX Media3 Transformer with source / 720p / 1080p output-size options.
- JSON project save/load and a local list of exported videos.
- Home screen prioritizes one clear action (select a video), keeps empty-project creation secondary, and presents recent videos and manual update controls in compact sections.
- The home gallery imports saved videos on a background thread so a slow MediaStore scan does not block the first screen.
- Version checks remain available through the in-app button, but do not run automatically on every launch.
- Preview canvas adapts to the source video's aspect ratio, including rotated sources.
- File access through Android's document picker; in-app update checking remains available from a manual button (not on launch), and a newer published version can be downloaded and opened for installation.

## Important limitations

- The old V7 APK and prototype archives were recoverable, but the original complete V7 source tree was not. The old app's social-data learning connectors and the exact old UI cannot be reconstructed from these files alone, and are not part of the present product goal.
- Some older internal source files and physics-related tests remain in the repository as reconstruction leftovers; they are not part of the home-screen editing workflow.
- GitHub Actions will compile/test source changes and capture emulator screenshots; this does not equal testing on a physical Android phone.
- The published APK remains the older documented baseline and does not include new source changes. Do not change versionCode or publish a replacement APK until explicitly requested.
- APK build and release are manual-only. Do not infer a new APK exists just because source commits were made.