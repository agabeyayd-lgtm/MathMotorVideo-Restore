# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export run on-device. Source videos must remain unchanged; finished edits are exported as new MP4 files.

## Current handoff — 2026-10-26

**Continue from this checkpoint. Do not restart completed work or repeat passing tests unless a related source change requires it. Keep the current release gate and record the exact failing assertion before changing code.**

- Active branch: `fix/editor-395-gate`; draft repair PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8).
- User-installed baseline: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- Source-branch verification: [CI run #193 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553), source commit [`941d5fd`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/941d5fd356c54c3278060a91a23a5e42d81340f4). Unit tests, debug APK, emulator launch, screenshots and editor interaction tests passed, including manual JSON save/reopen after scrolling the audio controls into view.
- Integration commit [`cb2d153`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/cb2d1538fba6d04eca397f7b0df153a4276a6dd1) combined unified editor history with the release-gate branch's playhead-restore guard. Its first CI run [#194 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40277453646) at Android-test compilation: the manual JSON test contained duplicate `restoredPlayheadMs` declarations (around lines 1119 and 1128). The failure happened before emulator interaction tests; do not count it as an editor behavior failure.
- Undo/Redo source test `unifiedUndoRedoRestoresTextAndAudioEdits` was green in source-branch validation. Remaining explicit coverage: trim/split/reorder, sound-effect mutations, source-audio toggle, preview/export parity and history state across save/reopen.
- **Release 395 remains blocked.** Do not change version metadata, sign/publish APK, or enable in-app updates before the integrated candidate passes the required gates and release is explicitly authorized.

## Verified — do not repeat without a relevant code change

- [x] Fixed-center timeline playhead interaction, pinch-zoom anchor stability, and timeline scrolling to the beginning/end.
- [x] Saved playhead restoration survives ExoPlayer preparation/`STATE_READY`; fix verified on the release-gate branch in [run #179](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40225562573). The integrated candidate retains this guard; #195 is checking the combined branch.
- [x] Left/right clip trim and restoration of the original source duration.
- [x] Filmstrip thumbnails load frames from distinct timestamps.
- [x] Text-layer creation, live preview, text-size changes, and text start/end timing controls.
- [x] Keyboard/layout-resize accessibility for the text input.
- [x] Two source clips export sequentially to a new MP4: approximately **11,029 ms** total, second clip's green frame visible at 9.5 seconds, both source fixture hashes unchanged.
- [x] Home/editor/text-panel screenshot smoke test passed. This is emulator evidence, not physical-phone usability sign-off.
- [x] Removed the old home-screen items for scenario/world/10-second video creation; home shows gallery import, empty project, drafts, and recent exports.
- [x] **Local saved-draft round trip passes in run #161:** restores the trimmed clip duration (**7.02 s**), text overlay and editable content (`ROUND TRIP TEXT`), and text size (**32 sp**). The test now checks the editable field before scrolling down to the size controls.
- [x] Manual JSON Save As → close editor → Open Document round trip passed in [run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553) on the source branch; expanded assertions cover clip order/trim, playhead/zoom/scroll, text style and audio/effect state. The integrated candidate must re-run this gate before release.
- [x] Distinct filmstrip frames passed in [run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375) after the decoder switched from `OPTION_CLOSEST_SYNC` to `OPTION_CLOSEST`. The full run passed unit tests, debug build, **8/8 Android instrumentation tests**, screenshot capture and artifact upload.

Useful latest evidence:
- [CI run #161 — all checks green](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107)
- [Run #161 emulator screenshots/diagnostics artifact](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107#artifacts)

## Remaining work — canonical checklist

Work top to bottom. Mark an item complete only when there is test or inspection evidence. Do not go back to the completed list above unless a related change breaks it.

### 1. Broader manual-project persistence

- [x] Source-branch run [#193 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553) passed the expanded manual JSON save → close → reopen test, including clip order/trim, playhead/zoom/scroll, text styling and audio/effect restoration checks.
- [x] Integrated candidate gate passed in [run #196 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719): unit tests, debug APK build, emulator launch, screenshots and interaction tests all succeeded on commit `fc76fb81c199f1df074819c54cd19ae70a9ea462`.

### 2. Unified editor Undo / Redo

- [x] Shared history implementation covers editor snapshots for clips, text, music/effect references and levels, effect timing, source-audio settings and timeline state.
- [x] `unifiedUndoRedoRestoresTextAndAudioEdits` passed on the source branch in run #192; run #193 then passed the complete source-branch workflow.
- [x] Integrated editor gate passed in [run #196 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719), including the existing unified Undo/Redo interaction and manual JSON save/reopen assertions.
- [ ] Add distinct coverage for trim/split/reorder, sound-effect add/remove/start/volume, source-audio toggle, preview/export parity and undo-history state after save/reopen.
- [x] Global Undo/Redo controls remain visible outside the tool panel and have visible hit targets; this assertion passed in `unifiedUndoRedoRestoresTextAndAudioEdits` in source-branch [run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553). The integrated candidate recheck is verified in integrated-candidate [run #196](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719).
- [ ] Verify one-history-entry-per-gesture/grouping for different edit types (especially repeated slider changes); the current text-step and music-volume Undo/Redo test does not cover all gesture types.

### 3. Text timeline and export parity

- [ ] Test dragging the start/end handles on a text timeline strip; passing the timing sliders does not prove strip dragging works.
- [ ] Export a clip with timed/styled text and compare the MP4 with preview for timing, position, size, colour and style.
- [x] Manual JSON Save As → close → Open Document restores tested text state: content, 7.00–9.00 s timing, top position, 32 sp size, cyan colour and non-bold style. Assertions passed on the source branch in [run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553); the integrated persistence rerun passed in [run #196](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719).
- [ ] Verify text settings/preview remain correct after timeline seek and zoom. This behavior is distinct from persistence and is not yet proven by the save/reopen test.

### 4. Audio export

- [ ] Validate source-video audio level/mute, background music looping/volume, timed sound-effect start/volume, and audio/video sync in the exported MP4. Controls and export code exist, but end-to-end proof is not complete.

### 5. Timeline edge cases

- [ ] Test split-at-playhead and confirm the split boundary in export matches the visible playhead.
- [ ] Test trim-versus-scroll gesture conflicts and multi-clip trim/reorder/seek at multiple zoom levels. Do not repeat the already-passing single-clip trim/zoom or two-clip export tests unless a relevant change warrants it.

### 6. Physical-device and release validation

- [ ] Review thumbnail density, tap targets, timeline lanes, text panel/keyboard behavior and home/editor layout on the user's actual phone. Emulator screenshots do not replace this.
- [ ] After every preceding blocker passes, verify the signed update/package installation path.

## Release boundary

- Keep versionCode/versionName at the confirmed installed baseline until release is explicitly approved.
- Do not prepare/publish version 395 or enable its in-app update until every required gate is verified and the user explicitly authorizes release.
- Update this checkpoint after meaningful work, recording the exact commit/run URL and result. Keep this checklist canonical; put only a concise pointer/current handoff in `RESTORATION_STATUS.md`.


### 2026-10-25 — Global Undo/Redo validation #183 / #184
- [x] #183 failed before tests ran: Kotlin compilation reported nullable receiver errors for `undoClipButton` and `redoClipButton` at `VideoEditorActivity.kt` lines 1282–1287.
- [x] Fixed by handling each nullable button with `?.let { button -> ... }` before styling and adding it to the bottom dock. Commit: `999a1d15eae6aababf8842655a6413ab2d694ad2` on `fix/global-editor-undo`.
- [x] #184 finished with an editor-startup crash (not queued anymore); the parent-attachment bug was fixed and run #185 passed, as recorded below: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40227983970
- [ ] Keep release 395 blocked until build and emulator interaction tests pass; do not merge this branch or publish APK yet.


### 2026-10-25 — Global Undo/Redo validation #184 / #185
- [x] #184 compiled but the editor crashed when opened: Android logcat showed `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.kt:1286`. The undo/redo buttons were still attached to the old, now-unused `clipHistoryRow` and were then added to the bottom dock.
- [x] Fixed in commit `09360482c1592858119b588f4e73f192285afc8d` on `fix/global-editor-undo`: create the buttons without attaching them to a temporary parent; attach each only once to the persistent bottom dock.
- [x] #185 passed: debug build and all 9 connected instrumentation tests succeeded. Details below: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472
- [ ] Keep release 395 blocked until build and emulator interaction tests pass. Do not merge this branch or publish APK yet.


### 2026-10-25 — Undo/Redo crash fix verified (#185)

- [x] Run #185 passed on commit `09360482c1592858119b588f4e73f192285afc8d`: Android emulator UI job succeeded; debug build and all 9 connected instrumentation tests passed.
- [x] Root cause from #184 fixed: Undo/Redo controls are no longer attached to a temporary row before being attached to the persistent bottom dock, preventing `The specified child already has a parent` crash in `VideoEditorActivity.buildUi()`.
- [x] Editor smoke flow reached clip import and produced the expected screenshots/logs; connected instrumentation test exit code was 0.
- [ ] This validates the crash fix and current emulator suite only. It does **not** close broader Undo/Redo behavior, text-strip/export parity, audio export, timeline edge cases, physical-device review, signed release, or in-app update/install gates.
- [ ] Keep version 395 blocked; do not merge/release until remaining gates are independently verified and release is explicitly authorized.

Run: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472




### 2026-10-25 — Manual JSON round-trip persistence handoff (#189–#192)

- [x] **#189** exposed the initial restored-playhead mismatch: the reopened timeline showed about 1.41 s instead of the saved 7.70 s. The restore path was allowing the programmatic horizontal-scroll restoration to be interpreted as a user seek.
- [x] **#190** aligned the fixture's stored horizontal scroll with the saved 7.70 s playhead, then exposed a smaller but real restore drift: the restoration marker reported `playheadMs=7705`, so the strict `playheadMs=7700` marker assertion failed.
- [x] Production correction committed on `fix/global-editor-undo`: [`976d4b3`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/976d4b38023ab4b5945577012b821b71476e85fd). During project restoration, horizontal-scroll callbacks no longer seek or overwrite the saved playhead. In **#191**, the manual JSON test passed the restored playhead/zoom and prior project-state assertions, then failed later while querying the cyan colour option.
- [x] Root cause of the **#191** failure was test navigation: the test scrolled the vertical text panel to `Üst`, then queried `Camgöbeği` farther down without scrolling that option into view. The assertion is retained; the test now explicitly scrolls to the colour option before checking its selected state. Test-only correction: [`6100ddb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/6100ddb26ac2b89b5bc4cd0061ac583870905d56).
- [x] Source-branch manual JSON Save As → close → Open Document persistence passed in [run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553) after the corrected text/audio-panel scrolling assertions. The separate integrated-branch check is run #195.
- [ ] Keep version 395 blocked until the complete emulator suite and remaining release gates pass. Do not merge, sign, publish, or enable in-app updates yet.


### 2026-10-26 — Integrated unified history and persistence fixes

- [x] Combined the shared `EditorEditSnapshot` Undo/Redo implementation with the release-gate branch's existing playhead-restoration guard instead of replacing either file wholesale.
- [x] Kept the release-gate branch's existing manual-save test changes and added the source-branch unified Undo/Redo interaction test; the manual JSON fixture now uses the source branch's tested two-clip, 7.70 s playhead/scroll setup.
- [x] Run #194 isolated the integration-only compile error: duplicate local `restoredPlayheadMs` declarations in `EditorTimelineInteractionTest.kt`; unit tests and debug APK compilation passed, but Android instrumentation-test compilation failed before emulator interaction tests.
- [x] Removed the obsolete 2.00 s assertion for the old fixture and retained the actual 7.70 s saved-playhead assertion plus 200% zoom and combined 9.52 s duration checks.
- [x] Pushed the focused test-only correction in commit [`934d52e`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/934d52e6a6c1fcaf3e477029e5eee97210726899); removed the duplicate local declaration while keeping the 7.70 s playhead, 200% zoom and 9.52 s trimmed-duration assertions.
- [x] Integrated candidate run [#195 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40277993448): unit tests and debug APK build passed; emulator interaction tests exposed a stale contradictory assertion in the manual JSON test. After correctly verifying the loaded project's 7.00–9.00 s / top / cyan / non-bold text state, the test incorrectly demanded a second, never-loaded project's 0.00–6.50 s / bottom / yellow / bold state. This is a test-fixture/assertion inconsistency; the run does not demonstrate an app regression.
- [x] Focused fix: removed only the obsolete second assertion block in [`fc76fb8`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/fc76fb81c199f1df074819c54cd19ae70a9ea462). Integrated gate [#196 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719); do not repeat already-passing unit/build steps without related source changes.
- [ ] Keep version 395 blocked. A successful earlier branch run does not certify this integrated tree.


### 2026-10-26 — #194 integration failure triage

- [x] Inspected job logs, not just the red status: failure was `compileDebugAndroidTestKotlin`, with duplicate local declarations at test lines 1119 and 1128. Unit tests and debug APK build had passed.
- [x] Removed only the stale 2.00 s playhead assertion/declaration from the combined manual JSON test. Kept the fixture's intended saved playhead at 7.70 s, the 200% zoom check and the 9.52 s combined trimmed duration check.
- [x] Started the single follow-up verification for this exact correction: [run #195](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40277993448). Do not queue a duplicate run or manually repeat unit/build stages that already passed in #194.
- [x] Recorded integrated gate outcome: [#196 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719). The obsolete contradictory assertion was removed; app code was not changed for that test-only failure.
- [ ] Continue with the next distinct uncovered behavior; version 395 remains blocked until all required release gates pass and release is explicitly authorized.


### 2026-10-26 — Integrated candidate gate passed (#196)

- [x] Run [#196 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719) on commit `fc76fb81c199f1df074819c54cd19ae70a9ea462`.
- [x] Job steps confirm unit tests, debug APK build, offline MP4 fixture setup, emulator launch/interaction tests, screenshot/diagnostic upload all completed successfully.
- [x] The test-only stale contradictory manual-JSON assertions are resolved; integrated candidate's current gate passes.
- [x] Added focused UI coverage for text-strip start/end trim handles in commit [`05a081e`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/05a081e5492ce538cabef74ca4544ba8d6f70f3c). [Run #197 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40302379479): unit tests/build passed; instrumentation failed because after dragging the start handle the end handle was outside the horizontal timeline viewport and therefore absent from UiAutomator's visible accessibility tree. This is a test visibility/scroll setup failure, not proof that the app's end handle is broken.
- [x] Adjusted the test to scroll the synchronized horizontal timeline lane to its end before querying the end handle in commit [`5093dd1`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/5093dd11f039e0be52e167d666558ee0f920eb2f).
- [ ] Follow-up [run #198 — IN PROGRESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40304600189). Wait for its outcome before changing this test again.
- [ ] After the handle test passes, add preview/export text parity coverage, then audio export behavior and remaining timeline edge cases. Do not rerun #196 absent related source changes.
- [ ] Physical-device review and signed update/install verification remain outstanding. Version 395 remains blocked; no release actions taken.


### 2026-10-26 — Text timeline trim-handle test follow-up

- [x] Run [#197 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40302379479): unit tests, debug APK build and fixture setup passed; emulator test failed because the end handle was not in UiAutomator's visible accessibility tree after dragging the start handle. The failure message was `Could not find the text-strip end handle`.
- [x] Root cause indicated by UI structure: the text strip can extend beyond the horizontal viewport; only visible accessibility nodes are discoverable. Updated the test to scroll the synchronized horizontal lane to the end before querying the end handle; app source unchanged.
- [ ] [Run #198](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40304600189) is the single verification of that focused test adjustment; don't queue a duplicate run.
