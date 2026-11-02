# MathMotor Video

## New editor layout target — CapCut-inspired mobile arrangement (2026-11-02)

**Status: DESIGN TARGET RECORDED — implementation and verification not yet started.** This is a layout-only redesign of the existing editor, based on the user's complete 720×1600 portrait reference specification. The installed 394 / Build 61 experience remains the baseline; do not create version 395 or publish an APK for this documentation update.

### Non-negotiable scope

- Change only layout, sizing, positioning and ordering. Preserve existing colors, icons, typography, button styling, labels and visual language.
- Do not rewrite editor logic, replace the editor screen, remove working controls or alter media/project data behavior.
- Make the layout responsive to actual device dimensions and insets. The 720×1600 coordinates below are a reference composition, not hard-coded pixel dimensions.
- Preserve all existing actions and connect moved controls to the same existing handlers/state.
- Keep the approved editor source, shared timeline coordinate system, source-frame-rate-aware ruler/zoom math, thumbnail alignment and fixed-center playhead behavior. Do not revive interim editor implementations.

### Target vertical composition (720×1600 reference)

1. Status/system bar: approximately y=0–60.
2. Existing top bar: approximately y=60–150; keep X, search, `YZ UHD ▾`, and `Dışa Aktar` in their current style.
3. Video preview: approximately y=150–860; centered, about 400 px wide in the reference, with side space. Reduce its current oversized footprint. Remove the fullscreen control from inside the preview and move it to the control row.
4. Playback/control row: approximately y=860–925. Left: fullscreen and current/total time. Center: existing play action without its enclosing box. Right: existing undo/redo actions. Keep `Sığdır` compact on this row. Undo/redo must retain their current behavior; do not imply they become global project history as part of this layout-only task.
5. Timeline workspace: approximately y=940–1380 (about 440 px in the reference, roughly 27% of the full screen). It must be a clearly separated, usable multi-lane area; the video filmstrip must not consume the entire vertical space.
   - Ruler near y=957 with sparse labels such as `00:00` and `00:05`, replacing dense `0 1 2 ... 10` labels while preserving accurate time mapping and scrubbing.
   - A thin vertical playhead aligned across ruler and all lanes, from approximately y=990 to y=1370 in the reference.
   - Video lane around y=1015–1110 (about 95 px reference height), with visible, distinct frame thumbnails. Add a 55×55 reference `+` affordance at its right edge as visual-only.
   - Two compact controls to the left of the video lane: `Klip sesini` and `Kapak`. `Klip sesini` must call the existing clip-audio mute/unmute behavior. `Kapak` is visual-only.
   - Audio lane around y=1137–1192 with a small music-note tile and `Ses ekle` affordance.
   - Text lane around y=1207–1262 with a small `T` tile and `Metin ekle` affordance.
   - Small round `YZ` visual-only affordance near the lower-left of the timeline.
   - Move the current speaker/mute control into the `Klip sesini` control; do not create a second independent mute state.
   - Keep video, text, music, sound effects, ruler, playhead and trim boundaries on the same shared time coordinate system. Scrolling/zooming must not alter layer timestamps.
6. Bottom tool navigation: approximately y=1380–1510; horizontal scrolling. Order: `Düzenle`, `Ses`, `Metin`, `Efektler`, `Bindirme`, `Yazılar`, `Proje`, with the next item partly visible at the right edge. Preserve existing styling; icon above label. Existing `Düzenle`, `Ses`, `Metin`, and `Proje` functions remain connected.
7. System navigation area: approximately y=1510–1600, respecting the device's real system insets.

### Clip selection and tool behavior

- The video clip is unselected on initial editor entry: no selection border or trim handles.
- Tapping a clip selects it and opens its contextual bottom tool row.
- Move these existing actions out of the always-visible main layout and into that selected-clip tool row: `Önizle`, `Kopyala`, `Klibi kaldır`, `Böl`, `Öne al`, `Sona al`, `Klip geri al`, `Klip yinele`, and the `Başlangıç/Bitiş` controls. Preserve their current handlers and exact behavior.
- `Ses ekle` opens the existing `Ses` tool; `Metin ekle` opens the existing `Metin` tool.
- The four existing tools `Düzenle`, `Ses`, `Metin`, `Proje` remain functional in the bottom navigation.
- `Kapak`, `Efektler`, `Bindirme`, `Yazılar`, the video-lane `+`, and `YZ` are appearance-only placeholders. Do not implement new feature logic. If tapped, show a brief `Yakında` message at most; no navigation into broken or empty flows.

### Layout acceptance checklist — all remain open until verified

- [ ] Recompose the existing editor screen into the specified vertical order without replacing its underlying editor implementation.
- [ ] Scale preview down and move fullscreen control to the playback row.
- [ ] Add compact playback row with current time, play, existing undo/redo and fit controls.
- [ ] Expand timeline workspace to the intended share of available screen height; constrain filmstrip height so separate audio/text lanes remain visible.
- [ ] Implement sparse ruler labels without changing time-to-pixel accuracy, seek behavior, shared lane alignment or pinch-anchor behavior.
- [ ] Render video, audio and text lanes with the requested relative heights and left-side controls.
- [ ] Keep playhead visually continuous/aligned across all timeline lanes.
- [ ] Make initial clip state unselected; tapping selects it and reveals the contextual clip action row.
- [ ] Move all listed clip actions and start/end controls into that row without changing their handlers.
- [ ] Wire `Klip sesini` to the existing clip mute/unmute state.
- [ ] Wire `Ses ekle` and `Metin ekle` to existing tool destinations.
- [ ] Add the specified visual-only placeholder affordances with safe `Yakında` feedback.
- [ ] Add horizontally scrollable bottom navigation in the requested order; preserve the four existing tool destinations.
- [ ] Verify responsive layout on the 720×1600 reference and at least one smaller/shorter phone viewport; no hard-coded screen-pixel positioning.
- [ ] Verify keyboard-open layout, system bars/insets, touch targets and that no lane is hidden or overlapped.
- [ ] Verify existing clip select, preview, copy, remove, split, reorder, trim, undo/redo, mute, play/pause, seek, pinch zoom, horizontal scroll, text/audio tools, project save/resume and export behavior.
- [ ] Capture before/after emulator screenshots and review actual layout, not only build success.
- [ ] Perform physical-phone acceptance before release.

**Important:** This is a layout-only target. Existing functional defects already listed elsewhere in this document remain separate work and must not be marked fixed by this redesign. A successful compile or screenshot alone does not prove preserved functionality. Keep versionCode 394 / Build 61 until all release gates pass and the user explicitly authorizes version 395.


## Canonical mainline state — 2026-11-01

- Emulator run #687 itself remains red because its full-log capture was truncated. The filtered-log correction was validated by run #688: the preview pixel exactly matched the centered 211ms filmstrip frame (maximum RGB channel delta 0), all 4/4 visible thumbnail samples were distinct, forward/reverse filmstrip scrubbing passed, and live playback synchronization passed across 18 samples. See the 2026-11-02 mainline verification section below.
- The sole active development source is `main`. The user-approved editor baseline from `b82245d02ad9f51a1cca6795ba33cd31360c8de4` was integrated by PR #16; merge commit: `668ed1c0653a05871b3ffb855cc3a3b3aa61435d`.
- The editor implementation and timeline math were restored from the approved source, including the approved `VideoEditorActivity.kt`, source-frame-rate-aware ruler/zoom math, filmstrip sampling/alignment, fixed center playhead and independent timeline layout. The app UI was not replaced with the interim `main` editor.
- PR-head verification passed: Verify Android build #452, APK build #488, and API 35 emulator screenshot/editor verification #669 (source commit `934d9011c8afd65e4764a39b2411a50cd216047d`; same app source tree as the merge).
- Emulator checks confirmed APK installation, home/editor opening, deterministic video load, thumbnail/preview frame agreement at 211 ms, forward/reverse filmstrip scrubbing, live playback synchronization (16 samples; two synchronized lanes; fixed playhead at screen center), and draft autosave/resume navigation.
- The initial emulator attempt was blocked by a Nexus Launcher system ANR, not a MathMotor app ANR. The harness was corrected to close the stalled launcher only; the successful #669 run then completed.
- The automated emulator run is not a physical-phone acceptance test. Confirm installation and touch behavior on the user's phone separately.
- Keep the installed baseline at **versionCode 394 / Build 61**. Version 395 remains blocked; do not change version metadata or publish a release without the user's explicit authorization and completion of the release checklist.

## Mainline verification update — 2026-11-02 (runs #441, #688 and #489)

This section supersedes older “pending” statements below where newer run evidence is available.

- **Run #688 — API 35 emulator smoke flow passed** ([Actions](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41352473376)). The debug APK installed, the home/editor opened, and the deterministic 8.023-second fixture loaded. At 211ms the preview and the centered filmstrip thumbnail matched exactly (RGB comparison delta 0); all 4/4 visible thumbnail samples were visually distinct. Forward and reverse filmstrip scrubbing passed. During playback, 18 samples advanced from 333ms to 4716ms; the ruler and video lane stayed synchronized, and the playhead/Play center stayed at x=160px. Draft autosave and return to the draft list also passed.
- **Run #489 — unit tests and debug build passed** ([Actions](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41352479949)): `testDebugUnitTest` and `assembleDebug` completed successfully.
- **Run #441 — focused API 35 gesture test passed** on PR #15. It checked pinch-out, pinch-in, the five-second ruler anchor within 12px after zoom, and one-finger VIDEO-lane scrolling that moves the ruler with it.
- Current main source already renders adaptive second/subsecond ruler intervals and source-frame ticks at close zoom. Unit tests cover interval changes, source-frame rates, pinch-anchor math and thumbnail sample sequencing. The feature is implemented; visual verification across every zoom state and on a physical phone remains open.
- Current source audit confirms a real gap: Undo/Redo stores `ClipEditSnapshot` only and the visible actions are “Klip geri al/yinele”. Text and audio changes are not covered by one shared project history.
- The #688 screenshots show one ruler/time header and no old green zoom slider, +/- zoom row, “Klip 1/2” labels or timeline navigation arrows. This is screenshot-level confirmation on the API 35 emulator, not full interaction/device acceptance.

**Still not accepted:** trim-handle and clip-selection interactions; full timeline endpoint movement after zoom; text/audio lane editing and alignment across layers; text timing/size in the live preview and export; global Undo/Redo; two-or-more-clip edit/export; MP4 content/audio/duration checks; project save-close-reopen; thumbnail fill latency/cache reuse/black-placeholder behavior across portrait, landscape and square sources; keyboard and physical-phone use. Keep versionCode 394 / Build 61 and do not create version 395 until those release checks pass and the user authorizes it.

- The older `Timeline frame/zoom repair` notes below describe an interim implementation that was superseded by the approved editor baseline. They are historical context, not the current source-of-truth description.


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

## Timeline frame/zoom repair — 2026-11-01 (implementation integrated; core API 35 checks verified)

A focused source review found that the existing timeline already shares one millisecond-to-pixel scale across its lanes and caches timestamped thumbnails, but it did not have a project-frame conversion layer. The timeline zoom controls also had no two-pointer pinch detector, and zoom did not preserve the time under a gesture anchor. The current UI code does not contain a dedicated adaptive ruler renderer; adaptive ruler ticks/labels therefore remain open work, not an existing feature.

### Changes made on `fix/frame-accurate-timeline-zoom`

- Added `TimelineFrameMath`: integer frame/time conversion with a 30 FPS default project rate; frame 1 maps to approximately 33 ms, frame 2 to 67 ms, and frame 30 to 1,000 ms. Milliseconds remain the storage/Media3 boundary, while user seek positions are snapped to the nearest project frame.
- Applied frame snapping to timeline seek requests and to the displayed playhead position synchronized from playback.
- Added a two-finger pinch recognizer scoped to the timeline area. It calculates the time beneath the pinch focus and restores that same time to the same screen offset after zooming, within the scrollable range.
- Added unit tests for 30 FPS boundaries, nearest-frame snapping, long-timeline conversion consistency, custom FPS values and invalid FPS rejection.
- Extracted pinch-anchor scroll positioning into shared timeline scale math and added a regression test proving the same timestamp remains under the viewport anchor after zoom.
- Enabled the unit-test/debug-build verification workflow for pull requests; this validates source without publishing an APK.
- Added `TimelinePinchGestureTest`, a focused Android UI Automator test against the deterministic eight-second emulator video. It checks pinch-out, pinch-in, five-second ruler-anchor stability, and one-finger scrolling on the video lane moving the ruler in sync.
- Wired the API 35 emulator and targeted gesture test into the already PR-triggered `Verify Android build` workflow. The dedicated screenshot workflow remains available for manual runs and main-branch pushes.
- Split the pinch interaction check into `.github/scripts/run-timeline-pinch-test.sh` and a single-process Bash wrapper `.github/scripts/run-emulator-checks.sh`. The screenshot flow stages the deterministic fixture; the gesture test no longer reinstalls the APK afterwards, avoiding invalidation of that app-private file. The instrumentation assertion uses the absolute app-private fixture path.
- Run #426: unit tests/Debug APK compile and screenshot/autosave flow passed, but pinch instrumentation stopped before gesture dispatch because the fixture was missing after the second APK installation. The emulator-runner inline multiline shell block also ended with a shell syntax error. Pinch behavior and anchor/lane synchronization remain unverified; harness corrections are now committed and awaiting rerun.
- Run #430: unit tests/build, deterministic fixture generation/staging, editor video-load, screenshots and autosave all passed. The pinch instrumentation's `UiDevice.executeShellCommand("run-as ... test")` fixture check failed before touch events, although the preceding ADB script confirmed the file exists in the target app's private directory. The check was changed to `InstrumentationRegistry.getInstrumentation().targetContext.filesDir`.
- Run #432: unit tests/Debug APK build, fixture staging, editor video-load, screenshots and autosave passed. The instrumentation test now started, but `TimelinePinchGestureTest.pinchKeepsTimeAnchorAndVideoLaneSwipeMovesRulerWithIt` failed and Gradle reported that the instrumentation process crashed. The job log did not include the test's crash/stack details, and the uploaded artifact was captured before the gesture test, so this run does **not** establish whether pinch behavior itself is wrong. The test script now captures post-test logcat, process/activity dumps and Android instrumentation XML/HTML reports even when Gradle fails; use that evidence on the next run to identify the exact crash before changing gesture behavior.
- No app version metadata was changed. Version 395 remains blocked.

### Still open / not yet proven

- [x] Run `testDebugUnitTest` and `assembleDebug` — GitHub Actions run [#413](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41318203123) completed successfully on commit `55d8cfd53266f7a858a39a078974df6d9aa5da3b`.
- [x] Run `TimelinePinchGestureTest` on API 35: PR #15 run #441 passed pinch-out/in, five-second ruler-anchor stability within 12px, and one-finger VIDEO-lane scrolling that moves the ruler with it.
- [ ] Confirm on a real phone that the timeline gestures do not interfere with trim handles, text/audio lane dragging, or clip selection.
- [x] Implement adaptive ruler-grid rendering: main source selects readable second/subsecond intervals and source-FPS frame ticks at close zoom; unit coverage passed in #489. [ ] Verify rendered labels at zoomed-out, second-level and frame-level zoom on an interactive emulator and physical phone.
- [ ] Complete full center-playhead acceptance. #688 verified that the playhead/Play control remain centered during playback while ruler and video lane track player time, and forward/reverse scrubbing passed. Still verify split-at-playhead semantics and multi-clip behavior.
- [x] Verify preview/filmstrip frame agreement on the deterministic API 35 fixture: #688 matched the preview and centered 211ms thumbnail exactly (RGB delta 0). [ ] Extend this check to varied real source videos, frame rates and orientations.
- [ ] Verify thumbnail density, fill latency, cache reuse and preview correspondence across zoom levels. #688 verified 4/4 visible samples are distinct and the centered 211ms sample matches preview; it did not measure progressive loading or zoom-out reuse.

Do not mark the existing release checklist items complete until the relevant automated and device checks pass. This source work is not a release APK and does not authorize version 395.

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

- [ ] Complete one-finger acceptance: #441 verified a VIDEO-lane swipe moving the ruler and #688 verified forward/reverse filmstrip scrubbing; trim handles, clip selection and text/audio-lane gestures still need tests.
- [x] Implement and API 35-test core two-finger pinch zoom with anchor preservation: #441 passed pinch-out/in and five-second ruler-anchor stability. [ ] Verify it on the physical phone and alongside trim/text/audio gestures.
- [ ] Fix the timeline appearing locked to the left; allow moving across the full timeline in both directions and reaching the beginning/end after zooming.
- [ ] Make the playhead behave like CapCut: timeline moves beneath a stationary playhead during scrubbing; preview follows the exact time; playback stays synchronized; split uses the playhead time.
- [ ] Keep time-to-pixel mapping correct after scroll, zoom, trim, clip reorder and multi-clip edits.
- [ ] Keep one unified timeline track/ruler only; do not show a duplicate second timeline or a separate duration/zoom strip. The assembled clip sequence is the single source of truth for total duration.
- [x] Implement adaptive ruler labels: current source and unit tests cover coarse seconds, subsecond spacing and source-frame ticks at close zoom. [ ] Verify visual transitions at multiple zoom levels in an interactive run and on a physical phone.
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
- [x] Ensure distinct visible samples come from distinct timestamps: #688's time-varying fixture produced 4/4 visually distinct visible thumbnail cells. [ ] Extend to varied real source videos and zoom/reopen scenarios.
- [ ] Reduce black placeholders and loading delay; show a useful loading state and replace placeholders promptly as frames become available.
- [ ] Cache/reuse extracted frames and avoid blocking timeline scrolling or editing while thumbnails are prepared.

### Text, audio and undo/redo

- [ ] Make text start/end time editable and ensure the text timeline strip can be trimmed at both ends.
- [ ] Make text size reliably adjustable; preview updates immediately and exported output matches the preview.
- [ ] Verify text timing, placement, size and style remain correct after seeking, zooming, saving/reopening and exporting.
- [ ] Replace clip-only history with shared project Undo/Redo: current source snapshots clips only; text and audio operations are not included. Then test text add/delete/timing/size, audio/music, clip trim/split/reorder/move, and preview restoration.
- [ ] Ensure undo/redo restores both timeline state and preview consistently.

### UI and controls

- [x] Remove the separate bottom timeline-control strip: #688 screenshots show a single ruler/time header and no duplicate green zoom slider or +/- zoom row.
- [x] Implement core CapCut-style pinch gestures: API 35 run #441 verified pinch in/out and anchor stability; physical-phone and trim/text/audio conflict checks remain separate.
- [x] Remove the visible "Klip 1" / "Klip 2" labels: absent in #688 editor screenshots.
- [x] Remove timeline navigation/scroll/zoom arrow buttons: absent in #688 screenshots; direct pinch and one-finger video-lane scrolling passed the focused #441 gesture test.
- [ ] Keep controls compact and balanced while retaining accessible touch targets and all existing actions.
- [ ] When a bottom tool such as Edit, Audio, Text or Export is selected, replace the four-item bottom dock with that tool's own controls in the same bottom panel. Do not open the tool menu underneath/behind the preview. Provide a clear back/confirm route to restore the four-item dock when the task is finished.
- [ ] Make the two-video join/add-clip area visually clear and uncluttered after removing timeline arrow buttons and clip labels; do not remove or change the underlying add, order or join functionality.
- [ ] Give the preview the space reclaimed from redundant labels and oversized controls; keep the preview and timeline responsive on small screens.

- [ ] Simplify the home screen: remove redundant menus and explanatory clutter; prioritize choosing/opening a video and entering the editor.
- [ ] Make the home screen and editor responsive to small screens, available height and the software keyboard; controls must remain reachable and not overlap.
- [ ] Remove redundant scissors icons. Reserve scissors for cut/split actions; make trim, split, delete and other actions visually distinct.
- [ ] Keep controls concise, consistently placed, accessible and functional rather than duplicating the same action in multiple places.

### Required validation before version 395

- [x] Run unit tests for timeline mapping, trim bounds, playhead-to-clip mapping and text timing: `testDebugUnitTest` passed in #489.
- [ ] Run an interactive Android emulator test covering one-finger scroll/trim, two-finger zoom, playhead scrubbing/sync, thumbnail variety/loading, text timing/size, undo/redo and home-screen navigation.
- [ ] Test with a 10-second video and a multi-clip sequence; check the beginning, middle and end of the timeline at multiple zoom levels.
- [ ] Export an MP4 and verify video, text, audio, duration and playhead cut position; confirm the original input video is unchanged.
- [ ] Save a project, close/reopen it and verify clips, text, audio, duration, zoom and timeline position.
- [ ] Review emulator screenshots and logs; fix regressions and rerun failed checks.
- [ ] Perform a final physical-device smoke test when possible.
- [ ] Only after every required item is complete and verified, prepare version 395. Until then, do not bump the app version or publish an APK.

## Verification boundaries

- Existing unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- The current emulator screenshot workflow builds a debug APK and captures screens, but its script does not by itself prove all touch gestures, exports, Undo/Redo or save/reopen flows work. Add/run interaction-level tests rather than treating screenshots alone as acceptance.
- Record a checkbox as complete only after the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, leave the item unchecked and say so.
- This repository is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree.


## API 35 pinch-test harness correction — 2026-11-01

- Inspected the #435 diagnostic artifact and the instrumentation source. The test itself ran `am force-stop com.mathmotor.video.next` while executing against that package; this could terminate the active target/instrumentation process and explains the generic process-crash result before the gesture assertions.
- Removed that force-stop command. The test clears stale logcat and launches the test editor activity without terminating the active instrumentation.
- The fix only addresses the test harness. Pinch-in/out, anchor preservation and ruler/video-lane synchronization remain **unverified** until the corrected API 35 emulator test passes.
- Version 395 remains blocked; no version metadata or release artifact was changed.


## Run #437 finding and compact-layout ruler fix — 2026-11-01

- Unit tests and Debug APK compilation passed. The API 35 instrumentation now ran (the previous force-stop crash was eliminated), but its first assertion failed because the five-second ruler marker was absent.
- Root cause: the editor omitted the entire `SÜRE` ruler whenever `compactEditorLayout` was true. The 320×640 emulator uses compact layout, so the test could not find `00:05`.
- Updated the editor to retain a compact 18 dp time-ruler lane on short phone screens; it is still hidden while the keyboard is active. This makes timeline time navigation available on compact screens rather than weakening the test.
- Corrected build and API 35 gesture verification are pending. Pinch behavior and ruler/video-lane synchronization are not yet marked complete. Version 395 remains blocked.


## Run #439 — pinch zoom input correction — 2026-11-01

- Run #439 passed unit tests, Debug APK compilation, deterministic video generation/staging, editor load, screenshots and draft autosave. The focused API 35 gesture test then failed at `Pinch-out did not zoom in enough: 1.0454545`; this is the actual failure, while the later emulator shutdown messages are cleanup noise.
- Replaced the timeline's `ScaleGestureDetector` callback accumulation with direct two-pointer span sampling on every `ACTION_MOVE`. The recognizer is scoped to the timeline frame, requires both fingers inside that frame, takes over the gesture after cancelling the initial one-finger child touch, and applies each current/previous span ratio.
- The pinch anchor still uses the midpoint between the two fingers and the shared timeline time/pixel scale; on gesture end, the same project time is restored beneath that viewport offset. A pinch outside the timeline does not affect timeline zoom.
- Added unit coverage proving that a 48 px to 184 px pinch accumulates to the full 3.83× span ratio across multiple intermediate movements, and that pinching inward reverses the scale.
- This is a source-level input correction and unit-test addition. The API 35 end-to-end gesture test must pass before pinch, anchor stability or ruler/video-lane synchronization can be marked verified. Version 395 remains blocked; version metadata was not changed.


## Timeline one-finger scroll conflict — 2026-11-01

- Follow-up source review found that each video filmstrip had a custom touch listener which called `requestDisallowInterceptTouchEvent(true)` on DOWN and consumed every event. That prevented its parent `HorizontalScrollView` from performing the required one-finger horizontal timeline scroll.
- Removed that bespoke filmstrip touch handler. Clip tap selection and the existing long-press action menu remain; horizontal swipes are now left to the shared horizontal scroll lane, whose scroll listener synchronizes the ruler and other timeline lanes.
- This is an input-routing correction. Verify shared lane movement and clip selection in the API 35 instrumentation run; do not mark the interaction complete until it passes. The previous in-progress emulator run may be superseded by this commit. Version 395 remains blocked.

## Run #664 — API 35 launcher ANR recovery — 2026-11-01

- Run #664 successfully built the debug APK, generated the deterministic video fixture, installed the APK, and launched `com.mathmotor.video.MainActivity`. Logcat records `Displayed com.mathmotor.video.next/com.mathmotor.video.MainActivity` after 2.584 seconds.
- The screenshot smoke step then stopped with exit code 8 because the captured accessibility hierarchy contained the Android system dialog `Pixel Launcher isn't responding`. Window diagnostics attribute the ANR to `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`, not MathMotor. No MathMotor fatal-exception or app-owned ANR was identified in the captured evidence.
- At #664, the system-ANR allowlist recognized `Process system` and `System UI` but not Pixel/Nexus Launcher. The launcher dialog was therefore classified as an app-level ANR and the script exited before tapping either dialog action. Commit `1baa987` added launcher recognition; run #665 then completed the screenshot/editor/autosave smoke flow. That run did not show a launcher ANR, so it did not exercise the recovery action.
- The follow-up harness change chooses `Close app` specifically when a Pixel/Nexus Launcher ANR is actually present; other explicitly system-owned ANRs retain `Wait`. MathMotor-owned ANRs remain hard failures and are never dismissed as emulator noise.
- This was an emulator-test recovery change only; editor source, versionCode 394, versionName and release state remain unchanged. The full screenshot/editor/autosave path was subsequently verified on main in #665 and #666. Physical-device behavior is still unverified. The launcher-specific `Close app` recovery path in #666 was not exercised because no launcher ANR dialog recurred; version 395 remains blocked.


## Run #441 gesture verification and main smoke runs #665/#666 — 2026-11-01

- PR #15 verification run #441 passed unit tests, Debug APK assembly, the API 35 screenshot/editor/autosave flow, and the focused `TimelinePinchGestureTest`. Instrumentation log: `Finished 1 tests on emulator-5554 - 15`; Gradle reported `BUILD SUCCESSFUL in 48s`. PR #15 was subsequently merged into the single `main` line.
- The focused gesture test verifies: pinch-out increases timeline zoom to at least 1.4×; the visible `00:05` ruler anchor remains within 12 px of its original screen position after pinch-out; pinch-in reduces zoom; and a one-finger swipe on the VIDEO lane moves the ruler marker by more than 12 px. This confirms those specific interactions on the API 35 emulator, not every timeline behavior.
- Main-branch runs #665 and #666 both passed the screenshot/editor/autosave smoke flow. In #666, APK assembly and installation succeeded, the app opened, the eight-second fixture was inserted (`durationMs=8023`, `clipCount=1`), and the draft autosave marker was recorded. Home, empty-editor, video-editor and drafts-home screenshots were uploaded.
- No launcher ANR appeared in #666's captured accessibility hierarchies, so the newly added Pixel/Nexus Launcher `Close app` fallback was not exercised. The earlier #664 recognition failure remains explained by the missing launcher allowlist entry, corrected in commit `1baa987`.
- These checks do not verify thumbnail-frame fidelity, every trim/drag interaction, audio/text editing, export correctness, Undo/Redo, or physical-device behavior. Keep the broader README acceptance checklist open and version 395 blocked. The screenshot workflow uploads diagnostics/screenshots, not a signed release APK.



## Filmstrip thumbnail responsiveness work — 2026-11-01

Source changes now on `main` (Build 61 remains the installed baseline; version 395 is still blocked):

- Pause/Stop now keeps the last rendered preview visible while the exact still frame is requested. If that still-frame decode fails during pause, the app no longer replaces the valid last frame with a black cover.
- Removed the `OPTION_CLOSEST_SYNC` thumbnail fallback. It can return the same GOP keyframe for different requested timestamps; the normal closest-frame path remains, with Media3 as the fallback when platform decoding fails. The Media3 fallback extractor is now reused per decoder worker/source URI instead of being rebuilt for every cell.
- Filmstrip cells now expose their source-time intervals. When zooming out, a cell can immediately reuse an already decoded thumbnail that falls inside that cell's time range. The cell nearest the playhead remains exact to protect preview alignment.
- Removed the Shorts-only 9:16 cell geometry. The video lane retains its portrait-reference height, while each cell's width is derived from the actual decoded frame aspect ratio. Portrait, landscape and square sources are fitted without cropping or stretching. A restored/imported clip uses the portrait fallback only until its first thumbnail reveals the actual ratio; the cached frames are retained when the lane is rebuilt.
- Added unit coverage for filmstrip cell ranges and portrait / landscape / square thumbnail sizing.

Validation:
- Verify Android build #474: unit tests and `assembleDebug` passed on commit `283a09e341489d77aac964f1bd944a0250bcd2b1`.
- API 35 run #682 initially failed the strict pixel check (preview RGB `(0, 0, 255)` vs thumbnail `(102, 0, 255)`). Mainline run #688 then passed the frame check at 211ms with exact RGB agreement and 4/4 visually distinct visible samples.
- Still required before calling this complete: prove distinct timestamps produce distinct fixture frames, measure initial filmstrip fill time, verify zoom-out causes no unnecessary re-decodes, exercise Play/Stop visually, and capture landscape/square filmstrips. No physical-phone acceptance is claimed.
- No app version metadata was changed. No APK is being offered as a replacement, and version 395 remains blocked.


## Ruler header refresh crash — 2026-11-02

- Emulator screenshot workflow #685 (run ID 41349936764) failed after the debug APK built and the editor test entry launched. Its diagnostic log records `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.refreshTimelineTracks$makeLane` while reattaching `timelinePlayheadLabel` to the rebuilt ruler header. The 30-second clip-import timeout was a downstream symptom; the app had already crashed.
- Root cause: responsive-layout handling invokes `refreshTimelineTracks()` a second time. `timelineTracks.removeAllViews()` detaches the old lane row, but does not detach its nested label. Reusing the same label in the new ruler header therefore attempts to add a View that still has a parent.
- Main commit `bf09c70efe0fc9d63aeff5437b369a38c341b825` now explicitly removes the shared label from its previous `ViewGroup` before adding it to the newly built ruler header. This preserves the single shared playhead/time label and the 96dp ruler/lane alignment.
- Verify Android build #479 passed before this correction; #685 did not pass and must not be treated as editor acceptance. The correction still requires a new green emulator launch/import run, followed by screenshot review.
- Version 394 / Build 61 remains the user-approved baseline. Version 395 remains blocked; no replacement APK or release is approved by this fix alone.


## Fixed playhead time mapping after 96dp ruler header — 2026-11-02

- Emulator run #685 exposed a view-parent lifecycle crash during a responsive timeline rebuild. The shared ruler time label remained parented to the detached old header. Main commit `bf09c70efe0fc9d63aeff5437b369a38c341b825` explicitly removes that label from its prior `ViewGroup` before placing it in the rebuilt header.
- Emulator run #686 confirmed that the crash no longer occurred: the editor opened, the 8.023-second sample clip was imported, and the thumbnail cell was aligned beneath the fixed playhead. It then failed the strict scrub assertion: the centered thumbnail represented 211ms, while preview decode events were at 542ms and 583ms.
- Root cause of the #686 mismatch: the ruler/media header had been widened from the old 54dp grip plus 3dp row inset (57dp) to a 96dp header plus the same 3dp inset (99dp), but the origin spacer, playhead-time mapping, and fit-width calculations still used 57dp. This shifted the time readout by roughly 39dp (about 330ms at the smoke-test scale).
- Main commit `102bdcd899b397dc439d1c2c3193b651e57fd6d6` now centralizes the actual 99dp gutter for timeline origin/end spacers and fit-to-project width. The strict 35ms preview/frame check remains unchanged. Unit geometry tests were updated in commit `3a6d6b474c6eb6cf24f8f0fb01a34601a1240653` to use the new header geometry.
- Emulator run #687 is the first retest of the corrected mapping; its result must be green before accepting the fix. Verify build #484 is running the updated geometry tests. Neither result is yet a physical-phone acceptance.
- Keep versionCode 394 / Build 61 as the user-approved baseline. Version 395 remains blocked; no replacement APK, signed release, or update is authorized by these changes alone.


## Chat handoff — 2026-11-02 (latest user checkpoint)

**Purpose:** This section is the restart point when continuing from a different ChatGPT account/session. Read this section first, then reconcile it with the latest GitHub branch, commits and Actions before changing code.

### Where we stopped

- Project: `agabeyayd-lgtm/MathMotorVideo-Restore`.
- The approved working baseline remains **main / versionCode 394 / Build 61**. Do not create version 395, change version metadata, sign/publish a release, or offer an update until the release gates are complete and the user explicitly authorizes it.
- The user has a TEST ZIP described in the conversation as containing `app-debug.apk`; the archive was reported to open successfully. **That only confirms the ZIP archive was readable; installation and real-phone runtime behavior have not yet been confirmed.** The exact ZIP filename, source branch/commit and Actions run that produced this latest ZIP were not established in the handoff conversation. Do not claim it is a main build or a release APK until its provenance is checked.
- Do not assume the latest ZIP is the same as the previously documented PR #11 artifact. Identify its source commit/workflow before using it as evidence.
- No new release/version should be created as part of this handoff.

### Latest user-reported issues to verify on the phone

1. **Fullscreen exit control:** after entering fullscreen, the control that returns to the normal/smaller preview is positioned too close to the bottom edge and is difficult/impossible to reach. Move it into a safe, reachable control area and respect system/navigation insets; verify on the actual phone.
2. **Filmstrip thumbnail speed:** thumbnail generation/loading appears slow again. Measure time to first visible thumbnail and full visible-strip fill; inspect decode scheduling, caching and progressive visible-first loading before changing implementation. Do not sacrifice frame accuracy or preview/thumbnail synchronization.
3. **Cover image:** the cover/thumbnail image is not being generated/shown. Reproduce and trace the cover-generation/display path; distinguish a missing image from a delayed/failed decode. Do not mark fixed until tested.

These are fresh phone observations and are separate from the existing #688 emulator evidence. #688 passed deterministic-fixture frame agreement (211ms preview/thumbnail RGB delta 0), 4/4 distinct visible samples, forward/reverse scrubbing and 18 playback synchronization samples. It did **not** prove fast thumbnail loading, cover generation, or physical-phone behavior.

### Exact next steps

1. Inspect GitHub `main`, open PRs/branches and the latest APK ZIP/workflow metadata. Establish the exact source commit and branch for the ZIP before treating it as the test candidate.
2. Keep the user's installed baseline at 394 / Build 61; do not bump version or create 395.
3. Reproduce the three reported issues on the supplied test APK/phone and capture device dimensions, Android version, logs and screenshots/video where possible.
4. Trace and fix the fullscreen exit-control position/insets first, then thumbnail loading latency/cache/progressive scheduling, then cover-image generation/display. Preserve the approved editor implementation, timeline math, frame alignment and all working controls.
5. Add focused regression tests for each fix. Run unit tests, debug build and targeted API 35 emulator tests; inspect logs and screenshots rather than relying on a green compile alone.
6. Rebuild a TEST APK ZIP only after the targeted fixes and checks; state the exact branch, commit, workflow run and artifact filename. Ask the user to install and verify on the phone.
7. Update this README and RESTORATION_STATUS.md with actual evidence and remaining failures. Only consider release 395 after all release gates pass and the user explicitly authorizes it.

### Continuation rules

- Start with **“HATIRLA BAĞLAN DÜZELT”** and this handoff section.
- Do not repeat already-passed tests without a specific reason; do not report a test as green unless its exact run and scope are confirmed.
- Preserve working functionality. Make the smallest targeted correction supported by evidence.
- Keep README.md and RESTORATION_STATUS.md synchronized as work progresses; do not create a new version just to test.
- The user's immediate request was to preserve the restart point in the repository so work can continue from another ChatGPT account/session.
