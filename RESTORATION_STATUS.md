# Restoration Status — 2026-10-25

## Resume here

**Do not start over.** The canonical completed-test record and remaining-work checklist are in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md).

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`
- Branch: `fix/editor-395-gate`
- PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed baseline: versionCode **394** / build **61** (`app-v8.3-61`)
- Latest tested decoder fix: [`52755a6`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/52755a6889f44e2c6936cf34dd0102743c0b875e) switches scaled filmstrip decoding to `OPTION_CLOSEST`.
- [Run #172 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375): unit tests, debug APK, offline fixture preparation, **8/8 Android instrumentation tests**, emulator screenshot capture, and diagnostic-artifact upload all passed.
- Verified in #172: distinct filmstrip timestamps now yield usable thumbnails; manual JSON Save As → close → Open Document restores the currently asserted trim, text content/size, music/effect names, and audio levels.
- The broader persistence gate remains open for untested clip order, zoom, and text position/colour/style. Playhead-ready race fix is now green in [run #179](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40225562573).
- Global Undo/Redo startup crash is fixed on [`fix/global-editor-undo`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/tree/fix/global-editor-undo): [run #185 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472) passed the debug build and all 9 connected instrumentation tests. This verifies the crash fix/current emulator suite; broader per-edit Undo/Redo state parity still needs explicit coverage.
- Installed baseline remains versionCode **394** / build **61**. Version 395 is still blocked.

The local saved-draft restore test now passes. It checks restored clip trim duration (7.02 s), preview text overlay, editable text (`ROUND TRIP TEXT`) and saved size (32 sp). The test order was corrected so the editable field is checked while visible, before scrolling down to the size control. Do not revisit this fixed selector/order issue unless a related code change regresses it.

Previously verified and not to repeat unnecessarily: timeline playhead/zoom/scroll, left/right clip trimming/source-duration restore, distinct filmstrip frames, text editing/timing controls, keyboard-resize text input, sequential two-clip MP4 export with originals unchanged, and emulator home/editor screenshots.

## Next steps — do these in order

1. **Manual JSON project persistence — still under full verification.** The newer JSON round-trip test got past restoration/playhead and related project-state assertions in [run #191](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40232290189), then failed because the test did not scroll the vertical text panel down to the `Camgöbeği` radio before querying it. This selector/scroll-order issue is fixed in [commit `6100ddb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/6100ddb26ac2b89b5bc4cd0061ac583870905d56); [run #192](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40233158607) must pass before the persistence gate can close. Earlier playhead-ready race remains fixed and green in [run #179](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40225562573).
2. **Unified Undo / Redo — crash fix green, full behavior gate still open.** The global controls now live in the persistent bottom dock; [run #185 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472) passed all 9 connected tests. Next, verify text content/appearance/timing/position, music/effect and source-audio levels, and clip edits, ensuring preview and timeline restore together. Do not weaken assertions.
3. **Text-strip interaction and export parity** — drag text-strip start/end handles; export timed/styled text and compare with preview.
4. **Audio export verification** — source audio level/mute, background music loop/volume, timed sound-effect start/volume and audio/video sync.
5. **Timeline edge cases** — split at playhead/export boundary; trim-versus-scroll conflicts and multi-clip trim/reorder/seek at several zoom levels.
6. **Physical-phone review** — compact layout, thumbnails, tap targets and keyboard/panel behavior on the actual device.
7. **Release/install gate** — verify signed update/package installation after blockers above pass. Version 395 remains blocked.

## Release rule

Version 395 is **not released**. Keep version metadata unchanged at the installed baseline; do not sign/publish an APK or enable in-app update until the remaining gates pass and the user explicitly authorizes release.

After each meaningful change, update README and this short handoff with commit/run links and evidence. Do not keep duplicating old failure logs here; README owns the canonical checklist.


### 2026-10-25 — Global Undo/Redo validation #183 / #184
- [x] #183 failed before tests ran: Kotlin compilation reported nullable receiver errors for `undoClipButton` and `redoClipButton` at `VideoEditorActivity.kt` lines 1282–1287.
- [x] Fixed by handling each nullable button with `?.let { button -> ... }` before styling and adding it to the bottom dock. Commit: `999a1d15eae6aababf8842655a6413ab2d694ad2` on `fix/global-editor-undo`.
- [x] #184 failed on a real editor startup crash; root cause and fix are recorded below.
- [ ] Keep release 395 blocked until build and emulator interaction tests pass; do not merge this branch or publish APK yet.


### 2026-10-25 — Global Undo/Redo validation #184 / #185
- [x] #184 compiled but the editor crashed when opened: Android logcat showed `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.kt:1286`. The undo/redo buttons were still attached to the old, now-unused `clipHistoryRow` and were then added to the bottom dock.
- [x] Fixed in commit `09360482c1592858119b588f4e73f192285afc8d` on `fix/global-editor-undo`: create the buttons without attaching them to a temporary parent; attach each only once to the persistent bottom dock.
- [x] #185 passed: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472
- [ ] Keep release 395 blocked until build and emulator interaction tests pass. Do not merge this branch or publish APK yet.


### 2026-10-25 — Undo/Redo crash fix verified (#185)

- [x] Run #185 passed on commit `09360482c1592858119b588f4e73f192285afc8d`: Android emulator UI job succeeded; debug build and all 9 connected instrumentation tests passed.
- [x] Root cause from #184 fixed: Undo/Redo controls are no longer attached to a temporary row before being attached to the persistent bottom dock, preventing `The specified child already has a parent` crash in `VideoEditorActivity.buildUi()`.
- [x] Editor smoke flow reached clip import and produced the expected screenshots/logs; connected instrumentation test exit code was 0.
- [ ] This validates the crash fix and current emulator suite only. It does **not** close broader Undo/Redo behavior, text-strip/export parity, audio export, timeline edge cases, physical-device review, signed release, or in-app update/install gates.
- [ ] Keep version 395 blocked; do not merge/release until remaining gates are independently verified and release is explicitly authorized.

Run: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472




### 2026-10-25 — Current handoff: run #192

- **Current branch:** `fix/global-editor-undo`; latest code/test commit [`6100ddb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/6100ddb26ac2b89b5bc4cd0061ac583870905d56).
- **#189 → #191:** project JSON restoration initially changed the saved playhead. The timeline scroll listener now ignores programmatic scroll events during restore ([`976d4b3`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/976d4b38023ab4b5945577012b821b71476e85fd)). Run #191 then passed the earlier playhead/zoom and project-state assertions but stopped because the test queried `Camgöbeği` without scrolling the text panel to that option.
- **#192:** the test now scrolls the text panel to the cyan colour radio before asserting it remains selected. Unit tests and debug APK build passed; emulator UI suite is running: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40233158607
- **Next:** inspect #192's final emulator result and fix any remaining assertion from evidence. Do not weaken assertions or restart from earlier completed work.
- Installed baseline remains versionCode **394** / build **61**. Version 395 remains blocked; do not merge, sign, publish, or enable in-app update until all gates pass and the user explicitly approves.
