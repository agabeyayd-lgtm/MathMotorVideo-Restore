# MathMotor Video — current project status

## Timeline frame/zoom repair — 2026-11-01

Source changes on `fix/frame-accurate-timeline-zoom`:
- Added integer `TimelineFrameMath` with a 30 FPS project default and nearest-frame seek snapping.
- Timeline scrub requests and playback-synchronized playhead display are aligned to project-frame boundaries.
- Added a timeline-scoped two-finger pinch recognizer that preserves the time under the pinch anchor after zoom.
- Added focused unit tests for frame boundaries, snapping, alternative FPS values and pinch-anchor preservation.
- Added `TimelinePinchGestureTest` for real API 35 emulator pinch-in/out, anchor stability and shared video/ruler scrolling.
- Wired the API 35 emulator and targeted gesture test into the existing PR-triggered `Verify Android build` workflow; the dedicated screenshot workflow remains for manual runs and main-branch pushes. These workflows do not publish an APK.
- Isolated the pinch instrumentation run in `.github/scripts/run-timeline-pinch-test.sh`; `.github/scripts/run-emulator-checks.sh` now runs the screenshot and gesture checks through one Bash wrapper. The gesture test no longer reinstalls the APK after the screenshot flow stages the deterministic video; the instrumentation assertion checks its absolute app-private path.
- Run #426 failed in the test harness before gesture dispatch: unit tests/build and screenshot/autosave passed, but the second APK installation invalidated the staged fixture. The inline emulator-runner multiline shell block also ended with a syntax error. Pinch zoom, anchor stability and lane/ruler synchronization remain unverified; corrected harness rerun pending.
- Run #430: unit tests/build, deterministic fixture generation/staging, editor video-load, screenshots and autosave passed. The instrumentation's `UiDevice.executeShellCommand("run-as ... test")` fixture check failed before touch events, although the preceding ADB script listed the file in the app-private directory. Replaced that check with `targetContext.filesDir` file validation; actual pinch/anchor/shared-scroll assertions remain unverified until rerun.

Review findings retained as open:
- Existing millisecond-to-pixel mapping is shared across timeline lanes; this part is reusable.
- Existing thumbnail extraction/cache is timestamp-based, but its visual accuracy and loading behavior remain unverified.
- No dedicated adaptive ruler renderer was found in the inspected editor source.
- The playhead is still positioned by absolute timeline time minus horizontal scroll; fixed-center CapCut playhead behavior is not implemented by this change.
- Source-video frame-rate/variable-frame-rate mapping and exact preview-frame correspondence still need verification.

**Validation state:** Unit tests and `assembleDebug` passed in runs #413, #430 and #432. Run #432's API 35 pinch instrumentation started but failed with an instrumentation process crash; the exact crash cause is not yet known. Diagnostic capture has been added for the next run. A physical-device check has not been run. Do not mark related README acceptance checkboxes complete until those checks pass. Version 395 remains blocked and version metadata is unchanged.

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
7. A separate project-duration control is confusing or conflicts with actual timeline duration; use one unified timeline track/ruler and one source of truth for total duration.
8. Timeline ruler labels must automatically adapt to pinch zoom: coarser minute/large-interval labels when zoomed out, seconds when zoomed in, and finer second spacing at close zoom.
9. Undo/Redo may not work; current known implementation records clip edits only and does not include text/audio in one global history.
10. Home screen has redundant explanatory copy/menus and needs a simpler professional layout.
11. Scissors icons are overused; cut/split/trim/delete actions need distinct, restrained controls.
12. The redundant bottom timeline strip (duration + green slider + +/- controls) should be removed; keep one unified timeline/ruler, show time once, and zoom with two-finger pinch gestures.
13. Remove "Klip 1" / "Klip 2" labels and give the reclaimed space to a larger video preview; retain clip selection and boundaries without the labels.
14. Remove timeline navigation/scroll/zoom arrow buttons entirely; use direct touch gestures (one-finger drag/swipe, two-finger pinch, tap-to-select and draggable trim handles) rather than arrow buttons. Keep only functional affordances such as the playhead and trim handles.
15. Bottom tool navigation should swap the four-item dock for the selected tool's controls in the same bottom panel, then restore the four-item dock on back/confirm; tool panels must not hide underneath the preview.
16. Timeline thumbnails need more visible width, clearer frame content and faster prioritized loading/caching.
17. Simplify the two-video join/add-clip area visually while preserving the actual add/order/join behavior.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.

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


## API 35 instrumentation crash root-cause fix — 2026-11-01

- Run #435 diagnostics and source inspection identified a test-harness defect: `TimelinePinchGestureTest` invoked `am force-stop com.mathmotor.video.next` from inside that package's active instrumentation run. The force-stop terminates the target package and its running instrumentation, explaining the generic instrumentation-process crash before gesture assertions could complete.
- Removed the in-test force-stop. The test now clears stale logcat and launches the editor test activity without killing the active test process.
- This is a test-harness correction, **not evidence that pinch behavior passes**. The focused API 35 gesture test must run again; its result is pending.
- No app version metadata changed. Version 395 remains blocked.


## Run #437 — compact-layout ruler omission fixed — 2026-11-01

- Run #437 confirms the earlier instrumentation-process crash was removed: the focused test started and executed on API 35. Unit tests and `assembleDebug` passed; the emulator screenshot/autosave smoke flow and fixture staging also completed.
- The actual assertion failure was `The five-second ruler marker did not appear`. Source inspection found the cause: `refreshTimelineTracks` created the `SÜRE` ruler only when both `!keyboardLayoutActive` and `!compactEditorLayout` were true. The 320×640 API 35 emulator is classified as compact, so the ruler was intentionally omitted and the test could not find `00:05`.
- Corrected the UI to keep the time ruler visible on compact phone layouts (18 dp lane instead of 22 dp); it remains hidden while the keyboard is active. This is a real responsive-editor behavior correction, not merely weakening the test.
- Next validation: rerun unit/build and the focused API 35 gesture test. Pinch-in/out, anchor preservation and video-lane/ruler shared scrolling are still **unverified** until the test completes successfully.
- The emulator termination messages (`stop: Not implemented`, snapshot and Netsim cleanup warnings) occurred after the failing assertion while the runner shut down the emulator; they were not the root cause.
- Version 395 remains blocked; no version metadata or release artifact was changed.


## Run #439 — pinch zoom input correction — 2026-11-01

- Confirmed #439's primary failure was the real UI assertion `Pinch-out did not zoom in enough: 1.0454545`; unit tests, Debug APK build, fixture staging, editor load, screenshots and autosave all passed. Emulator shutdown warnings occurred after the assertion and are not the root cause.
- Replaced the sampled `ScaleGestureDetector` callback path with direct two-pointer span ratios applied on every move event. Pinch ownership begins only when both pointers are inside the timeline; the initial child touch is cancelled so clip/trim controls do not continue processing the same gesture.
- Preserved anchor math: the midpoint time is captured at pinch start and restored to the same viewport offset after zoom. Added unit tests for complete multi-event outward/inward span accumulation and invalid span safety.
- Await the new API 35 instrumentation result. Do not mark gesture/anchor/shared-lane checks complete until it passes. No app version metadata changed; version 395 remains blocked.
