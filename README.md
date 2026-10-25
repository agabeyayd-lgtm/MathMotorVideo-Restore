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

## Release baseline and version 395 gate

- User-confirmed installed baseline: **versionCode 394, release/build 61 (app-v8.3-61)**.
- **Version 395 must not be created, signed, published, or offered for update until every required fix below is implemented and verified.**
- README checklist updates and source commits do not themselves mean a feature is fixed or included in an APK.
- Do not bump `versionCode` or `versionName` during this work until the release gate is met and the user explicitly authorizes the release.
- A successful source build is not the same as a tested, published APK. Keep the original media safe; export to a new file.

## Current baseline capabilities (present in source; not all are proven reliable)

- [x] Multi-clip timeline and clip selection/reordering controls exist.
- [x] Clip trim/split controls and timeline time/pixel mapping logic exist.
- [x] Text layer controls for timing and appearance exist in the source.
- [x] Background music/sound-effect controls exist in the source.
- [x] Editable JSON project save/reopen and local draft autosave are implemented in the source.
- [x] MP4 export path exists in the source.
- [ ] These checkmarks describe existing code paths only; they do **not** certify that the current build behaves correctly on a device. See the required verification checklist below.

## Required work for the next release (395) — NOT DONE until verified

### Timeline touch, scrolling and playhead

- [ ] Make one-finger interactions reliable: select clips, scroll the timeline and drag trim handles without gesture conflicts.
- [ ] Implement reliable two-finger timeline zoom; keep the time under the gesture anchor as stable as possible.
- [ ] Fix the timeline appearing locked to the left; allow moving across the full timeline in both directions and reaching the beginning/end after zooming.
- [ ] Make the playhead behave like CapCut: timeline moves beneath a stationary playhead during scrubbing; preview follows the exact time; playback stays synchronized; split uses the playhead time.
- [ ] Keep time-to-pixel mapping correct after scroll, zoom, trim, clip reorder and multi-clip edits.
- [ ] Make the timeline's time scale and total duration follow the actual assembled clip sequence. Remove or simplify the separate “project duration” UI if it duplicates/conflicts with the actual timeline; preserve any technical export limit only if genuinely needed and explain it clearly.

### Thumbnails

- [ ] Increase thumbnail height/visibility and use enough thumbnails to visually cover the clip; for a 10-second clip, aim for a dense, evenly spaced filmstrip comparable to common mobile editors, not a fixed arbitrary count.
- [ ] Ensure each thumbnail is extracted from its own timestamp so consecutive thumbnails do not all show the same frame.
- [ ] Reduce black placeholders and loading delay; show a useful loading state and replace placeholders promptly as frames become available.
- [ ] Cache/reuse extracted frames and avoid blocking timeline scrolling or editing while thumbnails are prepared.

### Text, audio and undo/redo

- [ ] Make text start/end time editable and ensure the text timeline strip can be trimmed at both ends.
- [ ] Make text size reliably adjustable; preview updates immediately and exported output matches the preview.
- [ ] Verify text timing, placement, size and style remain correct after seeking, zooming, saving/reopening and exporting.
- [ ] Verify Undo/Redo works for all supported edit operations, including text add/delete/timing/size, audio/music changes, clip trim/split/reorder/move; do not show a control for an operation that is not actually undoable.
- [ ] Ensure undo/redo restores both timeline state and preview consistently.

### UI and controls

- [ ] Simplify the home screen: remove redundant menus and explanatory clutter; prioritize choosing/opening a video and entering the editor.
- [ ] Make the home screen and editor responsive to small screens, available height and the software keyboard; controls must remain reachable and not overlap.
- [ ] Remove redundant scissors icons. Reserve scissors for cut/split actions; make trim, split, delete and other actions visually distinct.
- [ ] Keep controls concise, consistently placed, accessible and functional rather than duplicating the same action in multiple places.

### Required validation before version 395

- [ ] Run unit tests for timeline mapping, trim bounds, playhead-to-clip mapping and text timing.
- [ ] Run an interactive Android emulator test covering one-finger scroll/trim, two-finger zoom, playhead scrubbing/sync, thumbnail variety/loading, text timing/size, undo/redo and home-screen navigation.
- [ ] Test with a 10-second video and a multi-clip sequence; check the beginning, middle and end of the timeline at multiple zoom levels.
- [ ] Export an MP4 and verify video, text, audio, duration and playhead cut position; confirm the original input video is unchanged.
- [ ] Save a project, close/reopen it and verify clips, text, audio, duration, zoom and timeline position.
- [ ] Review emulator screenshots and logs; fix regressions and rerun failed checks.
- [ ] Perform a final physical-device smoke test when possible.
- [ ] Only after every required item is complete and verified, prepare version 395. Until then, do not bump the app version or publish an APK.

## Verification boundaries

- Existing unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- The current emulator screenshot workflow builds a debug APK and captures screens, but its script does not by itself prove all touch gestures, exports, Undo/Redo or save/reopen flows work. Add/run interaction-level tests rather than treating screenshots alone as acceptance.
- Record a checkbox as complete only after the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, leave the item unchecked and say so.
- This repository is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree.

### Work log — 2026-10-25 (repair branch, not yet release-verified)

Working branch: `fix/editor-gestures-thumbnails-text-undo`. The installed baseline remains 394 / build 61. No version number was bumped and no APK was signed or published.

**Source-code work completed on the repair branch (still awaiting tests):**

- [x] Implement fixed-playhead coordinates and add unit tests for time/scroll/pinch conversions.
- [x] Add a two-finger pinch handler and remove the filmstrip touch handler that was intercepting horizontal swipes.
- [x] Increase thumbnail height and decoded resolution; raise frame density; add distinct-frame diagnostics to CI.
- [x] Expand Undo/Redo snapshots from clip-only data to the complete project JSON; add history hooks for text, text gestures/timing/size, and audio operations.
- [x] Use a single output-timeline time readout; reduce the home intro copy and remove scissors icons from passive navigation/draft cards.
- [x] Update the home-screen instrumentation selector, run JVM and Android instrumentation tests on PRs, and add CI thumbnail-diversity validation.
- [x] Add a UI instrumentation test for one-finger scroll, two-finger pinch, text start/end, text size, and Undo.
- [x] Order CI so the shell smoke test stages the private sample video before the interaction test uses it.
- [x] Make Undo/Redo controls available from the shared timeline header so they remain accessible in text/audio panels.
- [x] Preserve the user's version 394 / build 61 baseline; leave app version fields unchanged and do not build/publish 395.

**Source changes listed above are not yet accepted as behavior fixes. The acceptance checklist below remains unchecked until tests pass.**

**CI status at this checkpoint:** Android debug APK build passed and JVM unit tests passed on workflow run [#82](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40146879693). The run for the prior source commit was cancelled by subsequent branch pushes, so it does not validate the current head. The new interaction test and corrected fixture/test order are committed; CI for the current head still needs to complete.

**Still open and release-blocking until tests pass:** one-finger scroll / two-finger zoom on real UI, timeline edge alignment, thumbnail performance and diversity on emulator, text range and size behavior in preview/export, global Undo/Redo across all edit types, multi-clip MP4 export, save/reopen, responsive home/editor layout, and physical-device verification. Do not mark the issue checklist complete merely because source code changed. The first CI build and emulator results must be inspected, failures corrected, and the tests rerun before version 395 can even be considered.
