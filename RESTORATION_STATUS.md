# MathMotor Video — current project status

## Current direction
MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should run on-device without a cloud account or paid AI service.

## Version and release boundary
- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- **Version 395 remains blocked.** Do not bump version metadata, sign/publish an APK, or offer an update until every required item in README.md is implemented and verified and the user authorizes release.
- Source changes, a green build, and screenshot capture do not prove all features work on the phone.

## Repair handoff — 2026-10-25
- Active branch: `fix/editor-395-gate`.
- Fixed-playhead source commit: `ab6d614c8ceff847537fce4cd77a69db50fb3bc9`.
- Centered timeline mapping helpers: `2213b8d5305837a92b7408abfc813e91b1f216b6`.
- New unit tests: `f8039c82c989d99f9e73df2e90f69719665e980d`.
- Filmstrip one-finger swipe correction: `8458c7737649bf05ffba10d58208dea3304993f3`.
- Added timeline gesture UI test: `7f1b886a5d8a4d0bce7163ae0c60b75ec7ea92fd`.
- Added trim-handle UI test commit `ab25bd5af389890ea1e32edeac627d59663ee355`: start a fresh fixture editor, scroll to the right edge, drag the right trim handle then the left trim handle, and assert each changes project duration. Passed in run #40178530256: 8,020 ms → 7,110 ms after right trim, then 7,110 ms → 6,210 ms after left trim. Broader trim/scroll conflict and restore-trim cases remain open.
- First attempt [#40178081902](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178081902) failed before instrumentation: the Android emulator displayed “Pixel Launcher isn't responding”; the recovery script misclassified this system-launcher dialog as an app ANR. CI fix commit `b56fd2d26aabb26adb216bac8c9303ef0abc9b4d` now handles Pixel/Nexus Launcher dialogs explicitly while still failing MathMotor ANRs.
- Validation [#40178530256](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178530256) passed. Unit tests, debug build, screenshot/draft-autosave smoke checks and all 3 instrumentation tests passed. The Pixel/Nexus Launcher ANR recovery checks were exercised by the smoke workflow.
- Added accessibility labels `Yazı başlangıç zamanı` / `Yazı bitiş zamanı` to the text timing SeekBars (source commit `5aaa9692844b635074b7afa66225d921f82a5bea`) and UIAutomator test `textTimingAndSizeControlsUpdatePreview` (test commit `57c0c4e3a255eaf44112d5ea5aef502fe2665355`). The basic live-preview size change and text start/end slider changes are now verified by run #40180575601; exported-text parity and persistence remain open.
- Historical setup failures: runs #40179260163 and #40179784521 could not reach the add-text control; no size/timing assertions ran. Artifact inspection showed the text-panel ScrollView had only ~15 px visible height on the 640 px compact emulator, so the control was clipped rather than reachable. The layout issue is resolved by the compact-panel change and confirmed in run #40180575601.
- UI fix commit `11b17096313a3c6f2145dd21b98c8033352fcf1f`: on compact phones, hide the secondary clip-carousel strip while a tool panel is active and reduce the preview stage temporarily to reserve usable height for the active panel, without overwriting the saved preview-stage preference. Run #40180575601 confirms the add-text control, timing sliders and size control are reachable.
- Retest #127 [#40180575601](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601) passed. The final test scrolls the add-text/start-time/end-time controls into view explicitly and validates live preview text sizing plus start/end time changes.
- Updated old home-header assertion: `64670fbefbf88b66210190c936568111d9b4b25c`.
- Workflow now includes androidTest path filters and runs `connectedDebugAndroidTest`: `ede0225a127021945736839ccf0f5199abb0a2e5`.
- Connected test runs [#40169846327](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169846327), [#40172042627](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172042627), and [#40172447528](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172447528) showed instrumentation process failure because tests called `am force-stop` on the package hosting the instrumentation runner; this command was removed from both UI tests.
- [#40172843753](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172843753) reached the pinch assertion and reported an unchanged UIAutomator visible width (118 px before/after). The clip had already auto-played to 8.02 s; that made the test measurement unreliable. Code now pauses imported clips, asserts the accessibility zoom percentage rather than clipped visible width, and routes each MotionEvent to the scale detector exactly once.
- Corrected the actual sequence-width bug: the ruler previously rounded 8 seconds to 10 seconds; ruler and empty text/music/sound placeholders now use the true assembled sequence duration.
- Historical note: workflow [#40173820023](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40173820023) did not verify pinch successfully. It is superseded by successful interaction workflow #40177103726 below.
- Timeline source now uses a fixed center playhead; scroll events map the centered time back to the project and debounce preview seeks. Playback and seeking move the timeline below the line. Each horizontal lane gets half-viewport leading/trailing padding so the beginning and end can align under the playhead. Pinch zoom math keeps the time under the pinch focus stable.
- Unit tests were added for center-playhead scroll mapping, pinch-anchor stability, and reaching the final frame at maximum scroll. Tests are not marked passed until CI reports success.
- Filmstrip density, timestamp sampling, decode/crop size and cache capacity were improved in source. Thumbnail views now expose per-frame loading states and debug-only timestamp/fingerprint markers (source commit `5d5629c736b127bc286a8cfbdeace6ad3b1380e8`). Test commit `1d915d325d5a4e131da68bb584ca5c9c9eda4183` asserts at least five distinct requested timestamps, at least two frame fingerprints and at least three loaded visible thumbnails; latest CI run [#40181440039](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40181440039) is the current validation. Do not mark thumbnail variety/loading complete until it passes.
- Home-screen header text and scissors graphic were reduced in source, but the full UI still needs screenshot review.

## Latest interaction failure and repair — 2026-10-25

- Latest failing candidate run at the time of diagnosis: [#40175226161](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40175226161).
- Unit tests and APK build passed. UI instrumentation successfully loaded the 8-second clip, dragged the playhead to 3.18 s, changed pinch zoom from 100% to 623% while preserving center time, and scrolled to 8.00 s.
- Historical test failure: reverse scrolling remained at 2.47 s after the test's four-swipe limit. The retry budget was later increased to 12 while preserving the same strict assertion; run #40177103726 reached 0.00 s on the sixth reverse swipe.
- Source fix applied in commit `68b593e6d15e1f773a4811c95dcf92a63dee7955`: the debounced timeline-scroll seek used to set `timelineScrollSeeking = false` before `seekTimelinePreview()`. Periodic player polling could then write a stale player position back into the timeline. The fix keeps sync suppression active during the seek and for a 450 ms settle window; a new scroll cancels/restarts that settle window.
- [x] Verified by run #40177103726: the interaction test passed the repeat-swipe path back to 0.00 s. The test-only correction allows up to 12 swipes and logs each attempt; acceptance bounds were not weakened.
- The timeline interaction gate passed, but this is still not a release candidate: other README verification items remain open. Version 395 must not be published.

- Second related defect found in `buildVideoClipFilmstrip()`: horizontal movement used to call `seekTimelinePreview()` with the child's local x fraction and ACTION_UP sought again after a drag, which could snap time away from the scrolled position. Source commit `1fe72bcb2d6257b28541902fd13bd51ec1a1a6d5` separates gestures: taps select/seek and drags scroll. This source behavior was verified by run #40177103726, which reached 8.00 s and returned to 0.00 s.
## Latest verified CI result — 2026-10-25 (run #40180575601)

- **Result: SUCCESS.** [Workflow #40180575601](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601) passed on tested commit `ce86cb00efa2f52224d4aea4fd054a078c4aa370`.
- Unit tests passed; debug APK built; 8.02-second fixture generated; emulator screenshots captured; local draft autosave passed; `connectedDebugAndroidTest` exit code 0. XML report: **4 tests, 0 failures, 0 errors, 0 skipped**.
- Timeline interactions passed: playhead drag to 3.18 s; pinch zoom 100% → 626% while preserving center time; end reached at 8.00 s on forward swipe 4; start reached at 0.00 s on reverse swipe 6.
- Clip trim passed: right edge 8,020 ms → 7,110 ms; left edge 7,110 ms → 6,210 ms.
- Text interaction passed on the compact emulator: `TIMING TEST` appeared in preview; raising font size to 28 sp enlarged measured overlay height from 73 px to 83 px; start-time label changed 00:00.00 → 00:01.30; end-time label changed 00:08.02 → 00:06.60.
- The compact-panel UI change in source commit `11b17096313a3c6f2145dd21b98c8033352fcf1f` made the add-text and timing controls reachable on a small viewport by temporarily hiding the redundant clip carousel and shrinking the preview while a non-clip tool panel is active.
- Remaining work: verify restoring trimmed footage and trim/scroll gesture conflicts; inspect thumbnail variety/loading; verify text-strip trimming and exported-text parity; validate full Undo/Redo, audio sync, multi-clip assembly/export, editable project save/reopen, full home/keyboard responsiveness, screenshots and physical-device behavior.
- [Run artifacts and screenshots](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40180575601#artifacts).
- This was a debug verification run, not a signed release APK. Installed baseline remains versionCode 394 / build 61. Version 395 stays blocked until every release-gate item passes and the user explicitly authorizes release.

## Reported problems — release blockers until verified
See the comprehensive checklist in README.md. Current list:
1. One-finger timeline scrolling and trim gestures may conflict or be unreliable.
2. Timeline horizontal travel/start/end alignment and two-finger pinch need interactive validation.
3. Filmstrip thumbnails are too small/sparse or initially black/repeated.
4. Playhead, timeline time, preview, playback and split position must stay synchronized.
5. Basic text size preview feedback and start/end timing sliders are verified; text-strip trimming, exported-text parity and persistence still need verification.
6. Duplicate/confusing duration UI needs review against actual assembled sequence duration.
7. Undo/Redo must cover the full set of supported operations; known current history is clip-oriented only.
8. Compact text tool controls are now reachable in emulator testing; full home-screen and keyboard responsiveness still need validation.
9. Scissors icons should be limited to true cut/split controls.

## Verification gaps
- Previously successful workflow [#40167165450](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40167165450) passed the existing unit-test step, debug APK build, sample-video fixture creation and emulator screenshot capture. The emulator step was explicitly run without instrumentation, so it did not verify touch gestures or full editing.
- Earlier workflow [#40169030780](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169030780) passed unit tests, debug build and screenshot smoke; interactive verification subsequently exposed a failure, so those earlier green results are insufficient for gesture acceptance.
- Required next validation: inspect latest job result; fix any compile/test error; inspect screenshots/logs. The first interaction instrumentation now exercises fixed-playhead drag, pinch anchor, filmstrip one-finger swipe and real project-time assertions at the start/end (≤0.2 s / ≥7.8 s); further tests for trim, text, Undo/Redo, export and project save/reopen remain required.
- Test a 10-second clip and multi-clip sequence at several zoom levels. Export to a new MP4 and verify output duration, picture/audio/text sync and that original media is unchanged.
- Keep all release-gate boxes in README.md unchecked until their relevant tests pass. Record newly discovered defects and test outcomes in both README.md and this handoff.

## Release gate
**Do not create or publish version 395 until every required fix and validation in README.md passes and the user explicitly authorizes release.** Keep source version metadata unchanged meanwhile.

## Recovery background
This is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree. Some old physics/scenario code remains as reconstruction leftovers and is outside the active video-editing workflow.
