# MathMotor Video

## Mobile editor rebuild — 2026-10-31

- The latest PR-head checks before this change were green: Verify #185, APK #260 and emulator screenshots #429. Screenshot review confirmed the imported test video is visible in the preview, but the timeline was visually unacceptable on a small phone: compact mode reduced the ruler/filmstrip to tiny 18dp rows and left a large empty card.
- New source correction: commit 92e9acdc02682211ff2852020fe5c1be770fcd5c. Compact phones now keep a visible ruler and substantially taller video/text strips; the timeline card height follows its actual track content instead of reserving a fixed empty panel. The preview stage gets more vertical room, redundant preview size buttons and timeline instructions were removed, and the status line was compacted.
- This is a targeted first layout pass, not CapCut acceptance. The new PR-head build and screenshots must pass; then inspect the updated screen and continue with the full one-finger/two-finger interactions, item selection, trim and contextual controls. No release/version changes.


## Latest emulator review — 2026-10-31

- Emulator screenshot workflow #427 passed and produced screenshots, but visual review found the timeline card consuming too much vertical space and a black preview frame. A targeted compact-height correction was committed as `a708a64877d1059373738144b5b39866cff101f3`.
- This is a source-level layout adjustment only. Await the new PR-head build and emulator screenshot before marking it visually verified. The black preview frame and all touch/keyboard/save-export acceptance checks remain open.
- Keep PR #11 draft and version 395 blocked.

MathMotor Video is a local-first Android video editor with a CapCut-inspired workflow. Normal editing runs on-device; AI generation, AI scoring and paid cloud APIs are outside the current product scope.

## Editing flow

1. Choose one video from the phone, or open an empty editor.
2. Use the timeline to add clips, select/reorder them, trim their start/end, split clips and move clips earlier or later.
3. Add text layers with timing, position, size, colour and style controls.
4. Add background music and/or a sound effect; adjust levels and effect start time.
5. Preview the result, choose source resolution, 720p or 1080p, and export an MP4 to Movies/MathMotorVideo.
6. Save/reopen an editable JSON project; local draft autosave stores the playhead, zoom and timeline scroll for resume.

The editor uses Android's document picker and AndroidX Media3 for playback/export. Video files are processed locally; no account or cloud upload is required for normal editing.

## Release baseline and version 395 gate

- User-confirmed installed baseline: **versionCode 394, release/build 61 (app-v8.3-61)**.
- **Version 395 must not be created, signed, published, or offered for update until every required fix below is implemented and verified.**
- README checklist updates and source commits do not themselves mean a feature is fixed or included in an APK.
- Do not bump `versionCode` or `versionName` during this work until the release gate is met and the user explicitly authorizes the release.
- A successful source build is not the same as a tested, published APK. Keep the original media safe; export to a new file.

## Progress reality check — 2026-10-28

**Honest assessment: recent work has not yet produced a newly verified, user-facing improvement that we can confidently call finished.** The project is not starting from zero: editing/export code and earlier focused checks exist. But repeated CI/emulator failures mean we have spent too much time cycling through test failures without closing the main phone-use problems. From the user's perspective, progress has therefore felt stalled; that concern is justified.

### What the latest runs actually prove

- Run [#269 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40680915280) on `fix/editor-395-gate`, head `8c78d8e`: unit tests, Debug APK build, and all **19 Android instrumentation tests** passed; emulator screenshots/diagnostics were uploaded as [artifact 12813448192](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40680915280/artifacts/12813448192). This is real automated interaction-test progress on the candidate branch, but it does not by itself prove the interface visually matches CapCut or replace a physical-phone review.
- Run [#270 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40682611758) on separate branch `work/focused-instrumentation-gates`: unit tests and Debug APK build passed, but emulator interaction failed with `androidx.test.uiautomator.StaleObjectException`. It is a test failure to investigate on that branch, not a release-candidate failure and not proof by itself that an editor feature is broken or fixed.
- Keep the distinction clear: #269 is a successful candidate-branch automated test run; visual CapCut resemblance and physical-device usability remain unverified. Version 395 remains blocked.

### How progress will be counted from here

- Report three states separately: **implemented in source**, **automated test passed**, and **verified in actual device use**.
- A test-harness fix or a passing build is engineering progress, but it does not close a user-facing editor requirement.
- Before rerunning a red test, inspect its failing assertion/log and compare with prior failures. Fix the identified cause first; avoid rerunning the same unchanged failure just to get another result.
- After each fix, record the exact behavior changed, the specific test evidence, and what remains unverified. Do not mark a feature done from screenshots or source presence alone.
- The next meaningful milestone is not “more green CI”: it is a repeatable, evidenced interaction/export/save-reopen flow that closes at least one of the open user-facing blockers listed below.
- Version 395 remains blocked. No version bump, signed release APK, publication, or update offer until the release gate passes and the user authorizes it.

## Current baseline capabilities (present in source; not all are proven reliable)

- [x] Multi-clip timeline and clip selection/reordering controls exist.
- [x] Clip trim/split controls and timeline time/pixel mapping logic exist.
- [x] Text layer controls for timing and appearance exist in the source.
- [x] Background music/sound-effect controls exist in the source.
- [x] Editable JSON project save/reopen and local draft autosave are implemented in the source.
- [x] MP4 export path exists in the source.
- [ ] These checkmarks describe existing code paths only; they do **not** certify that the current build behaves correctly on a device. See the required verification checklist below.

## Required work for the next release (395) — NOT DONE until verified

### Timeline touch, scrolling and playhead

- [ ] Make one-finger interactions reliable: select clips, scroll the timeline and drag trim handles without gesture conflicts.
- [ ] Implement reliable two-finger timeline zoom; keep the time under the gesture anchor as stable as possible.
- [ ] Fix the timeline appearing locked to the left; allow moving across the full timeline in both directions and reaching the beginning/end after zooming.
- [ ] Make the playhead behave like CapCut: timeline moves beneath a stationary playhead during scrubbing; preview follows the exact time; playback stays synchronized; split uses the playhead time.
- [ ] Keep time-to-pixel mapping correct after scroll, zoom, trim, clip reorder and multi-clip edits.
- [ ] Keep one unified timeline ruler and time-to-pixel scale. The project duration is the furthest end among the main video sequence and every active independent layer (text, music, sound/effect), not the sum of overlapping durations. Extending the longest strip extends the ruler; shortening/removing it lets the ruler shrink to the next furthest end.
- [ ] Make ruler labels adapt automatically to pinch zoom: show coarser minute/large-interval marks when zoomed out, switch to seconds as the user zooms in, and increase second-level spacing/detail at closer zoom levels. Keep labels readable, evenly spaced and synchronized with the actual time-to-pixel scale; do not require manual unit selection.
- [ ] Remove or simplify the separate “project duration” UI if it duplicates/conflicts with the actual timeline; preserve any technical export limit only if genuinely needed and explain it clearly.

### Shared timeline coordinate system for every editing layer

- [ ] Use the single main timeline ruler and one shared time-to-pixel coordinate system for video clips, text overlays, music, sound effects, playhead and trim boundaries. Do not give text/audio separate rulers or independent zoom scales.
- [ ] When the user scrolls or pinches to zoom, every visible layer must remain aligned to the same timestamps; existing start/end times must not drift or change just because the zoom level changed.
- [ ] Text and audio start/end points must be directly adjustable on their own lanes against the shared ruler, with accurate snapping/placement where supported. Preview and playback must show each layer only during its defined time range.
- [ ] Verify shared alignment at multiple zoom levels, after scrolling/seeking, clip trim/split/reorder, and after saving/reopening a project.

### Thumbnails

- [ ] Increase thumbnail height/visibility and use enough thumbnails to visually cover the clip; for a 10-second clip, aim for a dense, evenly spaced filmstrip comparable to common mobile editors, not a fixed arbitrary count.
- [ ] Increase the visible width/size of timeline thumbnails so the action in each frame is understandable; prioritize the visible viewport and avoid overly narrow frame tiles.
- [ ] Improve thumbnail generation/loading latency: prioritize frames currently on screen, prepare nearby frames next, cache results and keep scrolling/gestures responsive while extraction continues.
- [ ] Ensure each thumbnail is extracted from its own timestamp so consecutive thumbnails do not all show the same frame.
- [ ] Reduce black placeholders and loading delay; show a useful loading state and replace placeholders promptly as frames become available.
- [ ] Cache/reuse extracted frames and avoid blocking timeline scrolling or editing while thumbnails are prepared.

### Text, audio and undo/redo

- [ ] Make text start/end time editable and ensure the text timeline strip can be trimmed at both ends.
- [ ] Make text size reliably adjustable; preview updates immediately and exported output matches the preview.
- [ ] Verify text timing, placement, size and style remain correct after seeking, zooming, saving/reopening and exporting.
- [ ] Verify Undo/Redo works for all supported edit operations, including text add/delete/timing/size, audio/music changes, clip trim/split/reorder/move; do not show a control for an operation that is not actually undoable.
- [ ] Ensure undo/redo restores both timeline state and preview consistently.

### UI and controls

- [ ] Remove the entire separate bottom timeline-control strip that contains the duplicate duration readout, green zoom slider and +/- zoom buttons; keep time information in one clear timeline location.
- [ ] Implement CapCut-style pinch gestures: two fingers spread to zoom in and pinch together to zoom out. Zoom must operate on the actual timeline/filmstrip, preserve a sensible gesture anchor and remain smooth; do not rely on a visible zoom slider or +/- buttons.
- [ ] Remove the visible "Klip 1" / "Klip 2" labels from the timeline; use the reclaimed space to enlarge the video preview area where possible. Preserve clear clip boundaries and clip selection without those labels.
- [ ] Remove timeline navigation/scroll/zoom arrow buttons entirely. Follow CapCut's direct-touch approach: one-finger drag/swipe for timeline interaction, pinch with two fingers to zoom, tap to select clips, and drag trim handles. Keep only functional visual affordances such as the playhead and trim handles; do not replace the arrows with another row of navigation buttons.
- [ ] Keep controls compact and balanced while retaining accessible touch targets and all existing actions.
- [ ] Keep the compact tool dock anchored at the bottom. Tapping a tool or timeline item opens only its contextual settings panel at the top, over/above the preview region; it must not insert a panel between the timeline and bottom dock. Closing the panel restores the clean preview at the top, shared timeline in the middle and dock at the bottom.
- [ ] Make the two-video join/add-clip area visually clear and uncluttered after removing timeline arrow buttons and clip labels; do not remove or change the underlying add, order or join functionality.
- [ ] Give the preview the space reclaimed from redundant labels and oversized controls; keep the preview and timeline responsive on small screens.

- [ ] Simplify the home screen: remove redundant menus and explanatory clutter; prioritize choosing/opening a video and entering the editor.
- [ ] Make the home screen and editor responsive to small screens, available height and the software keyboard; controls must remain reachable and not overlap.
- [ ] Remove redundant scissors icons. Reserve scissors for cut/split actions; make trim, split, delete and other actions visually distinct.
- [ ] Keep controls concise, consistently placed, accessible and functional rather than duplicating the same action in multiple places.

### Required validation before version 395

- [ ] Run unit tests for timeline mapping, trim bounds, playhead-to-clip mapping and text timing.
- [ ] Run an interactive Android emulator test covering one-finger scroll/trim, two-finger zoom, playhead scrubbing/sync, thumbnail variety/loading, text timing/size, undo/redo and home-screen navigation.
- [ ] Test with a 10-second video and a multi-clip sequence; check the beginning, middle and end of the timeline at multiple zoom levels.
- [ ] Export an MP4 and verify video, text, audio, duration and playhead cut position; confirm the original input video is unchanged.
- [ ] Save a project, close/reopen it and verify clips, text, audio, duration, zoom and timeline position.
- [ ] Review emulator screenshots and logs; fix regressions and rerun failed checks.
- [ ] Perform a final physical-device smoke test when possible.
- [ ] Only after every required item is complete and verified, prepare version 395. Until then, do not bump the app version or publish an APK.



### Independent timeline elements and simplified layout — user-confirmed

- [ ] Remove the visible per-clip header labels (including “Video 1” / “Video 2” or “Klip 1” / “Klip 2”); retain clip boundaries and selection visually without text headers.
- [ ] Keep the timeline visually minimal: remove redundant explanatory labels, duplicate duration/zoom controls and decorative controls; retain only controls needed for the current selection.
- [ ] Make every timeline element independently movable: video, each text layer, music and each sound/effect item has its own horizontal strip and independently editable start/end times. Strips are not locked to permanent 'text', 'music' or 'effect' row positions: the user can drag a strip vertically to reorder where it sits, and drag it horizontally to set its project-time position.
- [ ] Moving, trimming, selecting or deleting one element must not move or modify other elements. Only an explicit multi-select/group/link action may affect multiple elements.
- [ ] Allow timeline elements to overlap in time; all lanes share the same ruler, zoom and horizontal scroll coordinate system.
- [ ] Keep clip thumbnails visible as a continuous filmstrip across the clip; show a clear seam between adjacent clips without separate “Video 1/2” headers.
- [ ] Keep editing controls contextual: show only the selected element's relevant actions; return to a compact tool dock when selection is cleared or the tool is closed.
- [ ] Verify independent placement, overlap, trim and save/reopen for two video clips, two text layers, music and a sound effect. Confirm moving one item leaves all others unchanged.

## CapCut-style editor layout and layer ordering — implementation added to PR #11 (2026-10-31)

- The contextual tool panel has been moved above the preview region and is hidden until a tool/item is tapped. The bottom tool dock remains at the bottom; the timeline remains in the middle.
- Timeline strips now use neutral drag handles rather than permanent visible type headings. The user can drag a strip vertically to change its order; its order is saved in the editable project JSON. Every strip still uses the same synchronized horizontal ruler and time scale.
- Empty placeholder rows for unused text/music/effect tools are removed. A strip appears when its corresponding item exists; text layers each have an independent strip, and the main video sequence remains one continuous filmstrip.
- Project duration is calculated from the furthest end of the video sequence, explicitly timed text layers, music and sound effect. Text whose end is set to follow the project inherits the shared end and cannot keep a removed/shortened longer item alive. Extending the furthest independent item extends the ruler; shortening/removing it lets the ruler shrink to the next furthest item.
- Selecting a music source initially uses its full source range as the timeline item duration, so a source longer than the current video extends the project ruler until the user trims it.
- Added pure unit tests for shared project-duration expansion, shrinkage and non-negative bounds.
- **Not yet device-accepted:** touch-driven vertical strip reorder, one-finger horizontal movement/scrub, two-finger pinch, panel placement with keyboard open, synchronized preview/export and save/reopen still require the current PR-head CI and emulator/phone review.

## Verification boundaries

- Existing unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- The current emulator screenshot workflow builds a debug APK and captures screens, but its script does not by itself prove all touch gestures, exports, Undo/Redo or save/reopen flows work. Add/run interaction-level tests rather than treating screenshots alone as acceptance.
- Record a checkbox as complete only after the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, leave the item unchecked and say so.
- This repository is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree.

## Latest editor work — 2026-10-31 (PR #11, draft)

- The redundant clip title/card strip is hidden; clip state and selection logic are retained.
- Filmstrip decoding requests **192×108** directly, with the Media3 fallback set to 108px height.
- Thumbnail caching is bounded by **16 MiB of bitmap allocation memory**; default filmstrip density is approximately one frame per second.
- Sound-effect blocks can be moved and trimmed from both edges. The left edge tracks a separate source-file in-point; this range is used by preview, export and JSON persistence. Older project files default the source in-point to zero.
- Music now has its own project-timeline start and duration. Its block can be moved by the body and shortened/extended from either edge without moving other tracks. Preview pauses outside that range; export repeats the source music only inside the chosen range and fills the rest of the project with silence.
- Music source duration and timeline start/duration are saved in the project JSON. Older projects without these fields default to music covering the full video timeline, preserving the earlier behavior.
- Added pure timing tests for sound-effect source trims and music timeline move/trim boundaries.

### Verification

- PR check #137 for the preceding head `20dd0757dff7f85bfb47acc1578a5bae5ab5bef3` **passed**: `testDebugUnitTest`, Kotlin compilation, `packageDebug` and `assembleDebug`.
- Latest PR-head check [#140 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41167186839) on `23c67766983eb53527b005ed7546fb26c855a9aa` passed the unit-test and Debug APK build job, including the new video lane/layer-priority tests.
- The workflow has one job (`test-and-build`); all steps completed successfully. It did not upload an APK artifact. No signed release or version 395 was created.
- This is source/unit-test/build verification only. Device-level touch gestures, actual audio/video preview and MP4 export, project save/reopen, and audio sync remain unverified on a physical phone or reliable emulator.

### Still open

- Independent video project positions, body drag, edge trim and persistence are implemented in PR #11. The video timeline is now being converted to one CapCut-style VIDEO strip; build/test and real-device gesture, preview/export and save/reopen validation remain required.
- Music source in/out selection is now implemented separately from project-timeline placement; CI and device/export validation are pending.
- Verify overlap and exact synchronization across all items in the shared timeline, live preview, MP4 export and saved/reopened projects.
- Test thumbnail clarity/load behavior, actual finger gestures, audio sync, small screens and keyboard behavior on a device.
### Independent video timeline — implementation added, verification pending (2026-10-31)

- Every video clip stores a project-timeline start separately from source in/out. Existing version-1 projects without this field load sequentially; new saves persist each position.
- Video clips retain independent shared-timeline start/end positions. The former row-assignment renderer has been replaced by one VIDEO strip: each real-frame filmstrip is positioned at its absolute project-time x-coordinate inside a single strip canvas. Overlaps are stacked within this one lane in the same layer order as preview/export; no additional VIDEO rows are created.
- The selected clip is raised above overlapping clips so its frame and trim handles remain editable. Long-pressing an overlapping clip exposes an “Örtüşen klibi seç” submenu for selecting a covered clip without adding another row. Body drag and edge trim continue to update only the chosen clip.
- Preview and export share one flattened video sequence: the last clip in layer order is visible during overlap and empty intervals become black/silent. Overlap remains top-layer occlusion, not alpha blending, crossfade or picture-in-picture.
- Touch, overlapping preview/export, actual MP4 output and save/reopen still require device/emulator verification. Version 395 remains blocked.

### Multi-track implementation constraint — Media3 1.10.1

- Transformer Composition can combine separate sequences that overlap in time, including video/audio layers.
- The current preview passes a single flattened video sequence to CompositionPlayer; overlapping clips are resolved to one active top-layer segment before preview rather than passed as multiple video sequences. This avoids relying on CompositionPlayer's still-developing multi-video-sequence preview path.
- This does not prove real-time touch/seek behavior or MP4 correctness. Both preview and final export still require device/emulator verification before acceptance.


### Music source trim — implementation added, verification pending (2026-10-31)

- Added independent music-file source in/out points, separate from the music item's project-timeline start and duration.
- Added source-range controls in the audio panel. The selected source range is normalized to the file duration and kept at a minimum non-empty span.
- Preview seeks and loops only inside the selected source range while the music item is active on the project timeline.
- Transformer export clips repeated music chunks to the selected source in/out range; timeline gaps before and after the music item remain silent.
- New project JSON stores the source in/out values. Older projects without these keys default to the full source file.
- Added pure unit tests for source range normalization, boundary handling and selected-range looping.
- CI run #146 checked the implementation commit; latest documentation-updated PR head is being checked by run #147 (pending). Do not mark tests passed until GitHub reports success.
- Physical-device checks of both source-range controls, preview seeking, exported MP4 audio and save/reopen remain required. Version 395 remains blocked.


### CI failure diagnosis and targeted correction — 2026-10-31

- PR-head CI run #148 failed in `testDebugUnitTest`: 43 tests completed, 1 failed. Kotlin compilation completed successfully; the failure was not an emulator or APK packaging error.
- Failing test: `MusicSourceRangeMathTest.sourceRangeIsClampedAndRemainsOrdered`. The test supplied an invalid range `(-5, 20,000)` for a 10,000 ms source but incorrectly expected the unrelated range `(2,000, 8,000)`.
- Corrected the test to expect the actual clamp result `(0, 10,000)` and added a separate assertion that a valid `(2,000, 8,000)` range remains unchanged.
- Correction commit: `1ac2eff891af503fe0083b33f46d6478128ea6c6`. The corrected PR head still requires a fresh CI result; do not mark verification passed until it completes successfully.
- No production timing implementation was changed for this failure. Version 395 remains blocked.


## Unified CapCut-style editor surface — user-confirmed requirements (2026-10-31)

These are new release-blocking UI/interaction requirements. They are recorded as **not yet implemented or device-verified**; do not mark them complete based on this documentation entry.

### One timeline, one playhead, one control set

- [ ] Replace the visually separated “Zaman Çizelgesi” and “Klip Düzenleme” sections with one coherent editor/timeline surface. “Klip Düzenleme” must be contextual actions for the currently selected clip, not a second timeline-like section or independent editing workspace.
- [ ] Keep exactly one Play/Pause control for the editor preview. Remove duplicate play controls and playback controls placed over the video preview.
- [ ] Keep exactly one time readout in the editor. Remove duplicate duration/progress/time displays and independent secondary seek controls.
- [ ] Use exactly one vertical red playhead, fixed at the horizontal center of the visible timeline viewport. Do not move the red line across the screen during playback or scrubbing.
- [ ] During playback, scroll the shared time ruler and every timeline lane horizontally underneath the stationary center playhead. The time under the playhead and the video preview must stay synchronized.
- [ ] When the user drags the timeline, keep the playhead fixed and update the current preview time from the ruler position under that line. All lanes must move together.
- [ ] Keep video preview visually clean and unobstructed: no persistent play/pause, seek, or duplicate timeline controls over the picture. Playback is controlled by the single editor Play/Pause control.
- [ ] Keep the editor visually restrained. Do not add a control, label, button row or separate panel unless it serves a distinct necessary editing action. Show clip actions contextually for the selected item.

### Pinch zoom and adaptive time ruler

- [ ] Implement two-finger pinch zoom on the actual shared timeline. The midpoint between the two fingers is the zoom anchor: the timestamp under that midpoint must remain at that same screen position while zooming in or out, subject only to timeline-boundary clamping.
- [ ] Pinch out expands horizontal time scale and reveals finer time detail; pinch in compresses it and shows a longer time range. Zoom changes visual scale only and must not change clip/layer timestamps or durations.
- [ ] The ruler labels and tick spacing must adapt to the current scale. The visible timeline span must expand when zoomed out and contract when zoomed in; it must not be a fixed-width/fixed-interval display.
- [ ] Preserve the single centered red playhead while pinch zooming. The gesture anchor and fixed playhead are distinct: zoom preserves the time under the finger midpoint, while the red playhead remains at viewport center.

### One shared timeline coordinate system

- [ ] Video clips, text, music, sound effects, trim handles, ruler and playhead must use one shared time-to-pixel mapping and synchronized horizontal scroll position.
- [ ] Every lane follows the same zoom factor and scroll offset. No independent timeline/ruler/zoom control for “Klip Düzenleme”, audio or text.
- [ ] Project duration is derived from the furthest end time of all timeline items (video, text, music and sound effects), not from adding overlapping durations. Show this project duration only in the single time display; do not add a second duration panel.
- [ ] Keep the existing independent-item behavior: moving or trimming one item must not move or modify another item unless the user explicitly groups/links them. Overlap must not create a duplicate timeline or an unintended extra editing section.

### Required validation for this UI change

- [ ] Verify exactly one Play/Pause, one time readout and one fixed center red playhead are visible; confirm no playback controls overlay the preview.
- [ ] Verify playback scrolls all lanes/ruler beneath the fixed playhead and scrubbing by dragging the timeline updates preview/time correctly.
- [ ] Verify pinch zoom in/out preserves the timestamp under the two-finger midpoint and keeps all lanes aligned at several zoom levels.
- [ ] Verify the “Zaman Çizelgesi” / “Klip Düzenleme” split is gone and clip actions appear contextually without creating another timeline surface.
- [ ] Verify project duration uses the furthest end among all lanes and does not change merely because of zoom.
- [ ] Test on a small screen and with the keyboard open; review screenshots and interaction logs.

**Status:** requirements recorded only. No production code change or passing test is claimed by this entry. Version 395 remains blocked; installed version 394/build 61 stays unchanged.


### CapCut-style continuous video filmstrip — user-confirmed (2026-10-31)

- [ ] Render each video clip as a continuous filmstrip made from actual frames decoded from that source video. Do not represent video clips as plain colored blocks, filename cards or text-only clip boxes.
- [ ] Fill the full visible clip width with sequential thumbnails from beginning to end, with no large empty gaps, repeated placeholder frames or persistent black thumbnails after loading.
- [ ] Keep thumbnail density adaptive to the shared timeline zoom: zooming in reveals larger/more detailed frames; zooming out shows a denser overview while preserving correct clip duration and frame order.
- [ ] Keep the filmstrip visually continuous across the clip and show a clear, subtle seam/boundary between adjacent clips.
- [ ] Show clear left/right trim handles at clip edges. Dragging the filmstrip body moves the clip; dragging either edge trims/extends the source range within valid bounds. Selection and handles must not create a separate clip-editing timeline or extra row.
- [ ] Preserve the filmstrip's alignment with the shared ruler, fixed center playhead and every other timeline lane at all zoom levels.
- [ ] Verify thumbnail timestamps/order, no black/duplicate frames, scrolling, pinch zoom, body drag, edge trim and save/reopen on a real device or reliable emulator.

**Status:** newly recorded design/acceptance requirement only. Existing thumbnail decode/cache work does not prove the final CapCut-style adaptive filmstrip behavior. Leave unchecked until implementation and relevant device verification pass.


### Fixed-center playhead implementation — 2026-10-31

- Added the first production-code step toward the confirmed CapCut-style timeline: the red playhead is positioned at the center of the visible timeline viewport rather than following its absolute time coordinate across the screen.
- During full-timeline and single-clip preview playback, the shared timeline lanes are programmatically scrolled to keep the current time beneath the fixed playhead.
- Manual horizontal scrolling of the primary lane updates the project time beneath the center guide and seeks the preview; the existing lane-scroll synchronization keeps the other lanes aligned.
- The filmstrip thumbnail slot count already derives from rendered clip width, so it increases with zoom-in and decreases with zoom-out. This is adaptive density groundwork; the full gesture/visual acceptance is still pending.
- Verification: PR-head Android build and unit-test workflows are running for the implementation commits. Do not mark the behavior complete until CI succeeds and center-follow, manual-scroll scrubbing, zoom, and small-screen behavior are checked on a device/emulator.


### Anchored zoom start-boundary correction — 2026-10-31

- CI on the previous helper commit found one failing unit test: `zoomAnchorClampsAtTimelineStart` (44 other tests passed; Kotlin compilation completed).
- Root cause: an anchor timestamp of zero could produce a positive scroll offset when the finger midpoint was inside the fixed label gutter.
- Corrected `scrollForAnchoredZoom` to clamp a zero/start timestamp directly to scroll offset zero.
- This is a boundary-math fix only. Actual two-finger gesture integration and device verification remain pending; do not treat pinch zoom as implemented.
- Re-run CI on the correction before proceeding. PR #11 remains draft; version 395 remains blocked.


### Two-finger anchored timeline pinch — implementation added (2026-10-31)

- Added an Android `ScaleGestureDetector` path scoped to touches beginning inside the timeline viewport. Two-finger gestures are captured so clip drag/scrub handlers do not also react during the pinch.
- The timestamp beneath the pinch midpoint is captured at gesture start; zoom updates recalculate the shared lane scroll offset using the anchored-zoom math, keeping that timestamp beneath the moving finger midpoint while the shared lane scroll position remains synchronized.
- Zoom remains clamped to 0.5×–8×. Clip timestamps/durations are not rewritten by the gesture.
- This is an implementation step, not device acceptance: run Verify/build, then test two-finger zoom on a physical phone at multiple timeline positions, both zoom directions, screen edges, and with video/audio/text lanes visible. Confirm one-finger scrub and clip drag still work after pinch. No release/395 created.


### One CapCut-style VIDEO strip — implementation added (2026-10-31)

- Removed visual lane assignment for video clips. The editor now creates exactly one row labelled **VIDEO** and places every clip in a single FrameLayout canvas at its true project-timeline x-coordinate.
- Each clip retains its own decoded-frame filmstrip, clip boundary, body-drag handler and left/right trim handles. Moving or trimming one clip does not rewrite another clip's start/end values.
- Overlapping clips are composited inside the same strip in clip-list/layer order, matching the current preview/export rule (the later layer is visible on top). No extra VIDEO row is created.
- The selected clip receives the top z-order so its handles remain available. Long-press on an overlapping clip offers an **Örtüşen klibi seç** submenu, allowing a fully covered clip to be selected and raised without creating another row.
- Removed the old assignLanes() helper and replaced tests that asserted multi-row behavior with tests for one-strip placement, exact timeline-range preservation, overlap order and fully covered clips.
- **Verification pending:** run PR-head unit tests/Kotlin compilation/debug APK build. Then test the single strip on the phone with sequential clips, gaps, partially overlapping clips and fully overlapping clips; check frame continuity, clip selection, body drag, both trim edges, pinch zoom and synchronized lane scrolling. Do not mark device acceptance complete until those checks pass.
- PR #11 remains draft; version 395 remains blocked; installed version 394/build 61 stays unchanged.


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


### Follow-up verification — 2026-10-31

- Fixed the Kotlin compile error in the contextual-panel layout (visibility belongs to the View, not LinearLayout.LayoutParams) in commit 65180c0328197e97d229aa45f2a80df18ca9e73d.
- Verify run [#175](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41179027832) completed **SUCCESS** on that PR head: unit tests, Kotlin compilation and Debug APK assembly passed.
- The separate APK workflow #250 and emulator screenshot workflow #419 were still running at the time of this update. No emulator screenshot has yet been inspected; actual gesture behavior, export and save/reopen remain unverified. PR #11 stays draft and version 395 stays blocked.


## CapCut mobile editor parity target — user-confirmed (2026-10-31)

- The user's acceptance target is a CapCut-like mobile editing workflow and screen structure, not merely a loosely inspired editor. Reproduce the recognizable layout and interaction model as closely as the app architecture allows; do not copy CapCut branding or proprietary assets.
- Main screen priorities: full-width video preview at the top, one shared layered timeline as the central work surface, and a persistent bottom tool dock. Selected-item settings should appear contextually above the preview rather than taking over the timeline.
- Keep one shared time ruler, one center-fixed playhead, one Play/Pause control and one time readout. All tracks scroll and zoom together; every clip/layer retains independent start/end positions unless explicitly grouped.
- The first visual defect found in source was a hard-coded 180dp PlayerView width. Commit 74829abf93ae8a16541bb0c5d057f44b0c3f159e changes the preview player to fill the preview frame width.
- This is only the first layout correction, not CapCut parity or acceptance. The rest of the screen hierarchy, bottom dock, contextual panels, gestures and editing behavior still require implementation and emulator/device review.
- Keep PR #11 draft. Do not create, sign or publish version 395 until implementation and interaction/export/persistence acceptance gates pass.


## Editor workspace layout pass — implementation awaiting device review (2026-10-31)

- [x] Remove the hard-coded 180dp PlayerView width and keep the preview player MATCH_PARENT in the preview frame (`74829abf93ae8a16541bb0c5d057f44b0c3f159e`).
- [x] Remove the later aspect-ratio sizing code that shrank the entire PlayerView to the calculated portrait-video width; set Media3 FIT mode so the source aspect ratio is retained inside the full-width preview canvas (`0cf5f0f1faeecbf21cc48af331dcd4072a75886b`).
- [x] Increase the preview stage share on regular-height phones and increase the timeline workspace height, with a smaller allocation on short screens.
- [x] Reduce timeline workspace height while the keyboard is visible so text editing has room.
- [ ] Confirm compilation and unit tests on the new PR head.
- [ ] Inspect fresh emulator captures on a portrait phone and a smaller viewport. Confirm preview width, crop/letterboxing, visible timeline rows, bottom dock anchoring and contextual panel position.
- [ ] Interactively test touch/zoom/scroll and verify no timeline gestures regressed due to responsive sizing.
- These layout changes are not yet visually accepted by the user and are not device-verified. Keep PR #11 in draft; version 395 remains blocked.
