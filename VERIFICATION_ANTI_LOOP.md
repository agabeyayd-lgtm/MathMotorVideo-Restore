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
- Latest completed verification: [#212 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40335645092), source fix commit `6e474c0c5f62a838329660353bc50f1ec9419969`; the workflow passed unit tests, debug APK build and the connected Android instrumentation suite. The docs-only branch head before the next test was `30be47782e6f0e4c27ab779e2650605aef1b6b08`.
- Do not repeat #210 text-strip drags or #212 timed-text export unchanged; both gates are green.
- The next distinct gap is audio control/history interaction. Added `sourceAudioToggleAndEffectVolumeUseUndoRedoHistory`: source-audio checkbox toggle plus actual effect-volume SeekBar drag with Undo/Redo. It is pending the next integrated CI run; until it passes, do not mark it verified.
- Audio export (source track mute/volume, music looping/volume, effect timing/volume, mix and A/V sync), split/reorder and gesture conflicts, preview behavior after seek/zoom, physical-device review, and signed update/install remain open.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action

Inspect the workflow triggered by the new test commit. Confirm its `head_sha` includes the test, then read the exact test report/logs. If it passes, record the observed assertions and move to genuine audio-export evidence. If it fails, classify the exact layer and fix only that root cause. Do not rerun #210/#212 unchanged, and never infer that an assertion passed if the test did not execute it.
