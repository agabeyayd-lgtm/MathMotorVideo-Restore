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
- [x] Implement sound-effect body drag to move the item without changing other timeline items.
- [x] Implement sound-effect left/right trim; left trim preserves the correct source-file in-point.
- [x] Save/restore sound-effect source duration, source in-point, timeline start and segment duration while keeping old JSON projects compatible.
- [x] Add pure unit tests for audio move/trim and boundary normalization.
- [x] Enable PR-triggered verification workflow; latest checked commit `7e75c016b7d01c4e1e7863a92ff72bfaa6bbc134` passed unit tests and Debug compilation/package.
- [ ] Verify sound-effect drag/trim gestures and live audio sync on an actual phone or reliable emulator.
- [ ] Make music independently movable and trimmable with its own start/end.
- [ ] Replace sequential-only video clip positioning with independently movable timeline items.
- [ ] Verify overlapping arrangements in both live preview and MP4 export.
- [ ] Verify thumbnail sharpness, distinct timestamps, load priority and memory behavior on a physical device.
- [ ] Verify full project save/reopen, audio sync and MP4 output with independently positioned layers.

**Release gate:** version 395 remains blocked. A green unit-test/Debug build is necessary but does not replace device, interaction, preview/export and project-persistence acceptance.

## Multi-track technical constraint — checked 2026-10-31

- App dependencies use Media3 1.10.1.
- Transformer Composition supports combining separate sequences with overlapping media items, including video/audio layering.
- CompositionPlayer is documented as early/experimental. Supported preview cases include one video sequence plus one audio sequence; multi-video-sequence preview remains active development.
- Do not declare multi-video overlap complete until the same arrangement is verified in both live preview and final export.
