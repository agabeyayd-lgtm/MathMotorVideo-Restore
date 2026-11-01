# MathMotor Video

## PR #11 existing-work audit — 2026-11-01 (after emulator #620)

This audit separates implementation already present from behavior actually exercised. Do not restart or re-implement the source items below just because older checklist entries remain unchecked.

### Confirmed on the current PR head `6ae6a9d4964ffbdbd4b7101370c58f964280ed45`

- [x] Verify Android #376: Kotlin compilation and unit tests passed.
- [x] Debug APK build #455 passed. This is a debug verification artifact, not a version 395 release.
- [x] Emulator #620 passed live playback synchronization: 17 samples, player position 283→4653 ms, two horizontal timeline lanes remained synchronized, fixed playhead/Play center at x=160, and ruler/video zero edges shared a coordinate.
- [x] Emulator #620 exercised filmstrip scrubbing in both directions: left advanced to 1025 ms; right returned to 233 ms.
- [x] Emulator #620 aligned one filmstrip cell with the playhead and verified one preview/thumbnail pair: sample 211 ms, preview/thumbnail center RGB delta 29 (threshold 55). This proves one sampled frame match, not all timestamps or behavior under sustained decode load.
- [x] Emulator #620 confirmed automatic draft save and that the draft appears in the home-screen draft list. It did not exercise a complete manual save → close → reopen → compare workflow.
- [x] Screenshot review: the current screen has the top export bar, large preview, one centered Play/Pause control, time/ruler row, timeline filmstrip and bottom tool dock.

### Already implemented in source (do not rebuild from scratch; acceptance still required)

- [x] Activity-level two-finger pinch handling, cancellation of an in-progress child gesture, anchored zoom math, and unit coverage exist.
- [x] Shared px/sec scale, common horizontal scroll synchronization, fixed center playhead, fit calculation and furthest-layer project-duration calculation exist with pure unit tests.
- [x] Clip body/edge interaction handlers, trim math, zero-origin trim behavior, independent timeline strip ordering/placement and overlap-aware video sequence construction exist in source.
- [x] Text timing/appearance controls, music source in/out controls, sound-effect controls, and independent layer timing/state exist in source.
- [x] Bottom contextual panel, fixed bottom dock, responsive-height/keyboard state handling exist in source.
- [x] MP4 export path, local project draft persistence and draft resume list exist in source.
- [x] Adaptive filmstrip sampling, prioritized asynchronous thumbnail decoding, paused exact-frame preview overlay and stale-generation protection exist in source.

### Still genuinely unverified (these are the remaining acceptance tasks, not missing source by default)

- [ ] Exercise real two-finger pinch in/out on emulator: midpoint anchor, every lane/ruler staying aligned, and no accidental first-finger clip move/trim.
- [ ] Exercise one-finger clip selection, body move, left/right trim, split/delete and empty-area deselection; verify unrelated items never move.
- [ ] Exercise independent positioning and overlap for multiple video clips, multiple text layers, music and sound effects; verify lane reorder and save/reopen persistence.
- [ ] Open each bottom contextual panel and test scroll/touch targets; test text editing with the software keyboard open on the compact viewport. The #620 screenshot has the panel closed, so it does not prove this.
- [ ] Verify text/audio preview and final MP4 timing, selected audio source range/looping, silence outside the range, duration and source-video preservation.
- [ ] Complete a manual project save → close editor/app → reopen project → compare all clip/layer positions, durations, zoom and scroll state.
- [ ] Inspect a varied real-content video (portrait and landscape) under rapid scrubbing/decode load; #620 used a deterministic colour-bar test clip and one pixel-match sample.
- [ ] Physical-phone acceptance remains outstanding.

### Checklist reconciliation note

Older checklist sections contain duplicated or superseded requirements (including conflicting playhead colour and contextual-panel placement). The latest user-provided CapCut reference is authoritative: white fixed playhead over the timeline; bottom dock fixed to the bottom; selected contextual drawer above that dock. Treat older conflicting entries as historical notes, not new work.

PR #11 remains draft and unmerged. Installed versionCode 394 / build 61 is unchanged. Do not create version 395 until the remaining acceptance gates pass and the user explicitly authorizes release.


## Thumbnail-alignment emulator attempt — 2026-11-01

- PR-head Verify #375 and APK build #454 passed after changing filmstrip samples to cell-time midpoints.
- Emulator #619 reached the new screenshot-alignment assertion, then stopped because the first automated swipe moved the filmstrip only 13px for a 25px gesture. The observed Android touch-slop consumption left the nearest cell center at x=172px while the fixed playhead remained at x=160px.
- Logs show the exact pending state: the centered-cell candidate represented 211ms, while the preview correctly decoded the current playhead at 108ms. This was not a failed pixel comparison; the cell had not actually been centered, so the validator correctly refused to compare different timestamps.
- Updated the validator to report even an already-centered cell and updated the emulator flow to re-dump the hierarchy after each drag, compensate for the measured 12px touch slop, and retry until the cell is within 2px of the fixed playhead (up to five measured attempts).
- [ ] Re-run emulator screenshot/pixel comparison with the measured alignment loop.
- The actual preview-versus-thumbnail pixel match remains unverified. Bottom dock/context-panel/keyboard acceptance remains separate and open. PR #11 remains draft; installed 394/build 61 unchanged; version 395 blocked.


## Filmstrip frame sampling aligned with the fixed playhead — 2026-11-01

- Root cause: the strip was split into `count` equal-width cells, but the decoder sampled `count` timestamps evenly across `count - 1` gaps (first frame through last frame). The sample timestamps therefore drifted away from the temporal centers of their own visible cells.
- Updated `TimelineThumbnailMath.sampleTime` to decode the midpoint of each cell's own time interval. The image under the fixed playhead now represents the same time position as the center of that visual cell; the first and last cells no longer force the first/last source frames onto their edges.
- Filmstrip cells expose their sample timestamp to accessibility diagnostics. Debug emulator logs now record thumbnail sample/ready timestamps and exact preview-frame request/ready/failure timestamps. No additional test-only behavior is packaged into release builds.
- Added unit checks for cell-center sampling, time-to-pixel center alignment, one-cell behavior, index clamping and clip-end bounds.
- Added an emulator screenshot acceptance step that moves a thumbnail cell center under the fixed playhead, confirms preview and thumbnail decode timestamps agree, and compares their rendered center pixels. This is a stronger check than playback/scroll synchronization alone.
- [ ] PR-head Kotlin compilation, unit tests and Debug APK build after the center-sampling change.
- [ ] Emulator screenshot/pixel comparison of the centered thumbnail and paused preview must pass. A build-green result alone does not close this item.
- [ ] Review the new aligned screenshot and repeat on a physical phone; inspect for black/partial/stale thumbnail cells under decode load.
- The bottom tool dock/context-panel/keyboard layout remains a separate open acceptance item; the current emulator screen without a selected panel does not validate it.
- PR #11 remains draft and unmerged. Installed versionCode 394/build 61 remains unchanged. Version 395 remains blocked.


## Non-negotiable visual reference — user-provided CapCut screenshot (2026-10-31)

The user's blue-marked screenshot is the **single authoritative visual acceptance reference** for the mobile editor. Do not substitute a generic editor layout, an alternative redesign, or a merely “CapCut-inspired” approximation. All future editor UI work must be compared directly against that screenshot.

### Required screen composition (top to bottom)

1. **Top action bar:** close (X) and search on the left; resolution selector (for example, “YZ UHD”) and the prominent cyan **Dışa Aktar** action on the right.
2. **Large video preview:** centered, portrait video canvas on the dark editor background, preserving the source aspect ratio.
3. **Preview control row:** a single Play/Pause button centered immediately below the preview, with the adjacent preview actions arranged like the reference. Do not duplicate playback controls.
4. **Playback/time/ruler rows:** one Play/Pause control stays horizontally centered on the full phone screen. The current-time / total-duration readout sits centered directly beneath Play and updates during playback. The ruler is on the next row below the readout.
5. **Timeline workspace:** video filmstrip on the upper track; text, music, sound and other items on independent tracks below. The white vertical playhead is centered over the timeline tracks as shown in the reference. It is a timeline indicator, not a line over the video preview. Timeline content moves beneath it during scrub/scroll.
6. **Bottom tool dock:** fixed at the bottom, with the selected tool's actions in the same visual region as the reference (for example, Metin ekle, Otomatik Altyazılar, Çıkartmalar, Çiz, Metin şablonları). Keep it anchored and reachable on small screens.

### Blue-marked details are mandatory

- Match the **placement, order, alignment, relative spacing and visual hierarchy** of the blue-marked Play control, time/ruler row, centered timeline playhead, independent lower-layer area and bottom tool dock.
- The time readout belongs at the **left of the ruler row**; ruler labels occupy the remaining horizontal space. Do not move the time readout below the tracks.
- Keep the centered white playhead over the timeline tracks. Do not place it over the preview or use a red line. The playhead and Play control stay fixed; the time readout updates above the ruler as playback advances.
- Preserve the large preview and give the timeline enough height for clearly visible tracks and usable touch targets. Do not compress the timeline into a tiny card or leave large avoidable empty gaps.
- Keep the bottom dock fixed to the bottom edge; timeline and preview must adapt to available height and keyboard visibility without overlapping or hiding controls.
- Preserve the dark background, compact icon-and-label tool presentation and cyan export emphasis visible in the reference. Avoid unnecessary explanatory copy, duplicate controls, decorative controls and unrelated layout experiments.
- Every video, text, music, sound-effect and other timeline item remains independently positionable and trimmable. Moving or trimming one item must not move or modify another unless the user explicitly invokes a group/link action. All tracks share the same ruler, zoom and horizontal scroll position.

### Acceptance rule

A change is **not accepted** merely because it compiles, passes unit tests, or looks generally similar. Review the actual emulator screenshot side by side with the user's reference at phone size and check every numbered region above. If the composition differs, revise it; do not present a different design as the result. Gesture behavior, preview/export synchronization, keyboard behavior and save/reopen still require their own tests. This reference section records the target; it does not claim those requirements are already implemented or verified.

**Scope guard:** Do not introduce a second competing editor design or ask the user to choose between layouts. Work only toward this reference. Version 395 remains blocked until all implementation and verification gates pass and the user explicitly authorizes release.


## Playback state and fit control — 2026-10-31
- Removed a duplicate Sığdır button declaration found by PR-head Kotlin compilation; one visible fit control remains.
- Fit now preserves the current playhead time under the fixed center marker; the scale calculation is a pure tested helper. Playback state also respects Media3 playWhenReady while buffering.
- CI caught one incorrect expected value in the new ruler-step unit test: at 22 px/sec and 48 px minimum label spacing the next readable interval is 5 seconds. Corrected the assertion; production Kotlin compilation had succeeded on the preceding run.

- Added the visible Sığdır control to the playback header; it invokes the deterministic fit calculation based on project duration and the available timeline width.
- Player errors use explicit player-error wording and a distinct error button state; thumbnail decode failures remain isolated to thumbnail rendering.
- Playback reaching STATE_ENDED moves the playhead to the project end and returns the control to paused.
- [ ] Emulator acceptance for fit repeatability, state transitions and end-of-project behavior remains pending.

## Ruler and thumbnail consistency — 2026-10-31
- Emulator smoke test now injects a long timeline scale and drags the actual first video filmstrip left and right, asserting playhead/scroll advance and reverse. This directly covers filmstrip scrubbing rather than playback-only synchronization.
- The leading video clip is now anchored at timeline zero: moving it cannot create an empty opening gap. Other clips remain independently movable.

- Ruler tick interval now derives from the same direct px/sec state as clip geometry; all tick x positions use absolute time-to-pixel conversion.
- Scroll and frame hit-testing use the shared viewport-origin time conversion rather than independent x-to-time formulas.
- Added deterministic Sığdır action using available timeline width / total project duration.
- Thumbnail cell count follows clip width (up to 256); decode failures retry twice and can temporarily reuse the nearest cached frame without deleting prior cached frames.
- [ ] Emulator: repeated fit produces identical px/sec; selection/move does not alter scale; ruler labels/readout agree at fit and pinch zoom.
- [ ] Emulator: zooming 41→83 px/sec increases cells to match clip width; old frames remain until replacement samples decode; failed thumbnails retry.
- PR #11 remains draft; version 395 remains blocked.

## Single px/sec timeline scale — 2026-10-31

- Replaced the live zoom multiplier with one authoritative `timelinePxPerSec` value. Pinch zoom changes it; selecting or moving a clip only scrolls the timeline.
- New project saves persist direct px/sec; old project files migrate from their saved `timelineZoom` multiplier.
- Added deterministic `Sığdır` calculation based on the available timeline width and total project duration; a visible button is added in the playback header in the next UI pass.
- [ ] Verify repeated fit actions return the same px/sec and selection/move never changes it.
- [ ] Verify ruler, playhead readout and clip geometry share the same conversion at multiple scales.
- PR #11 remains draft; version 395 remains blocked.

## Filmstrip scrubbing and preview seek coalescing — 2026-10-31

- Confirmed source cause: the primary lane's horizontal-scroll callback was calling Media3 `seekTo` for every scroll update. The 250 ms player-sync task could also write the previous paused player position back into the timeline during a manual scrub.
- PR #11 source correction: the visible playhead time/progress now updates on every horizontal movement; decoder seeks are coalesced to at most one per 120 ms while retaining the latest requested timestamp. The last pending position is delivered after scrolling becomes idle. Manual scrubbing pauses active composition playback, and paused-player polling no longer overrides the user's scrub position.
- Added JVM coverage for the seek-throttle timing policy.
- **Not yet accepted:** verify on emulator that dragging the filmstrip left scrubs forward and dragging right scrubs backward, the preview visibly follows the frame under the fixed center playhead without gray-frame/decoder errors, and release leaves the exact final frame. Build, unit-test and emulator workflows must pass on the updated PR head before this is called fixed.
- No release/version changes. PR #11 remains draft; version 395 remains blocked.

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



## Physical-phone CapCut comparison targets — 2026-11-01

Reference: the user's two screenshots with MathMotor and CapCut timelines expanded to comparable scales. These are new acceptance targets, not claims that the current source already implements them.

- [ ] **Pinch behavior:** two-finger pinch should control the timeline time scale/ruler without accidentally dragging, trimming, moving, or otherwise manipulating the selected video clip/filmstrip. Preserve correct ruler-to-filmstrip time alignment, stable strip height, and actual clip start/end/duration. The precise visual behavior of thumbnail density during zoom must be validated against the reference screenshots rather than guessed.
- [ ] **Duration placement:** show current position / total duration directly above the timeline ruler, not in a separate playback-control row. Keep one authoritative readout.
- [ ] **Adaptive ruler detail:** marks/labels become more precise as zoom increases, including frame subdivisions (2f/4f/6f where supported by source frame rate), while remaining readable at wider zoom levels.
- [ ] **Fixed playhead:** playhead remains fixed in the viewport while timeline content moves beneath it; fixed zero-time anchor/header controls do not scroll with the filmstrip.
- [ ] **Filmstrip consistency:** maintain stable strip height and clear, sequential thumbnail frames; zoom must not cause unintended vertical resizing, distortion, or clip manipulation.
- [ ] **Workspace hierarchy:** preview at top, ruler/video strip and independent audio/text lanes in the middle, compact contextual tools and bottom dock below. Reduce oversized action panels/unused space without sacrificing touch targets or preview aspect ratio.
- [ ] **Preview review:** compare preview scale/unused black area against CapCut on the physical phone; preserve source aspect ratio and do not crop to imitate the reference.

### Focused acceptance

- [ ] Compare the same source clip at equivalent timeline zoom on the physical phone.
- [ ] Capture minimum/middle/maximum zoom; verify ruler detail and strip geometry remain stable and no clip edit occurs during pinch.
- [ ] Verify pinch does not change clip start/end, trim bounds, actual/total duration, playhead timestamp, preview frame or exported duration.
- [ ] Verify the readout is above the ruler and remains synchronized during playback, scrubbing and seeking.
- [ ] Verify frame subdivisions against the source frame rate.
- [ ] Add only targeted regression coverage for newly uncovered behavior; do not repeat unchanged green tests.
- [ ] Physical-phone acceptance is required; CI/build/emulator screenshots alone do not close these targets.



### User clarification: match CapCut's complete pinch behavior (2026-11-01)

The user explicitly confirmed: **“CapCut'ta neyse bizde de o olsun.”** Do not freeze the thumbnail strip's horizontal presentation or force a fixed thumbnail count. Reproduce CapCut's natural timeline zoom behavior:

- Pinch changes the shared horizontal time scale (pixels per second) and the ruler's visible time range.
- The filmstrip follows that same time scale: visible thumbnail count/spacing may naturally change as more or less timeline time is shown, matching CapCut.
- The filmstrip's vertical lane height and aspect-correct thumbnail rendering remain stable; thumbnails must not be independently pinched, stretched, squashed, or distorted.
- Ruler, video, audio and text lanes share one consistent time-to-pixel mapping. Pinch must not edit clip timing, trim handles, clip duration or content.
- The playhead remains fixed in the viewport while timeline content moves beneath it; zoom should preserve the time anchored under the pinch midpoint in the same way CapCut does.
- Compare directly against CapCut using the same clip, same starting playhead position and equivalent zoom levels. Acceptance is behavioral parity, not an arbitrary fixed thumbnail density.

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
- [x] Make the preview consume remaining vertical space after accounting for the measured timeline, contextual panel, status strip and pinned bottom dock.
- [x] Size the timeline card from actual visible lane heights; compress lane/card sizing while the keyboard is visible and let the preview use the remaining area.
- [ ] Confirm compilation and unit tests on the new PR head.
- [ ] Inspect fresh emulator captures on a portrait phone and a smaller viewport. Confirm preview width, crop/letterboxing, visible timeline rows, bottom dock anchoring and contextual panel position.
- [ ] Interactively test touch/zoom/scroll and verify no timeline gestures regressed due to responsive sizing.
- These layout changes are not yet visually accepted by the user and are not device-verified. Keep PR #11 in draft; version 395 remains blocked.


## Flexible preview and vertically scrollable shared timeline — implementation pending CI/device review (2026-10-31)

- [x] Preview is a weighted flexible area: it uses the remaining vertical space after the top bar, contextual panel, timeline, status strip and bottom dock are measured.
- [x] Timeline height is derived from visible lane heights instead of a static empty card height.
- [x] Shared lanes are wrapped in a vertical scroll viewport so adding many independent layers does not force the preview or bottom dock off-screen. Horizontal lane scroll/zoom remains shared.
- [x] Keyboard mode uses shorter lane heights and a compact timeline-card target; the preview flexes around the keyboard and contextual settings.
- [ ] Compile and unit tests for the new ScrollView parent/layout parameters.
- [ ] Verify vertical layer scrolling, lane reordering, horizontal synchronized scrolling, center playhead and pinch zoom on an emulator/device.
- [ ] Review screenshots with many layers and keyboard open; verify the bottom dock remains anchored when the keyboard is closed.
- This is an implementation change, not accepted visual parity. PR #11 stays draft and version 395 stays blocked.


## Bottom tool dock visual pass — implementation pending visual approval (2026-10-31)

- [x] Change the bottom toolbar from rounded filled buttons to a flat dark dock with compact icon/label controls.
- [x] Rename existing functional tools to Düzenle, Metin, Ses and Proje; no placeholder or inert tools were added.
- [x] Selected tool receives a restrained accent highlight; closing it restores the neutral dock.
- [ ] Confirm the dock remains visible at the bottom on portrait and compact screens, including with contextual panels and keyboard.
- [ ] Review icon glyph legibility and touch target sizing on device. This is not yet visual acceptance.


## Verify #202 compile failure — targeted correction (2026-10-31)

- [x] Inspected Verify run #202 job log. The failures were Kotlin compile errors in the new timeline ScrollView wrapper: `ScrollView.LayoutParams` was not a resolvable type at the call/cast sites, and the initial responsive-layout block declared `previewParams` twice.
- [x] Replaced those references with `FrameLayout.LayoutParams` (the inherited child-param class used by ScrollView) and removed the obsolete second fixed-height preview declaration in commit `e65c6a736f41c118613748b276e493ef42feee77`.
- [ ] Verify the correction with a fresh Kotlin compile/unit-test/Debug APK run. Do not repeat the failed workflow without this source correction.
- [ ] Emulator screenshot and touch validation remain pending. Keep PR #11 draft and version 395 blocked.


## First visual layout pass — 2026-10-31

- The editor source has been rearranged toward the user's single CapCut reference: the generic project heading was replaced with close/search on the left and resolution/cyan export actions on the right; Play/Pause now sits in its own centered row directly below the preview; the current/total time readout has been moved above the timeline tracks and left-aligned; the redundant timeline-card header has been removed; and the timeline indicator is white.
- The bottom dock and shared timeline remain in place. The editor's time ruler is still a separate row from the current/total readout, and the dock still needs the reference's contextual tool presentation. These are known visual gaps, not accepted as finished.
- This is an implementation pass, **not** a claim of CapCut visual parity. Await PR-head build, unit tests and emulator screenshot review. Compare the actual screenshot against the user's marked image before making further layout changes.
- PR #11 remains draft. Version 395 remains blocked.




## Exact CapCut editor reference — final screenshot (2026-10-31)

The screenshot supplied by the user on 2026-10-31 is the **single, exact, non-negotiable editor layout target**. It supersedes earlier verbal interpretations and any intermediate mockups. Do not reinterpret the layer order or invent another editor layout.

### Match the screenshot from top to bottom

1. **Top action bar:** X/close and search on the left; premium/action icon where present; resolution selector (e.g. YZ UHD) and cyan Dışa Aktar button on the right.
2. **Large portrait preview:** centered, dark background, source aspect ratio preserved.
3. **Preview controls row:** expand/fullscreen at left; one centered Play/Pause; adjacent overlay/undo/redo actions to the right.
4. **One timeline header row:** current/total time at far left (e.g. 00:00 / 00:42); ruler labels to its right on the same horizontal row (00:00, 00:05, 00:10, 00:15 …).
5. **Timeline workspace:** compact, continuous, dark workspace. The **main video filmstrip is the first/top track**. Text/overlay strips appear immediately below it, followed by audio/music and other added layers as needed. These strips are close together and can overlap in time; they are not separated into large cards or unrelated panels. All tracks use one shared ruler, zoom, horizontal scroll and a centered white vertical playhead. Each strip has its own start/end and can be moved or trimmed independently; moving one must not shift the others.
6. **Bottom tool dock:** fixed, full-width, dark dock with the contextual icon-and-label actions shown in the screenshot (e.g. Metin ekle, Otomatik Altyazılar, Çıkartmalar, Çiz, Metin şablonları). The timeline ends directly above this dock.

### Critical layout rules

- Do not reverse the layer order: **video first/top; text and overlays immediately below; audio and additional tracks below those; tool dock last/bottom.**
- Do not add artificial six-track placeholders. Only show actual content layers, with compact row spacing like the reference.
- The current/total time and ruler labels share the same horizontal row.
- Keep the playhead over the timeline tracks, not the preview. Play/Pause remains centered below the preview.
- Keep the preview large and timeline usable. No tiny timeline card, no excessive blank gaps, no floating detached blocks.
- Match the reference's proportions, dark surfaces, compact spacing, clip filmstrip treatment, and bottom dock. Do not replace it with a generic “inspired by” design.
- Do not claim visual acceptance from compilation or unit tests alone. Inspect the emulator screenshot at phone dimensions side by side with this exact reference.

This is a layout target, not a claim that all behaviors are implemented. Gesture, independent trim/move, preview synchronization, keyboard, save/reopen and export checks remain separate verification gates. PR #11 stays draft; version 395 stays blocked until all gates pass and the user explicitly authorizes it.



### Fixed playhead and live time readout — 2026-10-31
- Play/Pause stays centered on the phone viewport.
- The white vertical playhead stays fixed while the synchronized ruler and all timeline strips scroll beneath it during playback and scrubbing.
- The elapsed/total time readout is directly below Play, centered, and updates as the playhead advances; it is above the ruler and tracks.
- This is a source/layout change only; do not treat it as visually verified until the small-screen emulator screenshot check passes.
- Version 395 remains blocked; no release/version bump is authorized.


## Centered timeline origin and fixed playhead — implementation pass (2026-10-31)

- User-confirmed playback behavior: the white vertical playhead and Play/Pause control remain stationary. The current/total time readout above the ruler changes as playback advances. The ruler and all timeline strips move underneath the fixed playhead.
- Source commit `693b49af2964fae5150d0e45d9fab4182212dc2e` changed scroll-to-time mapping so elapsed project time maps directly to horizontal scroll distance.
- Source commit `1faa0aa25fbf3a2d5e1cdba51cd2a57730c86a46` adds a shared half-viewport leading origin and matching trailing space to each lane: time zero begins at the center playhead, and the end of the timeline can also reach the playhead.
- This is implementation, not acceptance. PR-head unit/build and emulator screenshot runs are in progress; visually verify start/end alignment, scrubbing, playback, zoom, and every synchronized lane before marking complete. PR #11 stays draft; version 395 stays blocked.


## Center-origin responsive viewport follow-up — 2026-10-31

- Commit `0771c298bb2f2e8b65c7c741ef2ce4ad4a3424c9` keeps each lane's leading and trailing time-zero spacers synchronized with the **measured** horizontal-scroll viewport width, including later layout changes caused by keyboard/screen resizing.
- This closes a source-level gap in the earlier centered-origin pass, where a lane first measured at zero width could retain a fallback spacer width after its real viewport was laid out.
- The change is not yet accepted: wait for fresh PR-head CI and inspect the new emulator screenshots. Verify time-zero under the center playhead, final-frame reachability, ruler/track alignment, playback auto-scroll, scrubbing and pinch zoom after a viewport resize.
- PR #11 remains draft. Version 395 remains blocked.


## Filmstrip visual refinement — 2026-10-31 (PR #11)

- User confirmed the generated CapCut-style editor mockup as the exact visual direction; the supplied real-editor screenshot remains the source for timeline proportions and interaction behavior.
- Targeted source change `a0212de8fb79c25c17e5ca59c2264158602d1d2d` increases filmstrip sampling density from one thumbnail slot per 24dp to one per 12dp (bounded to 3–48 frames per clip). This is intended to make short clips read as a continuous, detailed filmstrip instead of a sparse row of a few large thumbnails.
- This change is not visually accepted until fresh unit/build and emulator screenshot workflows complete and the resulting phone screenshot is inspected. Confirm thumbnails are distinct, stable, and do not overload decoding or memory.
- PR #11 remains draft. Version 395 remains blocked.


## Video trim handle visual cleanup — 2026-10-31

- Replaced the oversized turquoise arrow-shaped video trim handles with slim white vertical markers, matching the supplied CapCut reference more closely. The larger transparent touch targets and existing trim gesture behavior remain intact.
- Source commit: `ccbece8c3ee0fc1171dcf0689a699f4fb1766eda`. Await fresh PR-head CI and emulator screenshot inspection before visual acceptance. PR #11 stays draft; version 395 stays blocked.


## Emulator startup crash fix — 2026-10-31 (PR #11)

- Emulator screenshot run #477 built the debug APK successfully but the editor process crashed on launch before the fixture video could be imported.
- The actual exception was `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.refreshTimelineTracks$makeLane(VideoEditorActivity.kt:1646)`.
- Initial root cause: the live time label was still attached to the obsolete `playheadRow`; that legacy attachment was removed in the first fix. A later responsive refresh exposed a second reparenting path in which the label remained attached to the previous ruler row. The follow-up fix is recorded below.
- Fix commit: `fef3116d0eb6c9394915b9663f82efda5406ef5e`.
- [ ] Run targeted compiler/unit + emulator launch/import verification on the fix commit; inspect a fresh screenshot. Do not count the failed #477 run as a visual or functional pass.
- PR #11 remains draft and unmerged. Version 395 remains blocked.


## Responsive timeline refresh — ruler label reparenting follow-up (2026-10-31, PR #11)

- The fresh PR-head emulator run `41192048687` built and launched the app, but fixture import triggered a second `IllegalStateException: The specified child already has a parent` at `makeLane()` line 1653.
- Root cause: the persistent time-label View had been attached to the previous ruler row. `refreshTimelineTracks()` removed the old lane from the timeline container but did not detach the label from that old parent; the next responsive layout refresh tried to attach the same View to the rebuilt ruler row.
- Fix commit `4c1e9cd71af19f5e90d13b33b27e3a633d99f90f` explicitly removes the time label from its current parent before clearing and rebuilding timeline rows.
- On the parent commit, APK build `41192048694` and Verify/unit/build `41192048740` passed; emulator UI run `41192048687` failed on the reparenting issue above. Those are not verification results for the new fix commit.
- [ ] Re-run PR-head compile/unit, debug APK and emulator fixture-import/screenshot checks on `4c1e9cd71af19f5e90d13b33b27e3a633d99f90f`. Inspect the screenshot and verify the time label, playhead and ruler still align after repeated layout refreshes.
- PR #11 remains draft and unmerged. Version 395 remains blocked; installed 394/build 61 remains unchanged.

## Timeline alignment and filmstrip readability — 2026-10-31

- User-confirmed timeline contract: the Play control is above the time ruler; the ruler labels run 0, 1, 2, 3…; the fixed white playhead sits at the exact horizontal center directly below Play. On editor entry, the imported video's first frame, ruler zero, time readout 00:00 and playhead must all align at zero, with playback paused.
- The video filmstrip is the actual moving video content, not a decorative/background strip that appears to move independently. Its thumbnails and the ruler share one horizontal scroll offset and time-to-pixel mapping beneath the stationary playhead.
- The first video frame is clamped to the playhead at project time zero: the filmstrip may move forward/right to reveal later time, but must not move left past its first frame and create negative pre-roll space.
- Thumbnail readability correction committed in `56ddc2dff0085bb215aed9a5cd082a1c4fa72def`: replace 12dp sliver cells with approximately 52dp-wide thumbnail cells, fill the clip width evenly, and cap the number of decoded thumbnails. This is a source change only; verify at phone size that frames are visibly wider and no longer look like narrow chopped rectangles.
- Thumbnail generation latency remains an open performance issue. Current implementation opens a decoder/retriever per requested frame and uses a two-thread executor; profile and optimize this separately without delaying preview startup or changing timeline coordinates.
- [ ] Verify fresh PR-head compilation/unit tests and Debug APK.
- [ ] Review fresh emulator screenshot and physical-phone behavior for paused-at-zero entry, ruler/filmstrip/playhead alignment, zero-boundary clamp, shared movement, and thumbnail cell width.
- [ ] Measure thumbnail first-frame and full-strip population latency; do not mark speed fixed until measured.
- PR #11 remains draft; version 395 remains blocked.

## Timeline zero-alignment pass — 2026-10-31

- PR #11 commit `ec6dcb9567c50650b58ab0aede58858a98c87745` gives the ruler the same 54dp left gutter as the video and other timeline lanes. The live time readout is in the Play header so it no longer changes the ruler's horizontal origin.
- The ruler now marks every second (`0, 1, 2, 3…`) using the same time-to-pixel scale as the video filmstrip.
- The first thumbnail samples the clip's actual source start frame. Thumbnail cells and separators are sized to fit the clip width without cumulative overflow.
- The shared half-viewport origin spacer keeps ruler time zero and the first video frame under the fixed playhead at the same instant. Negative pre-roll remains disallowed by the non-negative clip-time/drag bounds.
- These are source changes only. Fresh PR-head build/unit verification, emulator screenshot review, and physical-device confirmation are still required before accepting the timeline behavior. PR #11 stays draft; version 395 remains blocked.

### Keyboard-open timeline visibility follow-up

- Commit `f700d0017aced27600dc218bb6a39c6a5fd23d3a` removes the conditional that omitted the ruler row while the keyboard was open. The ruler now remains in the timeline stack and uses the compact lane height already applied for IME mode.
- Fresh emulator screenshot review is required to confirm the ruler remains visible and readable with the keyboard open, without hiding the filmstrip or pinned tool dock.

- Commit `67af75472c2452234fffc3ec7221cd8598ce3af8` resets the horizontal timeline offset and playhead to project time zero when opening/replacing with a fresh main video. This prevents a previous session's scroll position from shifting the new video's first frame away from the ruler's zero point.


## Emulator #532 — filmstrip scrub test range correction (2026-10-31)

- [x] Inspected failed emulator run `41213136450` (run #532) and its uploaded screenshots/diagnostics. The app launched, imported the deterministic 8-second MP4 (`EDITOR_SMOKE_CLIP_ADDED`), and rendered the preview plus three visible filmstrip thumbnails; no app crash or ANR caused this failure.
- [x] Confirmed the failure was in the smoke-test coordinate guard before any swipe was sent. UIAutomator reports the clip's visible viewport intersection (`[160,342][304,402]`, 144 px wide); the old fixed 35 px margins left only 74 px, below the test's 80 px minimum.
- [x] Updated `.github/scripts/capture-emulator-screenshots.sh` to derive a 10%-of-visible-width inset (clamped to 10–24 px) for both forward and reverse scrubs, and to report the measured usable range if it is genuinely too small.
- The measured forward range on the captured 144 px node is now 116 px, so the test can proceed to the actual gesture assertions.
- [ ] Run fresh PR-head emulator verification and confirm forward scrub advances the shared playhead, reverse scrub moves it backward, playback stays synchronized, and draft save/resume passes. The failed #532 run did not perform either swipe, so scrubbing is not yet accepted.
- PR #11 remains draft and unmerged. Version 395 remains blocked; installed 394/build 61 is unchanged.


## Emulator #536 — reverse scrub gesture hit the fixed playhead (2026-10-31)

- [x] Inspected the complete failed job log and uploaded emulator artifact. Exit code 22 is the test's explicit `filmstrip-scrub-reverse-not-reversed` assertion, not an emulator shutdown failure.
- [x] Forward filmstrip drag passed: playhead advanced to 842 ms and both synchronized lanes reported scrollX=101.
- [x] Reverse drag exposed a test-coordinate collision: the gesture began at x=93 and crossed the fixed playhead centered at x=160. Its 28dp touch target intercepted the drag as direct playhead seeking; the resulting sample was 1500 ms / scrollX=180. This is why the log said reverse was not reversed.
- [x] Updated the emulator script to read the fixed playhead center from the synchronization sample and start reverse dragging at least 20 px to its right, keeping the gesture in the filmstrip's scrollable area and away from the playhead overlay.
- Emulator shutdown messages (`stop: Not implemented`, Netsim Wifi stream removed) occur after the script has already exited 22 and are cleanup noise, not the root failure.
- [ ] Re-run emulator UI validation to confirm reverse scrub decreases playhead time, then continue playback synchronization and draft save/resume checks.
- PR #11 remains draft/unmerged; version 395 remains blocked.


## Emulator #538 — playback origin assertion correction (2026-10-31)

- [x] Confirmed the bidirectional filmstrip test now passes: left drag advanced to 800 ms; right drag returned to 83 ms.
- [x] Diagnosed the subsequent playback assertion failure. During playback the fixed playhead remains at x=160 while the time-zero edge correctly moves off-screen (sample origins: -1, -32, -63…); the old assertion incorrectly rejected every negative screen coordinate.
- [x] Changed the debug log's missing-origin marker from `-1` to the unambiguous `Int.MIN_VALUE` sentinel.
- [x] Updated `assert-timeline-sync.py` to reject only that explicit missing-view sentinel and to continue checking `originRightX + scrollX == fixedPlayheadCenter`. Negative screen coordinates are now valid when the origin is off-screen.
- [ ] Fresh PR-head Verify, APK build, and emulator playback/timeline synchronization run must pass before accepting this change.
- PR #11 remains draft/unmerged. Version 395 remains blocked.


## Floating add-video control (2026-10-31)

- [ ] Remove the old visible "+ Video ekle" timeline placeholder block; do not represent the add action as a fake 5-second clip.
- [ ] Show a transparent, standalone plus control at the right end of the main video filmstrip, matching the CapCut interaction pattern.
- [ ] Keep the plus visually outside the clip thumbnails and do not give it a timestamp, clip duration, or selectable clip state.
- [ ] Tapping the plus must open the existing gallery video picker and append/import the selected video without changing existing clip ranges, other layer positions, ruler mapping, or playhead time.
- [ ] Verify the plus placement at the beginning, middle and end of a horizontally scrolled timeline, with keyboard-open and compact-height layouts.
- [ ] Verify the empty-project state uses the same restrained plus affordance in the video lane.
- Source implementation added on PR #11 head commit `d61f7851aa4c6cff59505c38d598a5b22a219505`; this is implementation only. Leave all acceptance items unchecked until fresh build and interaction-level verification pass.


## 2026-10-31 — clip selection, trim handles and whole-second ruler (PR #11)

- [x] Clip trim handles are hidden until the user explicitly taps a clip in the timeline.
- [x] Explicitly selected clips use a white selection outline and reveal only their own left/right trim handles.
- [x] Selecting a clip opens the contextual clip-edit panel; tapping blank space in the video lane clears the visible selection and hides the clip panel.
- [x] Long-press feedback scales the clip down while it is held for repositioning; the scale returns to normal on release/cancel.
- [x] Ruler tick intervals now use whole seconds or larger (1, 2, 5, 10 …), never fractional-second labels such as 0.1 / 0.5.
- [ ] Verify one-tap selection, deselection on blank space, white outline, handle visibility/hit targets, trim/extend behavior, and contextual panel on emulator.
- [ ] Verify long-press shrink feedback and independent clip repositioning without moving other tracks.
- [ ] Verify ruler labels at minimum, fitted and maximum zoom; labels must remain readable and integer-second based.
- [ ] Video preview drag/pinch/rotate transforms are not yet implemented for video clips; existing preview gesture support is for text overlays. Keep this separate from timeline selection/trim acceptance.
- [ ] Verify timeline scrub stays within actual video segments and preview never shows black frames outside valid clip ranges.
- PR #11 remains draft/unmerged. Installed version 394/build 61 remains unchanged. Do not create or publish version 395.


## PR #11 validation results — 2026-10-31 (head `1e6ef499d952a588fcafff7846947d8e849f63aa`)

- [x] Verify Android build #303 passed.
- [x] APK build #378 passed and produced debug artifact `MathMotorVideo-restored-debug`.
- [x] Emulator screenshot/smoke workflow #548 passed and uploaded screenshots/diagnostics.
- [x] Timeline playback synchronization smoke check passed: 19 samples, player position advanced 195→4967 ms, timeline lanes synchronized, ruler/filmstrip scroll followed playhead time without negative scroll, fixed playhead and Play center aligned at x=160, and ruler/first-video-frame zero edges aligned.
- [ ] Visual screenshot review is not acceptance of all requested gestures. Captured editor screenshots show the test fixture's color-bar video; the empty editor also shows the standalone add-video plus. Need inspect layout density and confirm the plus position on a real phone.
- [ ] Explicit clip tap selection, white outline, handles hidden before selection, blank-space deselection, contextual panel, trim/extend, long-press move/reorder, and independence of other tracks still need targeted interaction verification.
- [ ] Whole-second ruler intervals need direct zoom-level and on-device readability verification; current unit tests cover whole-second step selection.
- [ ] Preview video drag/pinch/rotate remains unimplemented; text overlay preview gestures do not satisfy this requirement.
- [ ] Save/reopen, export parity, keyboard-open layout and physical-device acceptance remain separate gates.
- PR #11 remains draft/unmerged. Installed version 394/build 61 is unchanged. Do not create or publish version 395.


## Per-clip video transform gestures — implementation added, verification pending (2026-10-31)

- [x] Add independent scale, rotation and X/Y position to each video clip; transforms are part of clip state and clip undo snapshots.
- [x] Add a transparent preview gesture surface that activates only while a video clip is selected in its contextual clip panel.
- [x] One-finger drag changes only the selected clip's position; two-finger pinch changes its scale and finger-angle movement changes its rotation.
- [x] Persist the four transform values in the existing editable project JSON. Older version-1 projects without these optional fields load with an identity transform.
- [x] Apply a Media3 `MatrixTransformation` to each clipped `EditedMediaItem`. The same flattened video sequence is used by CompositionPlayer preview and Transformer export.
- [x] Keep clip selection/trim preview on the shared composition path after transform edits so the visible preview does not silently fall back to an untransformed single-clip player.
- [ ] Verify Kotlin compilation and unit tests for the new source commit.
- [ ] Verify touch behavior on a small-screen emulator/phone: drag, pinch, rotate, pointer-up re-anchoring and cancel.
- [ ] Verify each clip keeps its own transform when the playhead crosses clip boundaries and when clips overlap.
- [ ] Export a multi-clip MP4 and compare transformed output frames against the live preview; confirm text overlays remain at their intended project positions.
- [ ] Save and reopen a project; verify per-clip transforms persist and old version-1 projects still open.

This is an implementation change, not device acceptance. PR #11 remains draft. Installed version 394/build 61 remains unchanged; version 395 stays blocked.


### Transform math test coverage update — 2026-10-31

- [x] Extract transform normalization and 3×3 matrix-value generation into pure `ClipTransformMath`.
- [x] Add unit tests for identity, combined scale/rotation/translation, boundary normalization, non-finite values and rotation scale preservation.
- [ ] Run the new transform unit tests on the current PR head. Earlier green Verify #309 predates this helper/test extraction and does not verify these new tests.


## Latest verified checkpoint — 2026-10-31 (PR #11)

This section is the authoritative current checkpoint and supersedes older pending statements above when they refer to the same transform-math verification.

- PR #11 head verified: `87ad3d9e830a31322242ef0e1f68f65e30e9966d`.
- [x] Verify workflow #311 passed on this PR head, including Kotlin compilation and unit tests. This run includes the extracted `ClipTransformMath` helper and its five unit tests (identity; combined scale/rotation/translation; clamping and rotation normalization; non-finite inputs; rotation-matrix scale preservation).
- [x] Debug APK workflow #386 passed on the same PR head and produced artifact `MathMotorVideo-restored-debug` (artifact ID `13051529764`).
- [ ] Emulator screenshot workflow #556 was **cancelled** during deterministic sample-video generation, before app launch. It is not a pass and provides no gesture or visual acceptance evidence.
- [ ] Physical/emulator interaction verification remains open: one-finger drag; two-finger pinch/rotate; pointer-up re-anchoring and cancel; per-clip transform isolation across clip boundaries/overlap.
- [ ] Export verification remains open: export a multi-clip MP4 and compare transformed frames against live preview; confirm text overlays retain intended project coordinates.
- [ ] Persistence verification remains open: save/reopen transformed projects and load older version-1 projects with identity defaults.
- [ ] The generated APK is a **debug APK**, not a signed release/update package. Do not treat it as an in-app update for installed version 394/build 61.
- PR #11 remains draft and unmerged. Installed baseline remains versionCode 394 / build 61. Version 395 must not be created, signed, published, or offered until all release gates pass and the user explicitly authorizes it.

## User-reported editor issues and next work plan — 2026-10-31

The following eight issues were reported by the user after reviewing the current editor against the real CapCut reference. These are **requirements to investigate**, not claims that the source has already been corrected. Keep this list current as work progresses; after each targeted fix, record implementation, automated evidence, emulator/device evidence, and remaining gaps separately.

1. **Gap between adjacent video clips:** remove the unintended gray/empty area between clips that should meet edge-to-edge. Preserve intentional gaps only when the user explicitly positions clips apart.
2. **Clipped duration readout and timeline zoom:** the total-duration label (for example, `00:10…`) must remain fully visible within the phone width. Make the timeline comfortably usable and support reliable two-finger pinch zoom in/out.
3. **Persistent draft message:** remove the unnecessary “Taslak açıldı” message entirely; it must not remain stuck on screen.
4. **CapCut-style editing workspace and contextual tools:** allocate more vertical space to the timeline and less to an oversized preview where appropriate; add adaptive frame-level ruler detail at close zoom (for example, 20f/22f/24f), a concise current/total time display, working undo/redo and keyframe controls, and a selected-clip contextual toolbar (such as sound effect, split, volume, duplicate, delete). Remove the permanent generic tool row/long hint when it conflicts with contextual editing. Follow the user's supplied CapCut screenshot as the single layout reference; do not create a competing design.
5. **Selection dismissal and two-finger gestures:** tapping an empty area outside all lanes clears the selected strip, its handles and contextual controls. With no strip selected, two-finger gestures must reliably control timeline pan/zoom without accidentally moving or trimming a clip. Keep one-finger clip editing distinct from two-finger timeline navigation.
6. **Thumbnail/preview responsiveness:** make thumbnail loading and horizontal scrubbing feel immediate and fluid. The preview must react promptly to timeline movement and show the frame at the playhead; thumbnail extraction must not block gestures. Investigate decoding, caching, seek coalescing and UI-thread work rather than hiding latency with animation.
7. **Thumbnail framing:** stop aggressively center-cropping source frames. Preserve the source frame composition so the surrounding scene/background is visible and users can identify scene changes and cut points at a glance.
8. **Continuous filmstrip and frame-accurate ruler:** remove visible gaps/borders that make thumbnails look like separate boxes; make the filmstrip read as one continuous strip. At higher zoom, show frame-level ruler ticks/labels and support frame-accurate cut placement. Do not clip the first or last thumbnail halfway; balance strip height with the available timeline workspace.

### Work order and acceptance
- First inspect the current PR #11 source and existing tests for each reported issue; distinguish a real source defect from a test/device-only observation. Avoid repeating unchanged failing workflows.
- Fix in small, focused groups. After each group, update this document and `RESTORATION_STATUS.md` with exact commit/run evidence and mark only what is actually verified.
- Verify on emulator at phone size and, where possible, on the user's device: visual comparison to the supplied CapCut reference, one-/two-finger behavior, timeline alignment, preview response, thumbnail framing, and clip boundaries.
- Keep PR #11 draft and unmerged until acceptance checks pass. Installed baseline remains versionCode 394 / build 61. **Do not create, sign, publish, or offer version 395** until all release gates pass and the user explicitly authorizes it.

## First targeted corrections — 2026-10-31 (source only; verification pending)

Commit `90a877d7836df45adb27c5ced7f308d5ff210cce` makes a first small correction pass based on the user's eight reported issues:

- Saved-draft open no longer writes the persistent “Taslak açıldı” message into the status line. Genuine project-load errors remain visible.
- The playback readout now uses the compact `MM:SS / MM:SS` format rather than centiseconds, preventing unnecessary width pressure and matching the requested CapCut-style counter.
- Adding a video now appends it after the furthest video clip, not after the furthest project layer. A longer music/text layer therefore cannot create an unintended gray gap before the next video.
- Filmstrip thumbnails use fit/letterbox framing rather than center-cropping; the full source scene remains visible.
- Thumbnail cells no longer add 1dp separator gaps, so the filmstrip reads as a continuous strip.

**Not yet verified:** Kotlin compilation, unit tests, APK build, emulator screenshots, actual two-clip gap behavior, thumbnail performance, and visual comparison on a phone. These source changes must not be described as device-accepted until those checks pass. The other reported gesture, selection, zoom, frame-ruler and contextual-toolbar issues remain open.


## Empty timeline selection correction — 2026-11-01 (source only; verification pending)

Commit `6e3d68fea241b0855ffaec349a1f65e0e27570d1`:
- Empty portions of timeline lane rows and their horizontal scroll viewports now route taps to clip-selection clearing, rather than relying only on the video filmstrip background.
- Clearing selection now resets the selected video clip index, hides clip handles/contextual clip panel, refreshes the timeline and updates the playhead readout without leaving a persistent status message.
- Two-finger timeline zoom routing was inspected and retained; it already uses the activity-level ScaleGestureDetector and fixed-time anchoring. This source review is not proof of gesture acceptance.

**Pending:** compile/unit verification, emulator tap-outside-lane interaction, two-finger zoom/pan behavior, and regression checks for clip selection/trim.


## Pinch gesture isolation — 2026-11-01 (source change; interaction verification pending)

Commit `75885369fdb3f136abb451ae6e8fb135e2d413cd`:
- Empty, non-clickable portions inside each timeline track now bubble taps to the track and clear the current clip selection.
- When a second finger starts a pinch inside the timeline, the in-progress one-finger child gesture receives ACTION_CANCEL before the Activity captures the pinch. This prevents a pinch from continuing as a clip drag/trim.
- The Activity keeps ownership of the captured two-finger gesture until the final finger lifts; the existing anchored zoom calculation remains in place.

**Pending:** compile/unit checks, emulator/phone pinch zoom and pan, confirm the first finger does not move/trim a clip, verify one-finger clip drag/trim and empty-lane deselection. Screenshot smoke tests alone do not prove touch behavior.


## Fixed playhead, scroll-to-scrub and single-owner pinch — 2026-11-01 (source change; verification pending)

Source commit: `54daecf5864ed76694f1930eb684bddbd8499628`.

- The white playhead remains a visual-only guide at the center of the timeline viewport. It no longer intercepts touch; one-finger horizontal movement on the timeline strip is the scrubbing gesture.
- Existing horizontal lane scrolling remains the single scrub input: every lane follows the same scroll offset, the time beneath the center playhead updates, and preview seeks continue through the existing coalesced seek path.
- Programmatic scroll restoration after a timeline rebuild now uses the same guarded scroll path as playback-follow. A layout refresh or zoom commit must not be mistaken for a user's scrub and seek the preview to an unrelated time.
- Kept exactly one `ScaleGestureDetector`, at Activity level. A pinch that begins inside the timeline cancels the child clip/trim touch before Activity-level capture; there is no competing detector inside each HorizontalScrollView.
- Pinch feedback now uses a temporary scale/translation on lane content while fingers move, then commits the real pixels-per-second value and anchored scroll once when the pinch ends. The timestamp under the moving pinch midpoint remains the anchor; the same origin/end spacers continue to let time zero and the final frame reach the center playhead.
- The filmstrip uses edge-to-edge cells and timestamped samples. The earlier `FIT_CENTER` mode letterboxed portrait source frames inside landscape timeline cells, exposing the cell background as gray gaps. The latest focused fix switches the cells to `CENTER_CROP` and matches the strip background to the loading-cell background; this fills each thumbnail tile without inter-frame margins. Visual verification on the latest PR-head build is still pending.

**Pending acceptance:** Kotlin compilation and unit tests on this commit; emulator/phone verification of one-finger swipe scrubbing, live preview response, pinch zoom in/out and midpoint anchoring, no accidental clip drag/trim during pinch, all-lane synchronization, and no false scrub after programmatic rebuild/restore. Screenshot success alone is not touch acceptance. PR #11 remains draft; version 395 remains blocked.


Follow-up source commit `69140cfb4e64d18917b8e1e65750481f58c8c39d` closes one synchronization edge case: when the two-finger midpoint is off the fixed centre line, the committed scroll changes the time under the centre playhead. The playhead readout, seek control and preview are now updated to that actual centre time after the pinch. This remains subject to interaction verification.


Follow-up source commit `88ef7ea8e06cfb3537893f7c79e8466e6173599e`: pause the active preview as soon as a timeline pinch is accepted, preventing the periodic playback-sync task from scrolling the lanes against the user's two-finger gesture. Interaction verification remains pending.


A focused math regression test was added in `cfb24cf97e2b0661745ddb18ece934d3fe550c7e`: it checks that an off-centre pinch keeps the timestamp beneath the moving midpoint while the time beneath the fixed centre playhead is recalculated. The test still needs to pass on the latest PR head.


Pinch-capture follow-up commit `1a764b9c63ec9638289cb8f419861bfc73dc7ea7`: the Activity now takes over any two-pointer event as soon as its scale detector becomes active, not only the initial ACTION_POINTER_DOWN. This also handles devices where ScaleGestureDetector begins scaling on the first subsequent move.


## Whole timeline-strip gesture routing — 2026-11-01 (source change; interaction verification pending)

Source commit `5117d594ddd33a7d2fb319fae8fb975ed0658d80`:
- Horizontal one-finger swipes can now reach the shared HorizontalScrollView scrub path from video, text, music and sound-effect strip bodies. Audio/text bodies no longer claim the first finger movement as an immediate item drag.
- Moving a text, music or sound-effect strip now uses hold-then-drag, matching the existing video-filmstrip interaction. The dedicated trim handles remain direct one-finger controls.
- The Activity-level two-finger pinch hit test and lane-origin calculation now consistently use window-local coordinates. This removes the previous screen/window coordinate-space mismatch when deciding whether a pinch began in the timeline.
- Empty gray portions and ruler/lane spaces continue to use the shared horizontal scroll path; all lanes remain synchronized to the same horizontal offset and zoom.

**Still pending:** fresh PR-head compile/unit verification, emulator interaction proof for one-finger scrubbing from each row and blank area, two-finger pinch from top/middle/bottom rows, preview response, trim handles, and hold-drag item movement. Source changes are not device acceptance. PR #11 remains draft/unmerged; version 395 remains blocked.


## Filmstrip gray-gutter repair — 2026-11-01

- Source commit `581d5141d3f0aecbf1bbcf93a0a28c132051a4bc` changes timeline thumbnails from `FIT_CENTER` to `CENTER_CROP`. The screenshot showed portrait source frames letterboxed inside wide timeline cells; the exposed cell background looked like a gray rail even though there was no per-frame margin.
- The filmstrip background now matches the thumbnail loading-cell background. Existing fixed-width, edge-to-edge cells and timestamp sampling are preserved.
- [ ] Build and inspect a fresh PR #11 emulator screenshot; confirm the gray gutters disappear and frame content remains legible.
- [ ] Confirm on the user's phone. This source change is not yet device acceptance. No version bump; version 395 remains blocked.


## Filmstrip edge-to-edge and delete-anchor follow-up — 2026-11-01

Focused source follow-up on `fix/filmstrip-gap-and-stable-end-controls`:
- Thumbnail decoding now crops the source frame to 16:9 before creating the cached 192×108 bitmap. This removes dark bars baked into the bitmap itself; the previous ImageView `CENTER_CROP` could not remove those already-rendered pixels.
- The filmstrip and thumbnail loading cells use black as the neutral fallback, not a visible gray-blue rail.
- Clip deletion now computes the surviving selected clip and new playhead before rebuilding the timeline. It sets the pending horizontal scroll from that new playhead timestamp, so the rebuilt ruler/time-zero axis and trailing video-add control use the same time origin rather than inheriting a stale scroll position from the deleted clip.
- This is a focused source change only. Fresh build, emulator screenshot, delete-last/delete-middle regression and phone verification remain pending. PR #11 remains draft; version 395 remains blocked.


## Adaptive CapCut-style filmstrip and thumbnail loading — 2026-11-01

Source commits on PR #11:
- `919765d4736d6b9cd7c0464a8d283ad6fc192b11` reuses one `MediaMetadataRetriever` for consecutive frames from the same video instead of reopening the source for every thumbnail. Thumbnail jobs run through one decoder worker with a priority queue, so samples nearest the fixed playhead are decoded first; Media3 remains a bounded fallback for platform decode failures.
- `c2c163eb7a7d24203e9a12820dd0cf84ca923cc1` adds shared filmstrip sizing/sampling math. The number of thumbnail cells follows the actual clip width (`duration × px/sec`): zooming in creates more distinct timestamp samples; zooming out reduces the count. The first and last samples stay within the clip's source range.
- `d66038dfb374bf2debefa8ba9d80e0005b29f68b` adds unit tests for zoom-dependent frame density, bounded cell count and timestamp sampling.
- Empty thumbnail cells use black rather than gray-blue. Decoded source frames are cropped and downsampled into 192×108 cached bitmaps.

**Validation still required:** run unit tests and Kotlin compilation on this exact PR head; inspect the 10-second test video at fit, medium and close zoom; confirm newly revealed cells show distinct frames without long gray/blank waits; repeat with two clips and verify the playhead, ruler, zero anchor and trailing + control remain stable. Physical-phone acceptance is still pending. PR #11 remains draft/unmerged; version 395 remains blocked.


## Editor polish batch — 2026-11-01 (requested; implementation and verification pending)

The user reviewed the current phone build and requested one focused editor polish batch. Keep all work on PR #11; do not create version 395 or merge until the complete batch is implemented and verified.

- [ ] **Preview/filmstrip frame synchronization:** the preview frame at the playhead must match the timestamp represented by the filmstrip under the fixed playhead. Prevent stale/partial frames during fast scrubbing or thumbnail decoding; do not present an old frame as the current timestamp. Preserve source aspect ratio in the preview.
- [ ] **CapCut-style bottom tools:** change Edit/Text/Audio/Project contextual tools from the current panel that appears above the preview to a bottom-anchored, independently scrollable tool drawer above the fixed bottom dock. Keep the preview and timeline usable/visible; handle short screens and the software keyboard without clipping or pushing controls off-screen.
- [ ] **Remove redundant UI copy:** remove the “KLİP DÜZENLEME” heading, the selected-clip/source-duration information line, and the redundant bottom “Başlangıç/Bitiş” status strip. Preserve actual trim controls and editing functionality.
- [ ] **Zero-origin trim invariant:** when a clip is timeline-aligned at 0, trimming its source start must not move its timeline start to the right or create a leading gray gap. Keep source trim time and timeline placement independent.
- [ ] **Dynamic CapCut-style ruler:** use scale-dependent major/minor tick intervals and labels (e.g. seconds and intermediate subdivisions), updating smoothly as timeline zoom changes; avoid a fixed 1-second-only ruler.

**Acceptance before release:** add/update focused math and UI tests; run Kotlin compilation and unit tests on the final PR head; inspect fresh emulator screenshots and exercise preview/filmstrip sync, rapid scrubbing, trim-at-zero, ruler density at multiple zoom levels, bottom drawer on short screen/keyboard, and all retained editing controls on the user's phone. Keep PR #11 draft/unmerged and version 395 blocked until the user accepts the complete batch.


Implementation started on 2026-11-01: source commit `e6cbc2b73012d12afce49b64533d68b89f2ae446` keeps a clip already at timeline zero anchored at zero when its source start is trimmed, removes the redundant clip-panel heading/selected-source line, and moves the contextual tool panel below the timeline immediately above the bottom dock. These are source changes only; panel sizing/keyboard behavior and the remaining preview/ruler requirements are still pending verification and follow-up work.

Follow-up source commit `1be67ecc7d2ba7e71381399863d76ee732a236b4` adds five minor ruler divisions between each adaptive major label and compacts the bottom tool panel in normal/keyboard layouts. This improves ruler granularity and panel placement, but preview/filmstrip exact-frame synchronization remains unimplemented and all visual/device checks are pending.


## Preview sync, CapCut drawer and ruler completion pass — 2026-11-01

New source work on PR #11:
- The zero-time source trim path is now an explicit TimelineVideoMath.trimStartKeepingTimelinePosition operation. When a clip begins at project time zero, advancing/restoring its source in-point no longer moves its project start. Regression tests cover both directions and verify that no leading gap can be created.
- The preview now resolves the fixed playhead's project timestamp to the active clip's source timestamp through TimelineVideoMath.sourceTimeAtProjectTime. When paused/scrubbing, the old rendered frame is covered immediately; a still frame decoded for that exact source timestamp replaces the cover only when ready. Requests are generation-guarded, queued stale preview requests are removed, and no stale/nearby thumbnail is reused as though it were the requested frame.
- The exact paused-frame layer is texture-surface compatible, preserves source aspect ratio and applies the selected clip's preview transform. Timeline gaps intentionally remain black. Playback hides the still layer and returns to Media3.
- The thumbnail strip no longer temporarily displays a different cached timestamp when a cell's own frame is loading; unresolved cells stay neutral black until their exact sample is ready.
- Edit/Text/Audio/Project panels are moved into a rounded, bottom-anchored scroll drawer directly above the fixed tool dock. The panel height adapts for keyboard mode and is scrollable. The redundant persistent status strip is removed from the visible root layout.
- The ruler now uses scale-dependent major spacing plus five minor divisions per major interval. Minor tick offsets are extracted into tested shared math so density changes with zoom.

Still required before acceptance: final PR-head Kotlin compilation and unit tests; emulator screenshots at multiple zoom values; check the portrait/landscape preview aspect ratio; exercise fast scrubbing and verify that the paused preview's source timestamp is mapped from the exact playhead time; verify playback transitions, clip transforms, gaps and thumbnail loading; verify the bottom drawer on short screens and with the keyboard; physical-phone acceptance. The current source is not yet phone/device acceptance. PR #11 remains draft/unmerged; version 395 remains blocked.


## Composition preview surface compatibility — 2026-11-01

- Emulator screenshot workflow #615 exposed a concrete preview initialization failure: `CompositionPlayer` throws `UnsupportedOperationException` when `PlayerView` supplies a `TextureView` (`Use CompositionPlayer.setVideoSurface() for Surface output`). The timeline smoke test consequently recorded zero live playback samples; this is an app surface-type mismatch, not a generic emulator startup failure.
- Changed `editor_preview_player.xml` from `surface_type="texture_view"` to `surface_type="surface_view"`, allowing `PlayerView` to provide Surface output to `CompositionPlayer`. Updated the adjacent source comment. The paused exact-frame `ImageView` remains layered above the player.
- [ ] Run Kotlin compilation and unit tests on the resulting PR head.
- [ ] Re-run emulator preview/playback smoke test and confirm `TIMELINE_PREVIEW_READY`, live playback samples, and no `TIMELINE_PREVIEW_FAILED` surface exception.
- [ ] Inspect fresh screenshots during paused scrub and playback to ensure the SurfaceView does not obscure the exact-frame overlay; verify preview/filmstrip correspondence and aspect ratio.
- [ ] Continue the already-listed bottom-drawer/keyboard, dynamic ruler, zero-origin trim, and phone acceptance checks.

This is a targeted source fix, not yet verified. PR #11 remains draft/unmerged; installed versionCode 394/build 61 is unchanged; version 395 remains blocked.


## Emulator #621 thumbnail comparison correction — 2026-11-01

- #621 launched the app, imported the 8.023s fixture, aligned the 211ms filmstrip cell under the fixed playhead, and captured the preview/filmstrip screenshot. It stopped at the pixel assertion before later scrub/playback/autosave checks.
- Artifact inspection identified a false-failure mechanism in `.github/scripts/assert-thumbnail-alignment.py`: the fixed white playhead overlays the exact center of the thumbnail cell, while the assertion sampled that covered center pixel. The preview sample was blue `(103,102,153)` and the covered thumbnail sample was gray `(215,215,142)` (max delta 113).
- In the same captured screenshot, sampling an interior point offset from the playhead gave matching preview/thumbnail pixels (max delta 0). Updated the assertion to sample a nearby interior point instead of the covered center. Source commit: `53a7f6e1775ced2d43926ccd66a7bb127509d54d`.
- This diagnoses the #621 pixel mismatch as a test sampling defect for this capture; it does not yet establish full preview correctness across content/aspect ratios or rapid scrubbing. The corrected CI assertion still needs a fresh emulator run.
- #621 did not reach forward/reverse scrub, playback synchronization, or autosave assertions. Pinch zoom and clip-edit gestures remain untested. PR #11 stays draft/unmerged; versionCode 394/build 61 unchanged; version 395 remains blocked.

## User correction — complete 9:16 Shorts thumbnails (2026-11-01)

The user's explicit rule supersedes earlier filmstrip notes that requested 16:9 cropping, center-cropping, or black placeholder rails:

- Every thumbnail must show the entire frame at its sampled timestamp: head, arms, legs, objects and all other visible content. Do not crop any edge.
- Shrink the decoded frame proportionally into the thumbnail; never stretch, crop, or add black letterbox/pillarbox padding.
- Since the target source is YouTube Shorts (9:16), the thumbnail cell itself must follow 9:16 geometry. Keep the cell/track dimensions tied to the thumbnail's portrait aspect ratio instead of forcing a landscape 16:9 cell.
- Use aspect-preserving downsampling and FIT_CENTER; do not use CENTER_CROP. A normal 9:16 source must fill its portrait cell without black gaps.
- Keep the frame's real sample timestamp and shared timeline position unchanged. The preview and the filmstrip cell under the fixed playhead must still refer to the same instant.
- If the source frame is not 9:16, retain its complete original aspect ratio rather than silently cutting off people or objects. The current visual target is vertical Shorts footage.
- Source implementation added to PR #11: 162×288 decode target, full-frame fit sizing, portrait cell/lane geometry and focused unit tests. This is source-only until the PR-head test/build workflows pass and the physical phone is reviewed.
- [x] Kotlin unit tests and Android debug compilation passed on PR-head Verify #390 (`41307522355`, head `b215bc902ea374be71857b76904ae5735814d56a`).
- [x] Debug APK build and artifact upload passed on PR-head Build #471 (`41307522234`, same head; artifact `MathMotorVideo-restored-debug`). This is a test/debug APK, not a signed release.
- [ ] Review fresh emulator screenshots to ensure complete 9:16 frames, no cropping and no black gaps.
- [ ] Confirm on the user's phone that all frame content is visible at fit/medium/maximum zoom and that thumbnail timestamps remain aligned with the preview.
- No app version bump or version 395. PR #11 remains draft/unmerged.


## Filmstrip decode performance and frame-based ruler — 2026-11-01

- User priority remains the four filmstrip goals: preserve the full-frame 9:16 thumbnail appearance; keep pinch zoom horizontal-only without distorting thumbnail geometry; keep the thumbnail under the playhead frame-identical to preview; and populate the visible filmstrip quickly without scroll/zoom stalls.
- Performance experiment commit `237a47b00a386505b563975c9bcf3f17bd853536` changes thumbnail extraction from one shared decoder worker to two parallel workers, each owning and releasing its own `MediaMetadataRetriever`. Priority ordering remains in place.
- [x] PR-head Kotlin unit tests and debug APK compilation passed in Verify #393 (`41312913395`).
- [x] Debug APK build passed in Build #474 (`41312913393`).
- [x] Emulator workflow #641 (`41312913945`) passed its live timeline synchronization and draft autosave smoke checks; 17 playback samples were synchronized and the fixed playhead/ruler zero edge aligned.
- [ ] Measure the new filmstrip's 19-frame decode duration from the #641 artifact and compare against the previous 7.5-second baseline. Do not claim a speedup until measured.
- [ ] Confirm preview/thumbnail timestamp alignment and no decoder race/regression under rapid scrub, pinch zoom and scroll.
- [ ] Add CapCut-style frame ruler: as zoom increases, transition from seconds/subdivisions to frame labels such as `1f`, `2f`, `3f`, using the source video's actual FPS (including non-integer frame rates where applicable). Frame labels must represent frame indices/time accurately, not assume every source is 30 FPS.
- [ ] At maximum zoom, support precise frame-level playhead navigation and ensure the paused preview decodes the exact selected frame. Frame ruler and filmstrip must share one time-to-pixel mapping; zoom must not change clip timing or export duration.
- Physical-phone comparison and acceptance remain open. PR #11 stays draft/unmerged; do not bump version or create version 395.
