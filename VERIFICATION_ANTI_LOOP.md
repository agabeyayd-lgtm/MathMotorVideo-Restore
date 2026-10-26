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
- Latest completed run: [#210 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40328122889), head SHA `e3388c48ea927dfe79845ddd58f85d6eedd7af4d`; all 9 Android instrumentation tests passed (0 failures).
- #207 unit tests, debug APK build, deterministic MP4 fixtures, KVM setup, emulator launch and screenshot preflight passed. Instrumentation ran 9 tests: 8 passed, 1 failed.
- Exact failure at `EditorTimelineInteractionTest.kt:578`: `UiScrollable.scrollIntoView` could not bring the text-strip end handle into view inside `Zaman çizelgesi satırı: YAZI 1`. This happened before the end-handle drag and its label-change assertion. Do not classify it as a confirmed app drag defect or a passed drag test.
- Focused correction commit [`f5227ff`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/f5227ffb38c41d3935b65f6278842f24fa354078) replaced child `scrollIntoView` with `scrollToEnd(5)` on the selected timeline lane, then directly waited for the end handle.
- [#208 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40322912271): the revised scroll still did not expose the handle to UiAutomator; assertion failed at `EditorTimelineInteractionTest.kt:581` before drag/label-change. Unit/build/preflight passed, 8/9 instrumentation tests passed.
- #209 FAILED before end-handle drag because the value label was not exposed in the accessibility tree. Follow-up `e3388c4` brought the label into view.
- #210 SUCCESS: the uploaded XML test report contains `tests="9" failures="0"`. The interaction log confirms start time changed `00:01.30 → 00:03.35` and end time changed `00:06.50 → 00:04.46` after dragging the actual strip handles. The text-strip drag gate is closed; do not repeat without a relevant source change.
- #206's separate shell syntax error was fixed in `96e889b`; the latest #207 got beyond that preflight. Do not repeat the old preflight failure.
- Previously proven; do not repeat without relevant changes: #172 (8/8 instrumentation, filmstrip timestamps); #179 (playhead restoration); #185 (9/9 instrumentation after Undo/Redo parent-attachment fix); #193 (manual JSON save/reopen and text/music-volume Undo/Redo); #196 (integrated candidate gates before the new text-trim-handle test).
- Next distinct gate: timed/styled text export parity. Seed the deterministic 8-second fixture with a text layer of known start/end, position, size and colour; export a new MP4; use decoded frames before/during/after the interval to verify text visibility and styling/placement. Existing two-clip export test only proves clip order and unchanged inputs, not text parity.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all release gates and explicit authorization.

## Next action
Implement and run one focused timed/styled text export parity test. #210 proves text-strip start/end handles change their displayed timestamps; do not rerun that gate unless related code changes. Keep versionCode 395 blocked.
