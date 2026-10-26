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
- Latest completed run before this correction: [#207](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40318772112), head SHA `96e889be0cd923010b930530074225305ce695b0`.
- #207 unit tests, debug APK build, deterministic MP4 fixtures, KVM setup, emulator launch and screenshot preflight passed. Instrumentation ran 9 tests: 8 passed, 1 failed.
- Exact failure at `EditorTimelineInteractionTest.kt:578`: `UiScrollable.scrollIntoView` could not bring the text-strip end handle into view inside `Zaman çizelgesi satırı: YAZI 1`. This happened before the end-handle drag and its label-change assertion. Do not classify it as a confirmed app drag defect or a passed drag test.
- Focused correction commit [`f5227ff`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/f5227ffb38c41d3935b65f6278842f24fa354078) replaced child `scrollIntoView` with `scrollToEnd(5)` on the selected timeline lane, then directly waited for the end handle.
- [#208 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40322912271): the revised scroll still did not expose the handle to UiAutomator; assertion failed at `EditorTimelineInteractionTest.kt:581` before drag/label-change. Unit/build/preflight passed, 8/9 instrumentation tests passed.
- #209 FAILED: 8/9 instrumentation tests passed. The VIDEO-lane correction found the end handle on the first bounded swipe, but the test then failed to find the `Yazı bitişi:` value label at line 601. It never performed the end-handle drag, so the production interaction remains unverified.
- Test-only follow-up `e3388c4` explicitly scrolls the text panel's end-time label into view before reading it and again after the drag before comparing values. Check #210 before further edits; do not repeat the same unmodified approach or claim trim success prematurely.
- #206's separate shell syntax error was fixed in `96e889b`; the latest #207 got beyond that preflight. Do not repeat the old preflight failure.
- Previously proven; do not repeat without relevant changes: #172 (8/8 instrumentation, filmstrip timestamps); #179 (playhead restoration); #185 (9/9 instrumentation after Undo/Redo parent-attachment fix); #193 (manual JSON save/reopen and text/music-volume Undo/Redo); #196 (integrated candidate gates before the new text-trim-handle test).
- After the text trim passes, test distinct gates: text preview/export parity, source/music/effect audio export, remaining timeline edge cases, physical-device screen/keyboard/touch review, then signed update/in-app install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all release gates and explicit authorization.

## Next action
Inspect the uploaded [run #208 artifact](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40322912271) and timeline viewport/scroll geometry. Then make one evidence-based correction and run one focused verification. The end-handle drag remains unverified; do not start a duplicate workflow or repeat the same scroll method unchanged.
