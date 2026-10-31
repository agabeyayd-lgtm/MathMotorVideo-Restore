# MathMotor Video

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
- [ ] Keep one unified timeline track/ruler only; do not show a duplicate second timeline or a separate duration/zoom strip. The assembled clip sequence is the single source of truth for total duration.
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
- [ ] When a bottom tool such as Edit, Audio, Text or Export is selected, replace the four-item bottom dock with that tool's own controls in the same bottom panel. Do not open the tool menu underneath/behind the preview. Provide a clear back/confirm route to restore the four-item dock when the task is finished.
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
- [ ] Make every timeline element independently movable: video clips, each text layer, music and each sound effect have their own lane item and independently editable start/end times.
- [ ] Moving, trimming, selecting or deleting one element must not move or modify other elements. Only an explicit multi-select/group/link action may affect multiple elements.
- [ ] Allow timeline elements to overlap in time; all lanes share the same ruler, zoom and horizontal scroll coordinate system.
- [ ] Keep clip thumbnails visible as a continuous filmstrip across the clip; show a clear seam between adjacent clips without separate “Video 1/2” headers.
- [ ] Keep editing controls contextual: show only the selected element's relevant actions; return to a compact tool dock when selection is cleared or the tool is closed.
- [ ] Verify independent placement, overlap, trim and save/reopen for two video clips, two text layers, music and a sound effect. Confirm moving one item leaves all others unchanged.

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

- Independent video project positions, body drag, edge trim and persistence are now implemented in PR #11. Verification of the updated timeline row/layer ordering is pending CI; actual touch gestures, preview/export output and save/reopen still require device validation.
- Music source in/out selection is now implemented separately from project-timeline placement; CI and device/export validation are pending.
- Verify overlap and exact synchronization across all items in the shared timeline, live preview, MP4 export and saved/reopened projects.
- Test thumbnail clarity/load behavior, actual finger gestures, audio sync, small screens and keyboard behavior on a device.
### Independent video timeline — implementation added, verification pending (2026-10-31)

- Every video clip stores a project-timeline start separately from source in/out. Existing version-1 projects without this field load sequentially; new saves persist each position.
- Video clips are placed at shared timeline coordinates. Overlapping ranges render on separate rows; dragging a filmstrip moves only that clip; left/right trim maintains source and project boundaries.
- Preview and export share one flattened video sequence: the last clip in layer order is visible during overlap and empty intervals become black/silent. Timeline rows now follow that same layer priority, so the topmost overlapping item is shown above lower layers. This is top-layer occlusion, not alpha blending, crossfade or picture-in-picture.
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
