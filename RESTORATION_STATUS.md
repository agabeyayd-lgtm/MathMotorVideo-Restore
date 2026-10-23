# MathMotor Video — project status

## Current product direction

MathMotor Video is now a **local Android video editor** with an easy, CapCut-inspired workflow. AI generation, AI scoring, social-media data learning, and paid API integration are outside the current scope. Normal editing should work on-device without a cloud account or AI service.

## Published baseline and release policy

- Current baseline: versionCode = 384.
- Existing published release: app-v8.3-44 — MathMotor Video signed APK build 44.
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
- Home screen redesigned around creating an edit, selecting a video directly, and reopening recent exports.
- Preview canvas adapts to the source video's aspect ratio, including rotated sources.
- File access through Android's document picker; automatic network update checking and APK installation flow have been removed.

## Important limitations

- The old V7 APK and prototype archives were recoverable, but the original complete V7 source tree was not. The old app's social-data learning connectors and the exact old UI cannot be reconstructed from these files alone, and are not part of the present product goal.
- Some older internal source files and physics-related tests remain in the repository as reconstruction leftovers; they are not part of the home-screen editing workflow.
- The newly updated source has **not** been compiled or exercised on a physical Android device in this turn. The most recent published APK remains versionCode 384 and does not include these new source changes until a later, explicitly requested build.
- APK build and release are manual-only. Do not infer a new APK exists just because source commits were made.