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

## Current verification state — 2026-10-30 (latest focused shared-lane check #388 green; full suite #341; focused #362/#366; phone layout #371)

### Latest completed green evidence

- [Run #341 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621), candidate commit `e94fc618a1c149368eea6bf76677e65e11240c81`: unit tests, Debug APK build, fixture preparation, Android instrumentation (**20/20 tests, 0 failures, 0 skips**) and artifact upload all passed.
- The #341 artifact [12991473031](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621/artifacts/12991473031) contains both the empty-editor and loaded-video 393×873 phone-sized emulator captures, including `editor-phone-size-loaded.png`. This closes the tall-screen emulator screenshot collection gate, not physical-device acceptance.
- [Run #375 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41099038593): the test reached the zoomed measurement, but `visibleBounds` clipped the text block because it lay left of the viewport at the preserved 2.75 s playhead. The test now scrolls all synchronized lanes back to time zero before measuring zoomed block bounds; no product-code failure is established yet.
- [Run #371 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450), current candidate commit `45e34f74650a6a7c2b02593b1f3ef15e7aac3283`: unit tests, Debug APK, fixture preparation, the focused `EmulatorScreenshotTest#captureHomeEditorAndTextPanel` test (1/1), both screenshot pulls and artifact upload passed. Artifact [12997315254](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450/artifacts/12997315254) includes `editor-phone-size.png` and `editor-phone-size-loaded.png` at 393×873.
- [Run #340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996): 20/20 instrumentation tests passed, covering the 19 listed editing interactions plus the editor/home layout test. Artifact [12984726239](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996/artifacts/12984726239).
- [Run #337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720): 20/20 passed; the 8-second fixture loaded 6–10 thumbnails from distinct timestamps.

### Exact 10-second seek test — VERIFIED

- A dedicated generated 10-second, 30 fps MP4 fixture is created in CI. FFprobe verifies the container duration, and Android independently reads **10,000 ms** from the file metadata.
- The focused instrumentation method `tenSecondFixtureSeeksBeginningMiddleAndEndAcrossZoomLevels` checks seeking at **0 ms, 5,000 ms and 9,500 ms**, across two zoom changes. It also checks fixed-playhead stability and the 10,000 ms project duration.
- [Run #362 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580), candidate source `04a0284e8750d37930ec1c6e8eb190f7bf4bc8d7`: unit tests, Debug APK build, exact fixture preparation and the single focused Android instrumentation test passed. Artifact [12992984047](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580/artifacts/12992984047) was uploaded.
- [Run #366 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906), current PR head `c4b13e3e5ceb8094ef269a2c1c56cb8a7956e2a4`: the same focused method passed again (1/1), with unit tests, Debug APK build, fixture preparation and artifact upload all successful. Artifact [12994413275](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906/artifacts/12994413275).
- [Run #358](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41089252672) failed before the app test because a multiline conditional was interpreted by `/usr/bin/sh`; the condition was moved into a Bash wrapper. [Run #360](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090016116) was cancelled before recording a JUnit result. Both are historical CI attempts and are superseded by the passing #362 and #366 results.
- **Coverage boundary:** #362/#366 validate one exact 10-second source clip. The multi-clip reorder/trim/history/export scenarios passed separately in #340. Do not describe these as one combined 10-second multi-clip test.
- **No-repeat rule:** this exact focused test is now closed. Do not rerun it unchanged; only rerun it for a relevant code change or a specific regression.

### What the green emulator evidence closes — and what it does not

- Existing green tests already cover timeline pinch/scroll/trim/playhead interactions, multi-clip reorder and history, text timing/size/preview, audio/effect history and export, source-audio gain/mute, A/V seam alignment, split/export, draft and JSON save/reopen, and distinct filmstrip timestamps. Do not recreate these checks unchanged.
- #371's 393×873 screenshots were inspected against the user's supplied target: the loaded-video preview stage is about 392 px high (about 45% of the 873 px viewport), and the portrait canvas is about 221×392 px. The redundant single-clip `Klip 1` chooser row is absent. This closes the major preview-size and duplicate-row visual gaps on the emulator only; the target's wider tool dock and the empty-editor clip-panel copy still need visual polish.
- The #341 tall-screen screenshots are emulator captures, not a physical phone test; they do not prove touch comfort, performance, OEM-specific behavior or keyboard-open editing.
- The deterministic filmstrip density fixture is 8 seconds; the separate exact 10-second seek scenario is now verified by #362 and #366. The exact 10-second test is single-clip; multi-clip behavior has separate #340 coverage.
- Remaining acceptance work: finish visual polish of the four-item dock and empty-editor clip panel against the supplied target; inspect shared video/text/music/effect lane alignment and adaptive ruler readability at multiple zoom levels; test text-panel access with the software keyboard open on a short viewport; perform physical-phone visual/touch/performance smoke testing; and run signed install-over/relaunch verification on the current `fix/editor-395-mainline` branch.
- Version 395 remains blocked. The working installed baseline remains **versionCode 394 / build 61**. Do not bump version metadata, sign or publish a release APK, or activate the updater until all required gates pass and the user explicitly authorizes release.

## Current baseline capabilities (present in source; not all are proven reliable)

- [x] Multi-clip timeline and clip selection/reordering controls exist.
- [x] Clip trim/split controls and timeline time/pixel mapping logic exist.
- [x] Text layer controls for timing and appearance exist in the source.
- [x] Background music/sound-effect controls exist in the source.
- [x] Editable JSON project save/reopen and local draft autosave are implemented in the source.
- [x] MP4 export path exists in the source.
- [ ] These checkmarks describe existing code paths only; they do **not** certify that the current build behaves correctly on a device. See the required verification checklist below.

## Required work for the next release (395) — NOT DONE until verified

**Checklist interpretation:** items already covered by a specific green emulator/unit test are recorded as such in the evidence section above. Unchecked items below are remaining acceptance gaps or broader requirements not fully proved by that test; they do not mean the source feature is absent. Do not rerun a passed scenario unchanged.

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

- [x] Unit tests for timeline scale/mapping, zoom anchor, ruler interval, thumbnail work order and text timing passed in candidate run #340.
- [x] Candidate #340 emulator suite passed 20/20, covering one-finger timeline scroll/trim, two-finger pinch, playhead seek, thumbnail timestamp variety, text timing/size, text/audio/clip Undo/Redo, project reopen, MP4 export, and home/editor navigation/layout checks. The precise tested scenarios are listed above.
- [x] Exact 10-second single-clip seek at 0 / 5,000 / 9,500 ms across zoom levels passed in focused runs #362 and #366.
- [x] Multi-clip reorder/trim/seek/history/export scenarios passed separately in #340. These are separate evidence sets; no combined exact-10-second multi-clip run is claimed.
- [x] MP4 export scenarios passed in #340: two clips in order, timed styled text, looped music/timed effect levels, source-audio volume/mute, A/V seam alignment and split-at-playhead segment order. Keep the broader exact 10-second export case open.
- [x] Save/reopen emulator scenarios passed in #340 for local draft restoration and manual JSON save-close-reopen project state. Physical-device persistence remains unverified.
- [x] Reviewed the #340 screenshots (`home.png`, `editor.png`, `editor-empty.png`, `editor-phone-size.png`); its 393×873 capture was the empty-editor state.
- [x] #341 captured both empty-editor and loaded-video states at 393×873; artifact [12991473031](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621/artifacts/12991473031) contains `editor-phone-size-loaded.png`.
- [x] Compared the #371 empty/loaded 393×873 screenshots against the user's target for preview size and the redundant single-clip chooser row. Artifact [12997315254](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450/artifacts/12997315254).
- [ ] Complete remaining dock/empty-state visual polish and physical-phone review.
- [ ] Perform a final physical-device smoke test when possible.
- [ ] Only after every required item is complete and verified, prepare version 395. Until then, do not bump the app version or publish an APK.

## Verification boundaries

- Existing unit tests cover some timeline time/pixel mapping, clip trim bounds, playhead-to-clip mapping and text timing boundaries.
- The current emulator screenshot workflow builds a debug APK and captures screens, but its script does not by itself prove all touch gestures, exports, Undo/Redo or save/reopen flows work. Add/run interaction-level tests rather than treating screenshots alone as acceptance.
- Record a checkbox as complete only after the corresponding behavior is implemented and its relevant test passes. If a test cannot be run, leave the item unchecked and say so.
- This repository is a reconstruction from recoverable project files, not an exact recovery of the old V7 source tree.


## Latest technical update — 2026-10-30

- Fresh mainline candidate run [#329](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41040492815) completed with unit tests and Debug APK build passing, but the emulator interaction suite failed 2 of 20 tests. Both failures were right-edge trim gestures that left the project at 8,000 ms.
- Root cause identified from the view hierarchy and failed interaction path: the fixed playhead is a 40 dp-wide overlay brought to the front above the timeline lanes. When the clip's right edge is centered beneath the playhead, the overlay receives the touch before the right trim handle, so the trim handler never gets ACTION_DOWN.
- Source fix committed to the candidate branch: the playhead overlay now yields an initial touch to a visible clip trim handle when the touch point falls inside that handle's on-screen bounds. Playhead dragging remains unchanged elsewhere.
- Added instrumentation assertions to both right-trim interaction paths. They now verify that the trim handle receives both its DOWN and UP events before verifying the duration change; this distinguishes touch routing from trim math.
- Candidate commits: source fix `cbe72383fd080989563e70e6bd184e4f84ec6c9a`; interaction assertions `b2bcdee7c598fe380adbc668cb43667943fa566d`.
- The fix is **implemented in candidate source but not yet verified by a fresh CI run or physical-device test**. Keep the corresponding checklist items open until the new emulator run passes. Version 395 remains blocked; do not change version metadata, sign or publish an APK, or enable the updater.


## Follow-up after candidate run #332 — 2026-10-30

- Run [#332](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41051140320) completed with unit tests and Debug APK build passing; emulator instrumentation failed 1 of 20 tests.
- The failing test was `multiClipReorderTrimUndoRedoAndSeekStayCorrectAcrossZoomLevels`: its first direct playhead drag attempted to seek to 1,000 ms but remained at 0 ms.
- The right-trim touch-routing regression checks did not report failures; the failure occurred earlier in the same multi-clip test during playhead seeking.
- Cause: the test helper grabbed the vertical playhead overlay at the vertical center of the entire timeline. At the initial 0 ms position, the first clip's right trim handle can occupy that same screen coordinate, so the new correct trim-handle priority makes that ambiguous touch go to the trim handle.
- Test correction committed as `c8680d730de125793e7cb694888c98ba998679cc`: start the playhead drag on its distinct small top head, which is the intended unambiguous playhead grab target, rather than the line where it can overlap a trim handle.
- This is a test-coordinate correction, not a claim that the source fix has passed. A new PR-triggered emulator run is required; keep both playhead and trim acceptance gates open until it passes.


## Candidate run #334 — 2026-10-30

- Run [#334 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921) executed against PR #10's merge ref, including candidate head `452aa32c5812e203ba60abc581c53fde55cd0042`.
- Unit tests passed, Debug APK built successfully, and Android instrumentation completed **20/20 tests with 0 failures and 0 skips**. The CI log reports `connectedDebugAndroidTest exit code: 0`, `BUILD SUCCESSFUL`, and `Connected Android instrumentation tests passed.` The emulator screenshot/diagnostic artifact was uploaded [here](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921/artifacts/12976766821).
- This closes the specific automated regression check for right-edge trim touch routing and the multi-clip test's playhead drag helper correction within this emulator suite. It is automated emulator evidence, not a physical-phone confirmation.
- Emulator shutdown messages such as `stop: Not implemented` and Netsim Wifi stream cancellation occurred after the instrumentation command exited successfully; they did not fail the job.
- Keep the broader editor acceptance checklist open: this run does not prove all user-facing timeline, text/audio, global Undo/Redo, export, save/reopen, or physical-device requirements. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater.


## Candidate run #337 — 2026-10-30

- Run [#337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720) completed on `fix/editor-395-mainline`, candidate head `649cf70799cff4012c7cea2318cd8eed8e95c202`.
- Unit tests passed, Debug APK built successfully, and Android instrumentation completed **20/20 tests with 0 failures and 0 skips**. The workflow log confirms `connectedDebugAndroidTest exit code: 0`, `BUILD SUCCESSFUL`, and `Connected Android instrumentation tests passed.`
- The filmstrip density change uses a 22 dp thumbnail slot at the default 22 dp/second timeline scale, targeting roughly one readable frame per second rather than overly narrow slivers. The instrumentation check in `filmstripLoadsFramesFromDistinctTimestamps` verifies the 8-second fixture displays 6–10 loaded thumbnails while retaining the distinct-timestamp/frame checks.
- Emulator screenshots/diagnostics were uploaded as [artifact 12978811772](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720/artifacts/12978811772).
- This closes the automated emulator check for the targeted filmstrip density and distinct-frame behavior in this test scenario. It does **not** establish physical-device readability, thumbnail loading performance on a phone, or completion of the broader timeline/UI checklist.
- Version 395 remains blocked. No version bump, signed release APK, publication, or updater offer.


## Candidate run #340 — 2026-10-30

- Run [#340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996) completed on `fix/editor-395-mainline`, candidate source commit `388b4be0be7f2c3bb64f03c988565cc618bc9cdb` (with instrumentation test commit `6f18d6c14fcd6586f3205791baf68f4539960220`).
- Unit tests passed, Debug APK build passed, and Android instrumentation completed **20/20 tests, 0 failures, 0 skips**. The job concluded successfully and the log reports `BUILD SUCCESSFUL` and `Connected Android instrumentation tests passed.`
- The targeted empty-editor state check passed in this run: when no video is loaded, the panel exposes the add-video action and hides clip-only editing controls. This is automated emulator evidence for the tested empty state, not a full usability or physical-device sign-off.
- Emulator screenshots/diagnostics were uploaded as [artifact 12984726239](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996/artifacts/12984726239).
- Emulator shutdown emitted the familiar `stop: Not implemented` / Netsim cleanup warnings after the instrumentation command succeeded; they did not fail the workflow.
- Broader timeline, text/audio, Undo/Redo, export, save/reopen, and physical-device acceptance items remain open. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater.


## Candidate run #341 — 2026-10-30

- Run [#341 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621) completed on `fix/editor-395-mainline`, candidate commit `e94fc618a1c149368eea6bf76677e65e11240c81`.
- Unit tests, Debug APK build, fixture preparation, Android instrumentation and screenshot/diagnostic artifact upload all passed. Instrumentation: **20/20 tests, 0 failures, 0 skips**.
- Added loaded-video 393×873 layout assertion and capture `editor-phone-size-loaded.png`; artifact [12991473031](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621/artifacts/12991473031) contains the capture. The tall-screen empty-editor and loaded-video states are now both covered by emulator evidence.
- This closes the loaded-video tall-screen emulator capture gate only. It is not a physical-device sign-off. Do not rerun #341 unchanged.
- Next distinct validation: add an exact 10-second deterministic fixture test for beginning/middle/end seeking at different zoom levels, after checking existing coverage to avoid duplicating the 8-second fixture scenarios. Then review shared ruler/lane alignment and text/audio time labels only where current assertions do not already cover them.
- Version remains 394/build 61. Version 395 remains blocked; no version bump, signed release APK, publication or updater enablement.


## Exact 10-second timeline seek gate — 2026-10-30 (PASSED)

- Coverage audit found no prior assertion for beginning/middle/end seeking on an exact 10,000 ms source across multiple zoom levels. The existing 8-second filmstrip and multi-clip scenarios were preserved without duplicate testing.
- Added a generated 10-second / 30 fps MP4 fixture; FFprobe and Android metadata both verify 10,000 ms.
- Focused method: `EditorTimelineInteractionTest#tenSecondFixtureSeeksBeginningMiddleAndEndAcrossZoomLevels`. It seeks at 0, 5,000 and 9,500 ms through two zoom changes and checks fixed-playhead stability and total duration.
- [#362 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580): one focused test passed; artifact [12992984047](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580/artifacts/12992984047).
- [#366 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906): the same focused test passed again on current PR head `c4b13e3e5ceb8094ef269a2c1c56cb8a7956e2a4`; artifact [12994413275](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906/artifacts/12994413275).
- #358 (shell wrapper failure) and #360 (cancelled before a JUnit result) remain historical failed/cancelled attempts; they are superseded by #362/#366.
- Coverage boundary: exact 10-second seeking is proven on one clip; multi-clip workflows are proven separately in #340. No combined exact-10-second multi-clip scenario is claimed.
- Do not rerun this focused test unchanged. Version remains 394/build 61; version 395 remains blocked.


## Candidate run #369 and correction — 2026-10-30

- [#369 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41094950771): unit tests and Debug APK passed; the focused screenshot test failed its new tall-preview assertion because the test resized the emulator from 320×640 to 393×873 while keeping the same editor Activity alive. The app caches its short-screen classification at Activity creation, so the existing Activity retained the 180 dp short-preview layout. This did not test a fresh 393×873 launch and is not evidence of a failure on a real 393×873 first launch.
- The test now returns to Home, applies the 393×873 size, and launches a fresh editor Activity before measuring the preview. The test also packages the deterministic sample clip in androidTest assets and copies it to the target app's private files directory, so the focused test does not depend on the full-suite ADB fixture-push script.
- #370 was cancelled by the newer PR update and is not product-test evidence.

## Candidate run #371 — 393×873 target-layout check PASSED

- [#371 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450), head commit `45e34f74650a6a7c2b02593b1f3ef15e7aac3283`: unit tests, Debug APK build, deterministic fixture preparation, the focused screenshot test (1/1), screenshot collection and artifact upload all passed.
- Artifact [12997315254](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450/artifacts/12997315254) contains both 393×873 screenshots: `editor-phone-size.png` and `editor-phone-size-loaded.png`.
- The automated test requires the outer preview stage to be at least 320 dp in both empty and loaded states and asserts that the loaded single-clip editor does not expose the redundant `Klip seçici satırı`.
- Visual inspection against the user's target confirms a roughly 392 px / 45% tall preview stage and a roughly 221×392 px portrait canvas on the 393×873 emulator. The duplicate `Klip 1` chooser row is gone.
- Remaining visual polish: the app still exposes four supported bottom actions rather than the reference's wider tool dock, and the empty-editor view still displays a `KLİP DÜZENLEME` / `Henüz video eklenmedi` panel. Do not add inert tools merely to match the reference.
- This is emulator screenshot and layout evidence, not a physical-phone acceptance test. Keep the real-device review open.


## Shared-lane alignment candidate #375 — 2026-10-30

- [#375 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41099038593): unit tests and Debug APK passed; the focused emulator test passed its default-zoom lane measurements and pinch/playhead-stability checks, then failed only at the zoomed text-start assertion: `actual=0 expected=171`.
- Root cause is the test measurement, not yet a demonstrated product alignment defect: it used `UiObject2.visibleBounds`, which clips an off-screen child to the viewport. With the playhead anchored at 2.75 s after zoom, the text block starting at 1.25 s is correctly left of the viewport, so its reported visible left edge is clipped to the same viewport edge as the music lane.
- Corrected the focused test to scroll the synchronized VIDEO lane back to the timeline origin after confirming the pinch preserved the playhead, then measure all four lanes at that zoom. This preserves the actual multi-lane/zoom check while avoiding clipped bounds. Product code is unchanged; await the targeted rerun before marking the gate complete.


## Shared-lane alignment rerun #378 — 2026-10-30

- [#376](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41106652639) was cancelled before emulator setup completed; it produced no JUnit result and is neither a pass nor a product failure.
- [#378](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41106717136) passed unit tests, Debug APK, fixture preparation and emulator startup, then failed in the focused test with `UiAutomator StaleObjectException` at the post-pinch VIDEO-lane scroll.
- Diagnosis: pinch rebuilds the timeline row; the test reused the pre-pinch `UiObject2` handle. This is a stale test handle, not evidence of a timeline product defect.
- Test-only correction committed as `1a096d229ceca77a06ac9f2a53018c25d02c47bd`: reacquire the current VIDEO row after pinch before scrolling to origin. Product source remains unchanged. Keep the shared-lane gate open until this exact focused test passes.


## Shared-lane alignment #381 — confirmed assertion failure and next correction

- [#379](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41111773339) was cancelled during ffmpeg setup; no emulator assertion ran.
- [#381](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41111834964) completed setup, unit tests, Debug APK, fixtures and emulator launch. The focused test failed at zoomed text start: `actual=0 expected=171`.
- The #378 stale-object issue is resolved: reacquiring the VIDEO lane allowed the test to reach the zoomed alignment assertion.
- Diagnosis from the test flow: the test called `UiObject2.scroll(Direction.RIGHT)` while intending to return to the timeline origin. Emulator accessibility events show the VIDEO lane scrollX increased (390 → 496), moving farther from origin. Corrected the direction to `LEFT` so the viewport moves back toward the start. This remains a test-harness correction; do not change product code unless a correctly measured run demonstrates misalignment.
- CI reliability correction: the workflow previously used `cancel-in-progress: true`. Multiple PR synchronize events on the same pull request ref cancelled the active emulator run while later commits arrived. Set it to false so an active validation is not cancelled by a newer PR update; a newer pending run may replace an older pending one.
- Targeted rerun required. Shared-lane gate remains open; no release/version bump.

## Shared-lane alignment #382 — refined zoom fixture

- [#382](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41115009608) passed unit tests, Debug APK build and deterministic fixture setup, then failed its only instrumentation test at `Could not find zoomed-at-origin effect block`.
- The failure followed a maximum-scale pinch and a return-to-origin scroll. At that zoom, the effect strip was clipped outside the visible viewport; UiAutomator therefore did not expose its text node. This is a test-visibility/measurement issue, not evidence that the app deleted or misaligned the effect.
- Test-only correction `6759e65a41544b7212341f1161c4f7d382585d8e`: use a modest pinch that still increases zoom, keep the playhead at 2.75 s, explicitly require both text/effect strips to remain visible, then measure lane alignment. No production source changed.
- A focused validation of this correction is required. Keep the shared-lane gate open and 395 blocked until its JUnit result passes.


## Shared timeline lane alignment #388 — PASSED (2026-10-30)

- [Run #388 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41116127578) completed on PR #10 / branch `fix/editor-395-mainline`, candidate head `8cc976d6fc21eaf3c81250642cc3c50bf242b673`.
- Unit tests and Debug APK builds passed. The focused Android instrumentation method `EditorTimelineInteractionTest#sharedTimelineVideoTextMusicEffectLanesStayAlignedAcrossZoomLevels` passed; the log reports `connectedDebugAndroidTest exit code: 0`, `BUILD SUCCESSFUL`, and `Connected Android instrumentation tests passed.` The focused gate is therefore closed for the tested zoom/scroll scenario.
- The test-only refinement after #382 used a modest pinch, preserved the playhead at 2.75 s, required the text and effect strips to remain visible, and then measured the shared video/text/music/effect lane alignment. This is automated emulator evidence; it does not prove every zoom extreme, save/reopen state, or physical-device behavior.
- Emulator screenshot/diagnostic artifact: [13007025200](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41116127578/artifacts/13007025200).
- Emulator ADB startup/cleanup warnings appeared in the log, but the focused instrumentation command exited 0 and the workflow concluded successfully; they did not fail #388.
- Do not rerun #388 unchanged. Next distinct acceptance work: inspect the latest artifact/screenshots and finish visual polish of the four-item dock/empty-editor panel; then check adaptive ruler readability at zoom extremes and keyboard-open editing on a short viewport; then physical-phone smoke testing and current-branch signed install/update-over/relaunch preflight.
- Version remains 394/build 61. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater until all gates pass and the user authorizes release.
