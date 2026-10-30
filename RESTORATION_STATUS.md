# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- Version 395 is blocked until every required item in README.md is implemented and verified.
- Do not bump versionCode/versionName, sign a new release APK, publish a release, or offer an update for 395 before the release gate is met and the user authorizes release.
- README checklist and source commits do not mean the behavior is fixed or included in the installed APK.
- Existing repository release notes may lag the user's installed version; use the user's confirmed 394/build 61 as the working baseline.

## Latest focused regression — fixed-playhead pinch zoom #396 PASSED (2026-10-30)

## Short-viewport keyboard test — PASSED (#399, 2026-10-31)

- PR #10 added `EditorTimelineInteractionTest#compactPhoneKeyboardKeepsTextFieldEditableAndRestoresEditor`, using a deterministic 360×640 dp emulator viewport and the checked-in 8-second MP4 fixture.
- [Run #398 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41132536004) reached the final text-size-control assertion: keyboard open, text entry, keyboard dismissal, input persistence and timeline restoration had already passed. The failure was that the test queried the lower text-size SeekBar without scrolling the text tool panel to it; the SeekBar is intentionally below the initially visible panel area. This was a test reachability assertion, not evidence of a production layout regression.
- [Run #399 — SUCCESS (attempt 2)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41133544846) completed successfully on commit `cdde7caaec50d5ba60137e9062b36dd62b2f72af`; the `emulator-ui` job concluded `success`. The first attempt encountered a Maven Central HTTP 429 before emulator validation; the successful retry completed the focused emulator workflow.
- The corrected focused test verifies the 360×640 dp viewport, keyboard-visible responsive state (timeline/dock hidden, compact preview), text input above the IME, text entry persistence, restored timeline/dock after dismissal, and access to the lower text-size control after scrolling the panel.
- This closes the automated short-screen keyboard gate for the tested emulator path only. It does not replace physical-phone interaction/performance checks or signed install → update-over → relaunch preflight.
- Version remains 394/build 61. Version 395 remains blocked; no version metadata bump, release signing, publishing, or updater activation is permitted before all remaining gates pass and the user authorizes release.

## Fixed-playhead pinch zoom #396 — PASSED (2026-10-30)

- [Run #396 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41129149733) completed on PR #10 / branch `fix/editor-395-mainline`, candidate commit `7c4c526540524c9582685d3bb2575fa1b0336921`.
- Unit tests, Debug APK build, deterministic MP4 fixture preparation, emulator launch, the focused Android instrumentation test, and screenshot/diagnostic artifact upload all passed.
- The focused method was `EditorTimelineInteractionTest#filmstripScrollDoesNotTrimAndTrimHandleDoesNotBecomeScroll` (one test). It explicitly verifies that pinch zoom changes the zoom level while the fixed-playhead time moves no more than 150 ms; it then checks that a filmstrip-body swipe scrolls without trimming the clip.
- The product fix anchors pinch zoom to the time under the fixed center playhead, rather than the moving midpoint between the two fingers. The test now copies its checked-in 8-second MP4 fixture into the app's test files directory, so it does not depend on a pre-existing local fixture.
- Emulator artifact: [13012197695](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41129149733/artifacts/13012197695).
- This closes the automated fixed-playhead pinch/filmstrip-scroll regression gate for this tested path. It does not replace physical-phone smoke testing or signed install → update-over → relaunch preflight.
- PR #10 remains open and draft. Installed baseline remains versionCode 394/build 61. Version 395 is still blocked; do not bump metadata, sign/publish a release APK, or enable the updater until the remaining gates pass and the user authorizes release.

## Evidence-backed status — 2026-10-30

### Latest completed full-suite, focused and phone-layout verification

- [Run #341 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621), candidate commit `e94fc618a1c149368eea6bf76677e65e11240c81`: unit tests, Debug APK build, fixture preparation, Android instrumentation (**20/20, 0 failures, 0 skips**) and screenshot artifact upload all passed.
- Artifact [12991473031](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621/artifacts/12991473031) contains empty-editor and loaded-video 393×873 captures. Emulator screenshot collection is complete for both states; manual comparison with the user's target and physical-device validation remain open.
- [Run #371 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450), current candidate commit `45e34f74650a6a7c2b02593b1f3ef15e7aac3283`: unit tests, Debug APK, fixture preparation, focused screenshot instrumentation (1/1), screenshot pulls and artifact upload passed. Artifact [12997315254](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450/artifacts/12997315254) contains the empty and loaded 393×873 screenshots.
- [Run #340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996) passed 20/20 instrumentation tests, covering the detailed editor interactions and home/editor layout checks.
- [Run #337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720) passed 20/20; its 8-second filmstrip fixture showed 6–10 thumbnails from distinct timestamps.

### Exact 10-second focused seek test — PASSED

- A generated 10-second / 30 fps MP4 fixture is independently validated by FFprobe and Android metadata as exactly 10,000 ms.
- Focused method `tenSecondFixtureSeeksBeginningMiddleAndEndAcrossZoomLevels` seeks at 0, 5,000 and 9,500 ms across two zoom changes and checks fixed-playhead stability and total duration.
- [#362 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580), source commit `04a0284e8750d37930ec1c6e8eb190f7bf4bc8d7`: unit tests, Debug APK, fixture preparation and the single focused Android instrumentation test all passed. Artifact [12992984047](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090771580/artifacts/12992984047).
- [#366 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906), current PR head `c4b13e3e5ceb8094ef269a2c1c56cb8a7956e2a4`: the same focused method passed again (1/1); unit tests, Debug APK build, fixture preparation and artifact upload also succeeded. Artifact [12994413275](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41091787906/artifacts/12994413275).
- #358 was a shell-wrapper failure before the app test; #360 was cancelled before a JUnit result. Both are historical attempts superseded by #362/#366.
- **Coverage boundary:** exact-10-second seeking is verified on one source clip. Multi-clip reorder/trim/seek/history/export scenarios passed separately in #340; no combined exact-10-second multi-clip scenario is claimed.
- Do not rerun this focused test unchanged. Rerun only for a relevant code change or a specific regression.

### Already covered by green emulator evidence — keep closed

### Already covered by green emulator evidence — keep closed

- Timeline pinch/scroll/playhead and trim-handle touch paths; multi-clip selection/reorder/trim/seek and Undo/Redo.
- Text timing/size/preview and unified text/audio history.
- Sound-effect add/start/remove/history, music/effect export, measured gain controls, source-audio mute/gain and original-file preservation.
- Multi-clip and split MP4 export, A/V seam alignment, timed styled text, local draft restore and JSON save-close-reopen.
- Distinct filmstrip frames, 8-second fixture density, empty-editor and tall 393×873 home/editor captures.
- These results prove only the named emulator scenarios, not blanket physical-device quality. Do not rerun unchanged tests without a relevant code change or specific regression.



## Current source functionality (implementation exists; reliability still needs testing)

- Home screen with gallery import, empty-editor action, recent outputs and local drafts.
- Video import, multi-clip timeline, clip reorder/copy/delete/split, source trim start/end, playhead seek and timeline zoom.
- Trim and playhead controls use millisecond mapping; legal trim bounds consider the opposing edge and source duration.
- Video thumbnail extraction uses timestamped platform-frame APIs first, then Media3 for the requested timestamp.
- Text layer timing, position, size, colour and bold settings; background music and one timed sound effect; original audio volume.
- MP4 export with source / 720p / 1080p options via Media3 Transformer.
- JSON project save/load and local draft autosave, including playhead time, zoom and horizontal scroll.
- Responsive preview/timeline layout, reduced keyboard mode and compact dock.

## Remaining product acceptance gaps after #341, #362 and #366

The automated scenarios above have passed on the emulator. The following items remain open because they are broader than those scenarios, require visual/physical-device acceptance, or are not directly covered:

1. Physical-device smoke test on the actual installed phone; emulator success does not prove touch comfort, frame pacing, memory behaviour or OEM-specific playback/export.
2. No open focused seek-test gap remains: exact 10-second single-clip seeking at 0 / 5,000 / 9,500 ms across zoom levels passed in #362 and #366. Multi-clip reorder/trim/seek/history/export passed separately in #340; these are separate evidence sets, not one combined exact-10-second multi-clip test.
3. Major 393×873 target comparison is now complete in #371: preview height and portrait-canvas scale match the reference more closely, and the redundant single-clip chooser row is gone. Remaining visual polish is the four-item dock and the empty-editor clip-panel copy; physical-device comparison is still required.
4. Confirm the shared ruler and video/text/music/effect lanes remain visibly aligned after scroll, pinch, seek, clip edits and save/reopen. Shared time-to-pixel math has unit tests; the full visible lane alignment still needs an end-to-end visual acceptance pass.
5. Inspect adaptive ruler label spacing/readability on an actual device at both zoom extremes. The ruler-interval calculation has unit-test coverage.
6. Check keyboard-open editing and panel reachability on a short phone viewport. #340 covers a 393×873 emulator viewport, not the full keyboard interaction.
7. Confirm filmstrip thumbnail size/readability and loading/scroll performance on the user's phone; #337/#340 establish distinct frames and the 8-second emulator density scenario, not physical-device performance.
8. Finish remaining visual polish of the home screen, clip-add/join affordance, redundant icons/labels, four-item dock and empty-editor panel presentation against the user's target reference; do not add unsupported/inert tools.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.

## Verification gaps

- Unit tests cover timeline scale/time-pixel mapping, zoom-anchor preservation, ruler interval selection, thumbnail work ordering, trim bounds/playhead mapping, and text timing boundaries.
- The #340 candidate emulator suite passed 20/20 (19 interaction tests plus the home/editor screenshot test). It exercises gesture/trim, multi-clip edits, text/audio, Undo/Redo, project save/reopen, export and screenshot layout scenarios. The exact test method names and run details are recorded in README.md.
- The emulator tests use deterministic media fixtures; the main interaction and filmstrip fixtures are about 8 seconds. The separate exact 10-second single-clip seek test passed in #362 and #366.
- Screenshot diagnostics are uploaded. #371's 393×873 empty/loaded pair was inspected against the target: major preview-size and single-clip-row differences are addressed on the emulator. The four-item dock/empty-editor panel still need polish, and physical-device smoke testing remains required.
- Do not rerun already-green #337/#340/#341/#362/#366/#371 scenarios unchanged. If a new failure occurs, inspect its exact assertion/log first and fix the root cause before a targeted rerun.

## Release gate

**Version 395 must not be created or published until all required fixes and validations in README.md pass.** A successful build alone is not acceptance. Keep the source version metadata unchanged during this repair phase.

## Recovery background

Some older physics/scenario-related source and tests remain as reconstruction leftovers; they are outside the active video-editing workflow. The original complete V7 source tree was not recoverable.


## Latest technical update — 2026-10-30

- Candidate run #329 passed unit tests and the Debug APK build, then failed 2 of 20 emulator interaction tests. Both failures were attempts to shorten an 8,000 ms clip by dragging its right trim handle; the measured duration stayed 8,000 ms.
- Root cause: the centered fixed playhead overlay is 40 dp wide, elevated above the timeline tracks, and brought to front. When the clip's right edge is centered under the playhead, it intercepts the touch before the underlying trim handle can receive ACTION_DOWN.
- Candidate source fix: playhead dispatch now passes the initial touch through when its screen coordinate is inside a visible left/right clip trim handle. This preserves playhead drag behavior outside trim-handle bounds.
- Added interaction assertions verify that the right trim handle receives ACTION_DOWN and ACTION_UP before checking that project duration changes.
- Source commit: cbe72383fd080989563e70e6bd184e4f84ec6c9a. Test commit: b2bcdee7c598fe380adbc668cb43667943fa566d.
- Status distinction: fix is in candidate source; automated verification and physical-device verification are still pending. Keep affected items open. Version 395 remains blocked.


## Follow-up after candidate run #332 — 2026-10-30

- Run [#332](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41051140320) completed with unit tests and Debug APK build passing; emulator instrumentation failed 1 of 20 tests.
- The failing test was `multiClipReorderTrimUndoRedoAndSeekStayCorrectAcrossZoomLevels`: its first direct playhead drag attempted to seek to 1,000 ms but remained at 0 ms.
- The right-trim touch-routing regression checks did not report failures; the failure occurred earlier in the same multi-clip test during playhead seeking.
- Cause: the test helper grabbed the vertical playhead overlay at the vertical center of the entire timeline. At the initial 0 ms position, the first clip's right trim handle can occupy that same screen coordinate, so the new correct trim-handle priority makes that ambiguous touch go to the trim handle.
- Test correction committed as `c8680d730de125793e7cb694888c98ba998679cc`: start the playhead drag on its distinct small top head, which is the intended unambiguous playhead grab target, rather than the line where it can overlap a trim handle.
- This is a test-coordinate correction, not a claim that the source fix has passed. A new PR-triggered emulator run is required; keep both playhead and trim acceptance gates open until it passes.


## Candidate run #334 — 2026-10-30

- Run [#334 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921) completed on PR #10's merge ref (candidate head `452aa32c5812e203ba60abc581c53fde55cd0042`).
- Unit tests passed; Debug APK build passed; Android instrumentation passed **20/20 tests, 0 failures, 0 skips**. The log confirms `connectedDebugAndroidTest exit code: 0` and `Connected Android instrumentation tests passed.`
- Uploaded emulator screenshots/diagnostics: [artifact 12976766821](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41053725921/artifacts/12976766821).
- This validates the previously failing right-edge trim and playhead-drag regression paths in the automated emulator suite. It does not replace physical-device validation or close unrelated editor requirements.
- Emulator shutdown warnings after successful instrumentation are cleanup noise; the job concluded successfully.
- Version 395 remains blocked. Do not change version metadata, sign/publish an APK, or enable the updater until every release gate is implemented and verified and the user authorizes release.


## Candidate run #337 — 2026-10-30

- Run [#337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720) passed unit tests, Debug APK build and Android instrumentation: **20/20 tests, 0 failures, 0 skips**. The log confirms `connectedDebugAndroidTest exit code: 0` and `Connected Android instrumentation tests passed.`
- Candidate head: `649cf70799cff4012c7cea2318cd8eed8e95c202`.
- Filmstrip density implementation: thumbnail slot changed from 14 dp to 22 dp, matching the default 22 dp/second timeline scale to target roughly one readable thumbnail per second.
- The `filmstripLoadsFramesFromDistinctTimestamps` instrumentation test now additionally checks that the 8-second fixture has 6–10 visible loaded thumbnails; existing distinct-timestamp and distinct-frame checks remain.
- Emulator screenshots/diagnostics: [artifact 12978811772](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720/artifacts/12978811772).
- Evidence is limited to this automated emulator scenario. Physical-device thumbnail readability/performance and the wider user-facing checklist remain open. Version 395 remains blocked; do not change version metadata, sign/publish an APK, or enable the updater.


## Candidate run #340 — 2026-10-30

- Run [#340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996) completed on `fix/editor-395-mainline`, candidate source commit `388b4be0be7f2c3bb64f03c988565cc618bc9cdb` (with instrumentation test commit `6f18d6c14fcd6586f3205791baf68f4539960220`).
- Unit tests passed, Debug APK build passed, and Android instrumentation completed **20/20 tests, 0 failures, 0 skips**. The job concluded successfully and the log reports `BUILD SUCCESSFUL` and `Connected Android instrumentation tests passed.`
- The targeted empty-editor state check passed in this run: when no video is loaded, the panel exposes the add-video action and hides clip-only editing controls. This is automated emulator evidence for the tested empty state, not a full usability or physical-device sign-off.
- Emulator screenshots/diagnostics were uploaded as [artifact 12984726239](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996/artifacts/12984726239).
- Emulator shutdown emitted the familiar `stop: Not implemented` / Netsim cleanup warnings after the instrumentation command succeeded; they did not fail the workflow.
- Broader timeline, text/audio, Undo/Redo, export, save/reopen, and physical-device acceptance items remain open. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater.


## Candidate run #341 — 2026-10-30

- Run [#341 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621) passed unit tests, Debug APK build, fixture preparation, Android instrumentation (**20/20, 0 failures, 0 skips**) and artifact upload on candidate commit `e94fc618a1c149368eea6bf76677e65e11240c81`.
- The loaded-video 393×873 layout check passed and `editor-phone-size-loaded.png` is present in artifact [12991473031](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41083942621/artifacts/12991473031). Together with the prior empty-editor capture, both tall-screen states have emulator screenshot evidence.
- Close the loaded-video tall-screen emulator gate. Physical-device validation remains open. Do not rerun unchanged #341 scenarios.
- Next distinct check is an exact 10-second fixture across beginning/middle/end and multiple zoom levels. Before implementing, inspect existing instrumentation methods and extend only the uncovered case; do not repeat the existing 8-second filmstrip, trim, seek, multi-clip, export, undo/redo, or save/reopen tests.
- Version 395 remains blocked; version metadata remains 394/build 61.


## Exact 10-second timeline seek gate — 2026-10-30 (focused CI pending)

- Coverage audit confirmed the existing editor interaction fixtures are approximately 8 seconds; no test asserted beginning/middle/end seeking on an exact 10,000 ms source across multiple zoom levels. Existing 8-second trim, filmstrip, multi-clip, text/audio, export, Undo/Redo and persistence tests remain closed and are not being repeated.
- Added a dedicated generated 10-second, 30 fps MP4 fixture. CI checks the container duration with FFprobe; the instrumentation test independently checks Android metadata is exactly 10,000 ms.
- Added one focused interaction test: initial playhead at 0 ms/default zoom, seek to 5,000 ms after first pinch zoom, then seek to 9,500 ms after a second pinch zoom. It also checks zoom does not move the fixed playhead and project duration remains 10,000 ms.
- First focused run [#358](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41089252672) failed before the app test started: the emulator runner invoked a multiline conditional through /usr/bin/sh, which exited with a syntax error expecting fi. Unit tests and Debug APK build had already passed; no timeline assertion ran. This is CI wrapper failure, not an app/test failure.
- The conditional now lives in .github/scripts/run-focused-or-full-emulator-validation.sh, executed by one simple Bash command from the emulator action. INSTRUMENTATION_TEST_FILTER selects only EditorTimelineInteractionTest#tenSecondFixtureSeeksBeginningMiddleAndEndAcrossZoomLevels; screenshot smoke tests and the prior 20 interaction tests are deliberately excluded from this focused run.
- Focused retest [#360](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41090016116) was cancelled after emulator boot and focused instrumentation selection. No JUnit/assertion result was recorded; the exact-10-second gate remains open. Run only this focused method next.
- Version remains 394/build 61. Version 395 remains blocked.


## Current handoff — 2026-10-30

- Working branch: `fix/editor-395-mainline`; open draft PR: [#10](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/10).
- Latest full interaction suite: #341 (20/20). Latest focused phone-layout test: #371 (1/1), with empty and loaded 393×873 screenshots; artifact [12997315254](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41096422450/artifacts/12997315254). Latest focused four-lane alignment check: #388 (1/1).
- Exact 10-second seek gate: #362 and #366 both passed the one focused method (1/1). #358 was a shell-wrapper failure and #360 was cancelled before JUnit; both are superseded.
- No-repeat rule: do not rerun #337/#340/#341/#362/#366 unchanged. Exact 10-second single-clip seeking and separate multi-clip editing/export are covered; do not claim a combined exact-10-second multi-clip test.
- Dock/empty-state polish is implemented in source commit `fd6fc18dd56ae958c27d37334c62f28b922b3a54`: one cohesive four-item dock, clearer icon/label hierarchy, and a prominent primary add-video action; no inert tools were added. PR-triggered run #390 is queued to validate the changed screen and instrumentation suite. Do not mark the polish green until that run passes.
- Next work, in order after #390: (1) check adaptive ruler readability at zoom extremes; (2) verify keyboard-open editing on a short viewport; (3) perform physical-device visual/touch/performance smoke testing; (4) run signed build-61 install → current-mainline candidate update-over → relaunch verification.
- Signed preflight #9 passed on the older `fix/editor-395-gate` branch, but that branch is diverged and its `VideoEditorActivity.kt` and `TimelineScaleMath.kt` differ from current mainline. Do not transfer its result to PR #10; repeat the no-publish preflight on current mainline.
- Keep versionCode 394/build 61 unchanged. Version 395 remains blocked until all product/release gates pass and the user authorizes release.


### Shared-lane alignment test #375 — failure triage

- #375 failed at `zoomed text start differs: actual=0 expected=171`; unit tests, Debug APK and default-zoom lane assertions passed.
- Diagnosis: `UiObject2.visibleBounds` clips the text block at the viewport boundary after the pinch keeps the playhead at 2.75 s. This is a test-observation error, not proof that the underlying timeline positions are wrong.
- The focused test now scrolls the synchronized lanes to the timeline origin after validating playhead stability, then measures zoomed bounds. No product source was changed for this correction. Keep the gate open until a targeted rerun passes.


### Shared-lane alignment test #378 — second failure triage

- #376 was cancelled during fixture-tool setup before the emulator test; no JUnit result was produced.
- #378 passed build and fixture preparation, then failed at the post-pinch scroll with `androidx.test.uiautomator.StaleObjectException` on the VIDEO lane handle.
- Pinch rebuilds the timeline row, invalidating the previously captured `UiObject2`. The test now reacquires the VIDEO row after pinch before scrolling.
- Test-only correction commit: `1a096d229ceca77a06ac9f2a53018c25d02c47bd`. Product code unchanged. Gate remains open pending a conclusive targeted pass.


### Shared-lane alignment #381 — assertion diagnosis and next run

- #379 cancelled during ffmpeg setup before emulator execution; no JUnit result.
- #381 passed unit tests, Debug APK, ffmpeg fixture preparation, emulator startup and the earlier post-pinch row reacquisition. It failed only at `zoomed-at-origin text start differs: actual=0 expected=171`.
- The #378 stale `UiObject2` issue is no longer blocking. The remaining mismatch follows the test's wrong scroll direction: it requested `Direction.RIGHT` while trying to return to origin; accessibility scroll events show scrollX increasing from 390 to 496. Test now uses `Direction.LEFT`.
- Workflow concurrency was also corrected: `cancel-in-progress: false` prevents later PR synchronize events from cancelling an already-running emulator validation.
- Product source unchanged. Keep the four-lane alignment gate open until a conclusive targeted pass.

### Shared-lane alignment #382 — failure classified and test refined

- #382 passed unit tests, Debug APK and fixture preparation; the focused instrumentation test failed because the zoomed-at-origin effect strip was not present in UiAutomator's visible tree.
- The maximum pinch made the measured blocks too wide/off-screen after the test scrolled back to the timeline origin. The failure does not establish a product lane-alignment regression.
- Test-only follow-up `6759e65a41544b7212341f1161c4f7d382585d8e` reduces the pinch distance, leaves the 2.75 s playhead anchored, checks both timed strips remain visible, and compares their bounds at the second zoom level. Production code unchanged.
- Shared-lane alignment remains unverified pending the focused run; do not repeat unrelated green suites. Version 395 remains blocked.


## Shared timeline lane alignment #388 — PASSED (2026-10-30)

- [Run #388 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41116127578) completed on PR #10 / branch `fix/editor-395-mainline`, candidate head `8cc976d6fc21eaf3c81250642cc3c50bf242b673`.
- Unit tests and Debug APK builds passed. The focused Android instrumentation method `EditorTimelineInteractionTest#sharedTimelineVideoTextMusicEffectLanesStayAlignedAcrossZoomLevels` passed; the log reports `connectedDebugAndroidTest exit code: 0`, `BUILD SUCCESSFUL`, and `Connected Android instrumentation tests passed.` The focused gate is therefore closed for the tested zoom/scroll scenario.
- The test-only refinement after #382 used a modest pinch, preserved the playhead at 2.75 s, required the text and effect strips to remain visible, and then measured the shared video/text/music/effect lane alignment. This is automated emulator evidence; it does not prove every zoom extreme, save/reopen state, or physical-device behavior.
- Emulator screenshot/diagnostic artifact: [13007025200](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41116127578/artifacts/13007025200).
- Emulator ADB startup/cleanup warnings appeared in the log, but the focused instrumentation command exited 0 and the workflow concluded successfully; they did not fail #388.
- Do not rerun #388 unchanged. Next distinct acceptance work: inspect the latest artifact/screenshots and finish visual polish of the four-item dock/empty-editor panel; then check adaptive ruler readability at zoom extremes and keyboard-open editing on a short viewport; then physical-phone smoke testing and current-branch signed install/update-over/relaunch preflight.
- Version remains 394/build 61. Version 395 remains blocked; do not bump version metadata, sign/publish a release APK, or enable the updater until all gates pass and the user authorizes release.


### Empty-editor tall-phone action visibility — targeted validation queued (2026-10-31)

- Source change `4e285f09f8c16d981b6447ca0fc937564131fc90` hides the redundant empty-state status sentence while no clip is loaded and restores it when a clip is selected, preserving the primary add-video action in the compact tool panel.
- Instrumentation assertion added in `EmulatorScreenshotTest#captureHomeEditorAndTextPanel` requires the add-video action to meet the 44dp/120dp touch target and remain fully above the dock at 393×873.
- [Run #402 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41143199557) ran only `EditorTimelineInteractionTest#compactPhoneKeyboardKeepsTextFieldEditableAndRestoresEditor`; it confirms the short-screen keyboard gate, **not** the new 393×873 empty-editor assertion. The screenshot assertion remains pending a targeted run.
- The screenshot workflow filter now targets `EmulatorScreenshotTest#captureHomeEditorAndTextPanel`; validate the new assertion and inspect the resulting artifact before closing this gate.
- Version remains 394/build 61. Version 395 remains blocked; no version bump, signing, publishing, or updater activation.
