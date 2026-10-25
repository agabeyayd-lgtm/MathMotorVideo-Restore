# MathMotor Video

## Live handoff checkpoint — 2026-10-25

This checkpoint supersedes older statements below that still describe run #139 as pending. Continue from the existing repair work; do not restart completed tasks.

- Active repair line: `fix/editor-395-gate`. User-confirmed installed baseline remains versionCode 394 / build 61 (`app-v8.3-61`). Version 395 is still blocked.
- Latest full-suite run checked: [#40194075276](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40194075276) failed at the emulator instrumentation assertion **“Text editor input did not remain accessible after keyboard resize.”** Unit tests, debug APK build, and fixture generation passed. Keyboard behavior is NOT verified fixed; continue from this failure.
- Multi-clip test work exists on branch `work/multiclip-export-verification`, source/test commit [`d030c3788a649cc1ca67f6a9f893234d2206ec0e`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/d030c3788a649cc1ca67f6a9f893234d2206ec0e). It adds a two-clip MP4 export UI test, ordered-frame check, combined-duration check, and source-file integrity checks. The branch was initially excluded from push-triggered CI; I added it to the workflow branch filter in commit [`11ce461b8c533e82772e160aad27cce8ae847477`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/11ce461b8c533e82772e160aad27cce8ae847477). Confirm the actual Actions run and test result before marking multi-clip export verified.
- Latest multi-clip diagnosis: run [#40196560235](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40196560235) failed before export because the shell launch argument retained literal double quotes around the two file URIs; `MediaMetadataRetriever` could not open either input. This is a test-invocation failure, not evidence that the MP4 export engine failed.
- The first single-quote workaround in commits [`139fa16382b419eeacd486d2b1060ea40c20c9af`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/139fa16382b419eeacd486d2b1060ea40c20c9af) and [`112b9ff46a3858563c492f0025581bba3fa47131`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/112b9ff46a3858563c492f0025581bba3fa47131) was disproven: run #145 showed the single quotes also remained in the URI values.
- More robust correction: pass the two test URIs as separate unquoted extras (`video_uri_1`, `video_uri_2`) and reconstruct the clip list in the debug-only test bridge. Active branch commits [`295de6dda286fe7c6b935c149d37056554c59e5d`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/295de6dda286fe7c6b935c149d37056554c59e5d) and [`bbdbc5de254489259e04d6420c4609a1e8586e92`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/bbdbc5de254489259e04d6420c4609a1e8586e92) removed quote-based URI passing. Run [#148](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40197763281) reached the export panel, but could not locate the off-screen MP4 button; export assertions never ran.
- The current test now scrolls the vertical export panel to reveal `MP4 DIŞA AKTAR`, commit [`90c55ca3f94bfa10bc9c1071f0b0879c179136f3`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/90c55ca3f94bfa10bc9c1071f0b0879c179136f3). Retest [#150](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40198463805) is in progress.
- Next gate: verify import (`count=2 skipped=0`), then confirm one MP4 around 11 s, green second-clip frame at 9.5 s, and unchanged source hashes. Version 395 remains blocked.
- No signed release APK was produced by run #40194075276. Do not change version metadata, publish, or offer version 395 until release-gate tests pass and the user explicitly authorizes release.


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
- [x] Added accessible thumbnail states (`yüklüyor`, `yüklendi`, `yüklenemedi`) and debug-only per-frame fingerprints so CI can distinguish real frame diversity from repeated first-frame placeholders. Source commit `5d5629c736b127bc286a8cfbdeace6ad3b1380e8`.
- [x] **Thumbnail frame variety/loading verified in CI:** run [#40181440039](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40181440039) passed with test commit `1d915d325d5a4e131da68bb584ca5c9c9eda4183`. The UI test requires at least five distinct requested timestamps, at least two bitmap fingerprints, and at least three visible loaded thumbnails. This verifies frame extraction/loading in the emulator; thumbnail size/density and physical-phone readability still need visual review.
- [x] Replaced the home-screen scissors graphic with a play icon and removed redundant explanatory header copy.
- [x] Added two-finger zoom logic in source, plus unit-testable centered-scroll/anchor mapping helpers.
- [x] Implemented a stationary center playhead in source: every timeline lane receives symmetric half-viewport padding, scrolling maps the center position to project time, playback/seek moves the timeline under the playhead, and dragging the playhead scrolls the timeline.
- [x] Added unit tests for center-playhead mapping, keeping the zoom anchor under the same finger position, and reaching the exact timeline end with symmetric padding.
- [x] Updated CI path filters so changes in `app/src/test/**` and `app/src/androidTest/**` run the workflow.
- [x] Removed an early `requestDisallowInterceptTouchEvent(true)` in the video filmstrip's down event so ordinary one-finger horizontal swipes can reach the parent timeline scroller; long-press menu continues to own the gesture when activated.
- [x] Added a UIAutomator interaction test that starts the deterministic 8-second sample video, drags the fixed playhead, pinches to zoom, and repeatedly scrolls until it asserts the actual final frame (≥7.8 s) and start frame (≤0.2 s).
- [x] **Trim restoration verified in UI test #131:** both outward drags restored the original project duration of 8,020 ms. Log values: right restoration 7,110 → 8,020 ms; left inward trim 8,020 → 7,110 ms; left restoration 7,110 → 8,020 ms. Full-suite runs #133 and #137 still failed at compact-screen text input after keyboard resize. Source commit `0d73bf0545d1d8ab894c832861f0d9a94ae4ab53` moved the text editor field above the layer carousel. Follow-up source commit `cc56bde8181d65b6ef77c2229e70f5e62afe475f` adds repeated visibility checks after keyboard/inset layout changes rather than relying on one early correction. Workflow #138 was cancelled by the follow-up fix; run [#40192990315](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40192990315) (#139) is the current full-suite validation. Keep keyboard responsiveness unchecked until it passes.
- [x] Added a separate UIAutomator trim test for both the left and right filmstrip handles; it asserts that each drag reduces assembled project duration by at least 150 ms while duration remains valid. Test commit `ab25bd5af389890ea1e32edeac627d59663ee355`.
- [!] **First trim-test run was blocked by emulator setup, not the trim assertion:** [run #40178081902](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178081902) detected an Android system dialog, “Pixel Launcher isn't responding,” and stopped before instrumentation. The CI recovery script now recognizes Pixel/Nexus Launcher ANR dialogs as emulator/system noise while continuing to fail genuine MathMotor ANRs (commit `b56fd2d26aabb26adb216bac8c9303ef0abc9b4d`).
- [x] **Basic left/right trim drags verified:** workflow [#40178530256](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178530256) passed. The right handle changed total duration 8,020 ms → 7,110 ms; the left handle then changed 7,110 ms → 6,210 ms. Both bounds remained valid. The CI script also recovered the Pixel/Nexus Launcher setup issue and the editor screenshot/autosave smoke checks passed.
- [x] **Trim restoration verified in the individual UI test** in run [#40186006433](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40186006433): both trim handles shortened the clip and then restored source footage, returning duration to 8,020 ms. The full suite did not pass because a separate compact-keyboard text-input assertion failed. [ ] Still open: trim/scroll gesture conflicts, several zoom levels and multi-clip boundaries.
- [x] Added accessible text start/end timing sliders and verified live text overlay, size changes and both timing sliders in emulator run #40180575601. Source: labels `5aaa9692844b635074b7afa66225d921f82a5bea`; compact-panel layout fix `11b17096313a3c6f2145dd21b98c8033352fcf1f`; final test commit `ce86cb00efa2f52224d4aea4fd054a078c4aa370`.
- [x] **Compact text-panel reachability fix verified:** attempts #122/#124 found that the text panel had only about 15 px of visible height on the compact emulator. Source commit `11b17096313a3c6f2145dd21b98c8033352fcf1f` hides the redundant clip carousel and temporarily reduces the preview stage while compact tool panels are active. The final test explicitly scrolls the add-text, start-time and end-time controls into view. Run #40180575601 passed all checks.
- [x] **Basic text timing and live preview size verified:** run [#40180575601](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601) passed. `TIMING TEST` appeared in preview; raising font size to 28 sp enlarged the overlay bounds; start changed 00:00.00 → 00:01.30 and end changed 00:08.02 → 00:06.60. Export parity, text styling after save/reopen, and trimming text strips remain open.
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

- [ ] **Still not verified interactively:** trim/scroll conflict edge cases, thumbnail size/density on a physical phone, text/export parity after saving and reopening, complete Undo/Redo, audio behavior, multi-clip assembly/export, editable project reopen, full home-screen/keyboard responsiveness and physical-device behavior. Basic text timing/preview sizing, inward/outward left/right trim, and emulator thumbnail variety/loading are verified in runs #40180575601, #40178530256, #40186006433, and #40181440039.
- [ ] Text-strip trim handles and unified Undo/Redo history still need work; known history records clip edits only. The text start/end time sliders themselves now pass an emulator interaction test.
- [ ] Finish home-screen refinement after reviewing screenshots; scan every screen for redundant scissors icons.
- [ ] Extend interaction tests to cover trim handles, split at the playhead, text timing/size, Undo/Redo, audio, MP4 export and save/reopen.
- [ ] Do not bump versionCode/versionName or prepare a release APK until every checklist item passes and the user authorizes the release.

Fixed-playhead source change: `ab6d614c8ceff847537fce4cd77a69db50fb3bc9`. Timeline math helpers: `2213b8d5305837a92b7408abfc813e91b1f216b6`. Mapping tests: `f8039c82c989d99f9e73df2e90f69719665e980d`. Filmstrip swipe fix: `8458c7737649bf05ffba10d58208dea3304993f3`. Interaction test: `7f1b886a5d8a4d0bce7163ae0c60b75ec7ea92fd`; exact start/end assertions: `7dd20078dd35a0f68b70075ff5bb8e20769b712b`. Pinch dispatch first attempt: `af579dd0a156bbad0ee4f93a734a51d2caf65e3d`; dispatch-once/import-paused source change: `f2c3c520df6f82991bfb5540d911a720cf907143`. Updated zoom percentage test: `d60b2d7528ed5fe40183962a5ee4b93215767cf7`. Removed force-stop from screenshot test: `57e1a4c924d4afbbc9dbd88de0e4bdfbc333fa6d`; instrumentation test process fix: `1dd19108139c8b353d1c1cc3606b749b33d8228c`. Exact ruler duration fix: `f742117b01cd9e63a5b27cbacdc4d1d67a19ac68`; sound placeholder duration fix: `546d750957923b24b2376b4b29d413b637319256`. Diagnostic script: `4de40f3ad49d8b49d5f7ff3d5fcf439d6f135914`; workflow wiring: `080a15f50557c70bc0d5e030a38f26ed27539870`. Stale-home-test update: `64670fbefbf88b66210190c936568111d9b4b25c`. Instrumentation workflow: `ede0225a127021945736839ccf0f5199abb0a2e5`.

## Latest verified CI result — 2026-10-25 (run #40181440039, run 129)

- **Result: SUCCESS.** [Workflow #40181440039](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40181440039) passed with the thumbnail-interaction test on commit `1d915d325d5a4e131da68bb584ca5c9c9eda4183`.
- The uploaded JUnit report confirms **5 UI/instrumentation tests, 0 failures, 0 errors, 0 skipped**. Unit tests and debug APK build passed; screenshots and diagnostics were uploaded.
- **Filmstrip frames:** the test log measured **12 distinct requested timestamps and 11 distinct bitmap fingerprints**, and it asserted at least three loaded thumbnails were visible. This verifies real frame variety/loading on the CI emulator; it does not certify the filmstrip's visual size/readability on a physical phone.
- The compact-screen text test remains green in run #40180575601: live text in preview, text-size bounds changed, and start/end timing labels updated. Fixed-playhead/zoom/start-end scrolling passed in #40177103726; left/right inward trim passed in #40178530256.
- **Still open:** review thumbnail density on a physical phone; text-strip trimming/export parity; full Undo/Redo; audio/music sync; multi-clip assembly/export; editable project save/reopen; home/keyboard responsiveness and physical-device review. Trim restoration was verified at the individual-test level; the full suite remains blocked on the compact keyboard/text-input accessibility retest (#135).
- [Run artifacts, test report and screenshots](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40181440039#artifacts).
- This was a **debug APK** validation run, not a signed release APK. Installed baseline remains versionCode 394 / build 61. Version 395 remains blocked until the full checklist passes and the user explicitly authorizes release.

## Previous verified text/timeline CI result — 2026-10-25 (run #40180575601)

- **Result: SUCCESS.** [Workflow #40180575601](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601) completed successfully on commit `ce86cb00efa2f52224d4aea4fd054a078c4aa370`.
- Unit tests passed, debug APK built, the 8.02-second test fixture generated, emulator screenshots captured, local draft autosave verified, and `connectedDebugAndroidTest` exited with code 0. XML report: **4 tests, 0 failures, 0 errors, 0 skipped**.
- Timeline interactions passed: playhead drag to 3.18 s, pinch 100% → 626% while preserving center time, end reached at 8.00 s on forward swipe 4, start returned to 0.00 s on reverse swipe 6.
- Clip trim passed: right edge reduced duration 8,020 ms → 7,110 ms; left edge then reduced 7,110 ms → 6,210 ms.
- Text interaction passed on the compact emulator: `TIMING TEST` appeared in preview; increasing text size to 28 sp enlarged its measured bounds (height 73 → 83 px); start-time label changed 00:00.00 → 00:01.30; end-time label changed 00:08.02 → 00:06.60. To make controls reachable on small screens, the source hides the secondary clip carousel and temporarily shrinks the preview while a non-clip tool panel is active (source commit `11b17096313a3c6f2145dd21b98c8033352fcf1f`).
- Still open: verify trim/scroll gesture conflicts, review thumbnail size/density on a physical phone, validate text-strip trimming and exported text parity, unified Undo/Redo, audio/music sync, multi-clip export, project save/reopen, compact keyboard/text-input responsiveness in full suite #139, and final physical-device review.
- [Run artifacts and screenshots](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601#artifacts).
- This is a debug validation run, not a signed release APK. Installed baseline remains versionCode 394 / build 61 (`app-v8.3-61`). Version 395 remains blocked until the full release checklist passes and the user explicitly authorizes release.

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

- [ ] Review and tune thumbnail height/visibility and spacing on a physical phone; the CI verifies distinct frame extraction but does not certify that the filmstrip looks dense and readable on the user's device.
- [x] Emulator test confirms thumbnail frames are requested from distinct timestamps and at least two different frame fingerprints load (run #40181440039). Physical-device visual density/readability is still pending.
- [ ] Reduce black placeholders and loading delay; show a useful loading state and replace placeholders promptly as frames become available.
- [ ] Cache/reuse extracted frames and avoid blocking timeline scrolling or editing while thumbnails are prepared.

### Text, audio and undo/redo

- [ ] Make text start/end time editable and ensure the text timeline strip can be trimmed at both ends.
- [x] Make text size adjustable with immediate preview feedback (verified on emulator run #40180575601).
- [ ] Verify exported text output matches the live preview.
- [ ] Verify text timing, placement, size and style remain correct after seeking, zooming, saving/reopening and exporting.
- [ ] Verify Undo/Redo works for all supported edit operations, including text add/delete/timing/size, audio/music changes, clip trim/split/reorder/move; do not show a control for an operation that is not actually undoable.
- [ ] Ensure undo/redo restores both timeline state and preview consistently.

### UI and controls

- [ ] Simplify the home screen: remove redundant menus and explanatory clutter; prioritize choosing/opening a video and entering the editor.
- [ ] Make the home screen and editor responsive to small screens, available height and the software keyboard; controls must remain reachable and not overlap.
- [ ] Remove redundant scissors icons. Reserve scissors for cut/split actions; make trim, split, delete and other actions visually distinct.
- [ ] Keep controls concise, consistently placed, accessible and functional rather than duplicating the same action in multiple places.

### Required validation before version 395

- [x] CI unit tests pass for timeline mapping, zoom-anchor behavior, trim bounds, playhead-to-clip mapping and text timing (run #40180575601).
- [x] Emulator-tested: fixed-playhead scrubbing, centered pinch zoom, exact start/end scrolling, basic left/right trim, compact-screen text timing/size, and thumbnail frame variety/loading (runs #40177103726, #40178530256, #40180575601, #40181440039).
- [ ] Still required: outward trim restoration/scroll conflict, undo/redo coverage, audio, multi-clip assembly/export, project save/reopen, home-screen/keyboard navigation, physical-device review, and exported text parity.
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


## Latest validation update — 2026-10-25 (run #140)

- Run [#40194075276](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40194075276) passed unit tests, debug APK assembly and fixture generation, but the emulator text-entry UI test failed again at the accessibility check for the text input after the keyboard opened. The log showed the active tool ScrollView had a measured height of **0 px** while the keyboard was open (`panel=312x0`), so this is a real compact-layout space-allocation problem, not merely an unstable selector.
- The earlier input-only panel change in source commit `17d8cb1c1afd1c4497b8b661136b6a3118229de3` was not enough; the field still did not become accessible in run #140. A targeted follow-up now hides the compact phone's timeline card while the keyboard is open, freeing height for the text field and restoring the timeline after the keyboard closes. This follow-up is **not yet verified by CI**.
- A separate working branch `work/multiclip-export-verification` adds a deterministic green second-video fixture and a UI test that imports two different videos, exports them to a newly named MP4, checks approximately 11 seconds of duration, checks a green frame at 9.5 seconds to prove the second clip follows the first, and verifies both input files remain unchanged. This test is being prepared and has **not yet passed CI**.
- [ ] Compact keyboard/text input remains a release blocker until a full emulator suite passes.
- [ ] Two-clip single-MP4 export remains unverified until the new instrumentation test passes.
- [ ] Saved-project editing/resume, text/audio layer export parity, undo/redo, signed release/update, and physical-phone review remain release blockers.
- **Version 395 remains blocked. Do not bump version metadata, sign/publish an APK or offer an update.**

## Latest validation update — 2026-10-25 (run #141)

- Run [#40194882474](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40194882474): unit tests and debug APK assembly passed. Five of six emulator interaction/screenshot tests passed, including text timing/size input after keyboard resize and trim-handle restoration. This confirms the compact keyboard regression is fixed in this emulator run.
- New multi-clip export test stopped during import, before export: the two video URI extras reached the test entry and the editor (videoCount=2), but `EDITOR_SMOKE_CLIPS_ADDED` was never emitted. Both fixture files were copied into app-private storage. This is not yet proof the feature failed at export; more detailed logging was added around saved-draft selection, per-file metadata reads, and empty import results. Export sequence/duration/second-frame/original-hash assertions have not run yet.
- Multi-clip test now uses a fresh draft UUID per test to avoid accidental draft reuse. Re-run is required to determine why the ordered import did not complete.
- [x] Compact keyboard/text input check passed in run #141.
- [x] Left/right trim and trim restoration passed in run #141.
- [ ] Two-clip import and single-MP4 export remain unverified.
- [ ] Project reopen, text/audio export parity, undo/redo, physical-phone usability, signed release/update remain open.
- **Version 395 remains blocked. Do not bump version metadata, sign/publish an APK, or offer an update before remaining checks pass and the user explicitly authorizes release.**
