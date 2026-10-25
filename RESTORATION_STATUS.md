# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- Version 395 is blocked until every required item in README.md is implemented and verified.
- Do not bump versionCode/versionName, sign a new release APK, publish a release, or offer an update for 395 before the release gate is met and the user authorizes release.
- README checklist and source commits do not mean the behavior is fixed or included in the installed APK.
- Existing repository release notes may lag the user's installed version; use the user's confirmed 394/build 61 as the working baseline.

## Current source functionality (implementation exists; reliability still needs testing)

- Home screen with gallery import, empty-editor action, recent outputs and local drafts.
- Video import, multi-clip timeline, clip reorder/copy/delete/split, source trim start/end, playhead seek and timeline zoom.
- Trim and playhead controls use millisecond mapping; legal trim bounds consider the opposing edge and source duration.
- Video thumbnail extraction uses timestamped platform-frame APIs first, then Media3 for the requested timestamp.
- Text layer timing, position, size, colour and bold settings; background music and one timed sound effect; original audio volume.
- MP4 export with source / 720p / 1080p options via Media3 Transformer.
- JSON project save/load and local draft autosave, including playhead time, zoom and horizontal scroll.
- Responsive preview/timeline layout, reduced keyboard mode and compact dock.

## User-reported problems for the next release (all remain open)

See the detailed, release-blocking checklist in README.md. The active list is:
1. One-finger timeline gestures and two-finger zoom are not usable/reliable.
2. Timeline appears locked to the left and cannot be moved freely.
3. Timeline filmstrip thumbnails are too small and too sparse.
4. Thumbnails repeat the same frame and initially show black before resolving.
5. The red playhead does not behave like CapCut's playhead; time, preview, playback and split position must stay aligned.
6. Text start/end timing cannot be set reliably and text size is not adjustable as expected.
7. A separate project-duration control is confusing or conflicts with actual timeline duration.
8. Undo/Redo may not work; current known implementation records clip edits only and does not include text/audio in one global history.
9. Home screen has redundant explanatory copy/menus and needs a simpler professional layout.
10. Scissors icons are overused; cut/split/trim/delete actions need distinct, restrained controls.

## Verification gaps

- Unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- CI currently builds and captures screenshots, but does not fully exercise timeline gestures, all text/audio edits, global Undo/Redo, export, or save/reopen end-to-end.
- Test export with multiple clips, trim boundaries, timed text, music and sound effects. Verify output duration, frame content and audio sync; confirm the original input is unchanged.
- Test project save/reopen and verify clips, text, audio, duration, zoom and scroll position.
- Run an interactive Android emulator test and review logs/screenshots. Perform a physical-device smoke test when possible.
- Leave every checkbox in README.md unchecked until the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, report that limitation.

## Release gate

**Version 395 must not be created or published until all required fixes and validations in README.md pass.** A successful build alone is not acceptance. Keep the source version metadata unchanged during this repair phase.

## Recovery background

Some older physics/scenario-related source and tests remain as reconstruction leftovers; they are outside the active video-editing workflow. The original complete V7 source tree was not recoverable.

## Latest repair work (2026-10-25)

Repair branch: `fix/editor-gestures-thumbnails-text-undo`. Installed baseline remains 394 / build 61. No version bump, signed build, or publication has been made.

Source updates made but **not yet verified**:
- Fixed playhead design toward a stationary, white viewport marker; timeline scroll and zoom use shared pixel/time mapping helpers.
- Added pinch zoom handling and removed the clip-filmstrip touch listener that intercepted horizontal swipes.
- Increased thumbnail image resolution and filmstrip size/density; added per-frame diagnostic fingerprints for emulator validation.
- Undo/Redo now serializes the complete project state; hooks have been added for text, overlay movement/scale, text timing/size, audio volume and audio add/remove operations.
- Home introductory copy and non-action scissors icons have been reduced.
- CI executes JVM unit tests and Android instrumentation tests on pull requests, followed by the emulator import/screenshot/draft smoke flow and thumbnail-diversity validation. The fixture staging order has been corrected so the interaction test can access the generated video.

Current checkpoint: source changes and an interaction test are committed on the repair branch. Earlier source revisions built and passed JVM tests, but CI for the latest head is still pending. Fix any build/test failures first. Keep the user-facing issue list open until actual behavior and tests are confirmed; do not create version 395.
