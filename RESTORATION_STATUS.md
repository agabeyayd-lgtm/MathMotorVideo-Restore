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


## Latest technical update — 2026-10-30

- Candidate run #329 passed unit tests and the Debug APK build, then failed 2 of 20 emulator interaction tests. Both failures were attempts to shorten an 8,000 ms clip by dragging its right trim handle; the measured duration stayed 8,000 ms.
- Root cause: the centered fixed playhead overlay is 40 dp wide, elevated above the timeline tracks, and brought to front. When the clip's right edge is centered under the playhead, it intercepts the touch before the underlying trim handle can receive ACTION_DOWN.
- Candidate source fix: playhead dispatch now passes the initial touch through when its screen coordinate is inside a visible left/right clip trim handle. This preserves playhead drag behavior outside trim-handle bounds.
- Added interaction assertions verify that the right trim handle receives ACTION_DOWN and ACTION_UP before checking that project duration changes.
- Source commit: cbe72383fd080989563e70e6bd184e4f84ec6c9a. Test commit: b2bcdee7c598fe380adbc668cb43667943fa566d.
- Status distinction: fix is in candidate source; automated verification and physical-device verification are still pending. Keep affected items open. Version 395 remains blocked.


## Follow-up after candidate run #332 — 2026-10-30

- Run [#332](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41051140320) completed with unit tests and Debug APK build passing; emulator instrumentation failed 1 of 20 tests.
- The failing test was `multiClipReorderTrimUndoRedoAndSeekStayCorrectAcrossZoomLevels`: its first direct playhead drag attempted to seek to 1,000 ms but remained at 0 ms.
- The right-trim touch-routing regression checks did not report failures; the failure occurred earlier in the same multi-clip test during playhead seeking.
- Cause: the test helper grabbed the vertical playhead overlay at the vertical center of the entire timeline. At the initial 0 ms position, the first clip's right trim handle can occupy that same screen coordinate, so the new correct trim-handle priority makes that ambiguous touch go to the trim handle.
- Test correction committed as `c8680d730de125793e7cb694888c98ba998679cc`: start the playhead drag on its distinct small top head, which is the intended unambiguous playhead grab target, rather than the line where it can overlap a trim handle.
- This is a test-coordinate correction, not a claim that the source fix has passed. A new PR-triggered emulator run is required; keep both playhead and trim acceptance gates open until it passes.


## Candidate run #334 — 2026-10-30

- Run [#334 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921) completed on PR #10's merge ref (candidate head `452aa32c5812e203ba60abc581c53fde55cd0042`).
- Unit tests passed; Debug APK build passed; Android instrumentation passed **20/20 tests, 0 failures, 0 skips**. The log confirms `connectedDebugAndroidTest exit code: 0` and `Connected Android instrumentation tests passed.`
- Uploaded emulator screenshots/diagnostics: [artifact 12976766821](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921/artifacts/12976766821).
- This validates the previously failing right-edge trim and playhead-drag regression paths in the automated emulator suite. It does not replace physical-device validation or close unrelated editor requirements.
- Emulator shutdown warnings after successful instrumentation are cleanup noise; the job concluded successfully.
- Version 395 remains blocked. Do not change version metadata, sign/publish an APK, or enable the updater until every release gate is implemented and verified and the user authorizes release.


## Candidate run #337 — 2026-10-30

- Run [#337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720) passed unit tests, Debug APK build and Android instrumentation: **20/20 tests, 0 failures, 0 skips**. The log confirms `connectedDebugAndroidTest exit code: 0` and `Connected Android instrumentation tests passed.`
- Candidate head: `649cf70799cff4012c7cea2318cd8eed8e95c202`.
- Filmstrip density implementation: thumbnail slot changed from 14 dp to 22 dp, matching the default 22 dp/second timeline scale to target roughly one readable thumbnail per second.
- The `filmstripLoadsFramesFromDistinctTimestamps` instrumentation test now additionally checks that the 8-second fixture has 6–10 visible loaded thumbnails; existing distinct-timestamp and distinct-frame checks remain.
- Emulator screenshots/diagnostics: [artifact 12978811772](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720/artifacts/12978811772).
- Evidence is limited to this automated emulator scenario. Physical-device thumbnail readability/performance and the wider user-facing checklist remain open. Version 395 remains blocked; do not change version metadata, sign/publish an APK, or enable the updater.


## Candidate run #340 — 2026-10-30

- Run [#340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996) completed on `fix/editor-395-mainline`, candidate source commit `388b4be0be7f2c3bb64f03c988565cc618bc9cdb` (with instrumentation test commit `6f18d6c14fcd6586f3205791baf68f4539960220`).
- Unit tests passed, Debug APK build passed, and Android instrumentation completed **20/20 tests, 0 failures, 0 skips**. The job concluded successfully and the log reports `BUILD SUCCESSFUL` and `Connected Android instrumentation tests passed.`
- The targeted empty-editor state check passed in this run: when no video is loaded, the panel exposes the add-video action and hides clip-only editing controls. This is automated emulator evidence for the tested empty state, not a full usability or physical-device sign-off.
- Emulator screenshots/diagnostics were uploaded as [artifact 12984726239](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996/artifacts/12984726239).
- Emulator shutdown emitted the familiar `stop: Not implemented` / Netsim cleanup warnings after the instrumentation command succeeded; they did not fail the workflow.
- Broader timeline, text/audio, Undo/Redo, export, save/reopen, and physical-device acceptance items remain open. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater.
