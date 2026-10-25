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

This is the continuing handoff log. Resume from this branch and update this section after each meaningful change; do not restart the review from scratch. Add newly found defects to the release-blocking checklist before addressing them.

- Working branch: `fix/editor-395-gate`.
- User-confirmed installed baseline: versionCode 394 / build 61 (`app-v8.3-61`).
- Release gate: **do not create, sign, publish or offer version 395 until all required fixes pass and the user explicitly authorizes release.**
- [x] Created a repair branch without changing installed/release version metadata.
- [x] Increased thumbnail density, timestamp distribution, decode/crop size and bitmap cache capacity in source.
- [x] Replaced the home-screen scissors graphic with a play icon and removed redundant explanatory header copy.
- [x] Added two-finger zoom logic in source, plus unit-testable centered-scroll/anchor mapping helpers.
- [x] Implemented a stationary center playhead in source: every timeline lane receives symmetric half-viewport padding, scrolling maps the center position to project time, playback/seek moves the timeline under the playhead, and dragging the playhead scrolls the timeline.
- [x] Added unit tests for center-playhead mapping, keeping the zoom anchor under the same finger position, and reaching the exact timeline end with symmetric padding.
- [x] Updated CI path filters so changes in `app/src/test/**` and `app/src/androidTest/**` run the workflow.
- [x] Removed an early `requestDisallowInterceptTouchEvent(true)` in the video filmstrip's down event so ordinary one-finger horizontal swipes can reach the parent timeline scroller; long-press menu continues to own the gesture when activated.
- [x] Added a UIAutomator interaction test that starts the deterministic 8-second sample video, drags the fixed playhead, pinches to zoom, and repeatedly scrolls until it asserts the actual final frame (≥7.8 s) and start frame (≤0.2 s).
- [x] Updated the older home-screen screenshot test to expect the current `Videoların` heading instead of the removed `VİDEO STÜDYOSU` label.
- [!] **The first three UI-interaction runs failed before testing timeline behavior:** runs [#40169846327](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169846327), [#40172042627](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172042627) and [#40172447528](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172447528) all passed unit tests and debug APK build, then reported the instrumentation process as crashed. Artifact #12590244129 exposed the cause: the test itself ran `am force-stop com.mathmotor.video.next`, which killed the app process hosting the instrumentation runner. No playhead/pinch assertion had been reached, so these runs do not validate or disprove the timeline implementation.
- [x] Removed the self-killing `am force-stop` from the UI test and retained the explicit start of `EditorTestEntryActivity`; the next emulator run must verify that the test can now reach its assertions.
- [x] Added a newly discovered release blocker to the checklist: the ruler rounds a non-5-second total up to a 5-second boundary, which can make the ruler longer than the actual clip lanes (for example, 8 seconds becomes 10 seconds). This needs code correction and timeline interaction verification after the current test run.
- [x] A suspected pinch-dispatch defect was patched in source because the parent could intercept before `ScaleGestureDetector` saw the initial `ACTION_DOWN`. This change remains unverified until the interaction test gets through pinch assertions.
- [x] Added per-stage interaction-test log markers and a standalone CI script to preserve test XML/HTML reports and post-test logcat even when the test fails.
- [ ] **Revalidation pending:** the new workflow triggered by test commit `1dd19108139c8b353d1c1cc3606b749b33d8228c` must complete. Read the assertion report/logs before further timeline behavior changes.
- [ ] Align the ruler's scrollable extent with the assembled clip sequence; source review found the ruler currently rounds total duration up to a 5-second boundary, so an 8-second sequence gets a 10-second ruler and may scroll beyond the actual video tracks.
- [ ] **Still not verified interactively:** one-finger lane scrolling/trim, pinch, fixed playhead sync, thumbnail variety/loading, text timing and size, Undo/Redo, export, multi-clip assembly and save/reopen.
- [ ] Text-layer timing handles and unified Undo/Redo history still need work; known history records clip edits only.
- [ ] Finish home-screen refinement after reviewing screenshots; scan every screen for redundant scissors icons.
- [ ] Extend interaction tests to cover trim handles, split at the playhead, text timing/size, Undo/Redo, audio, MP4 export and save/reopen.
- [ ] Do not bump versionCode/versionName or prepare a release APK until every checklist item passes and the user authorizes the release.

Latest playhead source change: `ab6d614c8ceff847537fce4cd77a69db50fb3bc9`. Timeline math helpers: `2213b8d5305837a92b7408abfc813e91b1f216b6`. Mapping tests: `f8039c82c989d99f9e73df2e90f69719665e980d`. Filmstrip swipe fix: `8458c7737649bf05ffba10d58208dea3304993f3`. Interaction test: `7f1b886a5d8a4d0bce7163ae0c60b75ec7ea92fd`; strengthened exact start/end assertions: `7dd20078dd35a0f68b70075ff5bb8e20769b712b`. Pinch event dispatch fix (not yet validated): `af579dd0a156bbad0ee4f93a734a51d2caf65e3d`. Interaction test trace markers: `96cdf9d0686f6f0c7db86544dac093ab1a2ac56d`. CI failure-report capture attempt: `72562469110b7d5c18024af9e01aa512ad7daa06`. Consolidated diagnostic script: `4de40f3ad49d8b49d5f7ff3d5fcf439d6f135914`; workflow wiring: `080a15f50557c70bc0d5e030a38f26ed27539870`. Removed instrumentation self-force-stop: `1dd19108139c8b353d1c1cc3606b749b33d8228c`. Stale-home-test update: `64670fbefbf88b66210190c936568111d9b4b25c`. Instrumentation workflow: `ede0225a127021945736839ccf0f5199abb0a2e5`.

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
- [ ] Verify two-finger timeline zoom interactively; the source now calculates the pinch anchor so the time under the fingers stays stable.
- [ ] Verify free horizontal timeline travel, including exact start/end access. Source now adds symmetric half-viewport padding and uses centered playhead mapping; still pending interactive verification.
- [ ] Verify CapCut-style fixed-playhead behavior end-to-end: timeline moves beneath the stationary playhead, preview/playback stay synchronized, and split uses the playhead time. The core scroll/time mapping was just changed in source.
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

- [ ] Confirm CI unit tests pass for timeline mapping (new center-playhead/zoom-anchor tests were added), trim bounds, playhead-to-clip mapping and text timing.
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
