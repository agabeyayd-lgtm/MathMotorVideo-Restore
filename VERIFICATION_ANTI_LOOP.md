# Verification anti-loop protocol

This is the concise handoff for ongoing CI debugging. Read it and the latest README entry before changing code or starting a run.

## Mandatory rules
- Continue from branch `fix/editor-395-gate`; do not restart from scratch.
- Do not queue a duplicate run while the latest relevant workflow is queued/in progress. Inspect that run first.
- Classify each failure: source/app defect, test/API compile defect, emulator/CI preflight defect, assertion failure, or artifact/release defect.
- Read the exact failing step and log/diagnostic artifact before editing. Make one root-cause change at a time and record its commit SHA.
- Do not rerun unchanged tests when failure occurs before the test under investigation. Fix the failing layer first.
- Confirm the workflow `head_sha` contains the fix; require the previously failing step to pass before calling the issue resolved.
- A green unit test/build or a booted app does not prove an interaction passed. The target assertion must execute and pass.
- If instrumentation did not start, do not call it an app defect or a passed test.
- Record run number, failing step, evidence, root cause, fix commit, unique verification, and result in README and this file.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app update without all release gates and explicit authorization.

## Latest known state (2026-10-26)

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`; branch: `fix/editor-395-gate`.
- Installed phone baseline: versionCode 394, build 61 (`app-v8.3-61`).
- [#212 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40335645092): timed-text export fix passed unit/build/instrumentation. Do not rerun unchanged.
- [#213 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40339578967), head SHA `278b5b03bb13f5ab71b218386ccdee9e4c6f068d`: unit tests, Debug APK build, fixtures and emulator preflight passed. 10/11 instrumentation tests passed. New `sourceAudioToggleAndEffectVolumeUseUndoRedoHistory` successfully exercised source-audio on/off history and effect-volume drag/Undo; failure occurred only at its final Redo assertion because the changed effect-volume label was searched immediately after switching tabs without scrolling it into view. The Undo assertion explicitly scrolled the panel and found 100%; no value check was logged after Redo.
- Applied a focused test-only correction: scroll the shared audio panel to `Efekt sesi: $changedValue%` before checking Redo. This keeps the expected value intact and adds a specific visibility assertion. It is not yet verified until CI completes.
- Do not change production Undo/Redo code unless the focused test demonstrates the changed value is actually wrong after proper navigation.
- Remaining gates: exported audio behavior (source track mute/volume, music looping/volume, timed effect and A/V sync), split/reorder/trim-scroll conflicts, text preview after seek/zoom, physical-device review, and signed update/install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action

Inspect the workflow triggered by the focused #213 test-navigation correction. Confirm the run's `head_sha` includes the correction and check the exact test report. Do not repeat #210 text-strip drags or #212 timed-text export unchanged. If the Redo value still fails after the scroll assertion passes, inspect the actual visible label and history status before changing production code.
