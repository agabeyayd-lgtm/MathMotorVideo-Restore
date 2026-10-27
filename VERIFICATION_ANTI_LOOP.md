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
- [#216 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40372603373), head SHA `e71ecb195a0685023616f96e05fa159c2a5e2b11`: real exported MP4/AAC verification passed for looped background music and the timed-effect window.
- Earlier #214 validated source-audio toggle and effect-volume Undo/Redo UI history only.
- `sourceAudioToggleAndEffectVolumeUseUndoRedoHistory` is verified for UI state/history. This does not validate exported MP4 audio.
- [#228 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40399875346), head SHA `5380eef327bb3adfe8cdff2a314fd45b79721233`: all 13/13 Android instrumentation tests passed. Decoded AAC source-audio gate measured baseline 16380.5592, kept early ratio 0.3990434, kept late magnitude 6533.6536, muted ratio 0.0000145058, and `originalsUnchanged=true`. Both draft lookup/restore markers and both MP4 saves were observed. Do not repeat this gate unchanged.
- Workflow dependency timeouts (#218/#226) are resolved by bounded FFmpeg installation; do not repeat that setup fix unchanged.
- Do not repeat #210 text-strip drags or #212 timed-text export unchanged.
- [#229 — FAILED BEFORE INSTRUMENTATION](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40452759400), head SHA `2b227a66bb3981d254aa094eb3bf4abbd9722ee8`: unit tests and Debug APK build passed; FFmpeg install timed out with `exit code 124` while downloading from the throttled Azure Ubuntu mirror. No emulator/text assertions ran; classify as CI preflight, not app behavior.
- [#230 — FAILED, 13/14 instrumentation tests passed](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40454123893), head SHA `30ff517bdb0f943f9ca71d19982da4e843552228`: the mirror switch fixed FFmpeg setup and instrumentation ran. The only failure was the new text seek/zoom test's coordinate-based SeekBar mapping: requested return time 4200 ms but actual label stayed at 3330 ms. The test computed the thumb from outer bounds and ignored Android SeekBar internal insets; this is not yet evidence of an app preview defect.
- [#231 — IN PROGRESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40456248654), head SHA `7e050ee535bc025f5793fe69faaa30cfcccb4d71`: use UiAutomator's accessible SeekBar progress action, then explicitly assert project time is within 150 ms of target. Verify this test interaction change; do not weaken the preview visibility/settings assertions.
- Remaining after #229: measured A/V sync, split-at-playhead/export boundary, trim/scroll and multi-clip edge cases, extra Undo/Redo coverage, physical-device review and signed update/install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action (superseded by live run #226 below)

Do not launch a duplicate source-audio run while #227 is in progress. FFmpeg installation and fixture preparation already passed. Inspect the instrumented draft-restoration precondition, then decoded 220 Hz magnitudes and output/source ratios for 40% gain and mute; if it fails before fixture generation, repair only that CI preflight layer. Keep the #216 music-loop/timed-effect test green and do not repeat it unchanged; versionCode 395 remains blocked.


## #215 root-cause record
- #215 failed in the newly added exported MP4 audio test, not during setup: `VideoEditorActivity.exportEditedVideo()` crashed while building the timed sound-effect sequence because its first item is a gap and the sequence did not declare an audio track. Fix the sequence builder with explicit `C.TRACK_TYPE_AUDIO`; preserve timing and amplitude assertions. Next run must prove actual AAC decoding and tone windows.


## #216 passed / next gate
- Run #216 passed on `e71ecb195a0685023616f96e05fa159c2a5e2b11`: the actual exported MP4's AAC audio decoded and passed music-loop and effect-window signal checks. The root cause of #215 was the leading gap in the timed effect sequence lacking an explicit audio track; this is now verified.
- Next unique candidate test checks actual source-audio amplitude at 40%, mute behavior, and preservation of the original audio-bearing fixture. Do not mark the full audio gate green until these pass and A/V sync is independently checked.


## #218 — preflight root cause and repair
- Run #218 failed before emulator setup. Unit tests and Debug APK build passed; fixture preparation exited at `command -v ffmpeg` because ffmpeg/ffprobe are not present on this runner image.
- This is a CI dependency/preflight failure, not an application assertion. The workflow now installs ffmpeg only when unavailable, then prepares the deterministic audio-bearing MP4 and verifies its streams before copying it into the emulator app sandbox.
- Fixture preparation passed in #227/#228. The remaining evidence is the second-draft restore, muted MP4 export/decode, quantitative source-audio ratios and unchanged source hash; do not mark this complete from setup/build alone.


## #227 result / #228 unique verification (2026-10-26)
- [#227 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40396049579), head SHA `7560ce792288ece7d2b3ca2116502589a68a24f1`: build, FFmpeg setup, fixture preparation and emulator setup passed; 12/13 instrumentation tests passed. The first source-audio-on draft was restored and its MP4 saved. The muted second draft never reached the intended EditorTestEntryActivity: Android's `am start` returned result code 3 and reused/brought forward the existing task, with no entry/lookup/restore marker for its draft ID. Thus #227 did not reach the muted export or the final 220 Hz comparisons.
- Test-only root-cause fix in [`5380eef`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/5380eef327bb3adfe8cdff2a314fd45b79721233): add `--activity-clear-task` to each seeded draft launch without force-stopping the package (force-stop could kill the instrumentation runner). Wait for the fresh entry marker, exact draft lookup and restore-completion marker before continuing. Include recent marker lines in failed assertions.
- [#228 — IN PROGRESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40399875346) is the only verification of this focused change. Do not queue another run while it is active.
- If #228 restores both drafts, inspect the actual decoded source-baseline, 40% kept early/late ratios, muted ratio and original-source SHA. Keep thresholds unchanged. #221's actual muted ratio `0.3990` vs expected `<0.08` is still an unresolved audio-behavior failure; 395 remains blocked.
