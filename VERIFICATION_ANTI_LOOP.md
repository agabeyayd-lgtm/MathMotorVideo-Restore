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
- Run #207: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40318772112
- #207 head SHA: `96e889be0cd923010b930530074225305ce695b0`.
- Last observed: unit tests, debug APK build, offline MP4 fixture preparation, and KVM setup passed; step 9 “Launch app, capture screens, and run interaction tests” was in progress. Inspect this run before any new run.
- #206 failed before instrumentation because `.github/scripts/capture-emulator-screenshots.sh` had a stray quote after the Python command-substitution closing `)`; fixed in `96e889b`.
- Text-strip trim remains UNVERIFIED until its start/end handle assertions execute. #202 was a UiSelector/BySelector compile mismatch; #203–#206 were emulator/system launcher ANR or preflight/script failures, not proof that trim interaction failed.
- If #207 fails, identify its exact failing step and diagnostics first. Do not repeat the same failed preflight; fix the cause and run one verification only.
- After trim passes, test distinct gates: text preview/export parity, source/music/effect audio export, remaining timeline edge cases, physical-device screen/keyboard/touch review, signed update and in-app installation.
- Previously proven; do not repeat without relevant changes: #172 (8/8 instrumentation, filmstrip timestamps); #179 (playhead restoration); #185 (9/9 instrumentation after Undo/Redo parent-attachment fix); #193 (manual JSON save/reopen and text/music-volume Undo/Redo); #196 (integrated candidate gates before the new trim-handle test).

## Next action
Inspect run #207 and its diagnostics. Do not start another run unless #207 is complete and its exact failure has been identified and fixed. Update README and this file with the observed outcome.
