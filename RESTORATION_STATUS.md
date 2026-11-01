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
18. The filmstrip shows a gray rail between adjacent thumbnails; source inspection traced it to a 1 dp right margin. A candidate source fix removes the gap, but device verification is still required.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.

## Focused timeline finding — 2026-11-01

- Candidate branch `fix/timeline-filmstrip-thumbnail-separators` removes the per-thumbnail 1 dp right margin and aligns the filmstrip backing color with the thumbnail placeholder color. This is source-level only until a build and actual-device check confirm the gray rail is gone.
- In the inspected source, the `00:00` label and timeline zoom `+`/`−` controls sit outside the horizontal timeline scroll views. Their reported horizontal movement remains unverified and must not be marked fixed based on source inspection alone.
- No version bump or version 395 release work is permitted as part of this repair.

## Fixed timeline controls — source change pending verification

- The ruler's `00:00` anchor is now rendered in the fixed left lane label; the scrolling ruler content begins at the 5-second position so the time scale remains aligned with the video filmstrip.
- The video-add `＋` action remains a child of the fixed timeline header, outside the horizontal clip list and timeline lane scrollers. A source comment records this layout constraint.
- The no-gap filmstrip change, fixed zero anchor, and fixed add-video control are source changes on `fix/timeline-filmstrip-thumbnail-separators`; none is marked complete until a successful build and visual/device verification.

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


## User phone comparison — new CapCut parity targets (2026-11-01)

Reference: the two user-supplied screenshots captured with both editors' timelines expanded to a comparable scale. The first is MathMotor; the second is CapCut.

Newly observed gaps to implement and verify:

1. **Pinch/ruler separation:** current MathMotor pinch affects both the time scale and filmstrip presentation. Target behavior is to adjust the time ruler/scale without changing filmstrip height, thumbnail tile geometry, or thumbnail density. Keep clip timestamps and ruler-to-filmstrip alignment correct; never change actual clip duration or trim bounds during zoom.
2. **Duration placement:** move the current-time / total-duration readout from the playback-button row to directly above the timeline ruler, as in CapCut. Keep one authoritative readout.
3. **Ruler detail:** ruler marks and labels must become more precise as zoom increases, including frame subdivisions (2f/4f/6f where supported by the clip frame rate); retain readable seconds/major ticks at wider scales.
4. **Fixed playhead/header:** playhead remains fixed in the viewport while timeline content moves beneath it. Zero-time anchor and fixed controls must not move with the horizontal filmstrip.
5. **Filmstrip consistency:** thumbnail strip keeps stable height and tile geometry across zoom levels, with clear sequential frames and no stretching/reflow caused by pinch.
6. **Workspace hierarchy:** keep preview above, ruler/video strip and independent audio/text lanes in the middle, compact contextual tools and bottom dock below. Reduce oversized action-panel occupation and unused space without sacrificing touch targets or preview aspect ratio.
7. **Preview:** compare preview scale/black unused area against CapCut on the physical phone; preserve source aspect ratio and do not crop to imitate the reference.

### Focused validation for these targets

- Compare the same clip at equivalent timeline zoom on the physical phone.
- Capture minimum/middle/maximum zoom before and after; prove ruler scale changes while filmstrip height/tile geometry remain stable.
- Verify pinch does not change clip start/end, trim bounds, actual duration, total duration, playhead timestamp, preview frame or exported duration.
- Verify duration readout is above the ruler and remains synchronized while scrubbing/playing/seeking.
- Verify frame-level ruler subdivisions against the source frame rate.
- Add only targeted tests for uncovered behavior; do not repeat unchanged green interaction, thumbnail or seek tests.

These are **open targets**, not completed fixes. Existing emulator/CI passes remain limited to their recorded scenarios and do not constitute physical-phone acceptance.

### Work boundary

- Active candidate for the current filmstrip separator repair: `fix/timeline-filmstrip-thumbnail-separators`, draft PR #12. Keep PR #12 draft/unmerged while these targets are implemented and verified.
- Installed baseline remains versionCode 394 / build 61. Do not create version 395, bump version metadata, publish/sign a release, or activate the updater until all required gates pass and the user explicitly authorizes it.

