# MathMotor Video — current project status

## Canonical mainline state — 2026-11-01

- Emulator run #687 itself remains red because its full Android log capture was truncated. The filtered-log correction was validated in #688; see the 2026-11-02 evidence section below for successful frame-match, scrub, playback-sync and autosave checks.
- Active source: `main` only. The user-approved UI/editor baseline from `b82245d02ad9f51a1cca6795ba33cd31360c8de4` is now integrated in merge commit `668ed1c0653a05871b3ffb855cc3a3b3aa61435d` (PR #16).
- Preserved the approved editor activity, filmstrip, fixed center playhead, source-frame-rate-aware scale/ruler math, thumbnail sample/alignment logic, independent track/layout helpers and matching tests. Do not replace these with the interim main-only `VideoEditorActivity.kt` or fixed-30-FPS `TimelineFrameMath` implementation.
- PR-head checks passed on the merged tree: Verify Android build #452, debug APK build #488, and API 35 emulator editor/screenshot flow #669 (integration commit `934d9011c8afd65e4764a39b2411a50cd216047d`).
- #669 confirmed: debug APK installed; home, empty editor and populated editor launched; test clip loaded; thumbnail at 211 ms matched the exact preview frame under the playhead; forward/reverse filmstrip scrub passed; playback sync passed across 16 samples and two lanes; playhead remained centered; local draft autosave/resume navigation completed.
- The first emulator run failed only because Nexus Launcher was stuck in a system ANR dialog. The harness now closes that specific stalled launcher; it does not dismiss MathMotor-owned ANRs. The corrected run #669 passed.
- Physical-device validation remains outstanding. The debug APK is not a signed release. No user-phone installation is claimed.
- Release baseline remains versionCode 394 / Build 61. Version 395 remains blocked pending the user's remaining acceptance checklist and explicit release approval.
- Older timeline/frame repair notes below are historical notes for an interim implementation. They must not override this canonical approved-source state.


## Verified mainline state — 2026-11-02 (supersedes older pending notes)

- **#688 passed** ([Actions](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41352473376)): API 35 debug APK install and editor smoke flow; deterministic 8.023-second fixture import; centered preview/filmstrip match at 211ms with RGB delta 0; 4/4 visible thumbnail samples distinct; forward and reverse filmstrip scrubbing; 18 playback samples from 333ms to 4716ms with ruler/video lanes synchronized and fixed playhead/Play center at x=160px; draft autosave and return to draft list.
- **#489 passed** ([Actions](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41352479949)): `testDebugUnitTest` and `assembleDebug`.
- **#441 passed** on PR #15: pinch-out/in, five-second anchor stability within 12px, and one-finger VIDEO-lane scrolling that moves the ruler with it.
- Current main source already contains adaptive ruler-grid rendering (`TimelineScaleMath.rulerGrid`) and source-frame ticks at close zoom; unit tests cover interval changes, source frame rates, pinch-anchor math and thumbnail sample sequencing. Visual checks at multiple zoom states and physical-phone confirmation remain open.
- Confirmed implementation gap: Undo/Redo is backed by `ClipEditSnapshot` and only restores clip state. Text and audio changes are not part of a shared project history.
- #688 screenshots show one ruler/time header and no old bottom green zoom slider/+/- controls, “Klip 1/2” labels or timeline navigation arrows. This is visual evidence at the API 35 emulator size, not full multi-screen UI acceptance.
- Still open: trim-handle and clip-selection gestures; timeline beginning/end access after zoom; shared text/music/sound alignment and edit interactions; text timing/size through preview/export; global Undo/Redo; multi-clip order/trim/join/export; MP4 video/audio/duration validation; project save-close-reopen; thumbnail latency/cache reuse/black-placeholder behavior across portrait/landscape/square sources; keyboard responsiveness and physical-phone smoke testing.
- Keep versionCode 394 / Build 61. Version 395 remains blocked; no release APK, version bump or update offer is authorized.


## Sequential filmstrip thumbnail decoder — 2026-11-02 (source change; verification pending)
- Latest PR #17 source: `d12a3088fb2c61eec0e3b49755e6bd55c1b52c02`. Verify #505 passed unit tests and debug compilation; APK build #504 passed and uploaded a debug artifact. Emulator #703 booted Android 35 but was cancelled before the app-launch/screenshot script started, so it produced no new app-level result. This is an infrastructure cancellation, not an app failure. The draft branch has not been merged to main.
- API 35 timings from #699 on the preceding source commit `6972223`: provider poster 1.626s; first exact timestamp frame 3.182s; all 19 distinct samples 5.977s. Compared with the preceding sequential-only result of 9.355s, full-strip fill improved about 36%. #699 was cancelled during the final playback wait after screenshot/log collection and is not a fully green emulator run. Forward/reverse scrub logs show preview updates at 667ms, 1500ms and 625ms. Commit `d12a308` moves the poster request to URI selection so it overlaps metadata reads; this latest change is awaiting timing validation in #703.

- The two-pass sync-preview experiment was withdrawn. API 35 timings showed that it added a second decode per visible cell and still delayed the first provisional image.
- Filmstrip thumbnails now use one persistent Media3 `FrameExtractor` for the current source URI. Media3's internal implementation manages a shared ExoPlayer and serializes extraction requests; the app now feeds it from a single bounded worker rather than maintaining two competing per-thread platform decoders.
- Seek mode is explicitly `EXACT`. The existing `Presentation.createForHeight(288)` effect downscales during frame extraction; timestamped exact frames remain the authoritative cell images. `MediaMetadataRetriever` is retained only as a compatibility fallback if Media3 extraction fails.
- Duplicate requests caused by a rebuilt timeline view attach to one in-flight request. Existing exact-frame cache reuse inside a cell's source-time interval remains enabled.
- Diagnostics separately record queue wait, decode duration, request-to-ImageView assignment, and the actual presentation timestamp returned by Media3.
- **Verification pending:** API 35 runtime checks must confirm actual returned timestamps, exact playhead/preview match, distinct visible samples, first-frame latency, full visible-strip fill time, bidirectional scrubbing, and fallback behavior. Compare measured results against the prior two-pass run; no speed improvement is claimed yet.
- The first clip cell now requests an Android document/media-provider poster asynchronously; it is a temporary cover for slot 0 only, and exact extraction replaces it. The poster is never repeated across the strip or treated as a timestamp-accurate frame.
- The first cell also schedules a high-priority Media3 warmup for that cell's actual sample timestamp. The normal queued task rechecks the in-memory cache before decoding, avoiding a duplicate decode of the warmup frame.
- Visible timestamp samples within half the centered timeline viewport are scheduled ahead of off-screen samples; off-screen filmstrip completion remains background work.
- The poster path uses `ContentResolver.loadThumbnail` on API 29+ and a one-frame `MediaMetadataRetriever` fallback on older/unsupported providers. The timeline's exact-frame decoder and timestamp mapping remain authoritative.
- No persistent disk cache is included yet; first-load and reopen measurements will decide whether it is needed.
- **Verification pending:** measure poster-ready time, first exact-frame time, warmup decode time and full-strip fill time on API 35; verify the temporary cover is replaced, only slot 0 uses it, provider-failure fallback works, and portrait/landscape/square videos remain correctly aligned. A successful build alone will not close this item.
- No version metadata changed. Keep versionCode 394 / Build 61. Version 395 remains blocked until the complete release checklist passes and the user authorizes release.

## Timeline frame/zoom repair — 2026-11-01

Implementation from PR #15 was merged into `main` in commit `0acdbb3`. The feature branch is historical; continue development only on `main`.
- Added integer `TimelineFrameMath` with a 30 FPS project default and nearest-frame seek snapping.
- Timeline scrub requests and playback-synchronized playhead display are aligned to project-frame boundaries.
- Added a timeline-scoped two-finger pinch recognizer that preserves the time under the pinch anchor after zoom.
- Added focused unit tests for frame boundaries, snapping, alternative FPS values and pinch-anchor preservation.
- Added `TimelinePinchGestureTest` for real API 35 emulator pinch-in/out, anchor stability and shared video/ruler scrolling.
- Wired the API 35 emulator and targeted gesture test into the existing PR-triggered `Verify Android build` workflow; the dedicated screenshot workflow remains for manual runs and main-branch pushes. These workflows do not publish an APK.
- Isolated the pinch instrumentation run in `.github/scripts/run-timeline-pinch-test.sh`; `.github/scripts/run-emulator-checks.sh` now runs the screenshot and gesture checks through one Bash wrapper. The gesture test no longer reinstalls the APK after the screenshot flow stages the deterministic video; the instrumentation assertion checks its absolute app-private path.
- Run #426 failed in the test harness before gesture dispatch: unit tests/build and screenshot/autosave passed, but the second APK installation invalidated the staged fixture. The inline emulator-runner multiline shell block also ended with a syntax error. Pinch zoom, anchor stability and lane/ruler synchronization remain unverified; corrected harness rerun pending.
- Run #430: unit tests/build, deterministic fixture generation/staging, editor video-load, screenshots and autosave passed. The instrumentation's `UiDevice.executeShellCommand("run-as ... test")` fixture check failed before touch events, although the preceding ADB script listed the file in the app-private directory. Replaced that check with `targetContext.filesDir` file validation; actual pinch/anchor/shared-scroll assertions remain unverified until rerun.

Review findings retained as open:
- Existing millisecond-to-pixel mapping is shared across timeline lanes; this part is reusable.
- Existing thumbnail extraction/cache is timestamp-based, but its visual accuracy and loading behavior remain unverified.
- No dedicated adaptive ruler renderer was found in the inspected editor source.
- The playhead is still positioned by absolute timeline time minus horizontal scroll; fixed-center CapCut playhead behavior is not implemented by this change.
- Source-video frame-rate/variable-frame-rate mapping and exact preview-frame correspondence still need verification.

**Validation state:** PR #15 run #441 passed unit tests, `assembleDebug`, the screenshot/editor/autosave flow, and one focused API 35 `TimelinePinchGestureTest`. The test confirmed pinch-out zoom, the five-second ruler anchor within 12 px after zoom, pinch-in reduction, and ruler movement following a one-finger VIDEO-lane swipe. Main-branch smoke runs #665 and #666 also passed APK build/install, home and editor capture, 8.023-second fixture insertion, and draft autosave. The Pixel/Nexus Launcher `Close app` fallback added after #665 was not exercised in #666 because no launcher ANR recurred. Physical-device validation and broader editing/export checks remain outstanding; version 395 remains blocked.

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

## User-reported problems for the next release (end-to-end acceptance remains open)

Some implementation and narrow automated checks have since passed. The active list below describes remaining full acceptance, not a claim that every underlying code path is absent. See the 2026-11-02 verified mainline section above.
1. One-finger and pinch basics have partial API 35 evidence (#441 and #688); clip selection, trim handles, text/audio-lane gestures and physical-device conflicts remain unverified.
2. Timeline appears locked to the left and cannot be moved freely.
3. Timeline filmstrip thumbnails are too small and too sparse.
4. #688 confirmed 4/4 visible samples are distinct and the centered 211ms thumbnail matches preview exactly on the deterministic fixture; black-placeholder behavior, load latency, caching and varied source-video coverage remain open.
5. #688 confirmed fixed-center playback synchronization and forward/reverse scrubbing; exact split-at-playhead behavior and multi-clip scrubbing still need validation.
6. Text start/end timing cannot be set reliably and text size is not adjustable as expected.
7. A separate project-duration control is confusing or conflicts with actual timeline duration; use one unified timeline track/ruler and one source of truth for total duration.
8. Adaptive ruler-grid logic and unit tests now exist in main; visible minute/second/frame transitions still need interactive multi-zoom review.
9. Confirmed code gap: Undo/Redo currently snapshots clip edits only; text and audio do not participate in one global project history.
10. Home screen has redundant explanatory copy/menus and needs a simpler professional layout.
11. Scissors icons are overused; cut/split/trim/delete actions need distinct, restrained controls.
12. #688 screenshots show the old green slider and +/- bottom zoom strip are gone; the pinch interaction passed #441. Full device-level gesture review remains.
13. “Klip 1/2” labels are absent from #688 editor screenshots; clip selection/boundary interactions still need acceptance tests.
14. Navigation arrows are absent from #688 screenshots; pinch and one-finger VIDEO-lane scroll passed #441. Tap-to-select and draggable trim handles remain unverified.
15. Bottom tool navigation should swap the four-item dock for the selected tool's controls in the same bottom panel, then restore the four-item dock on back/confirm; tool panels must not hide underneath the preview.
16. Timeline thumbnails need more visible width, clearer frame content and faster prioritized loading/caching.
17. Simplify the two-video join/add-clip area visually while preserving the actual add/order/join behavior.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.

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


## API 35 instrumentation crash root-cause fix — 2026-11-01

- Run #435 diagnostics and source inspection identified a test-harness defect: `TimelinePinchGestureTest` invoked `am force-stop com.mathmotor.video.next` from inside that package's active instrumentation run. The force-stop terminates the target package and its running instrumentation, explaining the generic instrumentation-process crash before gesture assertions could complete.
- Removed the in-test force-stop. The test now clears stale logcat and launches the editor test activity without killing the active test process.
- This is a test-harness correction, **not evidence that pinch behavior passes**. The focused API 35 gesture test must run again; its result is pending.
- No app version metadata changed. Version 395 remains blocked.


## Run #437 — compact-layout ruler omission fixed — 2026-11-01

- Run #437 confirms the earlier instrumentation-process crash was removed: the focused test started and executed on API 35. Unit tests and `assembleDebug` passed; the emulator screenshot/autosave smoke flow and fixture staging also completed.
- The actual assertion failure was `The five-second ruler marker did not appear`. Source inspection found the cause: `refreshTimelineTracks` created the `SÜRE` ruler only when both `!keyboardLayoutActive` and `!compactEditorLayout` were true. The 320×640 API 35 emulator is classified as compact, so the ruler was intentionally omitted and the test could not find `00:05`.
- Corrected the UI to keep the time ruler visible on compact phone layouts (18 dp lane instead of 22 dp); it remains hidden while the keyboard is active. This is a real responsive-editor behavior correction, not merely weakening the test.
- Next validation: rerun unit/build and the focused API 35 gesture test. Pinch-in/out, anchor preservation and video-lane/ruler shared scrolling are still **unverified** until the test completes successfully.
- The emulator termination messages (`stop: Not implemented`, snapshot and Netsim cleanup warnings) occurred after the failing assertion while the runner shut down the emulator; they were not the root cause.
- Version 395 remains blocked; no version metadata or release artifact was changed.


## Run #439 — pinch zoom input correction — 2026-11-01

- Confirmed #439's primary failure was the real UI assertion `Pinch-out did not zoom in enough: 1.0454545`; unit tests, Debug APK build, fixture staging, editor load, screenshots and autosave all passed. Emulator shutdown warnings occurred after the assertion and are not the root cause.
- Replaced the sampled `ScaleGestureDetector` callback path with direct two-pointer span ratios applied on every move event. Pinch ownership begins only when both pointers are inside the timeline; the initial child touch is cancelled so clip/trim controls do not continue processing the same gesture.
- Preserved anchor math: the midpoint time is captured at pinch start and restored to the same viewport offset after zoom. Added unit tests for complete multi-event outward/inward span accumulation and invalid span safety.
- Await the new API 35 instrumentation result. Do not mark gesture/anchor/shared-lane checks complete until it passes. No app version metadata changed; version 395 remains blocked.


## Timeline one-finger scroll conflict — 2026-11-01

- Found that the custom video-filmstrip touch listener consumed all touch events and explicitly disallowed parent interception, preventing one-finger horizontal scrolling of the timeline.
- Removed the competing listener so the lane's HorizontalScrollView receives swipe gestures; clip taps and long-press menu remain available.
- Await API 35 interaction verification for shared ruler/video-lane movement and preserved clip selection. Version 395 remains blocked.

## Run #664 — API 35 launcher ANR recovery — 2026-11-01

- **Confirmed before failure:** debug APK build succeeded; deterministic fixture generation succeeded; ADB install succeeded; `MainActivity` launched and Android logged it as displayed after 2.584 seconds.
- **Actual failure:** the screenshot workflow exited 8 after UI hierarchy inspection found `Pixel Launcher isn't responding`. `dumpsys window lastanr` identifies `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`; the app activity is present behind the system dialog. Available diagnostics do not identify a MathMotor crash or MathMotor-owned ANR.
- **Root cause at #664:** the system-ANR allowlist did not include Pixel/Nexus Launcher, so the dialog was misclassified as an app-level ANR and the script exited before pressing `Wait`. Commit `1baa987` added launcher recognition; #665 then completed the screenshot/editor/autosave smoke flow. No launcher ANR appeared in #665, so its recovery action was not exercised.
- **Follow-up harness change:** when a Pixel/Nexus Launcher ANR is actually present, select its `Close app` action to clear the stalled launcher process; retain `Wait` for other explicitly system-owned ANRs. Any MathMotor-owned ANR remains a hard failure and must never be dismissed as emulator noise.
- No application source or version metadata is changed by this correction. The full screenshot/editor/autosave flow subsequently passed in #665 and #666. The launcher-specific `Close app` fallback was not exercised in #666 because no launcher ANR recurred. No physical-device validation has occurred. VersionCode 394 / Build 61 remains the only baseline; version 395 remains blocked.


## Run #441 gesture verification and main smoke runs #665/#666 — 2026-11-01

- PR #15 run #441 passed unit tests, Debug APK assembly, screenshot/editor/autosave smoke checks, and the API 35 `TimelinePinchGestureTest` (one instrumentation test; `BUILD SUCCESSFUL in 48s`).
- The passing test asserted pinch-out zoom of at least 1.4×, the `00:05` ruler marker remaining within 12 px of its original screen position after pinch-out, pinch-in reducing zoom, and one-finger VIDEO-lane swipe moving the ruler marker by more than 12 px.
- Main runs #665 and #666 passed the screenshot/editor/autosave flow. Run #666 also confirms successful APK build/install, app launch, insertion of the deterministic 8.023-second video as one clip, and draft autosave. The artifact contains screenshots/diagnostics; it is not a signed release APK.
- No launcher ANR recurred in #666, so the Pixel/Nexus Launcher `Close app` recovery branch remains unexercised. It is scoped to the named launcher only; app-owned ANRs remain hard failures.
- These results verify only the listed smoke and gesture checks. Thumbnail accuracy/scaling, all trim and multi-track edits, export, Undo/Redo and physical-device behavior remain open. The 394 / Build 61 baseline and release gate are unchanged.



## Filmstrip thumbnail responsiveness — 2026-11-01 (source changes; emulator verification pending)

- Pause/Stop preserves the last rendered video frame while the exact still-frame request runs. A failed pause-time still decode no longer clears a valid preview to black.
- Removed the `OPTION_CLOSEST_SYNC` fallback from filmstrip extraction because sync-frame fallback can repeat one keyframe across different timestamps. The closest-frame path remains; the Media3 fallback extractor is reused per decoder worker/source URI rather than rebuilt for each cell.
- Added source-time start/end bounds for each thumbnail cell. On zoom-out, an already decoded bitmap inside the new cell's time interval is reused immediately; the playhead-nearest cell is kept exact for preview synchronization.
- Replaced Shorts-only 9:16 cell geometry with a fixed-height strip and source-aspect-ratio-derived cell width. The first decoded thumbnail teaches the layout the actual ratio; portrait, landscape and square sources are fitted without crop/stretch, and cached bitmaps survive the one-time layout refresh.
- Added unit tests for cell-range partitioning and portrait / landscape / square cell sizing.
- Verify Android build #474 passed unit tests and `assembleDebug` on commit `283a09e341489d77aac964f1bd944a0250bcd2b1`.
- API 35 run #682 failed the preview/thumbnail pixel check at 211ms (different RGB values). Mainline run #688 subsequently passed the centered preview/filmstrip pixel check exactly (RGB delta 0) and confirmed 4/4 visible samples distinct on the deterministic fixture. Physical-phone testing remains open.

Still open:
- Extend the passing #688 thumbnail distinctness/frame-match checks beyond the deterministic fixture to varied real source videos, orientations and zoom levels.
- Measure initial thumbnail fill time and verify progressive visible-first loading.
- Verify zoom-out reuses in-range thumbnails without launching new decode tasks, and zoom-in only decodes newly needed time intervals.
- Visually verify Play/Stop retains the final frame, including the decode-failure path.
- Capture and verify portrait, landscape and square filmstrip layouts and confirm the preview/filmstrip source frame correspondence.
- Keep versionCode 394 / Build 61 as the user-approved baseline. Do not create version 395, sign/publish a release, or offer an update before all acceptance checks pass and the user explicitly authorizes it.


## 2026-11-02 — ruler header refresh crash

- Run #685 / Actions run `41349936764` built and installed the debug APK, launched `EditorTestEntryActivity`, then crashed during the responsive-layout-triggered timeline rebuild. The captured stack is `IllegalStateException: The specified child already has a parent` at `VideoEditorActivity.kt:1765`, in `refreshTimelineTracks() -> makeLane()`. The reported clip-import timeout was secondary; the editor process had already exited.
- Cause: `timelineTracks.removeAllViews()` detached the previous lane row but left its nested `timelinePlayheadLabel` parented by the old header `FrameLayout`. The subsequent refresh reused that same View in the new header.
- Correction committed to main as `bf09c70efe0fc9d63aeff5437b369a38c341b825`: explicitly remove `timelinePlayheadLabel` from its current `ViewGroup` before adding it to the rebuilt ruler header. Preserve the single label and the shared 96dp header alignment.

Verification still required:
- Run a new API 35 emulator smoke test and confirm editor launch plus fixture import now complete without a crash.
- Review the newly captured editor/ruler screenshot and verify ruler ticks remain aligned with every media lane.
- Only after that proceed to broader timeline/thumbnail acceptance checks. The #685 failure is not a green test and #479 only verifies compilation/unit tests.
- Keep versionCode 394 / Build 61 as the approved baseline. Do not create version 395, publish/sign a release, or offer a replacement APK until all acceptance checks pass and the user explicitly authorizes it.


## 2026-11-02 — corrected scrub time offset from ruler header width

- Run #685 was a pre-import editor crash: a repeated timeline refresh tried to add `timelinePlayheadLabel` to a new parent while it still belonged to the old detached header. Main commit `bf09c70efe0fc9d63aeff5437b369a38c341b825` detaches the label from its former `ViewGroup` before reusing it.
- Run #686 passed editor launch and sample-clip import and reached the frame-alignment assertion. The filmstrip's centered cell was at 211ms, but ready preview frames were at 542ms and 583ms; the strict check correctly failed. This is not the old view-parent crash.
- The mismatch came from stale gutter geometry: the visible ruler/media header is 96dp wide and the row has a 3dp leading inset (99dp total), while spacer and time conversion helpers still subtracted the former 57dp grip gutter. At 120dp/s this explains approximately 330ms of error.
- Commit `102bdcd899b397dc439d1c2c3193b651e57fd6d6` centralizes the 99dp header gutter for leading/trailing spacer geometry and fit-to-project width. Commit `3a6d6b474c6eb6cf24f8f0fb01a34601a1240653` updates `TimelineScaleMathTest` geometry to the new header width. The preview/frame tolerance was not relaxed.

Verification pending:
- Emulator run #688 passed the corrected 211ms alignment check: the preview and centered filmstrip sample had exact RGB agreement (maximum channel delta 0).
- Verify Android build #484 validates the revised geometry unit tests and APK compilation.
- Run #688 also passed forward/reverse scrub and live ruler/video playback synchronization. Continue with still-open checks for thumbnail reuse/fill speed, Stop frame retention, portrait/landscape/square sources and physical-phone acceptance.
- Keep versionCode 394 / Build 61 as the approved baseline. Do not create version 395, sign/publish a release, or offer a replacement APK until all acceptance checks pass and the user explicitly authorizes it.


## 2026-11-02 — Media3 decoder-selection experiment (verification pending)

- Run #713 restored one shared Media3 FrameExtractor and passed the editor smoke checks. The timestamp log identifies the largest stall: first exact frame (211 ms) took 4,060 ms; the other 18 took 80–456 ms each, 3,298 ms combined; entire strip finished at 7,877 ms.
- Source inspection of Media3 1.10.1 shows FrameExtractor defaults to MediaCodecSelector.PREFER_SOFTWARE. This prioritizes software decoders over the platform-preferred order and is a plausible cause of the expensive initial preparation.
- Current experiment keeps one shared extractor, exact seeks and the existing 288px Presentation, but selects MediaCodecSelector.DEFAULT. Decode exceptions are caught and the existing platform retriever remains the compatibility fallback.
- Risk is explicit: Media3's FrameExtractor source warns some hardware decoders can fail/crash when flushed for repeated seeks combined with video effects. Require successful API 35 alignment, unique frames, forward/reverse scrubbing and playback synchronization. Compare first-frame and full-strip timing to the 5.98-second baseline.
- If hardware selection causes decoder failures, a wrong/black preview, or slower results, revert to PREFER_SOFTWARE. No speed improvement is claimed until the full artifact is measured.
- Build 394 / Build 61 remains unchanged; no release APK.
