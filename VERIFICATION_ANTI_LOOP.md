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
- [#216 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40372603373), head SHA `e71ecb195a0685023616f96e05fa159c2a5e2b11`: unit tests, Debug APK, connected instrumentation and artifact upload passed. The production timed-effect leading-gap fix is verified through a real exported MP4/AAC decode test; music is present across the output and the 880 Hz effect is limited to the configured interval.
- Earlier #214 validated source-audio toggle and effect-volume Undo/Redo UI history only.
- `sourceAudioToggleAndEffectVolumeUseUndoRedoHistory` is verified for UI state/history. This does not validate exported MP4 audio.
- Current unique gate: [#227](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40396049579), queued after #226 proved the package-install layer can time out. #226's exact failure was `apt-get install ffmpeg` exit 124 after its 120-second child timeout while downloading `libcodec2-1.2`; unit tests and Debug APK build passed, fixture generation and instrumentation did not start.
- Workflow fix commit `7560ce792288ece7d2b3ca2116502589a68a24f1` allows up to 660 seconds for installation, a 16-minute step cap and APT network retries. Inspect #227 before doing anything else.
- Do not repeat #210 text-strip drags or #212 timed-text export unchanged.
- Remaining after this test: source-video audio mute/volume with a fixture that contains its own audio track, A/V sync, text preview after seek/zoom, split/reorder/trim-scroll conflicts, physical-device review, signed update/install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action (superseded by live run #226 below)

Do not launch a duplicate source-audio run while #227 is queued/in progress. Inspect its FFmpeg setup result first. If it passes, inspect decoded 220 Hz magnitudes and compare output/source ratios for 40% gain and mute; if it fails before fixture generation, repair only that CI preflight layer. Keep the #216 music-loop/timed-effect test green and do not repeat it unchanged; versionCode 395 remains blocked.


## #215 root-cause record
- #215 failed in the newly added exported MP4 audio test, not during setup: `VideoEditorActivity.exportEditedVideo()` crashed while building the timed sound-effect sequence because its first item is a gap and the sequence did not declare an audio track. Fix the sequence builder with explicit `C.TRACK_TYPE_AUDIO`; preserve timing and amplitude assertions. Next run must prove actual AAC decoding and tone windows.


## #216 passed / next gate
- Run #216 passed on `e71ecb195a0685023616f96e05fa159c2a5e2b11`: the actual exported MP4's AAC audio decoded and passed music-loop and effect-window signal checks. The root cause of #215 was the leading gap in the timed effect sequence lacking an explicit audio track; this is now verified.
- Next unique candidate test checks actual source-audio amplitude at 40%, mute behavior, and preservation of the original audio-bearing fixture. Do not mark the full audio gate green until these pass and A/V sync is independently checked.


## #218 — preflight root cause and repair
- Run #218 failed before emulator setup. Unit tests and Debug APK build passed; fixture preparation exited at `command -v ffmpeg` because ffmpeg/ffprobe are not present on this runner image.
- This is a CI dependency/preflight failure, not an application assertion. The workflow now installs ffmpeg only when unavailable, then prepares the deterministic audio-bearing MP4 and verifies its streams before copying it into the emulator app sandbox.
- Next run must show fixture preparation passed and the source-audio assertions actually ran. Do not mark source audio complete from a build-only result.


## #226 — current live run (2026-10-26)
- Run [#226](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40395040884), head SHA `2ca65a0c50a3443218647640249128c471b35747`, is in progress.
- Unit tests and `assembleDebug` passed. The current step is `Install ffmpeg for deterministic audio fixture`; it has a six-minute timeout. Fixture preparation, emulator setup and Android instrumentation have not started.
- #225 was cancelled by the newer run and is not evidence of a pass or failure. #224 did not reach MP4 export; #221 remains the last actual mute measurement and failed with a 220 Hz ratio of `0.3990` instead of `<0.08`.
- Next action: inspect #226's setup outcome before changing code or starting any new run. If FFmpeg setup fails, repair only that CI preflight issue. If it passes, verify the dedicated draft-restoration event, expected checkbox state, 40% gain measurement, mute measurement and unchanged source-file hash in the same test run. Do not weaken thresholds. Keep versionCode 395 blocked.
