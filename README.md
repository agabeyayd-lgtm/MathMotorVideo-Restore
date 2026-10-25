# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export run on-device. Source videos must remain unchanged; finished edits are exported as new MP4 files.

## Current handoff — 2026-10-26

**Continue from this checkpoint. Do not restart completed work or repeat passing tests unless a related change requires it. Record the exact failing assertion before changing source or tests.**

- Repository: [MathMotorVideo-Restore](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore)
- Working branch for the latest verification loop: `fix/global-editor-undo`. The existing draft PR #8 targets `fix/editor-395-gate`; do not assume commits on this branch are in that PR.
- User-installed baseline: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- Historical green baseline: [CI run #161 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107), commit [`3d910eb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/3d910eb5466ae05aca2e4fa5f17088dfa9602577). Unit tests, debug APK build, screenshot capture and the interaction checks in that run passed.
- Latest failure: [run #192 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40233158607), commit [`6100ddb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/6100ddb26ac2b89b5bc4cd0061ac583870905d56). Unit tests and debug APK build passed. The manual JSON round-trip progressed through clip order/trim, playhead/zoom/scroll, text styling, and the project-restoration log confirmed both the music and effect fixture URIs. It then failed while looking for the effect-title label in the audio panel, before scrolling that label into view.
- Focused follow-up: commit [`941d5fd`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/941d5fd356c54c3278060a91a23a5e42d81340f4) updates the Android interaction test to scroll to the music and effect labels separately before asserting their visibility. [Run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553) is validating this change. If it fails, inspect the new assertion and UI/log evidence first; do not blindly repeat the same patch.
- **Release 395 remains blocked.** Do not change version metadata, sign/publish an APK, or enable the in-app update until all required gates below pass and the user explicitly authorizes release.

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

- [ ] Confirm manual project-file restoration retains clip order and trim bounds, playhead/zoom/scroll, text timing/position/style, music/effect URI and titles, all volume levels, and effect start time.
- Current evidence: run #192 got past the previous cyan text-colour-control failure and reached the audio assertions. The restoration marker reported `soundStartMs=750`, `musicFixtureMatches=true`, and `soundFixtureMatches=true`; the remaining failure was the effect-title selector not finding the off-screen label without scrolling. Commit [`941d5fd`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/941d5fd356c54c3278060a91a23a5e42d81340f4) adds explicit scrolling before the label assertions. Validation is pending in [run #193](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40234200553). Keep this gate unchecked until the complete interaction workflow passes.

### 2. Unified editor Undo / Redo

- [x] Replace the clip-only history with shared editor snapshots for text, clip, music/effect references and levels, sound-effect timing, source-audio settings, and editor timeline state.
- [x] The interaction test `unifiedUndoRedoRestoresTextAndAudioEdits` passed in [run #192](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40233158607): it verifies text-layer add/edit Undo/Redo and a real music-volume slider gesture restoring/reapplying the value.
- [ ] Extend explicit interaction coverage to clip trim/split/reorder, sound-effect add/remove/start/volume, source-audio toggle, preview/export parity, and state after saving/reopening. Do not infer full coverage from the shared snapshot implementation alone.
- [ ] Verify buttons accurately reflect whether Undo/Redo is available after every supported operation and that each gesture creates only one history entry.

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
