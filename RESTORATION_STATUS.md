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
- Added trim-handle UI test commit `ab25bd5af389890ea1e32edeac627d59663ee355`: start a fresh fixture editor, scroll to the right edge, drag the right trim handle and then the left trim handle, and assert each drag shortens project duration. Validation is pending workflow [#40178081902](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40178081902); keep trim unchecked until that run passes.
- Updated old home-header assertion: `64670fbefbf88b66210190c936568111d9b4b25c`.
- Workflow now includes androidTest path filters and runs `connectedDebugAndroidTest`: `ede0225a127021945736839ccf0f5199abb0a2e5`.
- Connected test runs [#40169846327](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169846327), [#40172042627](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172042627), and [#40172447528](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172447528) showed instrumentation process failure because tests called `am force-stop` on the package hosting the instrumentation runner; this command was removed from both UI tests.
- [#40172843753](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40172843753) reached the pinch assertion and reported an unchanged UIAutomator visible width (118 px before/after). The clip had already auto-played to 8.02 s; that made the test measurement unreliable. Code now pauses imported clips, asserts the accessibility zoom percentage rather than clipped visible width, and routes each MotionEvent to the scale detector exactly once.
- Corrected the actual sequence-width bug: the ruler previously rounded 8 seconds to 10 seconds; ruler and empty text/music/sound placeholders now use the true assembled sequence duration.
- Historical note: workflow [#40173820023](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40173820023) did not verify pinch successfully. It is superseded by successful interaction workflow #40177103726 below.
- Timeline source now uses a fixed center playhead; scroll events map the centered time back to the project and debounce preview seeks. Playback and seeking move the timeline below the line. Each horizontal lane gets half-viewport leading/trailing padding so the beginning and end can align under the playhead. Pinch zoom math keeps the time under the pinch focus stable.
- Unit tests were added for center-playhead scroll mapping, pinch-anchor stability, and reaching the final frame at maximum scroll. Tests are not marked passed until CI reports success.
- Filmstrip density, timestamp sampling, decode/crop size and cache capacity were improved in source, but thumbnail quality/loading remains unverified on emulator/device.
- Home-screen header text and scissors graphic were reduced in source, but the full UI still needs screenshot review.

## Latest interaction failure and repair — 2026-10-25

- Latest failing candidate run at the time of diagnosis: [#40175226161](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40175226161).
- Unit tests and APK build passed. UI instrumentation successfully loaded the 8-second clip, dragged the playhead to 3.18 s, changed pinch zoom from 100% to 623% while preserving center time, and scrolled to 8.00 s.
- Historical test failure: reverse scrolling remained at 2.47 s after the test's four-swipe limit. The retry budget was later increased to 12 while preserving the same strict assertion; run #40177103726 reached 0.00 s on the sixth reverse swipe.
- Source fix applied in commit `68b593e6d15e1f773a4811c95dcf92a63dee7955`: the debounced timeline-scroll seek used to set `timelineScrollSeeking = false` before `seekTimelinePreview()`. Periodic player polling could then write a stale player position back into the timeline. The fix keeps sync suppression active during the seek and for a 450 ms settle window; a new scroll cancels/restarts that settle window.
- [x] Verified by run #40177103726: the interaction test passed the repeat-swipe path back to 0.00 s. The test-only correction allows up to 12 swipes and logs each attempt; acceptance bounds were not weakened.
- The timeline interaction gate passed, but this is still not a release candidate: other README verification items remain open. Version 395 must not be published.

- Second related defect found in `buildVideoClipFilmstrip()`: horizontal movement used to call `seekTimelinePreview()` with the child's local x fraction and ACTION_UP sought again after a drag, which could snap time away from the scrolled position. Source commit `1fe72bcb2d6257b28541902fd13bd51ec1a1a6d5` separates gestures: taps select/seek and drags scroll. This source behavior was verified by run #40177103726, which reached 8.00 s and returned to 0.00 s.
## Latest verified CI result — 2026-10-25 (run #40177103726)

- **Result: SUCCESS.** [Workflow #40177103726](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40177103726) passed on commit `832113681b1eb8e9ad6fcea14d99ffcb8790e87f`.
- Unit tests passed; debug APK built; sample fixture generation and emulator screenshot capture passed; `connectedDebugAndroidTest` exited with code 0. XML confirms 2 tests, 0 failures, 0 errors, 0 skipped.
- Verified in the deterministic 8.02 s single-clip scenario: playhead drag to 3.18 s; pinch 100% → 626% with center time preserved; timeline end reached at 8.00 s on forward swipe 4; timeline start reached at 0.00 s on reverse swipe 6.
- Earlier 2.47 s and 2.04 s results occurred after a hard limit of four reverse gestures. The test now allows 12 attempts and logs every position without relaxing the boundary rule (start ≤0.20 s, end ≥7.8 s). This confirmed the start boundary is reachable.
- The latest commit changes the test only; the editor source being tested already includes the filmstrip gesture separation and debounced-seek settle-window fixes. No signed/release APK was produced or published by this workflow.
- [Run artifacts, screenshots and logs](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40177103726#artifacts).
- Remaining release blockers: trim-handle reliability; thumbnail quality/loading; text timing and size including preview/export parity; complete Undo/Redo; audio sync; multi-clip assembly/export; project save/reopen; small-screen/keyboard layout; final screenshot review and physical-device smoke test.
- Installed baseline remains versionCode 394 / build 61 (`app-v8.3-61`). Do not prepare or publish version 395 until every README release-gate item passes and the user authorizes release.

## Reported problems — release blockers until verified
See the comprehensive checklist in README.md. Current list:
1. One-finger timeline scrolling and trim gestures may conflict or be unreliable.
2. Timeline horizontal travel/start/end alignment and two-finger pinch need interactive validation.
3. Filmstrip thumbnails are too small/sparse or initially black/repeated.
4. Playhead, timeline time, preview, playback and split position must stay synchronized.
5. Text timing handles and text size/preview/export behavior need verification and possibly further fixes.
6. Duplicate/confusing duration UI needs review against actual assembled sequence duration.
7. Undo/Redo must cover the full set of supported operations; known current history is clip-oriented only.
8. Home screen should be simplified and responsive to small screens and keyboard.
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
