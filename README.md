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
- [x] Added a separate UIAutomator trim test for both the left and right filmstrip handles; it asserts that each drag reduces assembled project duration by at least 150 ms while duration remains valid. Test commit `ab25bd5af389890ea1e32edeac627d59663ee355`.
- [!] **First trim-test run was blocked by emulator setup, not the trim assertion:** [run #40178081902](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178081902) detected an Android system dialog, “Pixel Launcher isn't responding,” and stopped before instrumentation. The CI recovery script now recognizes Pixel/Nexus Launcher ANR dialogs as emulator/system noise while continuing to fail genuine MathMotor ANRs (commit `b56fd2d26aabb26adb216bac8c9303ef0abc9b4d`).
- [x] **Basic left/right trim drags verified:** workflow [#40178530256](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178530256) passed. The right handle changed total duration 8,020 ms → 7,110 ms; the left handle then changed 7,110 ms → 6,210 ms. Both bounds remained valid. The CI script also recovered the Pixel/Nexus Launcher setup issue and the editor screenshot/autosave smoke checks passed.
- [ ] Broader trim validation remains open: test restoring trimmed footage by dragging outward, trim/scroll gesture conflicts, several zoom levels, and multi-clip boundaries. Do not mark all trim behavior complete based on one fixture.
- [x] Updated the older home-screen screenshot test to expect the current `Videoların` heading instead of the removed `VİDEO STÜDYOSU` label.
- [x] **Historical pinch failure, now resolved in the latest interaction run:** run [#40173820023](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40173820023) initially failed pinch. Run [#40177103726](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40177103726) verified zoom 100% → 626% while preserving the 3.18 s center time.
- [x] Added diagnostics and tried releasing child scroll interception when the second finger lands; source commit `10d79a153dd37ca70b28e47e497106b66ea70c53`. Its runtime test did not yet establish a successful zoom.
- [x] Fixed a separate screenshot-test failure by writing screenshots into the app-under-test's writable cache rather than relying on an unavailable instrumentation external-files directory; commit `eca5a83e19b0de2c4a3e87aa56b21efc1714a662`.
- [x] Made the UIAutomator pinch slower and wider (85%, 60 steps); test commit `f7ada92caddfe683710ff8162dd0e7a47104d3ae`.
- [x] Replaced reliance on `ScaleGestureDetector.onScale` with an explicit two-pointer span ratio. The gesture captures initial distance, zoom and anchor time, then recalculates zoom and scroll from the current finger distance; commit `453e35e2f0b94e6d3d813583fdd61a82bf44c475`. Verified in run #40177103726 (100% → 626%; center time preserved).
- [x] Coalesced pinch redraws to at most one complete timeline rebuild per frame, rather than rebuilding all tracks synchronously for every movement; commit `fa0faa998fbab5e2efed8a89f847ae241dfd7d1a`. The pinch interaction passed in run #40177103726.
- [x] Corrected filmstrip drag/tap handling in source commit `1fe72bcb2d6257b28541902fd13bd51ec1a1a6d5`: horizontal drags scroll the whole timeline; taps select/seek. Verified in run #40177103726, including scrolling to 8.00 s and back to 0.00 s.
- [x] **Historical boundary-test failure resolved by correcting the test retry budget:** run #40175226161 stopped at 2.47 s after four reverse swipes; run #40176057466 stopped at 2.04 s with the same limit. After increasing the limit to 12 without relaxing the ≤0.20 s assertion, run #40177103726 reached 0.00 s on the sixth reverse swipe.
- [x] Patched the scroll/preview race in source commit `68b593e6d15e1f773a4811c95dcf92a63dee7955`: the debounced scroll seek remains protected by a 450 ms settle window. The latest interaction test in run #40177103726 passed the end-to-start scroll path.
- [x] **Historical revalidation run #40175226161** exercised the explicit span-based pinch and coalesced redraws, but failed the return-to-start interaction assertion at 2.47 s. This result is superseded by the newer run recorded below; neither run qualifies the timeline for release.
- [x] Removed self-killing `am force-stop` commands from both UI tests. The earlier instrumentation process-crash problem is fixed; the interaction test now reaches assertions.
- [x] New clip imports are paused at the first frame instead of auto-playing while the editor is opening.
- [x] Reworked pinch event routing so the parent observes each original MotionEvent once before child interception. Added an accessible zoom-percentage label and diagnostic scale logs.
- [x] Replaced the clipped visible-width pinch assertion with an accessible zoom-percent assertion and centered-time stability check.
- [x] Corrected the ruler and empty text/music/sound lanes to end at the actual assembled duration; the old 5-second rounding made an 8-second sequence's ruler run to 10 seconds.
- [x] Added a consolidated CI diagnostic script to preserve Android test reports and logcat on failure.
- [x] **Timeline-interaction revalidation passed:** [run #40177103726](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40177103726) passed unit tests, debug build, the screenshot smoke test, and the interaction test. The old cancelled run #40174657026 is superseded.

- [ ] **Still not verified interactively:** trim/scroll conflict edge cases and restoring trimmed footage, thumbnail variety/loading, text timing/size, complete Undo/Redo, audio behavior, multi-clip assembly/export, editable project reopen, keyboard/small-screen reachability and physical-device behavior. Basic left/right trim drags on the 8.02 s fixture are verified in run #40178530256.
- [ ] Text-layer timing handles and unified Undo/Redo history still need work; known history records clip edits only.
- [ ] Finish home-screen refinement after reviewing screenshots; scan every screen for redundant scissors icons.
- [ ] Extend interaction tests to cover trim handles, split at the playhead, text timing/size, Undo/Redo, audio, MP4 export and save/reopen.
- [ ] Do not bump versionCode/versionName or prepare a release APK until every checklist item passes and the user authorizes the release.

Fixed-playhead source change: `ab6d614c8ceff847537fce4cd77a69db50fb3bc9`. Timeline math helpers: `2213b8d5305837a92b7408abfc813e91b1f216b6`. Mapping tests: `f8039c82c989d99f9e73df2e90f69719665e980d`. Filmstrip swipe fix: `8458c7737649bf05ffba10d58208dea3304993f3`. Interaction test: `7f1b886a5d8a4d0bce7163ae0c60b75ec7ea92fd`; exact start/end assertions: `7dd20078dd35a0f68b70075ff5bb8e20769b712b`. Pinch dispatch first attempt: `af579dd0a156bbad0ee4f93a734a51d2caf65e3d`; dispatch-once/import-paused source change: `f2c3c520df6f82991bfb5540d911a720cf907143`. Updated zoom percentage test: `d60b2d7528ed5fe40183962a5ee4b93215767cf7`. Removed force-stop from screenshot test: `57e1a4c924d4afbbc9dbd88de0e4bdfbc333fa6d`; instrumentation test process fix: `1dd19108139c8b353d1c1cc3606b749b33d8228c`. Exact ruler duration fix: `f742117b01cd9e63a5b27cbacdc4d1d67a19ac68`; sound placeholder duration fix: `546d750957923b24b2376b4b29d413b637319256`. Diagnostic script: `4de40f3ad49d8b49d5f7ff3d5fcf439d6f135914`; workflow wiring: `080a15f50557c70bc0d5e030a38f26ed27539870`. Stale-home-test update: `64670fbefbf88b66210190c936568111d9b4b25c`. Instrumentation workflow: `ede0225a127021945736839ccf0f5199abb0a2e5`.

## Latest verified CI result — 2026-10-25 (run #40178530256)

- **Result: SUCCESS.** [Workflow #40178530256](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178530256) passed on source/test commit `a5a56d8b569b03a385af2fe6c3b06b3abfc00068`.
- Unit tests passed, debug APK built, deterministic 8.02-second fixture generated, emulator screenshots captured, local draft auto-save verified, and `connectedDebugAndroidTest` exited with code 0. XML report: 3 tests, 0 failures, 0 errors, 0 skipped.
- Timeline test verified playhead drag, centered pinch zoom, and access to the first and last frame. The new trim interaction test completed both edge drags: right trim reduced project duration from 8,020 ms to 7,110 ms; left trim then reduced it from 7,110 ms to 6,210 ms.
- Pixel/Nexus Launcher ANR handling is now corrected in both dialog classification and the final persistent-dialog check (commits `b56fd2d26aabb26adb216bac8c9303ef0abc9b4d` and `a5a56d8b569b03a385af2fe6c3b06b3abfc00068`). Genuine MathMotor ANRs still fail the smoke test.
- This is still a **debug verification run**, not a signed release APK. Last test change added instrumentation; no production app-source feature fix or release was made in this run.
- Remaining release blockers: trim/scroll gesture conflicts and restoring trimmed footage; thumbnail quality/loading; text timing/size and preview/export parity; complete Undo/Redo; music/effect sync; multi-clip assembly/export; project save/reopen; responsive small-screen/keyboard layout; final screenshot review and physical-device smoke test.
- [Run artifacts and screenshots](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178530256#artifacts).
- Installed baseline remains versionCode 394 / build 61 (`app-v8.3-61`). Version 395 remains blocked until every remaining README release-gate item passes and the user explicitly authorizes release.

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
