# MathMotor Video

A local-first Android video editor with a CapCut-inspired workflow. Editing and export should happen on-device; no account or paid AI service is required for normal use. Original source videos must remain unchanged; exports are written as new MP4 files.

## Live handoff — 2026-10-25

**Continue from this checkpoint. Do not redo completed tests unless source changes affect them.**

- Active repair branch: `fix/editor-395-gate`; draft PR: [#8 — repair editor interactions for version 395](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8).
- Installed baseline confirmed by the user: **versionCode 394 / build 61 (`app-v8.3-61`)**.
- **Release 395 is blocked.** Do not change version metadata, sign/publish an APK, or offer an update until the true blockers below pass and the user explicitly authorizes release.
- Latest full emulator workflow [#40202008562 (#156)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202008562), commit `147a0d57094330d4e13408ba6ae4e4162ced914d`, completed with **6 tests passed and 1 failure**. The new draft test stopped at a stale assertion that searched for `Kesit 00:07.02`; the actual editor label is `Klip 00:01.00 / 00:07.02` (playhead at 1.00 s, clip duration 7.02 s). That failure is an incorrect test selector, not evidence the app lost the trim bounds. Corrected the assertion in [`55c69c2`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/55c69c27cd6b370a2f1eb41fa335028bd81cc4cc). Run [#40202911219 (#157)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202911219) failed again at the duration assertion. The exact full-label selector was too brittle; the test now searches for the distinctive restored duration `00:07.02` in commit [`47b0e6f`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/47b0e6fd119f27573956d4065e109da9da1618e5). Wait for the new CI run; do not mark this gate green yet.
- Earlier run [#40201158067 (#154)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40201158067) failed before opening the draft because the debug-only entry bridge refused to start without a video URI and the test waited for the wrong status. Fixes: [`e7820c1`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/e7820c1202a09f7bd0b1de6100c5e36bb73b4f0a) permits a valid saved-draft ID as a launch target; [`288e21d`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/288e21da2a9971c89c9e9914eefa9c6fcfe50dec) matches the actual “Taslak açıldı” status; [`147a0d5`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/147a0d57094330d4e13408ba6ae4e4162ced914d) adds `app/src/debug/**` to CI path filters.

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
- [ ] Rerun `savedDraftReopensTextLayerAndClipState` after selector updates `55c69c2` and `47b0e6f`. Run #157 still failed before preview text/editable text/32 sp checks; the next run must confirm the distinctive `00:07.02` duration is visible and then pass the remaining restore assertions.
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


### Latest handoff — 2026-10-25 (run #158)
- [Run #158](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40203831760) built and unit-tested successfully, but the emulator suite failed only in `savedDraftReopensTextLayerAndClipState`: `Could not find restored text-size label`.
- The restored clip-duration assertion passed this run, so the failure moved further into the draft round-trip test. This is currently an accessibility/viewport assertion, not proof that the stored text size is wrong.
- Test-only follow-up commit [`eeda257`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/eeda2577ec86f9371fea6a00087abc9843de6ccb) scrolls the actual vertical text-controls panel before searching for the restored size label. Await the new CI run before treating draft restoration as verified.
- Do not release an APK; version 395 remains blocked by the round-trip gate and the outstanding editor validations below.


### Latest handoff — run #159 diagnosed, test correction submitted
- Run [#159](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40204548336) built successfully but failed in the draft-round-trip test at the new scroll step.
- Root cause of that assertion: `UiScrollable.scrollTextIntoView("Yazı boyutu:")` creates an exact-text selector, while the real label includes its value (for example `Yazı boyutu: 32`). It could never match the full label.
- Test correction [`1e0f987`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/1e0f987a90d37e8547621950ce93d4edb5ae24d7) now uses `scrollIntoView(UiSelector().textContains("Yazı boyutu:"))`, preserving the actual assertion that the restored text size is 32 sp. Await CI before marking draft restoration green.


### Latest handoff — run #160 attempt 2 and follow-up
- [Run #160, attempt 1](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40205495392) hit an emulator/system launcher ANR during home screenshot capture; retrying the failed job advanced beyond that infrastructure issue.
- [Run #160, attempt 2](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40205495392/attempts/2) passed unit tests, debug APK build, fixture generation, screenshot capture and six of seven editor interaction tests. The remaining draft round-trip test now verifies the source trim duration, text overlay, and restored `32 sp` label, but failed when looking for the editable text field **after scrolling the text panel down** to the size control. The field is above the current viewport; this is a UI test ordering/visibility issue, not evidence that the text is missing from the saved payload.
- Follow-up test-only commit [`3d910eb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/3d910eb5466ae05aca2e4fa5f17088dfa9602577) checks the restored editable text field before scrolling down to verify the 32 sp label. The draft round-trip gate remains open until the next CI run passes.
