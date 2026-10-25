# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export run on-device. Source videos must remain unchanged; finished edits are exported as new MP4 files.

## Current handoff — 2026-10-25

**Continue from this checkpoint. Do not restart completed work or repeat passing tests unless related source changes or a regression requires it.**

- Repository: [MathMotorVideo-Restore](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore)
- Active branch: `fix/editor-395-gate`
- Draft PR: [#8 — repair editor interactions for version 395](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- User-installed baseline: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- Latest green baseline: [CI run #161 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107), commit [`3d910eb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/3d910eb5466ae05aca2e4fa5f17088dfa9602577). Unit tests, debug APK build, screenshot capture and **all 7 emulator interaction tests passed**.
- Current task: manual JSON Save As → close editor → open saved JSON, then resolve the last filmstrip regression. Run #171 fixed the project-loaded audio-volume label: the JSON restore test now reaches and accepts `Müzik sesi: 37%`; the only remaining reported failure was identical filmstrip fingerprints. Root cause was in the app decoder: the scaled-frame path used `MediaMetadataRetriever.OPTION_CLOSEST_SYNC`, which may repeatedly return the same keyframe for long-GOP videos. Commit [`52755a6`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/52755a6889f44e2c6936cf34dd0102743c0b875e) switches the first scaled-frame request to `OPTION_CLOSEST`; [run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375) is validating the actual decoder fix. Keep the manual JSON gate unchecked until the full Android round trip and follow-on home screenshot test both pass.
- **Release 395 is still blocked.** A green debug CI run is not a signed release. Do not change version metadata, publish/sign an APK, or enable the in-app update until the remaining gates below pass and the user explicitly authorizes release.

## Verified — do not repeat without a relevant code change

- [x] Fixed-center timeline playhead interaction, pinch-zoom anchor stability, and timeline scrolling to the beginning/end.
- [x] Left/right clip trim and restoration of the original source duration.
- [x] Filmstrip thumbnails load frames from distinct timestamps.
- [x] Text-layer creation, live preview, text-size changes, and text start/end timing controls.
- [x] Keyboard/layout-resize accessibility for the text input.
- [x] Two source clips export sequentially to a new MP4: approximately **11,029 ms** total, second clip's green frame visible at 9.5 seconds, both source fixture hashes unchanged.
- [x] Home/editor/text-panel screenshot smoke test passed. This is emulator evidence, not physical-phone usability sign-off.
- [x] Removed the old home-screen items for scenario/world/10-second video creation; home shows gallery import, empty project, drafts, and recent exports.
- [x] **Local saved-draft round trip passes in run #161:** restores the trimmed clip duration (**7.02 s**), text overlay and editable content (`ROUND TRIP TEXT`), and text size (**32 sp**). The test now checks the editable field before scrolling down to the size controls.

Useful latest evidence:
- [CI run #161 — all checks green](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107)
- [Run #161 emulator screenshots/diagnostics artifact](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107#artifacts)

## Remaining work — canonical checklist

Work top to bottom. Mark an item complete only when there is test or inspection evidence. Do not go back to the completed list above unless a related change breaks it.

### 1. Manual project Save As → reopen

Test fixture CI diagnosis: [run #167](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40218228081) hit the hosted runner apt-mirror timeout; [run #168](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40218541657) received an invalid FFmpeg archive. [`9c3c1f2`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/9c3c1f2228ee04c5e7d68fce96dfe9606c1e19b9) removed those network dependencies by checking in deterministic MP4 fixtures. In [run #171](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40220299247), offline fixtures, unit tests and debug build passed; the audio label/JSON restore assertion no longer failed, but the filmstrip check still saw one fingerprint. Source inspection found the true decoder cause: the scaled-frame path used `OPTION_CLOSEST_SYNC`, potentially reusing a keyframe. Commit [`52755a6`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/52755a6889f44e2c6936cf34dd0102743c0b875e) changes it to `OPTION_CLOSEST`. [Run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375) validates this production-code fix. Leave this gate unchecked until the complete suite, including manual JSON Save As → reopen and the subsequent home-screen screenshot, passes.
- [ ] Confirm manual project-file restoration retains clip order and trim bounds, playhead/zoom, text timing/position/style, music/effect URI and volume levels.

### 2. Unified editor Undo / Redo

- [ ] Current history is clip-only (`undoClipHistory` / `redoClipHistory`) and buttons say “Klip geri al / Klip yinele”; it is not a global editor history.
- [ ] Extend undo/redo to supported text, audio/music and clip edits. Verify timeline and preview return to the same state together. Do not relabel the existing clip-only feature as global undo/redo.

### 3. Text timeline and export parity

- [ ] Test dragging the start/end handles on a text timeline strip; passing the timing sliders does not prove strip dragging works.
- [ ] Export a clip with timed/styled text and compare the MP4 with preview for timing, position, size, colour and style.
- [ ] Verify text settings remain correct after manual JSON save/open and after timeline seek/zoom.

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
