# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export should happen on-device; no account or paid AI service is required for normal use. Original source videos must remain unchanged; exports are written as new MP4 files.

## Live handoff — 2026-10-25

**Continue from this checkpoint. Do not redo completed tests unless source changes affect them.**

- Active repair branch: `fix/editor-395-gate`; draft PR: [#8 — repair editor interactions for version 395](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8).
- Installed baseline confirmed by the user: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- **Release 395 is blocked.** Do not change version metadata, sign/publish an APK, or offer an update until the true blockers below pass and the user explicitly authorizes release.
- Latest completed full emulator workflow: [run #40199353737 (#152)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40199353737), commit `515df8035d31508acb03f5e9fe59cf0e78879508`. Result: **6 tests passed, 0 failures, 0 errors, 0 skipped**.
- A separate saved-draft round-trip UI test was added afterward in commit [`b109bbe`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/b109bbeff71876d93a90e53acd17f41cad1ad64f). Its CI result is **not yet confirmed**; treat that test as pending until a workflow run proves it.

## Verified — do not repeat without a relevant code change

- [x] Fixed-center playhead interaction, pinch zoom anchor stability, and scroll to the beginning/end on the emulator.
- [x] Left/right clip trim, then restoration of the original source duration.
- [x] Filmstrip loads frames from different timestamps; screenshot/log evidence is attached to run #152.
- [x] Text-layer creation, live preview, size adjustment and start/end timing controls.
- [x] **Keyboard-resize input accessibility:** test `textTimingAndSizeControlsUpdatePreview` explicitly waits for `Metin katmanı metin girişi` after the keyboard/layout resize. This test passed in run #152. Do not queue the same keyboard assertion again unless this layout code changes or a device-specific regression is reported.
- [x] Two source clips exported as one new MP4: **11,029 ms**, green second-clip frame at 9.5 s, and both source fixture hashes unchanged.
- [x] Home/editor/text-panel emulator screenshot and smoke capture. These screenshots were reviewed; this is not a physical-phone usability sign-off.
- [x] Home screen no longer contains the old scenario/world/10-second video creation items. The home screenshot currently presents gallery import, empty project, drafts, and recent exports.

### Run #152 evidence

[JUnit report and emulator artifacts](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40199353737#artifacts)

Passed tests:
`fixedPlayheadScrollAndCenteredPinchStayInSync`,
`rightAndLeftTrimHandlesCanTrimAndRestoreSourceFootage`,
`filmstripLoadsFramesFromDistinctTimestamps`,
`textTimingAndSizeControlsUpdatePreview`,
`twoClipsExportToOneMp4InOrder`, and
`captureHomeEditorAndTextPanel`.

Multi-clip export log: `durationMs=11029 secondFrameRGB=0,254,2 originalsUnchanged=true`.

## True remaining work — one canonical checklist

### 1. Saved project and resume
- [ ] Run and fix the new `savedDraftReopensTextLayerAndClipState` test from commit `b109bbe`. It checks that a stored draft reopens with the clip and text layer, and restores the editable text and 32 sp size.
- [ ] After that test passes, add a separate end-to-end check of manual JSON **Save As → close → Open**. The current test covers the local draft store/restore path, not the Android document-picker path.
- [ ] Verify project restoration of clip trims/order, playhead/zoom, text timing/position/style, music/effect URI and levels.

### 2. Undo / Redo is genuinely incomplete
- [ ] Current implementation stores `undoClipHistory` / `redoClipHistory` snapshots for clip edits only, with controls labelled “Klip geri al / Klip yinele”. This is not a unified editor history.
- [ ] Implement and test history for supported text and audio/music edits as well as clip edits; undo and redo must restore timeline and preview together. Do not relabel the existing clip-only behavior as global Undo/Redo.

### 3. Text-strip and export parity
- [ ] Test dragging the start/end handles on a text timeline strip; the start/end sliders passing is **not** evidence that strip trimming passes.
- [ ] Export a clip with a timed, styled text layer and compare output against preview for timing, position, size, colour and style.
- [ ] Verify text settings survive manual project save/open and timeline seek/zoom.

### 4. Audio and sound export
- [ ] Validate original-video audio level/mute, background music looping/volume, timed sound-effect start/volume, and the audio/video sync in the exported MP4. Source controls/export code exist, but this behaviour does not yet have end-to-end pass evidence.

### 5. Timeline cases not covered by current tests
- [ ] Test split-at-playhead and confirm the split/export boundary matches the visible playhead.
- [ ] Test trim versus scroll gesture conflicts, and multi-clip trim/reorder/seek at more than one zoom level. The one-clip start/end/zoom checks and the two-clip export test already pass; do not repeat those identical checks.

### 6. Physical-device review and release
- [ ] Review thumbnail density, tap targets, timeline lanes, keyboard panel and home/editor layout on the user's actual phone. Emulator screenshots are useful but cannot certify physical-device usability.
- [ ] Check the final signed update/package installation only after all blockers above pass.

## Important release boundary

- A green debug build/test is **not** a signed release APK.
- Keep versionCode/versionName at the confirmed installed baseline until the release gate passes.
- Do not prepare/publish version 395 or enable its in-app update unless every required item above has verified evidence and the user explicitly authorizes the release.
- Update this checkpoint after meaningful changes with the exact commit/run URL and test outcome. Keep only this checklist canonical; do not copy stale unchecked versions into another section.
