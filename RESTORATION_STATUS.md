# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- Version 395 is blocked until every required item in README.md is implemented and verified.
- Do not bump versionCode/versionName, sign a new release APK, publish a release, or offer an update for 395 before the release gate is met and the user authorizes release.
- README checklist and source commits do not mean the behavior is fixed or included in the installed APK.
- Existing repository release notes may lag the user's installed version; use the user's confirmed 394/build 61 as the working baseline.

## Repair handoff — 2026-10-25

- Active branch: `fix/editor-395-gate`.
- Latest source/UI commit before pinch-zoom change: `ebf12bb8c9c752e22d2824c3c3b0c2ec600615b2`.
- Pinch zoom added in source commit `0c4692d391a8893dd0fe1a908700b22667323c47`; anchored zoom behavior is not yet emulator/device verified.
- Emulator workflow run for the current code: [#40167165450](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40167165450), pending when last checked.
- Latest README progress commit: `ac7ac751adb276cfde6428a4f733792b649b3821`.
- Emulator workflow run: [#40167092999](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40167092999), queued when last checked.
- Current work: filmstrip density/resolution/cache and timestamp distribution improved in source; the “Klip” tab no longer uses a scissors icon; workflow now includes unit tests and can run on the repair branch.
- These changes are **not yet emulator/device verified**. Keep them marked in progress until CI and interactive tests confirm the behavior.
- Next: check workflow result; then fix timeline scroll/pinch/playhead behavior, text timing strip drag controls, global undo/redo, simplify home screen and remove redundant scissors icons; add interaction tests; keep this handoff log current.
- Do not change versionCode 394/versionName, do not create version 395 and do not publish an APK before all release-gate items pass.

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
