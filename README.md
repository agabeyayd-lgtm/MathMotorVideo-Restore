# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export run on-device. Source videos must remain unchanged; finished edits are exported as new MP4 files.

## Current handoff — 2026-10-25

**Continue from this checkpoint. Do not restart completed work or repeat passing tests unless related source changes or a regression requires it.**

- Repository: [MathMotorVideo-Restore](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore)
- Active branch: `fix/editor-395-gate`
- Draft PR: [#8 — repair editor interactions for version 395](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- User-installed baseline: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- Latest green baseline: [CI run #161 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107), commit [`3d910eb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/3d910eb5466ae05aca2e4fa5f17088dfa9602577). Unit tests, debug APK build, screenshot capture and **all 7 emulator interaction tests passed**.
- Current task: manual JSON Save As → close editor → reopen JSON. [Run #163](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40214305429) saved the JSON but hit a stale `Downloads` row; [run #164](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40215482800) then revealed the real remaining issue: the test tried `UiObject2.click()` on a non-clickable filename `TextView`, so the document was never opened and the restore-status assertion failed. The follow-on screenshot failure was a cleanup cascade, not a new product regression. Test fix [`de8948e`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/de8948e649431fd7e2b6aa32210b70d519a5222e) taps the filename's current screen coordinates and captures visible app error text while returning home on failure. [CI run #165](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40216650783) is validating that fix. This gate is **not passed yet**.
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

Test fixture CI diagnosis: [run #167 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40218228081) showed `apt-get update` timing out against the hosted runner's package mirror; [run #168 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40218541657) showed the static FFmpeg download action received an invalid archive. To remove both network dependencies, commit [`9c3c1f2`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/9c3c1f2228ee04c5e7d68fce96dfe9606c1e19b9) now decodes two small, checked-in MP4 fixture payloads from `ci-fixtures/` (8-second moving test pattern and 3-second solid-green clip) without installing FFmpeg. [Run #169](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40219003521) is checking this offline path. Keep this gate unchecked until manual JSON save/open and the subsequent home-screen test pass.
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
