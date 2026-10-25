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

## Repair progress log — 2026-10-25

This section is the handoff point. Continue from this branch and update this log after every meaningful change; do not restart the review from scratch. Add any newly discovered defect to the release-blocking checklist before addressing it.

- Working branch: `fix/editor-395-gate`.
- Installed baseline reported by the user: versionCode 394 / release `app-v8.3-61`.
- Release gate: **395 must not be version-bumped, signed, published, or offered as an update until all required items below pass and the user explicitly authorizes release.**
- [x] Created a repair branch without changing the installed/release version.
- [x] Increased filmstrip thumbnail density (target spacing reduced; more frames can be requested for a clip).
- [x] Changed thumbnail sampling to evenly spaced timestamps including clip edges.
- [x] Increased thumbnail decode/crop resolution from 96×54 to 144×81.
- [x] Increased thumbnail cache capacity from 256 to 384 entries.
- [x] Removed the redundant scissors symbol from the bottom “Klip” tab; the dedicated “Böl ✂” action remains the cut/split control.
- [x] Replaced the home-screen scissors graphic with a play icon and removed the extra “VİDEO STÜDYOSU / Videonu hazırla / Kes, birleştir…” explanatory stack.
- [ ] **Not yet verified on emulator or device:** the code changes above may improve the filmstrip, but must be tested with changing and static video content, scroll/zoom, loading latency, memory use and black-frame behavior.
- [ ] Timeline gestures, scroll/zoom and playhead alignment still need implementation and interactive validation.
- [ ] Text-layer timing drag handles and a unified Undo/Redo history still need implementation.
- [ ] Finish home-screen simplification after emulator review; inspect all screens for any remaining redundant scissors icons.
- [ ] Build/unit tests/emulator interaction tests have not yet been run for this repair branch.

Last completed code commit: `f0b9af235fb35f6b03581a41cae653a77e29249f`.

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
