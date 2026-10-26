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
- Next unique gate: an audio-bearing source-video fixture with a deterministic 220 Hz track; export at 40% source gain and again with source audio muted, then decode AAC and compare against the known source baseline.
- Do not repeat #210 text-strip drags or #212 timed-text export unchanged.
- Remaining after this test: source-video audio mute/volume with a fixture that contains its own audio track, A/V sync, text preview after seek/zoom, split/reorder/trim-scroll conflicts, physical-device review, signed update/install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action

Run the new source-audio MP4 test, inspect the decoded 220 Hz magnitudes and compare the output/source ratio. Keep the #216 music-loop/timed-effect test green and do not repeat it unchanged; versionCode 395 remains blocked.


## #215 root-cause record
- #215 failed in the newly added exported MP4 audio test, not during setup: `VideoEditorActivity.exportEditedVideo()` crashed while building the timed sound-effect sequence because its first item is a gap and the sequence did not declare an audio track. Fix the sequence builder with explicit `C.TRACK_TYPE_AUDIO`; preserve timing and amplitude assertions. Next run must prove actual AAC decoding and tone windows.


## #216 passed / next gate
- Run #216 passed on `e71ecb195a0685023616f96e05fa159c2a5e2b11`: the actual exported MP4's AAC audio decoded and passed music-loop and effect-window signal checks. The root cause of #215 was the leading gap in the timed effect sequence lacking an explicit audio track; this is now verified.
- Next unique candidate test checks actual source-audio amplitude at 40%, mute behavior, and preservation of the original audio-bearing fixture. Do not mark the full audio gate green until these pass and A/V sync is independently checked.


## #218 — preflight root cause and repair
- Run #218 failed before emulator setup. Unit tests and Debug APK build passed; fixture preparation exited at `command -v ffmpeg` because ffmpeg/ffprobe are not present on this runner image.
- This is a CI dependency/preflight failure, not an application assertion. The workflow now installs ffmpeg only when unavailable, then prepares the deterministic audio-bearing MP4 and verifies its streams before copying it into the emulator app sandbox.
- Next run must show fixture preparation passed and the source-audio assertions actually ran. Do not mark source audio complete from a build-only result.


## Staged timeline gates
- Prepared but not yet run: `timedTextPreviewFollowsSeekAndZoom` tests preview visibility around the active text interval and playhead stability across zoom.
- Prepared but not yet run: `splitAtPlayheadMatchesExportBoundary` tests that the split equals the playhead in both clips and that export duration/frame samples preserve the source timeline.
- These changes are isolated on `staging/editor-preview-seek-test` while #219 runs. Do not update the active release-gate ref until #219 completes, so the concurrency group does not cancel the current audio validation.
