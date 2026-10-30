# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- User-confirmed installed baseline: versionCode 394, release/build 61 (`app-v8.3-61`).
- Version 395 is blocked until every required item in README.md is implemented and verified.
- Do not bump versionCode/versionName, sign a new release APK, publish a release, or offer an update for 395 before the release gate is met and the user authorizes release.
- README checklist and source commits do not mean the behavior is fixed or included in the installed APK.
- Existing repository release notes may lag the user's installed version; use the user's confirmed 394/build 61 as the working baseline.

## Evidence-backed status — 2026-10-30

### Latest successful candidate run

- [Run #340 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996): unit tests and Debug APK build passed; connected Android instrumentation passed **20/20, 0 failures, 0 skips**. Job steps for unit tests, debug build, deterministic fixture preparation, emulator interaction and artifact upload all completed successfully.
- The 19 interaction tests cover timeline swipe/centered pinch, trim-handle hit routing and source-footage restoration, clip reorder/trim/Undo/Redo/seek, text timing/size/preview, text/audio history, sound-effect lifecycle/history, draft and JSON project reopen, multi-clip/split MP4 export, timed text/music/effect/source audio, A/V seam alignment, and distinct filmstrip timestamps.
- The separate screenshot test checks home/editor/text panels, the empty-editor add-video state, single time/ruler presentation and responsive layout at 393×873. Screenshot/diagnostic artifact: [12984726239](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41066572996/artifacts/12984726239).
- [Run #337 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41058278720) also passed the 8-second filmstrip density/distinct-frame scenario (6–10 loaded thumbnails from distinct timestamps).
- These are verified emulator scenarios, not physical-device approval. The tested video fixture is about 8 seconds; the exact 10-second case is not yet verified. Do not rerun the same green #340 scenarios unchanged.

### Already covered by automated evidence — do not classify as “not implemented”

- Timeline pinch zoom and keeping the centered time anchor stable; one-finger filmstrip scroll; both trim edges and restoration; timeline start/end reachability.
- Multi-clip selection/reordering, trim, seek, Undo/Redo and split-at-playhead export.
- Text timing/size/preview after seeking and zooming; unified text/audio history; continuous timing-drag grouping; sound-effect add/start/remove/Undo/Redo.
- Local draft and manual JSON save-close-reopen scenarios.
- MP4 cases for two clips in order, timed/styled text, looped music, timed sound effect, source audio gain/mute and audio/video seam alignment.
- Timestamp-distinct filmstrip frames and the compact home/editor/text-panel emulator layout.

## Current source functionality (implementation exists; reliability still needs testing)

- Home screen with gallery import, empty-editor action, recent outputs and local drafts.
- Video import, multi-clip timeline, clip reorder/copy/delete/split, source trim start/end, playhead seek and timeline zoom.
- Trim and playhead controls use millisecond mapping; legal trim bounds consider the opposing edge and source duration.
- Video thumbnail extraction uses timestamped platform-frame APIs first, then Media3 for the requested timestamp.
- Text layer timing, position, size, colour and bold settings; background music and one timed sound effect; original audio volume.
- MP4 export with source / 720p / 1080p options via Media3 Transformer.
- JSON project save/load and local draft autosave, including playhead time, zoom and horizontal scroll.
- Responsive preview/timeline layout, reduced keyboard mode and compact dock.

## Remaining product acceptance gaps after #340

The automated scenarios above have passed on the emulator. The following items remain open because they are broader than those scenarios, require visual/physical-device acceptance, or are not directly covered:

1. Physical-device smoke test on the actual installed phone; emulator success does not prove touch comfort, frame pacing, memory behaviour or OEM-specific playback/export.
2. Exact 10-second video scenario at the beginning, middle and end, on multiple zoom levels. Current deterministic fixture is about 8 seconds; multi-clip editing/export already has emulator coverage.
3. Visual inspection of the uploaded #340 screenshots against the supplied target UI reference. Uploading screenshots is not the same as reviewing them.
4. Confirm the shared ruler and video/text/music/effect lanes remain visibly aligned after scroll, pinch, seek, clip edits and save/reopen. Shared time-to-pixel math has unit tests; the full visible lane alignment still needs an end-to-end visual acceptance pass.
5. Inspect adaptive ruler label spacing/readability on an actual device at both zoom extremes. The ruler-interval calculation has unit-test coverage.
6. Check keyboard-open editing and panel reachability on a short phone viewport. #340 covers a 393×873 emulator viewport, not the full keyboard interaction.
7. Confirm filmstrip thumbnail size/readability and loading/scroll performance on the user's phone; #337/#340 establish distinct frames and the 8-second emulator density scenario, not physical-device performance.
8. Finish visual polish of the home screen, clip-add/join affordance, redundant icons/labels and tool-panel presentation against the user's target reference; existing screenshots and interaction checks cover only the assertions named above.

## Shared timeline architecture requirement

- All editor content must use the same timeline time coordinate system and adaptive ruler: video clips, text overlays, music, sound effects, playhead and trim boundaries.
- Text/audio lanes must align to the same timestamps and respond to timeline scroll/zoom without changing their actual start/end times. Do not create independent time scales for individual tools.
- Text and audio placement, preview and playback synchronization must be verified at multiple zoom levels and after project save/reopen. These are requirements, not yet device-verified results.

## Verification gaps

- Unit tests cover timeline scale/time-pixel mapping, zoom-anchor preservation, ruler interval selection, thumbnail work ordering, trim bounds/playhead mapping, and text timing boundaries.
- The #340 candidate emulator suite passed 20/20 (19 interaction tests plus the home/editor screenshot test). It exercises gesture/trim, multi-clip edits, text/audio, Undo/Redo, project save/reopen, export and screenshot layout scenarios. The exact test method names and run details are recorded in README.md.
- The emulator tests use deterministic media fixtures; the primary video fixture is about 8 seconds. A separate exact 10-second test remains open.
- Screenshot diagnostics are uploaded and the #340 screenshots have been visually inspected. A loaded-video 393×873 screenshot and physical-device smoke test are still required.
- Do not rerun already-green #340 scenarios without a code change or a specific regression reason. If a new failure occurs, inspect its exact assertion/log first and fix the root cause before a targeted rerun.

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
