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
- [#214 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40357110853), head SHA `7f96ad989e975e02f9232373fbdaedc70bfa3754`: unit tests, Debug APK, offline fixtures, emulator setup, connected instrumentation and artifact upload all passed. The #213 effect-volume Redo visibility assertion was corrected by scrolling to the exact label before reading the unchanged expected value; no production history change was required.
- `sourceAudioToggleAndEffectVolumeUseUndoRedoHistory` is verified for UI state/history. This does not validate exported MP4 audio.
- Next unique gate: an end-to-end MP4 audio test creates deterministic 440 Hz music and an 880 Hz timed effect WAV, exports the video, decodes the saved AAC track, and measures tone presence at configured intervals and gains. Its first run is pending.
- Do not repeat #210 text-strip drags or #212 timed-text export unchanged.
- Remaining after this test: source-video audio mute/volume with a fixture that contains its own audio track, A/V sync, text preview after seek/zoom, split/reorder/trim-scroll conflicts, physical-device review, signed update/install.
- Keep versionCode 395 blocked. Do not change version metadata, sign/publish an APK, or enable in-app updates without all required gates and explicit authorization.

## Next action

Run the newly added exported-MP4 audio test, inspect decoded PCM measurements, and distinguish an export/mix issue from a decoder or fixture issue. The #214 Undo/Redo UI-history gate is green. Do not repeat passed text-strip or timed-text tests unchanged; keep versionCode 395 blocked.


## #215 root-cause record
- #215 failed in the newly added exported MP4 audio test, not during setup: `VideoEditorActivity.exportEditedVideo()` crashed while building the timed sound-effect sequence because its first item is a gap and the sequence did not declare an audio track. Fix the sequence builder with explicit `C.TRACK_TYPE_AUDIO`; preserve timing and amplitude assertions. Next run must prove actual AAC decoding and tone windows.
