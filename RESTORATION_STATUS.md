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
- Global Undo/Redo remains blocked: [run #178](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40224804039) could not reach the button by scrolling the compact panel. [Run #181 failed](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40226711268): the separate row reduced compact panel height, breaking two text/persistence checks, and the audio assertion searched a hidden tab. On [`fix/global-editor-undo`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/tree/fix/global-editor-undo), controls now share the existing bottom dock and the test checks the volume in the Audio tab; [run #183](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40227774694) is pending.
- Installed baseline remains versionCode **394** / build **61**. Version 395 is still blocked.

The local saved-draft restore test now passes. It checks restored clip trim duration (7.02 s), preview text overlay, editable text (`ROUND TRIP TEXT`) and saved size (32 sp). The test order was corrected so the editable field is checked while visible, before scrolling down to the size control. Do not revisit this fixed selector/order issue unless a related code change regresses it.

Previously verified and not to repeat unnecessarily: timeline playhead/zoom/scroll, left/right clip trimming/source-duration restore, distinct filmstrip frames, text editing/timing controls, keyboard-resize text input, sequential two-clip MP4 export with originals unchanged, and emulator home/editor screenshots.

## Next steps — do these in order

1. **Manual JSON project persistence — fix queued for verification.** Basic Save As → close editor → Open Document passed in [run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375). [Run #177](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40224656700) exposed a real race: the periodic playhead sync copied ExoPlayer's default 0 ms position while the restored clip was still preparing, overwriting the saved 2.00 s playhead. Both sync paths now wait for `Player.STATE_READY`; [run #179 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40225562573) passed the emulator workflow.
2. **Unified Undo / Redo — still failing instrumentation.** [Run #178](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40224804039) could not scroll to the global Undo button, even with stable accessibility descriptions. The next fix puts the history controls in a persistent row outside the tool panel; [run #181](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40226711268) is pending. Do not weaken the assertion or mark this complete.
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
- [ ] #184 validation is queued: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40227983970
- [ ] Keep release 395 blocked until build and emulator interaction tests pass; do not merge this branch or publish APK yet.


### 2026-10-25 — Global Undo/Redo validation #184 / #185
- [x] #184 compiled but the editor crashed when opened: Android logcat showed `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.kt:1286`. The undo/redo buttons were still attached to the old, now-unused `clipHistoryRow` and were then added to the bottom dock.
- [x] Fixed in commit `09360482c1592858119b588f4e73f192285afc8d` on `fix/global-editor-undo`: create the buttons without attaching them to a temporary parent; attach each only once to the persistent bottom dock.
- [ ] #185 validation is running: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472
- [ ] Keep release 395 blocked until build and emulator interaction tests pass. Do not merge this branch or publish APK yet.


### 2026-10-25 — Undo/Redo crash fix verified (#185)

- [x] Run #185 passed on commit `09360482c1592858119b588f4e73f192285afc8d`: Android emulator UI job succeeded; debug build and all 9 connected instrumentation tests passed.
- [x] Root cause from #184 fixed: Undo/Redo controls are no longer attached to a temporary row before being attached to the persistent bottom dock, preventing `The specified child already has a parent` crash in `VideoEditorActivity.buildUi()`.
- [x] Editor smoke flow reached clip import and produced the expected screenshots/logs; connected instrumentation test exit code was 0.
- [ ] This validates the crash fix and current emulator suite only. It does **not** close broader Undo/Redo behavior, text-strip/export parity, audio export, timeline edge cases, physical-device review, signed release, or in-app update/install gates.
- [ ] Keep version 395 blocked; do not merge/release until remaining gates are independently verified and release is explicitly authorized.

Run: https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472
