# MathMotor Video — current project status

## Latest UI rebuild — 2026-10-31

- Verified previous PR-head run set: Verify #185, APK #260 and emulator screenshots #429 all completed successfully.
- Visual review of #429 showed the test video actually decoded and appeared in the preview, correcting the earlier #427 black-frame concern for this fixture/run. However, the UI was still rejected: compact mode made timeline ruler/filmstrip lanes only 18dp high and left a large empty fixed-height card.
- Committed focused layout correction 92e9acdc02682211ff2852020fe5c1be770fcd5c: meaningful compact-screen lane heights, visible ruler on small screens, taller video filmstrip, content-sized timeline card, larger preview allocation, removal of preview +/-/reset clutter and redundant timeline help text.
- [ ] Verify Kotlin compilation/unit tests and Debug APK build on the new PR head.
- [ ] Review the new emulator screenshots on the same small-screen profile; do not accept based on green CI alone.
- [ ] Continue full CapCut-style interaction acceptance: one-finger select/drag/trim/scrub, two-finger zoom, shared playhead, contextual panels, text/audio placement, save/reopen and export parity.

PR #11 stays draft. Version 395 stays blocked; installed version 394/build 61 remains unchanged.


## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## CapCut-style layout, movable strips and shared project duration — 2026-10-31

**Latest code changes committed to PR #11; acceptance is still pending.**

- Context panel placement: moved the contextual settings ScrollView above the preview in the root layout. It starts hidden, appears when a tool/item is selected, and is hidden when the active tool is closed. The bottom dock remains at the bottom; the timeline remains in the middle.
- Layer rows: replaced visible fixed type captions on each lane with a neutral drag grip. Each existing text layer, active music item and active sound-effect item receives its own row; empty unused text/music/effect placeholder rows are no longer shown. The main video sequence stays in one continuous filmstrip.
- Vertical ordering: dragging a row's grip up/down reorders that strip. The chosen order is persisted as `timelineLaneOrder` in the existing editable project JSON (old project files without the field use the default order). All rows continue to share the same horizontal scroll position, pinch zoom and absolute time coordinate system.
- Shared project duration: `TimelineProjectDurationMath` returns the furthest end among the video sequence, explicitly timed text layers, music and sound effect. Text whose end follows the project inherits the shared end and is excluded from extending that end itself. A later-ending independent strip extends the ruler; removing/shortening the furthest item lets the ruler contract to the next furthest item. Overlaps do not add durations together.
- Music selection: the initial timeline segment now uses the full selected source in/out range. If the source extends beyond the video, the shared project duration extends accordingly; user trimming can shorten it.
- Added unit coverage for project duration growing to the furthest layer, shrinking after removal, and non-negative values.

### Current verification state

- [ ] PR-head unit tests, Kotlin compilation and Debug APK build for the new layout/duration changes.
- [ ] Inspect rerun #419 emulator screenshot output and verify that preview / middle timeline / bottom dock and optional top panel appear in the intended order.
- [ ] Verify vertical grip drag persists after project save/reopen and doesn't interfere with horizontal lane scrolling.
- [ ] Verify one-finger scrub/clip move/trim, two-finger pinch, playhead center alignment and shared time position across every reordered strip.
- [ ] Verify timeline grows/shrinks with the furthest active layer and that preview/export duration matches the ruler.
- [ ] Verify text-edit panel with software keyboard open on a small screen.

Do not mark these as device-accepted from source changes or a green build alone. PR #11 remains draft. Version 395 remains blocked; installed 394/build 61 is unchanged.

## Emulator screenshot review — 2026-10-31

- PR-head emulator workflow #427 completed successfully and uploaded screenshots.
- Screenshot review confirms the editor launches and the bottom tool dock remains visible. The captured timeline card stretched through most of the remaining screen, leaving excessive empty vertical space around the single visible clip strip. This is a visual acceptance failure despite the green workflow.
- Applied a focused layout correction in commit `a708a64877d1059373738144b5b39866cff101f3`: the timeline card now uses a compact responsive height (224 dp on compact phones, 252 dp otherwise) instead of a weighted height that expands into unused space.
- The captured preview frame is black in the screenshot; logs confirm the fixture video was imported, but a green emulator job does not establish decoded-frame visibility. This remains an open preview-render verification item.
- [ ] Re-run PR-head build/unit tests after the timeline-height change.
- [ ] Re-run emulator screenshot and inspect the updated layout.
- [ ] Confirm visible video frames after the composition player is prepared, not only that the clip metadata loaded.
- [ ] Validate multiple independent strips, gestures, text keyboard layout and save/reopen; these are not accepted by screenshot #427.

PR #11 remains draft. Version 395 remains blocked; do not change version metadata or publish a release.

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
15. Keep the tool dock fixed at the bottom. A tapped tool/item opens its contextual settings panel at the top over/above the preview; the panel must never occupy the timeline area or be inserted between timeline and bottom dock.
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
- Video clips now render in one CapCut-style VIDEO strip at their absolute project-time positions. Overlapping clips are visually stacked inside the same strip in preview/export layer order; they do not create additional VIDEO rows.
- Selecting a covered clip raises it above the other items and long-press offers an overlap-selection submenu. Filmstrip body drag moves only the selected clip; edge trim keeps source/project boundaries aligned.
- Old assignLanes() multi-row behavior and its tests have been removed. New tests assert all clips remain represented in one strip with exact start/end times and unchanged overlap/layer priority. Current change awaits PR-head CI.
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


## CI #148 diagnosis and correction — 2026-10-31

- [x] Inspected the failed workflow job log: Kotlin compilation succeeded; 43 unit tests ran and one assertion failed in `MusicSourceRangeMathTest.sourceRangeIsClampedAndRemainsOrdered`.
- [x] Identified the test-data mismatch: normalizing input `(-5, 20,000)` against a 10,000 ms source must produce `(0, 10,000)`; the old expected value `(2,000, 8,000)` was unrelated to the supplied input.
- [x] Corrected the clamp expectation and added a separate valid-range preservation assertion in commit `1ac2eff891af503fe0083b33f46d6478128ea6c6`.
- [ ] Await fresh PR-head CI for the corrected test. Do not treat the correction as verified until unit tests and Debug APK assembly pass.
- [ ] Physical-device preview/export, gestures and project persistence remain unverified. Version 395 remains blocked.


## Unified editor surface — newly confirmed by user (2026-10-31)

The user clarified the desired interaction model. This section is a requirements record only; none of these items is marked implemented or device-verified by this update.

- [ ] Merge the separately titled “Zaman Çizelgesi” and “Klip Düzenleme” areas into one coherent editor surface. Clip editing becomes contextual actions for the selected clip, not a second timeline-like panel.
- [ ] Exactly one Play/Pause control, one time readout and one vertical red playhead. Remove duplicate controls/time displays and persistent playback buttons over the video preview.
- [ ] The red playhead stays fixed at the center of the visible timeline viewport. During playback, the ruler and all timeline lanes scroll together underneath it. Dragging the timeline changes the time under the fixed playhead and updates preview.
- [ ] Video preview stays clean and unobstructed; do not place persistent playback controls over the picture.
- [ ] Two-finger pinch zoom acts on the real shared timeline. The timestamp under the midpoint between the fingers remains anchored at that screen position during zoom, subject to timeline boundaries.
- [ ] Zooming in expands the time scale and reveals finer detail; zooming out compresses it and shows a longer time span. Zoom must not alter clip/layer timestamps or durations.
- [ ] Ruler, video, text, music, sound effects, trim handles and playhead share one time-to-pixel scale and one synchronized horizontal scroll position.
- [ ] Project duration is the furthest end among all timeline items, not the sum of overlapping durations. Show time/duration once, not in a second panel.
- [ ] Keep the UI minimal: no duplicate timeline, redundant panel, decorative control row or unnecessary buttons. Show only contextual actions for the selected clip.

### Acceptance checks (not yet run)

- [ ] Confirm one Play/Pause, one time readout and one fixed center red playhead.
- [ ] Confirm all lanes and ruler move together beneath the stationary playhead during playback and timeline scrubbing.
- [ ] Confirm pinch midpoint anchoring and synchronized zoom across all lanes at multiple scales.
- [ ] Confirm “Zaman Çizelgesi” and “Klip Düzenleme” no longer appear as separate timeline-like sections.
- [ ] Confirm preview has no persistent overlay controls and project duration is based on the furthest lane end.
- [ ] Confirm small-screen and keyboard-open layout.

**Current status:** documentation only; implementation and device verification remain pending. Keep PR #11 in draft. Do not create/sign/publish version 395; installed 394/build 61 remains the baseline.


## CapCut-style video filmstrip — user-confirmed (2026-10-31)

New visual requirement; this is a documentation record, not a claim that the UI is already fixed.

- [ ] Every video clip is displayed as a continuous strip of real sequential thumbnails decoded from the source video, not a plain block, filename card or text-only clip box.
- [ ] Thumbnails fill the clip from start to end with correct frame order and no persistent black, duplicated or placeholder frames.
- [ ] Thumbnail scale/density adapts to the shared timeline zoom: closer zoom shows larger, more detailed frames; wider zoom shows a denser overview without changing actual clip duration.
- [ ] Adjacent clips have a clear seam/boundary. Clip edges expose usable trim handles; dragging the body moves the clip and dragging edges trims/extends it.
- [ ] Filmstrip remains aligned with the shared time ruler, stationary center playhead and all audio/text lanes at every zoom level.
- [ ] No separate clip-editing timeline, extra row or independent panel is introduced to support selection/trim controls.
- [ ] Device verification must cover frame sequence/timestamps, black/duplicate thumbnail avoidance, pinch zoom, horizontal scroll, body drag, edge trim and project save/reopen.

**Status:** requirement recorded; implementation and device verification remain pending. Existing 192×108 extraction and bounded cache are groundwork only, not acceptance proof. Version 395 remains blocked.


## First timeline behavior implementation — 2026-10-31

- **Implemented in code:** red playhead is now positioned at the horizontal center of the visible timeline viewport.
- **Implemented in code:** full-timeline playback and single-clip preview scroll the synchronized lanes to keep the current project time under that fixed center line.
- **Implemented in code:** manual horizontal scrolling of the primary lane maps the time under the center line back to project time and seeks the preview; synchronized lanes follow the same scroll offset.
- **Existing groundwork confirmed:** filmstrip thumbnail count is calculated from clip pixel width, so the number of decoded frames changes with zoom scale. It is not yet accepted as complete adaptive filmstrip behavior.
- **Not yet verified:** CI for the new code is running; physical-device/emulator checks for scrolling, playback-follow, touch seeking, zoom anchoring and small-screen layout remain pending.
- Keep the PR draft and version 395 blocked until the relevant CI and device acceptance checks pass.


## Anchored zoom start-boundary correction — 2026-10-31

- Previous Verify run failed at `TimelineScaleMathTest.zoomAnchorClampsAtTimelineStart`; 45 tests ran and only this test failed. Main Kotlin compilation succeeded.
- Cause: zero-time anchor plus a viewport x inside the fixed 57 px label gutter yielded a positive scroll offset.
- Fixed by returning zero scroll for a non-positive anchor timestamp in `scrollForAnchoredZoom`.
- This corrects the pure math helper only. Pinch gesture wiring and device validation are still pending. Re-run CI on the fix; keep PR #11 draft and version 395 blocked.


## Two-finger anchored timeline pinch — implementation added (2026-10-31)

- `VideoEditorActivity` now routes timeline-area multi-touch through `ScaleGestureDetector`; gestures starting outside the timeline are not treated as timeline zoom.
- Captures the time at the initial pinch midpoint and recalculates the shared scroll offset during scaling so that time follows the moving midpoint. All timeline lanes are restored to the same horizontal offset after each zoom rebuild.
- Zoom range is 0.5×–8×; clip start/end/duration values are not modified by pinch.
- Pending: CI Verify/debug APK result and real-device validation for pinch at center/edges, zoom-in/out, synchronized lanes, one-finger scrubbing and clip drag. This does not authorize a release or version 395.


## Single CapCut-style VIDEO strip — implementation added (2026-10-31)

- Replaced assignLanes() rendering with one main timeline row labelled **VIDEO**. All video clips are children of one strip canvas and are positioned by the shared time-to-pixel mapping using their stored timelineStartMs and source-trimmed duration.
- Each clip remains an actual frame filmstrip with its own boundary, body-drag behavior and left/right trim handles. No clip start, source in/out or duration is changed merely to fit the one-row UI.
- Overlapping clips are drawn in clip-list/layer order inside the same strip. The top layer matches the existing preview/export winner; overlapping ranges no longer produce extra VIDEO rows.
- The selected clip is promoted above other overlapping clips so it remains visible/editable. Long-press provides an overlap-selection submenu, including for clips fully covered by a later layer.
- Removed the obsolete lane-assignment helper and replaced multi-row unit tests with one-strip tests covering full clip retention, exact start/end positions, fully covered clips and top-layer preview selection.
- **Pending validation:** PR-head unit tests, Kotlin compilation and Debug APK build; then physical-phone checks for sequential clips, gaps, overlapping clips, filmstrip frame continuity, selection of hidden clips, body drag, both trim handles, pinch zoom and all-lane synchronization.
- Keep PR #11 draft. Do not create or publish version 395. Installed 394/build 61 remains unchanged.


## Centered playback and fixed playhead control — implementation added (2026-10-31)

- [x] Remove the playback button from the timeline title/header row; keep one Play/Pause control only.
- [x] Place the single Play/Pause control on the timeline center axis, aligned with the stationary red playhead (not over the video preview).
- [x] Keep the red vertical playhead fixed at the center time axis while the timeline lanes scroll underneath it.
- [x] Remove the visible horizontal seek slider and +/- zoom buttons; timeline scrubbing and two-finger pinch remain the intended gestures.
- [x] Reduce the visible time display to one project-position / total-duration readout.
- [ ] Confirm PR-head unit tests, Kotlin compilation and Debug APK build.
- [ ] Inspect an emulator/phone screenshot to confirm the Play/Pause button and red playhead share the exact center axis and stay fixed while playback scrolls the lanes.
- [ ] Verify play/pause icon state updates correctly during playback, pause, seek and preview rebuild.

**Status:** source implementation committed to PR #11; visual/device verification is pending. Version 395 remains blocked and the installed 394/build 61 baseline is unchanged.


## Follow-up compile verification — 2026-10-31

- [x] Corrected the invalid visibility assignment on LinearLayout.LayoutParams in commit 65180c0328197e97d229aa45f2a80df18ca9e73d.
- [x] Verify run [#175](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41179027832) passed on that PR head: unit tests, Kotlin compilation and Debug APK assembly.
- [ ] Separate APK workflow #250 and emulator screenshot workflow #419 were still running at this update; inspect their final results.
- [ ] Inspect a successful emulator screenshot and perform real UI gesture, preview/export and save/reopen acceptance.
- No physical-device acceptance is claimed. PR #11 remains draft; version 395 remains blocked; installed version 394/build 61 stays unchanged.


## CapCut mobile editor parity target — user-confirmed (2026-10-31)

- The user's acceptance target is a CapCut-like mobile editing workflow and screen structure, not merely a loosely inspired editor. Reproduce the recognizable layout and interaction model as closely as the app architecture allows; do not copy CapCut branding or proprietary assets.
- Main screen priorities: full-width video preview at the top, one shared layered timeline as the central work surface, and a persistent bottom tool dock. Selected-item settings should appear contextually above the preview rather than taking over the timeline.
- Keep one shared time ruler, one center-fixed playhead, one Play/Pause control and one time readout. All tracks scroll and zoom together; every clip/layer retains independent start/end positions unless explicitly grouped.
- The first visual defect found in source was a hard-coded 180dp PlayerView width. Commit 74829abf93ae8a16541bb0c5d057f44b0c3f159e changes the preview player to fill the preview frame width.
- This is only the first layout correction, not CapCut parity or acceptance. The rest of the screen hierarchy, bottom dock, contextual panels, gestures and editing behavior still require implementation and emulator/device review.
- Keep PR #11 draft. Do not create, sign or publish version 395 until implementation and interaction/export/persistence acceptance gates pass.
