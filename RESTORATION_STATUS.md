# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- Version 395 is blocked until every required item in README.md is implemented and verified.
- Do not bump versionCode/versionName, sign a new release APK, publish a release, or offer an update for 395 before the release gate is met and the user authorizes release.
- README checklist and source commits do not mean the behavior is fixed or included in the installed APK.
- Existing repository release notes may lag the user's installed version; use the user's confirmed 394/build 61 as the working baseline.

## Current source functionality (implementation exists; reliability still needs testing)

- Home screen with gallery import, empty-editor action, recent outputs and local drafts.
- Video import, multi-clip timeline, clip reorder/copy/delete/split, source trim start/end, playhead seek and timeline zoom.
- Trim and playhead controls use millisecond mapping; legal trim bounds consider the opposing edge and source duration.
- Video thumbnail extraction uses timestamped platform-frame APIs first, then Media3 for the requested timestamp.
- Text layer timing, position, size, colour and bold settings; background music and one timed sound effect; original audio volume.
- MP4 export with source / 720p / 1080p options via Media3 Transformer.
- JSON project save/load and local draft autosave, including playhead time, zoom and horizontal scroll.
- Responsive preview/timeline layout, reduced keyboard mode and compact dock.

## User-reported problems for the next release (all remain open)

See the detailed, release-blocking checklist in README.md. The active list is:
1. One-finger timeline gestures and two-finger zoom are not usable/reliable.
2. Timeline appears locked to the left and cannot be moved freely.
3. Timeline filmstrip thumbnails are too small and too sparse.
4. Thumbnails repeat the same frame and initially show black before resolving.
5. The red playhead does not behave like CapCut's playhead; time, preview, playback and split position must stay aligned.
6. Text start/end timing cannot be set reliably and text size is not adjustable as expected.
7. A separate project-duration control is confusing or conflicts with actual timeline duration; use one unified timeline track/ruler and one source of truth for total duration.
8. Timeline ruler labels must automatically adapt to pinch zoom: coarser minute/large-interval labels when zoomed out, seconds when zoomed in, and finer second spacing at close zoom.
9. Undo/Redo may not work; current known implementation records clip edits only and does not include text/audio in one global history.
10. Home screen has redundant explanatory copy/menus and needs a simpler professional layout.
11. Scissors icons are overused; cut/split/trim/delete actions need distinct, restrained controls.
12. The redundant bottom timeline strip (duration + green slider + +/- controls) should be removed; keep one unified timeline/ruler, show time once, and zoom with two-finger pinch gestures.
13. Remove "Klip 1" / "Klip 2" labels and give the reclaimed space to a larger video preview; retain clip selection and boundaries without the labels.
14. Remove timeline navigation/scroll/zoom arrow buttons entirely; use direct touch gestures (one-finger drag/swipe, two-finger pinch, tap-to-select and draggable trim handles) rather than arrow buttons. Keep only functional affordances such as the playhead and trim handles.
15. Bottom tool navigation should swap the four-item dock for the selected tool's controls in the same bottom panel, then restore the four-item dock on back/confirm; tool panels must not hide underneath the preview.
16. Timeline thumbnails need more visible width, clearer frame content and faster prioritized loading/caching.
17. Simplify the two-video join/add-clip area visually while preserving the actual add/order/join behavior.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.



## Newly confirmed editor requirements (2026-10-31)

- User confirmed that all timeline content must be independent and freely positionable: video clips, text layers, music and sound effects each need separate start/end positions. Moving or trimming one item must not alter another unless the user explicitly groups/links them.
- User confirmed the editor screen should be simplified at the same time: remove “Video 1/Video 2” / “Klip 1/Klip 2” text headers and redundant visual clutter; preserve clip boundaries and selection without those labels.
- Video thumbnails should form a clear, continuous filmstrip with a visible join seam; thumbnail extraction should prioritize visible frames and avoid delayed/black/repeated frames.
- These are newly confirmed requirements, not completed work. No source behavior is marked fixed by this status update.

## Verification gaps

- Unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- CI currently builds and captures screenshots, but does not fully exercise timeline gestures, all text/audio edits, global Undo/Redo, export, or save/reopen end-to-end.
- Test export with multiple clips, trim boundaries, timed text, music and sound effects. Verify output duration, frame content and audio sync; confirm the original input is unchanged.
- Test project save/reopen and verify clips, text, audio, duration, zoom and scroll position.
- Run an interactive Android emulator test and review logs/screenshots. Perform a physical-device smoke test when possible.
- Leave every checkbox in README.md unchecked until the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, report that limitation.

## Release gate

**Version 395 must not be created or published until all required fixes and validations in README.md pass.** A successful build alone is not acceptance. Keep the source version metadata unchanged during this repair phase.

## Recovery background

Some older physics/scenario-related source and tests remain as reconstruction leftovers; they are outside the active video-editing workflow. The original complete V7 source tree was not recoverable.

## PR #11 follow-up — 2026-10-31

- [x] Hide the redundant clip title/card strip without deleting clip state.
- [x] Decode filmstrip frames at 192×108 and set the Media3 fallback output height to 108px.
- [x] Bound thumbnail cache by 16 MiB of bitmap allocation size.
- [x] Keep default filmstrip density near one frame per second at 22 dp/s with 24 dp slots.
- [x] Implement sound-effect body drag and two-edge trim; left trim preserves the source-file in-point.
- [x] Save/restore sound-effect source duration, source in-point, timeline start and segment duration; keep older project files compatible.
- [x] Implement music timeline start/duration with independent body drag and left/right trim.
- [x] Synchronize music preview to its project-time window and keep it silent outside that range.
- [x] Export music only inside its timeline window, repeating the source as needed and adding silence before/after the item.
- [x] Save/restore music source duration and project-time range; old project files default to full-video music duration.
- [x] Add pure unit tests for sound-effect source trim and music timeline move/trim boundaries.
- [x] Enable PR-triggered verification workflow; commit `1ea29d100a1c01cdfa801f9d520ee0aa446ff4c1` passed unit tests, Kotlin compilation and Debug APK assembly.
- [x] Latest PR #11 head `23c67766983eb53527b005ed7546fb26c855a9aa` passed CI run [#140](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41167186839): unit tests (including lane/layer-priority tests), Kotlin compilation and Debug APK assembly.
- [ ] Verify music/sound-effect drag and trim gestures plus live audio sync on an actual phone or reliable emulator.
- [ ] Verify exported audio start/end, repeat behavior, silence outside the segment and source-video preservation in a real MP4.
- [ ] Verify project save/reopen retains edited music/effect positions and durations.
- [ ] Verify independently positioned/trimmable video clips on-device. The implementation now stores independent positions, supports body drag and edge trim, persists positions, and flattens overlaps for preview/export; automated/device acceptance is still pending.
- [x] Implement source-in/source-out selection for music, separate from its project-timeline range; automated and device validation remains pending.
- [ ] Verify overlapping layers in both live preview and MP4 export.
- [ ] Verify thumbnail sharpness, distinct timestamps, load priority and memory behavior on a physical device.
- [ ] Complete full interaction, small-screen and keyboard tests.

**Release gate:** version 395 remains blocked. A green unit-test/Debug build does not replace device, interaction, preview/export and project-persistence acceptance.
## Independent video timeline — implementation added, verification pending (2026-10-31)

- Each video clip stores an independent project start separate from source in/out trim.
- New project JSON persists positions; old version-1 files without the field restore sequentially.
- Overlapping clips render on separate rows. Filmstrip body drag moves only one clip; edge trim keeps source/project boundaries aligned.
- Timeline row assignment now follows the same layer priority as preview/export: the highest-index overlapping clip is placed in the uppermost available row; non-overlapping clips can share a row. Two unit tests cover this contract; PR-head CI #140 passed.
- Preview and Transformer export share one flattened stream. Highest layer order wins overlap intervals; empty intervals are black/silent.
- Current overlap behavior is top-layer occlusion, not blending, crossfade or picture-in-picture.
- CI and emulator/phone validation of drag/trim, overlap preview/export, MP4 correctness and save/reopen remain required.

## Multi-track technical constraint — checked 2026-10-31

- App dependencies use Media3 1.10.1.
- Transformer Composition supports combining separate sequences with overlapping media items, including video/audio layering.
- CompositionPlayer is documented as early/experimental. Supported preview cases include one video sequence plus one audio sequence; multi-video-sequence preview remains active development.
- Do not declare multi-video overlap complete until the same arrangement is verified in both live preview and final export.


## Music source in/out — implementation added, verification pending (2026-10-31)

- [x] Add separate music source-file in/out state without conflating it with project-timeline position/duration.
- [x] Add audio-panel controls for selecting the source-file start and end.
- [x] Persist source in/out in project JSON; older projects default to the complete source file.
- [x] Make live music preview seek/loop only inside the selected source range.
- [x] Make MP4 export repeat only the selected source range, with silence outside the music item's timeline window.
- [x] Add unit tests for source-range normalization, minimum span, boundaries and looping.
- [ ] Confirm latest PR-head CI run #147 passes unit tests and Debug APK assembly.
- [ ] Verify source-range controls and preview audio on a physical phone or reliable emulator.
- [ ] Verify exported MP4 uses the selected source in/out and project save/reopen preserves it.
- [ ] Keep version 395 blocked until all device, interaction, export and persistence acceptance checks are complete.

**Current limitation:** implementation has been committed to PR #11, but CI #147 is still running and no physical-device validation has been performed. This is not a signed release or an installable OTA update.
