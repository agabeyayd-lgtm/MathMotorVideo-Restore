# MathMotor Video — current project status

## Current direction
MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should run on-device without a cloud account or paid AI service.

## Version and release boundary
- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- **Version 395 remains blocked.** Do not bump version metadata, sign/publish an APK, or offer an update until every required item in README.md is implemented and verified and the user authorizes release.
- Source changes, a green build, and screenshot capture do not prove all features work on the phone.

## Repair handoff — 2026-10-25
- Active branch: `fix/editor-395-gate`.
- Latest relevant source commit: `ab6d614c8ceff847537fce4cd77a69db50fb3bc9`.
- Centered timeline math/helpers: `2213b8d5305837a92b7408abfc813e91b1f216b6`.
- New unit tests: `f8039c82c989d99f9e73df2e90f69719665e980d`.
- CI test path update: `4d31899d4a838dd135c3d69850c98a3be55f8452`.
- Latest workflow at last check: [#40169030780](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169030780), queued; check the result before declaring the latest changes successful.
- Timeline source now uses a fixed center playhead; scroll events map the centered time back to the project and debounce preview seeks. Playback and seeking move the timeline below the line. Each horizontal lane gets half-viewport leading/trailing padding so the beginning and end can align under the playhead. Pinch zoom math keeps the time under the pinch focus stable.
- Unit tests were added for center-playhead scroll mapping, pinch-anchor stability, and reaching the final frame at maximum scroll. Tests are not marked passed until CI reports success.
- Filmstrip density, timestamp sampling, decode/crop size and cache capacity were improved in source, but thumbnail quality/loading remains unverified on emulator/device.
- Home-screen header text and scissors graphic were reduced in source, but the full UI still needs screenshot review.

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
- The current workflow [#40169030780](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40169030780) was queued when last checked after centered-playhead changes and new mapping tests.
- Required next validation: inspect the latest job result; fix any compile/test error; inspect screenshots/logs; then add and run instrumentation that exercises scrolling, pinch zoom, text timing/size, Undo/Redo, MP4 export and project save/reopen.
- Test a 10-second clip and multi-clip sequence at several zoom levels. Export to a new MP4 and verify output duration, picture/audio/text sync and that original media is unchanged.
- Keep all release-gate boxes in README.md unchecked until their relevant tests pass. Record newly discovered defects and test outcomes in both README.md and this handoff.

## Release gate
**Do not create or publish version 395 until every required fix and validation in README.md passes and the user explicitly authorizes release.** Keep source version metadata unchanged meanwhile.

## Recovery background
This is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree. Some old physics/scenario code remains as reconstruction leftovers and is outside the active video-editing workflow.
