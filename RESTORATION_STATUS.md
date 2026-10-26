# Restoration Status — 2026-10-26

**Resume from this checkpoint; do not restart completed work.** The canonical test history and remaining-work checklist live in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md).

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`
- Active branch: `fix/editor-395-gate`
- Draft PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed phone baseline: versionCode **394** / build **61** (`app-v8.3-61`)
- **Version 395 remains blocked**. Do not change version metadata, sign/publish an APK, or enable in-app update.

## Current gate

- [Run #202 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40311694275): test-code compilation error because the test supplied `BySelector` to this AndroidX `UiScrollable.scrollIntoView` overload. No trim interaction ran.
- Fixed API usage in [`b1ec215`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/b1ec215e8d4a66342367491026773fbc23849562): use `UiSelector` for scrollIntoView and `BySelector` for UiDevice queries.
- [Run #203 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40312901567): unit tests and debug APK build passed, but the screenshot/setup script stopped before instrumentation because Android's own Pixel/Nexus Launcher displayed a persistent ANR dialog. Diagnostics showed MathMotor's `MainActivity` was already resumed but the system overlay retained focus. This run does **not** demonstrate a MathMotor editor or trim-handle failure.
- Corrected system-ANR recovery in [`2c278f3`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/2c278f3d45c21ac62a8505849924af179da3a5cb): after three Wait attempts, the helper can close only a confirmed Android-owned launcher ANR and re-check the window hierarchy. A MathMotor app-level ANR remains a hard failure.
- [Run #204 — IN PROGRESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40313820818) verifies that recovery change. Check its result before taking further action; do not queue a duplicate run.

## Proven earlier; do not repeat without relevant code changes

- [Run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375): 8/8 instrumentation tests passed, including distinct filmstrip timestamps.
- [Run #179](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40225562573): playhead restoration guard survived ExoPlayer preparation.
- [Run #185](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40228445472): debug build and 9/9 instrumentation tests passed after the Undo/Redo button-parent crash fix.
- [Run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553): source-branch manual JSON save/reopen and text/music-volume Undo/Redo interaction tests passed.
- [Run #196](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40300395719): integrated-candidate unit/build/emulator gate passed before the new text-strip trim interaction test was added.

## Continue after #204

1. Confirm the emulator setup reaches the instrumentation suite; then inspect the exact right/left text-strip trim result.
2. Add/verify preview-vs-export text parity using actual MP4 output.
3. Verify audio export behavior and remaining timeline edge cases.
4. Perform physical-device layout/keyboard/touch review.
5. Verify signed update and in-app install only after all earlier gates and explicit release authorization.

The trim-handle interaction, preview/export parity, full audio export, gesture-grouping edge cases, physical-device review, and signed update/install are not yet verified. No release actions taken.
