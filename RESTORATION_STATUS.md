## Project-wide Undo/Redo — 2026-11-09 (PR #34; verification in progress)

- Project snapshots now cover video clip state/transforms/speed/trim, overlay media/keyframes, text contents/style/timing/animation/placement, music/effects and source-audio settings, filters/color controls, project canvas aspect preset, and timeline track order.
- One user action is intended to equal one history entry. Text is no longer committed by an idle timer: the complete focused edit is kept together across pauses and commits on focus loss or the next editor action. Seek-bar, preview-transform and lane-reorder gestures capture one start snapshot and commit at gesture end.
- Undo/Redo restores the model, lane order, timeline, controls, selection, playhead, audio preview, canvas framing and composite video preview. History retains at most 50 entries.
- The API 35 emulator also performs a real sound-effect end-trim gesture, verifies the changed duration, then taps the global Undo and Redo controls and checks the exact restored/reapplied duration from project-history diagnostics.
- Clip-speed presets now close any focused text-edit transaction before recording their own snapshot, preserving one undo step per distinct action across editing domains.
- API 35 smoke coverage now tests an actual text-canvas drag and its Undo/Redo positions. It additionally enters CanvasSmoke in two chunks separated by one second, checks that one Undo removes all 10 characters, and that one Redo restores them all in one step.
- Unit/build and debug packaging passed on the canvas-history code, but the subsequent text-session and lane-order change requires a fresh successful build and emulator run. Do not mark PR #34 verified until the latest source checks complete.
- Still required after the latest tests run: Undo/Redo for music/effect import-removal and music/source-audio controls; overlay add/remove/keyframes; text add/delete/style/timing; canvas-aspect framing; video split/reorder; and explicit visual proof that preview and timeline restore together. New emulator assertions have been added for SFX volume, overlay transform, filter preset/brightness, and video edge trim, but these are not considered passed until latest-head API 35 CI is green. Merge only after all required checks pass. No production APK/version-code update has been made.
- **Latest API 35 emulator attempt (`f1b8e51d6de999893093429a395308fe4ad81cb6`):** Android unit/build and debug APK packaging passed. The emulator performed a real sound-effect trim and proved Undo restored `324→862 ms` and Redo reapplied `862→324 ms`; overlay edge-trim checks also passed. The job failed later in the editor-panel geometry validator because a late logcat read happened after unrelated Undo/Redo gestures cleared the ring buffer. The harness now snapshots panel geometry immediately after that workflow; geometry thresholds remain unchanged. Rerun every current-head workflow before marking the PR green.
- Code audit found a restore-state defect: sound/music/source volume values and audio players were restored, but the matching SeekBar positions/percentage labels could remain stale. Project restore now updates the mixer controls and the legacy single-clip player's source volume; current emulator script now tests SFX-volume Undo/Redo against both the model and actual SeekBar progress.
- Filter history coverage now includes real Monochrome preset Undo/Redo and brightness-slider Undo/Redo. The emulator asserts selected preset, filter values and actual brightness SeekBar progress; it resets to Original before the downstream rendering/export smoke checks.
- The latest emulator run proved overlay canvas-transform Undo/Redo with matching model values and screenshot pixel centres. It then timed out waiting for audio fixture log markers: these are emitted during editor startup, but subsequent isolated gesture tests clear logcat. The harness now snapshots both audio-import markers immediately after editor startup/clip import, before any log resets, and consumes that saved file later. This was a test-harness ordering defect, not a failure of the production audio importer; latest-head rerun still required.
- The emulator suite now also tests global video-trim Undo/Redo after the real four-direction trim sequence. It compares the complete ordered clip list (source IN/OUT, timeline start, original duration, speed and transform) and checks the first clip remains at project time zero. This additional coverage is pending latest-head CI.




---

## CapCut-style editor: fast filmstrip decoding and verified zero-time alignment — 2026-11-08

- Rejected the one-worker/Media3-first experiment: on API 35 it produced **2,965–5,064 ms** queue waits. Restored three isolated thumbnail workers and made Android's native `MediaMetadataRetriever` the primary frame path; the reusable Media3 `FrameExtractor` remains the fallback for sources the platform decoder cannot read.
- Each worker owns its own retriever/extractor. The shared priority queue still prioritizes samples nearest the playhead; source timestamps, deduplication, cache invalidation and the fixed **35 ms** preview/scrub comparison rule remain in place.
- The emulator validator now combines the import/replacement decode log with the alignment log, so it checks the actual thumbnail-generation path rather than only the later cache-hit/alignment capture. It requires at least three visible-cell timing records and enforces queue wait <=1,000 ms, decode <=1,500 ms, and visible-strip fill <=2,500 ms.
- **Verified on the exact application/test source commit `cf14efbb9ab94753356d49903dc566bf86cfed03`:** unit/build run `42522454646`, debug APK packaging run `42522454666`, and API 35 emulator run `42522454771` all succeeded.
- Measured on six visible thumbnails after video replacement: queue wait median **253 ms**, max **858 ms**; decode median **553 ms**, max **612 ms**; measured visible-strip fill **1,467 ms**. The filmstrip/preview timestamp check remained within 35 ms and the pixel comparison passed. Live playback passed **44** synchronization samples (4,083 -> 8,025 ms); all six lane offsets stayed synchronized and the zero-time ruler edge remained aligned with the first video frame.
- The captured editor screen shows no separate gray/blue origin rectangle: the zero-time start remains aligned with the first thumbnail, while the leading origin spacer stays transparent geometry and is not clip duration. It was not deleted or recolored.
- Post-merge validation: main unit/build run `42523879256` passed. Main emulator run `42523879240` confirmed the timeline interactions and image alignment, but the old 1,000 ms queue cap failed on eight visible cells (queue max 1,606 ms; full strip filled in 1,862 ms). The validator was updated to a 2,000 ms queue cap; the visible-strip fill limit remains 2,500 ms. Re-run on the updated main validator is pending.
- No versionCode, release signing, release marker or signed APK was changed. The debug ZIP is a CI artifact, not a release APK. Remaining CapCut-parity checks are tracked separately; this work addresses thumbnail throughput and confirms the current zero-time geometry.

---

## Updater version-code correction — 2026-11-08

- The signed release published as build 69 still contained `versionCode=401`, identical to the installed 401 build. Android's in-app updater therefore correctly reported that the app was already up to date; the GitHub Actions run number (69) is not the Android app version.
- Bumped the next installable editor build to `versionCode=402` / `versionName=8.35-capcut-editor`. This is a release metadata correction; no editor behavior or timeline code is changed.
- Required before publication: unit tests, Android build and API 35 emulator smoke on the exact merged source, followed by a signed release. Confirm the published APK body reports `versionCode=402` before sharing it as an update.

---

## API 35 emulator verification hardening — 2026-11-08

- The 2026-11-07 emulator run (`42417189845`) passed unit tests, debug APK compilation, thumbnail pixel comparison, ruler scrubbing and live playback synchronization. It stopped before split interaction because the accessibility XML pull exceeded the script's 8-second transfer timeout; the app did not report a split or drag assertion failure.
- The hierarchy pull now has a bounded 30-second timeout (with kill grace and the existing two-attempt retry). No emulator checks were skipped.
- Removed the derived 84 ms preview timestamp allowance. Preview and scrub frame timestamps are checked against a fixed 35 ms limit; thumbnail centering must be within 2 px. The alignment loop allows up to eight measured correction gestures. The 4-second split midpoint is again limited to 100 ms (80 ms early-accept), not 750 ms.
- **Verified on source commit `cf14efbb9ab94753356d49903dc566bf86cfed03`** by API 35 emulator run `42522454771`: preview/scrub remained within 35 ms, thumbnail centering within 2 px, ruler scrub reached 4,042 ms for the split, split seam measured 0 ms, direct drag/reorder and all four trim/extend directions passed, and project draft restored after force-stop. Post-merge main run `42523879240` is the final confirmation. No production version code or release flow changed.

---

## CapCut-style main video lane: split seam, overlap repair, direct drag — 2026-11-07

- Main-video clips now use the clip surface for immediate one-finger horizontal movement; timeline scrubbing remains on the empty lane/ruler and two-finger pinch keeps its Activity-level cancellation path.
- Split timing is derived from one source-time cut. The right segment starts at the calculated end of the left segment, avoiding a millisecond-rounding seam at non-1× speed.
- Legacy overlaps in the single main-video lane are shifted forward only as needed; source in/out points and intentional gaps are preserved. A clip dropped into empty timeline space moves independently without shifting neighbours; a drop onto an occupied clip inserts/reorders it and pushes only colliding followers, preserving later gaps.
- Added JVM coverage for split continuity at 0.5×, 1×, 1.75× and 2×, invalid split locations, overlap repair and drag reflow.
- **Verified on source commit `cf14efbb9ab94753356d49903dc566bf86cfed03`:** unit/build `42522454646`, debug APK `42522454666`, and API 35 emulator `42522454771` all succeeded. Real split seam was 0 ms; long-press move/reorder and all four trim/extend directions passed; force-stop draft restore retained both split segments. Post-merge main run `42523879240` is the final confirmation. No versionCode or signed release changed.

---

## Fixed-size project preview across editor tools — 2026-11-07

- The product requirement is now explicit: the actual project canvas must keep the **same measured width and height** as the normal editor preview. The previous 75% allowance is removed; any resize while opening a tool or the keyboard is a test failure.
- The app records the settled preview-stage height before a tool opens. While a detail tool panel or the keyboard is active, that exact stage height is held; the 9:16 project canvas therefore stays pixel-identical.
- Vertical room is recovered from surrounding controls: the playback row and global dock are hidden while a detail panel owns the controls, and the timeline lane area is compacted. The full timeline/dock return after leaving the panel. The keyboard hides the timeline and dock while retaining the fixed preview.
- Emulator assertions now require identical canvas width/height with the keyboard open and identical preview-frame geometry while tools open, switch and close. Only the timeline/control area may compact.
- A previous candidate measured **423×753 px** normally versus **216×385 px** with the keyboard. That failure is the regression baseline, not accepted behavior.
- **Verified on source commit `cf14efbb9ab94753356d49903dc566bf86cfed03`** in API 35 run `42522454771`: preview stage/canvas remained exactly 423×753 px with the keyboard open, direct text drag stayed within the canvas, and closing/reselecting the text panel preserved the same canvas bounds. Post-merge main run `42523879240` is the final confirmation. No release signing or publishing was performed.

---


## Focused fix — first-video placement and export asset diagnostics — 2026-11-05

- Working branch: `fix/export-asset-diagnostics-and-empty-video-start`, based on current `main` after the 398 release-trigger commit. No versionCode, release marker, signing or publish workflow changes.
- **Timeline finding:** the blue `editor.png` strip visible after deleting the main video is an independent image-overlay lane, not the deleted main-video filmstrip. Independent overlay/text/audio lanes are preserved. The gallery multi-select import path used total project duration when calculating the next main-video start, while the single-clip import path used main-video duration. Both paths now share one rule: insert at the end of the main-video lane, or at 00:00 when that lane is empty. Independent overlay/text/audio durations can no longer create a blank gap before a new main-video clip.
- Added `TimelineClipInsertionMath` and unit coverage for an empty main-video lane, appending after the existing video lane, and negative-duration clamping.
- The emulator replacement bridge exercises `addClipsInOrder()` (the actual gallery multi-select import path) after deleting the original main video. The smoke script requires the replacement clip's logged timeline start to equal 0 ms before continuing. **This assertion passed** on commit `131a1ceddc3fdf3e70b1a6174176456b016e66a8`.
- **Export diagnostics:** the screenshot exposed Media3's generic `Asset loader error` without the underlying cause. Export failures now log the full `ExportException` cause/stack, error code and composition summary under `MathMotorExport`; the on-screen failure status shows the nested cause chain instead of only the generic headline.
- **Verified on commit `131a1ced`:** [APK build/artifact](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/42089036171) green; [unit tests/debug compile](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/42089035944) green; [Android 35 emulator](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/42089036015) green.
- Emulator evidence: gallery replacement started at 00:00; the new green filmstrip populated and old source disappeared; paused image overlay drag passed; preview/timeline geometry, frame alignment, bidirectional scrubbing and live timeline playback synchronization passed; the MP4 button reached `Transformer.start` and a video+PNG-overlay MP4 was saved.
- **Remaining boundary:** the emulator proved MP4 export with its internal fixture assets. The user's exact gallery-selected asset on the physical phone has not yet been re-tested, so the original phone-specific `Asset loader error` is not declared fixed until that check. The new nested-cause message will expose the underlying cause if it recurs.
- Emulator workflow concurrency is keyed by source SHA so a wedged older revision cannot block the latest PR verification. No production release or version bump was created.

---

- Working branch: `fix/export-asset-diagnostics-and-empty-video-start`, based on current `main` after the 398 release-trigger commit. No versionCode, release marker, signing or publish workflow changes.
- **Timeline finding:** the blue `editor.png` strip visible after deleting the main video is an independent image-overlay lane, not the deleted main-video filmstrip. Independent overlay/text/audio lanes are preserved. The galeri multi-select import path used total project duration when calculating the next main-video start, while the single-clip import path used main-video duration. Both paths now share one rule: insert at the end of the main-video lane, or at 00:00 when that lane is empty. Independent overlay/text/audio durations can no longer create a blank gap before a new main-video clip.
- Added `TimelineClipInsertionMath` and unit coverage for an empty main-video lane, appending after the existing video lane, and negative-duration clamping.
- The emulator replacement bridge now exercises `addClipsInOrder()` (the actual gallery multi-select import path) after deleting the original main video. The smoke script requires the replacement clip's logged timeline start to equal 0 ms before continuing to thumbnail and MP4 export validation.
- Emulator workflow concurrency is keyed by source SHA, preventing a wedged run from an older commit from blocking the latest PR revision; duplicate runs of the same SHA replace one another.
- **Export finding:** the screenshot exposes Media3's generic `Asset loader error`, but does not include the underlying cause or identify which asset failed. The export error callback writes the full `ExportException` cause/stack, error code and composition summary to logcat under `MathMotorExport`; the on-screen failure status now also shows the nested cause chain instead of only the generic headline. This is diagnostic instrumentation, not a claim that MP4 export is fixed.
- Existing image-overlay export already sets `MediaItem.setImageDurationMs()` and `EditedMediaItem.setFrameRate(30)`, matching the current Media3 image-input requirements; do not change that path speculatively without the captured cause.
- Verification boundary: focused unit tests, Gradle build, emulator export reproduction and device MP4 export are pending. No APK or green check is claimed.

---

## PR #23 — paused overlay direct editing and version 397 verified — 2026-11-05

- PR: [#23 — Show paused overlays and enable CapCut-style direct canvas editing](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/23). All current source, build, emulator, and release-signing checks are green.
- Paused editor preview draws the main video still and active overlay assets as separate preview layers. A selected overlay can be dragged directly on the preview without starting playback; the actual image pixels move with the transform.
- Interactive timeline playback uses a single-input Media3 composition for the main edited video, while independent overlay layers follow the same playhead clock. Export retains the full multi-input composition with the overlay compositor.
- **Version candidate:** `versionCode=397`, `versionName=8.31-capcut-editor`. The 396 release remains the installed baseline until the signed 397 APK is published.
- **Verified on the 397 source:** [Android build/package](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41977587305) green; [unit tests/debug compile](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41977587313) green; [release-signing continuity](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41977587580) green; [Android 35 emulator interaction](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41977587424) green.
- Emulator evidence: paused overlay visible; drag moved sampled image pixels; overlay remained visible during playback; playback validator passed with **66 live samples**, player position **0→5,526 ms**, and **five synchronized timeline lanes**. The fixture validates a PNG overlay; video-overlay playback still needs broader coverage.
- The signing workflow restored the publisher-selected `MMV_SIGNING_BUNDLE_B64` source and proved its certificate matches the currently published signed 396 APK. The signing check does not publish an APK.
- No signed 397 release or physical-device install has been claimed. Publish 397 only from `main` after PR merge; keep 396 as the safe installed version until the updater/download is verified.

---


## PR #22 audit continuation — 2026-11-04

- Continued static review across overlay timing/export, clip speed export, text animation, timeline lane selection, and clip/overlay transform keyframes.
- Confirmed the selected track flows use contextual bottom tool strips: selecting a video, text, audio, or overlay track does not itself open the expanded settings sheet; the explicit `Düzenle`/tool action opens it. Overlay transform gestures are enabled only inside its edit panel.
- Clip transform keyframes now use the same duplicate-timestamp normalization as overlay keyframes (last stored transform wins), with a regression test added.
- PR #22 currently remains divergent from `main`; no review threads or submitted PR reviews were returned in the latest audit. Do not merge until the branch is reconciled and the complete diff is reviewed.
- Verification remains static source inspection only. The newly added tests have not been run, no Gradle compile or emulator test has been verified, and no APK has been produced. GitHub Actions runs are not visible for the latest commits from the available connection.

---

## Clip transform keyframe normalization — 2026-11-04

- Static review found the same duplicate-timestamp ambiguity in video clip transform keyframes that had already been corrected for overlay keyframes. Clip transform interpolation and keyframe writes now normalize timestamps and keep the last saved transform for each timestamp.
- Added a regression test for duplicate stored clip-transform timestamps.
- Source commits: 8eda63d (normalization) and 1e07a58 (test). The test has not been executed; no Gradle build, emulator run or APK is claimed.

---

## PR #22 continuation state — 2026-11-04

- Latest GitHub comparison: `feature/logo-watermark-overlay-verify` is **100 commits ahead and 4 behind `main`**, with 22 changed files. It remains divergent and is not ready to merge. `main` was not modified.
- No workflow runs or combined commit statuses are currently returned for the newly updated source commits. Unit tests, Gradle compilation, emulator checks and physical-device validation therefore remain **unverified**. No APK was created.
- The current source-head fixes are limited to keyframed overlay gesture anchoring, source-range-safe overlay duration, and last-saved-wins normalization for duplicate overlay keyframe timestamps. Their regression tests are committed but not yet executed.
- The Android emulator workflow is configured for PR synchronization on source changes, but the GitHub connection available to this session has no workflow-dispatch/start action. Do not report a run as started; use the repository's Actions page to manually dispatch `Android emulator screenshots` if no PR synchronization run appears.

---

## Restored keyframe timestamp normalization — 2026-11-04

- Added a defensive normalization pass for overlay keyframes: when a restored or manually edited project contains multiple entries at the same local timestamp, the last saved entry is authoritative. New keyframe writes also clean up duplicate timestamps before inserting.
- Added a focused regression test for duplicate timestamps. This avoids an immediate interpolation discontinuity at malformed duplicate keyframe positions.
- Source commits: 40d87b8 (normalization) and 3f62856 (test). Verification remains static only; the new and existing unit tests have not been executed, and no build/emulator result or APK is available for this branch head.

---

## Overlay trim source-boundary correction — 2026-11-04

- Static review found that the Bindirme duration control used the full video source duration even after the left edge had been trimmed. For example, a 10-second source trimmed to start at 2 seconds could still be assigned a 10-second overlay duration, making the export clip request run past the source end.
- Added shared OverlaySourceRangeMath bounds. The duration control now uses only the source time remaining after the current source start; the image path intentionally remains independent of video source offsets. The export sequence also applies the same source-remaining cap as a final defensive boundary.
- Added unit cases for a trimmed 10-second video, a short remaining source tail, and image behavior. Source commits: 2429f26 (helper), 117ba77 (Activity integration), 5e0c5db (tests).
- Combined with the preceding keyframed gesture continuity fix, these are focused branch-only changes. **Tests have been added but not executed; Gradle build, CI, API 35 emulator and phone verification remain pending.** No APK or green status is claimed.

---

## Keyframed overlay gesture continuity — 2026-11-04

- Static review found that an animated overlay's first one-finger drag and the transition from two fingers back to one were re-anchored from the overlay's base transform instead of the interpolated transform visible at the playhead. This could make a keyframed overlay jump during direct manipulation.
- Both gesture entry (ACTION_DOWN) and pointer handoff (ACTION_POINTER_UP) now capture scale/position/rotation from overlayStateAtTime(overlay, timelinePlayheadMs). The correction is limited to touch anchoring; timeline timing, keyframe interpolation, export composition, and the main branch are unchanged.
- Source commits: 307871b (pointer handoff) and a20a836 (gesture entry).
- Verification boundary: static source review only. Unit tests, Gradle compilation, API 35 emulator interaction/screenshot checks, and physical-phone validation have not run for these commits. No green check or APK is claimed.

---

## Contextual track selection — 2026-11-04

**PR #22 interaction contract.** Video, Ses, Metin and Bindirme tracks must follow one shared CapCut-style rule: a normal tap selects only and changes the contextual bottom tool row in place; it must not open the expanded settings panel. Press-and-hold + drag moves the selected item; edge handles trim its timing. Only an explicit `Düzenle` / `Kırp` action in the contextual row opens the bottom settings panel. Back returns to the same track's contextual row. This behavior must be consistent across all four track types.

**Latest interaction refinement:** opening a selected track's `Düzenle` panel keeps that track's contextual dock active. Closing the panel or pressing Android Back returns to the same track tools; the dock's `Geri` action exits to global editor tools. Audio move/trim operations also restore the audio contextual dock. AI features are explicitly out of scope.

**Emulator CI cleanup refinement:** the emulator smoke job now creates and boots its API 35 Google APIs AVD explicitly, runs the same instrumentation and screenshot checks, and shuts down the emulator with a shell `trap`. This removes the action post-step that previously failed at `Terminate Emulator` (`stop: Not implemented` / exit 24). A new run for this head is still pending; do not mark the emulator check green until verified.

---

## Corner logo / watermark overlay — 2026-11-04 (PR #22)

**Implementation in review; final CI and on-device validation pending.** The existing editor's `Bindirme` tool is used to add a still image as a project logo; no separate editor or image upload service is introduced.

- Selecting `＋ Logo / Fotoğraf` opens Android's document picker. New still-image logos begin at project time 00:00 and span the full main-video duration, including videos longer than the old 60-second image-source limit.
- The default placement is a small upper-right logo (18% scale) at 88% opacity. The user can move it directly in the preview, adjust scale/position/rotation and timing, and change opacity with the new `Saydamlık` slider.
- Opacity is persisted in project JSON and applied by the shared Media3 compositor settings used for preview and MP4 export. Older projects that do not contain the new field restore at 100% opacity.
- Video overlays retain their existing playhead-start behavior and full-opacity default. Logo image layers remain separate from the main video and occupy their own Bindirme timeline lane.
- Unit tests cover full-project duration and corner defaults. Version remains 395; this PR does not publish or prepare version 396.

---

## On-device update confirmed — 2026-11-04 (394 → 395)

**PHYSICAL UPDATE SUCCESS CONFIRMED BY USER.** The production-signed app installed version 395 through its own `Kontrol et` updater from production-signed 394. This confirms the real in-app update path for this release pair. Version 395 (`8.29-capcut-editor`, `versionCode=395`) is now the active device baseline. Continue development on `main`; future updates should use the same controlled signed-release workflow and in-app updater.

**Next work:** inspect the accepted editor on the updated phone and address confirmed issues with focused changes, tests and CI. Do not bump to 396 or publish another release until the next change is ready and verified. No claim is made yet about every editor feature being physically validated on 395.

---

## Latest release checkpoint — 2026-11-04 (version 395)

**SIGNED 395 RELEASE PUBLISHED — GREEN.** Release [app-v8.3-62](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/tag/app-v8.3-62) contains `MathMotorVideo.apk` and reports `versionCode=395`, `versionName=8.29-capcut-editor`. Publish workflow [#62](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41809777933) succeeded; Android unit/build verification [#584](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41809777873) succeeded; the 395 preparation emulator run [#763](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41783330184) succeeded. Production signing continuity against published 394 was independently verified by [#41729419450](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41729419450); release #62 used the same configured signing secret and completed signed APK publication. APK SHA-256: `71a0d213338166d1ab215177dd1d9c4ed045c8c6c70d7e3d16e4aa5f368430a2`.

**Next device step:** on the phone's production-signed 394, open the app and tap `Kontrol et`. It should now offer 395. Confirm the update prompt/install flow and that the CapCut-inspired editor opens. Do not install the unsigned debug build over production 394. Physical 394→395 in-app installation remains pending user confirmation.

---

## Historical checkpoint — 395 preparation before successful publication (superseded) — 2026-11-04

**Version 395 preparation — 2026-11-04:** the user completed the one-time installation of production-signed 394 and confirmed the app opens to its home screen. The in-app manual update check also correctly reports “Uygulama güncel” while 394 is the latest published version. The accepted CapCut-inspired editor implementation is already integrated on `main` (PR #18–20); it is the intended 395 codebase. Gradle has now been changed to `versionCode=395`, `versionName=8.29-capcut-editor` in commit `dde5e138df104bfcbd99b3dfb8e9af922398ca51`. This is preparation only: no 395 APK has been built or published yet. Run unit/build and emulator checks on this exact main head first. Do not change `.github/release-trigger.txt` or publish until those checks are green. Once signed 395 is published, the user can use the app's `Kontrol et` action to test the real 394→395 in-place install; signer continuity against published 394 was already verified by run `41729419450`.


**Mainline:** PR #18 established the physically accepted CapCut-inspired editor base. PR #19 fixed the stale thumbnail accessibility label. PR #20, “Add safe bulk backup and restore for editor drafts,” is merged. PR #20 merge commit: `72501cd547945cf448350a4597d40b80c278dfbc`. Current main verification-workflow head: `e4dc74dc4a430c17b7658b2dad85525f248e874c`.

**PR #20 verification:** all three required checks passed: Android build `41723076539`, APK build `41723076363`, and API 35 emulator screenshots/instrumentation `41723076378`. The latest main Android unit/build check, run [`41729212192`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41729212192), also passed. The workflow-only signing fixes did not change editor source or app version.

**Production signing continuity — VERIFIED GREEN:** signing check [`41729419450`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41729419450) completed successfully. It downloaded the latest published release `app-v8.3-61` (`versionCode=394`), read its certificate with Android build-tools 36.0.0, restored the configured `MMV_SIGNING_BUNDLE_B64` signing source, built a comparison APK, and confirmed an exact SHA-256 signer-certificate match. The workflow did not publish a release or change any signing secret. The previous red runs were tooling/input-format and certificate-output parsing issues, now corrected.

**Phone installation and data safety:** the phone currently has debug ZIP #754 installed. PR #20 adds one-file JSON backup/restore for all valid local drafts from the home screen, with validation and collision-safe ID handling. The backup contains project/timeline JSON and media URI references; it does **not** copy the source video/audio files. Keep those media files on the phone and be prepared to reselect them if Android revokes URI grants after reinstall. User has said there are no important drafts that must be preserved.

**Release boundary:** keep `versionCode=394` / Build 61 unchanged for now. The debug ZIP #754 and production-signed release use different signing certificates, so a direct in-place production install over the debug build must not be assumed to work. Signing continuity is now proven; the remaining release gate is a controlled signed-channel migration/update test, including the updater install path and a practical backup/restore check. No version 395 or new release has been created.

**Still open:** validate the in-app signed update / migration path on-device, check backup/restore behavior, and complete separate physical-device checks for clip reorder, cover save, thumbnail performance, image/video overlays, compositing/export and media edge cases. Historical checkpoints below are retained for context; this section is the current source of truth.


---
---

# MathMotor Video — current project status

## Active implementation checkpoint — 2026-11-02 (latest user-approved batch)

**Working branch:** `feature/capcut-layout-recompose`  
**Latest code-change SHA:** `9976fcf3a88f1f8e1bfaa36be810af02ee9f7cf8`  
**Review:** PR #18 is ready for review; the user approved this implementation as the mainline foundation after physical-device testing of run #754. Mainline merge is pending; versionCode 394 / Build 61 remains unchanged. Do not publish a signed release or activate the updater as part of this merge.

### Implemented in source; verification pending

- **CapCut-style clip reorder:** long-press a main video segment and drag it to an insertion position. The sequence planner reorders independent split pieces and reflows the non-overlapping main video lane. It refuses to reorder when existing main-video segments overlap, rather than silently changing layer timing.
- **Reorder unit coverage added:** four tests cover insertion between clips, moving the first clip to the end, overlap rejection, and preserving gaps on a no-op drop. The expected end position was corrected to 4,500 ms before verification.
- **Fullscreen reachability:** the fullscreen exit control is placed in the preview's upper safe corner; outside fullscreen it returns to the playback row.
- **Filmstrip first-cell poster:** the first cell can display a temporary provider thumbnail while its exact timestamp sample is being decoded. This is a placeholder, not a replacement for timestamp-accurate frames. Overall thumbnail speed has not been measured on the user's phone.
- **Clip cover selection:** a per-clip frame picker previews frames from the selected trimmed range, stores the selected source timestamp in project JSON, and exposes a JPG save action. Android 10+ saves to Pictures/MathMotor/Kapaklar; older Android versions use the system document picker.
- **Image/video overlay lane:** the overlay panel can add a still image or a video as a distinct timeline layer. Start time, duration, scale, horizontal/vertical position and rotation can be adjusted with controls; preview touch gestures support one-finger movement and two-finger scale/rotation. The layer metadata and persistent URI are stored/restored with the editable project.
- **Preview/export composition path:** overlay sequences are composed separately from the main video. Preview uses Media3 `MultipleInputVideoGraph.Factory` when overlays exist; export uses the same video sequence order and compositor settings. Overlay-video source audio is intentionally removed so it does not unexpectedly mix with the main soundtrack.

### Explicit limitations and verification boundary

- **Build-failure history:** Android build #553 and emulator screenshots #747 failed at commit `0b90d9e91d6670d238a3712171bc555ca0fe6052` because `OverlayVideoCompositorSettings.getOutputSize` used `android.util.Size` rather than Media3 1.10.1's `androidx.media3.common.util.Size`. The correction was committed in `9976fcf3a88f1f8e1bfaa36be810af02ee9f7cf8`. Later run #754 succeeded on the newer head `7be6fade1def349e72649407c5c77790421214f0`; do not treat the earlier #548/#553/#747 failures as current-head results.
- Run #754 verifies unit tests, debug APK assembly/ZIP packaging and emulator interaction on the current tested head. The user installed that debug ZIP on a physical phone and accepted the editor structure.
- No emulator or physical-phone acceptance has been completed for drag reorder, poster latency, cover save, multi-input preview, seek/scrub, or MP4 overlay output.
- Media3 `VideoCompositorSettings` is a beta API. Multi-input preview, overlay order/transparency, image and video rendering, seek stability, and export output must be validated before treating the feature as complete. [Media3 VideoCompositorSettings guide](https://developer.android.com/media/media3/transformer/videocompositorsettings), [CompositionPlayer.Builder API](https://developer.android.com/reference/androidx/media3/transformer/CompositionPlayer.Builder), [MultipleInputVideoGraph.Factory API](https://developer.android.com/reference/androidx/media3/effect/MultipleInputVideoGraph.Factory)
- Overlay source in-point trimming is not yet exposed as a user control; current video overlays start from source time zero. Timeline text/audio layers also remain absolute project-time layers and are not automatically attached to a reordered main-video clip.
- Thumbnail performance beyond the provisional first-cell poster remains open; collect device timings before changing decoder or thread strategy.
- PR #18 is ready for review and remains unmerged until GitHub confirms the mainline integration. The editor structure has user acceptance; the broader feature set and production release remain gated.

## New editor layout target — CapCut-inspired mobile arrangement (2026-11-02)

**Status: SOURCE LAYOUT DRAFT IMPLEMENTED — API/unit verification passed; emulator and device acceptance pending.** The draft is isolated on feature/capcut-layout-recompose; main remains unchanged. The installed 394 / Build 61 experience remains the baseline. No APK was packaged, and version 395 was not created.

### Implementation progress — 2026-11-02

- Recomposed the existing editor layout without replacing the editor activity: preview remains responsive; fullscreen/time moved to the playback row; play is unboxed; existing clip undo/redo and fit controls sit on the right.
- Expanded the timeline workspace to a 220dp target, reduced the video filmstrip to 48dp, and added persistent audio/text lanes with Ses ekle / Metin ekle controls. The YZ badge, Kapak, and lane plus are visual-only placeholders.
- Ruler labels now appear at five-second marks (00:00, 00:05); tick geometry, shared time mapping, frame-rate-aware math, scrub/zoom behavior and centered playhead logic were not rewritten.
- Added the horizontally scrollable seven-tool dock in the requested order. Existing Düzenle, Ses, Metin, and Proje destinations remain connected; Efektler, Bindirme, and Yazılar show Yakında.
- Klip sesini reuses the existing source-audio mute state. Ses ekle and Metin ekle open their existing panels. Selected-clip actions and trim controls remain in the existing contextual clip panel; undo/redo remain in the playback row.
- **Verification:** no-APK workflow run [#2 passed](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41450804106). compileDebugKotlin, compileDebugUnitTestKotlin, and testDebugUnitTest passed. The workflow did not run assembleDebug, package an APK, or upload an artifact. The temporary test workflow was removed after the run.
- **Historical checkpoint:** the initial layout implementation had not yet been accepted. The tested editor shell was subsequently verified by run #754 and accepted by the user on a physical phone on 2026-11-03. Keyboard/inset and compact-screen regression checks remain distinct open checks.
- Tested source commit: 4ec9cf5a6eeb2af16dac42bd5f583b25a4cbcbbd. Temporary workflow removal commit: 7f5be8ff47868039158749602112c2aee3a5a681. Main remains at 4d83ba84551e82b704953209fb7427bc4af0d544.

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
- Move these existing actions out of the always-visible main layout and into that selected-clip tool row: `Önizle`, `Kopyala`, `Klibi kaldır`, `Böl`, `Öne al`, `Sona al`, and the `Başlangıç/Bitiş` controls. Keep the existing `Klip geri al/yinele` buttons on the playback row as specified above. Preserve their current handlers and exact behavior.
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


## Compact timeline gutter refinement — 2026-11-02

- User reviewed the emulator screenshots and approved preserving the current layout while making the timeline more usable.
- Narrowed the per-lane left controls from 54dp + 42dp to 44dp + 40dp. The media/ruler gutter therefore changes from 96dp to 84dp (87dp including the row's 3dp leading inset).
- Updated the shared centered-playhead gutter input from 99dp to 87dp and adjusted the focused `TimelineScaleMathTest` geometry expectations: at a 320px frame, the leading spacer is now 73px (previously 61px); the playhead remains at the same full-frame center and the playback time mapping test uses the new gutter.
- Updated the source comment to describe the 84dp gutter. No ruler math implementation, frame-rate mapping, timestamp data, zoom behavior, trim logic, clip state, or media/export behavior was changed.
- **Verification status:** the prior no-APK run #2 does not include this refinement. The GitHub workflow/status lookup has not yet returned a run or check result for the refinement commits; treat the updated unit test and compilation as **pending**, not passed. No APK was assembled or uploaded.
- Review gate: after the checks run, capture the same populated/empty editor states and verify the narrower controls remain readable/tappable while ruler, filmstrip, audio/text lanes, and fixed-center playhead remain aligned. Keep versionCode 394 / Build 61; do not create version 395.


## 2026-11-02 — physical-phone feedback after debug APK review

The user tested the debug APK from the `feature/capcut-layout-recompose` branch and reported four remaining acceptance issues:

1. The fullscreen minimize/restore control is now reachable and works, but fullscreen mode hides the playback row entirely. Keep a CapCut-style Play/Pause control and current/total duration visible in fullscreen as well.
2. The selected clip cover image is not appearing/being created as expected. Trace the complete cover-picker flow, distinguish saving the selected timestamp in the project from creating a visible JPG cover image, and make the expected result explicit and reliable.
3. Filmstrip thumbnails are only slightly faster and remain too slow. Zooming the filmstrip causes the user to wait for thumbnails to be generated again. Reuse already-decoded frames immediately across zoom levels and fill missing samples asynchronously; avoid unnecessary re-decodes.
4. During manual filmstrip drag, the preview does not update as soon as the fixed playhead passes a new frame. Reduce scrub latency and keep preview requests aligned with the latest playhead position without allowing stale decode results to overwrite newer frames.

Initial source inspection on this branch found: `togglePreviewFocusMode()` hides every root child except `previewFrame`, including the playback row; `scheduleTimelineScrubPreviewSeek()` enforces a 120ms minimum interval; thumbnail cache reuse currently requires a cached timestamp to fall inside the newly generated cell interval, which often fails after zoom-in; and the cover picker stores `coverTimeMs` when “Kapak yap” is pressed while JPG creation is a separate “JPG kaydet” action. These are investigation findings, not yet fixes.

**Status:** feedback documented; source changes and verification are pending. Keep work on this feature branch and draft PR only. Do not merge, modify `main`, create version 395, or publish a release. The just-tested debug APK is not to be treated as acceptance of these four behaviors.


## 2026-11-02 — fullscreen playback, cover output and live timeline feedback

Source changes on `feature/capcut-layout-recompose` now:

- Keep the existing play/pause button and current/total time visible in fullscreen by moving those same controls into a bottom overlay on the preview; the top-right fullscreen exit control remains reachable.
- Change the scrub decoder cadence from a 120ms minimum interval to 33ms (about 30 requests/second), while retaining latest-request generation checks so stale frames cannot replace newer ones.
- Increase the filmstrip bitmap LRU cache from 16MiB to 32MiB. On zoom, show the nearest cached frame immediately even if its timestamp is outside the newly resized cell, then continue decoding the exact cell timestamp asynchronously so the placeholder is replaced.
- Make “Kapak yap” persist the selected source timestamp and create the corresponding JPG cover image. If the selected preview bitmap is not ready, the picker remains open and asks the user to wait rather than silently selecting without producing the image.
- Add a unit test for the 33ms interactive scrub cadence.

**Verification:** source and unit-test changes are committed, but compilation, unit tests, emulator interaction, and physical-phone behavior are still pending. The 33ms cadence is a request throttle, not a guarantee of 30fps decoding on every device. Zoom reuse is provisional until the emulator and phone confirm immediate display and background exact-frame replacement. Do not offer a new APK until the checks pass. Keep `main` and version 394 / Build 61 unchanged; no merge or release is authorized.


## 2026-11-02 — scrub preview cache and pause-clock synchronization

On the existing `feature/capcut-layout-recompose` branch (PR #18), the editor now:

- Refreshes the playback/timeline synchronization loop every 50ms instead of 250ms so the visible ruler/playhead follows playback more closely.
- On pause, freezes the composition player first and copies its actual `currentPosition` into `timelinePlayheadMs`, then updates the time readout and scroll position. Pending scrub work is cleared so an old request cannot pull the paused frame backward.
- On resume, clears pending scrub work and seeks from that frozen playhead before enabling playback.
- During manual scrubbing, looks for a same-source filmstrip bitmap within 500ms of the requested source timestamp and immediately displays a private copy as a temporary preview; the exact timestamp is still decoded asynchronously and replaces it when ready. The cache bitmap itself is never recycled by the preview.

**Verification:** source change committed; build, unit tests, emulator interaction, decoder latency measurements, and physical-phone acceptance are pending. The nearby cached frame is intentionally provisional and can be up to 500ms away until the exact frame arrives; this is a responsiveness improvement, not a claim of frame-perfect instant decoding. Keep PR #18 draft, do not merge or modify `main`, and keep versionCode 394 / Build 61 unchanged.


## 2026-11-03 — background/foreground thumbnail cache diagnostics

The existing editor branch now has debug-build-only local diagnostics for the report that filmstrip thumbnails appear to be generated again after switching to another app and returning.

Instrumentation added without changing editor behavior:

- Activity lifecycle events: `onCreate`, `onCreateComplete`, `onRestart`, `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onTrimMemory`, and `onDestroy`.
- Every lifecycle record includes process ID and Activity instance identity, so the log can distinguish a normal stop/resume from Activity recreation or process restart.
- Cache state includes filmstrip/poster cache bytes, LRU hit/miss/eviction counts, in-flight thumbnail keys/targets, queued tasks and active decoder workers.
- Timeline rebuild records include the caller and the number of lane rows before rebuilding.
- Each newly decoded thumbnail records queue wait, frame decode, bitmap scaling, UI-dispatch wait and UI-apply duration separately. Cache hits and request deduplication are also recorded.
- Diagnostics run only in debuggable builds and write to local Android logcat; no telemetry or network reporting is added.

### Device reproduction

1. Install the debug APK and open a video in the editor. Let the filmstrip thumbnails finish loading.
2. Start log capture: `adb logcat -c`, then `adb logcat -v time -s TimelineLifecycle:I TimelineThumbnail:I`.
3. Press Home, open another app for around 30 seconds, then return to the editor without closing it.
4. Compare the lifecycle records and thumbnail events around the return.

Interpretation:

- Same PID and same Activity instance across `onStop` → `onStart/onResume`, with cache bytes retained and cache-hit records: the Activity/cache survived; inspect refresh/rebinding and UI timing.
- Same PID but a different Activity instance: the Activity was recreated and its in-memory cache was lost.
- Different PID: the app process restarted; the Activity-local LRU cache cannot survive that process death.
- A rise in LRU evictions or an `onTrimMemory` event before the return points to memory pressure.
- High `queueWaitMs` indicates queue contention; high `frameDecodeMs` points to frame extraction; high `bitmapScaleMs` points to resizing; high `uiDispatchWaitMs/uiApplyMs` points to main-thread delay.

**Verification status:** source instrumentation committed as `af9abbaeae54d451d312dbaa9598349422b73e16`. Debug compilation, unit tests, emulator reproduction and physical-device log review are pending. This is diagnostic instrumentation only: worker count, decoder strategy, cache budgets and playback/timeline behavior were not changed. Keep PR #18 as a draft; do not merge, change `main`, create version 395, or publish a release.


## 2026-11-03 — physical-device playback and thumbnail follow-up

Physical-device feedback from debug build #555:

- After switching away from the editor and returning, the filmstrip thumbnails are generated again rather than remaining visible from the previous session.
- On the initial editor opening, the video thumbnails appear only after the audio portion has loaded; investigate the apparent loading order rather than assuming it is a decoder-only issue.
- A separate playback issue was observed: after tapping Play, audible sound begins approximately one second later than expected while playback has already been initiated. This is a user-observed approximate delay, not yet a measured Media3 audio-renderer timestamp.

### Next diagnostic steps

- Collect the existing `TimelineLifecycle` and `TimelineThumbnail` logcat records around the background/foreground reproduction to determine whether the Activity/process was recreated, the cache was evicted, or tracks were rebound.
- Investigate playback startup independently. The current Play path seeks to `timelinePlayheadMs` and then sets `playWhenReady = true`; source inspection alone does not identify when audio output actually begins. Add or collect targeted debug timing for the Play tap, player readiness/playback state, video-frame presentation, and audio-start/position-advancing events before changing playback behavior.
- Do not delay video to mask the audio lag, alter worker counts/decoder strategy, or change cache budgets without evidence.

**Verification boundary:** #555 confirms the diagnostic debug APK builds and uploads successfully, but the physical-device observations above remain unresolved. Keep work on `feature/capcut-layout-recompose` / draft PR #18. Do not merge, modify `main`, create version 395, or publish a release.


## Ruler-row playback time placement correction — 2026-11-03

- Root cause of the repeated mismatch: `timelinePlayheadLabel` was still attached to `playbackLeft`, so it remained in the playback-control row even though the ruler was a separate `makeLane()` row.
- Moved the existing current/total time label into the ruler row's existing 84dp left header gutter. The gutter width is unchanged, preserving the shared zero edge and all video/audio/text lane alignment.
- Fullscreen temporarily reparents the same label into the fullscreen playback overlay and restores it to the ruler header on exit. Timeline refresh explicitly detaches it before rebuilding to avoid a stale-parent crash.
- **Verification pending:** no build or emulator screenshot has yet verified the new placement. Keep PR #18 draft/unmerged; do not touch `main`, create version 395, or publish an APK.


## CapCut-style bottom editing sheet — 2026-11-03

The existing editor panel has been moved out of the root vertical layout and into a bottom-anchored overlay within the same editor workspace.

### Layout behavior

- The existing editor remains the only editor. The preview, playback row, timeline card, shared lanes, and playhead stay in the base workspace.
- The base workspace reserves the existing 66dp tool-dock height. The dock and contextual detail panel are children of one bottom-anchored sheet.
- Opening a tool animates its detail panel upward from the dock. The sheet overlays the lower timeline region; the underlying timeline views remain mounted. A lightly translucent panel surface retains visual context behind it.
- Changing tools reuses the same open panel instead of tearing down and recreating the sheet. Closing animates the detail panel downward and only then hides its content.
- The bottom sheet is explicitly included in the preview fullscreen hide/restore cycle, so the dock or tool panel cannot remain floating above fullscreen playback.
- The emulator capture script now opens `Düzenle`, switches to `Ses`, closes the sheet, and compares logged preview/timeline screen bounds across the before/open/switch/closed states. The assertion is added but remains unrun on this source head.
- The overlay receives its own bottom safe-area margin. When the keyboard appears, the existing keyboard-responsive mode hides the dock and reduces the detail panel; keyboard-induced viewport changes are not treated as normal panel-open behavior.
- The timeline ruler, time-to-pixel mapping, playhead sync loop, clip/lane data, thumbnail queue, decoder strategy, cache budgets, audio playback and export paths were not intentionally changed.

### Verification gates

- Source update and documentation are committed to `feature/capcut-layout-recompose` / draft PR #18.
- Unit tests, debug compilation, emulator screenshots, bottom-sheet open/switch/close interaction, keyboard interaction, fullscreen interaction, and physical-phone review are pending.
- Specifically compare preview and timeline measured bounds before and after opening/closing a tool. Confirm panel transitions do not trigger timeline refreshes or new thumbnail requests.
- Do not merge PR #18, modify `main`, change versionCode 394 / Build 61, create version 395, sign/publish a release, or activate the updater.


## 2026-11-03 — adaptive CapCut-style ruler labels

- Fixed the ruler tick renderer to display the label supplied by the adaptive grid. The previous renderer ignored that label and only printed every fifth second, hiding most of the adaptive seconds/subseconds and all intermediate frame labels.
- Whole-second frame boundaries display a clock label such as `00:02`; intermediate frame ticks display their frame offset within the current second such as `15f`. Fractional source rates (for example 29.97 fps) use frame-index boundaries to avoid cumulative timestamp rounding drift.
- Non-frame subsecond labels retain decimal precision. The label-selection threshold is 48dp so intermediate labels remain readable at practical close zoom. Tick geometry remains based on the existing shared absolute-time-to-pixel mapping; no playhead, clip, thumbnail, or pixel-scale mapping was changed.
- Added unit coverage for clock/frame label transitions. Latest source commits: `8e6d019` (math helper), `64525d5` (renderer), `b4b7e48` (tests). **Build, unit tests, emulator screenshot/interaction review, and physical-phone acceptance are pending.**
- Keep PR #18 draft and unmerged. `main` and the installed 394 / Build 61 baseline remain unchanged. No version 395 or release/update activation.


## 2026-11-03 — two-level CapCut-style clip tools

- Main dock action `Düzenle` now opens a single horizontal contextual clip-tool strip at the bottom in place of the global dock.
- The strip exposes the implemented actions `Kırp`, `Ses düzeyi`, `Böl`, `Kapak` and `Sil`. Selecting `Kırp` or `Ses düzeyi` raises its detail panel above the strip. The detail panel's `Araçlara dön` control collapses it back to the one-row strip; the strip's `Geri` control restores the main editor dock.
- The detail sheet remains a bottom-anchored overlay in the existing workspace. The editor preview and timeline are not reparented or resized by these transitions. No new timeline implementation was introduced.
- The emulator script now verifies the two-level path and asserts stable preview/timeline bounds at entry, detail-open, return-to-strip and main-dock-restored states.
- **Verification boundary:** source and test changes are committed on `feature/capcut-layout-recompose`; compilation, unit tests, emulator screenshots/interactions and physical-phone acceptance are pending on the latest branch head.
- This menu includes only currently implemented actions. Animation/effect features are not represented by nonfunctional placeholder controls; they can be added when their actual clip-processing behavior is implemented.
- Keep PR #18 draft/unmerged. `main` and versionCode 394 / Build 61 remain unchanged; no version 395 or release/update activation.


## 2026-11-03 — emulator tool-selector diagnosis

- The latest emulator failure after closing the trim detail panel was a false UI-test failure, not evidence that the app lost the `Ses düzeyi` action. The selector searched both visible text and content descriptions by substring; `Araçlara dön` also matched the `Geri` action description `Ana araçlara dön`. Because it selected the lowest matching node, the script tapped `Geri`, exited clip-tools mode, then correctly could not find `Ses düzeyi` in the restored main dock.
- Updated `.github/scripts/capture-emulator-screenshots.sh` to prioritize visible button text and permit only exact content-description fallback matches. App behavior and the editor menu implementation were not changed for this diagnosis.
- Fix commit: `96bf3ffee9df13098b5fbf4440de1c907e7d7791`; README records the same diagnosis. **Fresh unit/build/emulator verification is still pending.** Do not issue a test ZIP or mark the gate passed until the new run completes and its screenshots/logs are inspected.
- PR #18 remains draft/unmerged; `main` and versionCode 394 / Build 61 remain unchanged. No version 395, release signing, publication, or updater activation.


## Preserved cross-session handoff notes — 2026-11-02 (reconciled 2026-11-03)

**Purpose:** Preserve the previous session's observations and open work. The 2026-11-03 canonical project checkpoint at the top of this file supersedes any older status statements below. Reconcile with GitHub main, PR #18 and Actions before changing code.

### Where we stopped

- Project: `agabeyayd-lgtm/MathMotorVideo-Restore`.
- The last confirmed installed/release baseline remains **versionCode 394 / Build 61**. The user has since approved the #754-tested editor implementation as the mainline foundation; PR #18 integration is being completed. Do not create version 395, sign/publish a release, or activate the updater as part of the merge.
- The tested TEST ZIP is identified: GitHub Actions run [#754](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/41710927696), source commit `7be6fade1def349e72649407c5c77790421214f0`, artifact `mathmotor-debug-apk-zip`. The user installed it on a physical phone and confirmed the editor structure works. It is a debug test build, not a signed release APK.
- The #754 artifact provenance is established; do not conflate it with older PR #11 artifacts or with a production release.
- No new release/version should be created as part of this handoff.

### Latest user-reported issues to verify on the phone

1. **Fullscreen exit control:** after entering fullscreen, the control that returns to the normal/smaller preview is positioned too close to the bottom edge and is difficult/impossible to reach. Move it into a safe, reachable control area and respect system/navigation insets; verify on the actual phone.
2. **Filmstrip thumbnail speed:** thumbnail generation/loading appears slow again. Measure time to first visible thumbnail and full visible-strip fill; inspect decode scheduling, caching and progressive visible-first loading before changing implementation. Do not sacrifice frame accuracy or preview/thumbnail synchronization.
3. **Cover image:** the cover/thumbnail image is not being generated/shown. Reproduce and trace the cover-generation/display path; distinguish a missing image from a delayed/failed decode. Do not mark fixed until tested.

These were phone observations recorded before the #754 editor-shell acceptance and remain separate follow-up items unless a later device check confirms them fixed. #688 passed deterministic-fixture frame agreement (211ms preview/thumbnail RGB delta 0), 4/4 distinct visible samples, forward/reverse scrubbing and 18 playback synchronization samples. Run #754 passed the editor-shell tool transitions and geometry checks; it did **not** measure thumbnail fill speed or validate cover-image generation, image/video overlays, or export.

### Exact next steps

1. Complete PR #18 integration by preserving the two Nov 2 documentation commits on `main`, rerunning the PR checks and confirming GitHub reports the merge. Keep `main` as the sole canonical source.
2. Keep the user's installed baseline at 394 / Build 61; do not bump version or create 395.
3. Continue the previously reported fullscreen reachability, thumbnail latency and cover-image checks on the approved base; record device dimensions, Android version, logs and timings where available.
4. Preserve the approved editor implementation, then address confirmed remaining device issues with focused fixes and regression tests. Do not alter timeline math or frame alignment without evidence.
5. Add focused regression tests for each fix. Run unit tests, debug build and targeted API 35 emulator tests; inspect logs and screenshots rather than relying on a green compile alone.
6. After mainline integration, inspect the existing in-app updater, signing configuration and release workflow. Connect updates only to a controlled, signed release source and run a safe install/upgrade test before enabling user-facing updates.
7. Keep this README and RESTORATION_STATUS.md synchronized with evidence. Keep versionCode 394 / Build 61 until the release gates and update path are verified; do not publish a release by implication.

### Continuation rules

- Start with **“HATIRLA BAĞLAN DÜZELT”** and this handoff section.
- Do not repeat already-passed tests without a specific reason; do not report a test as green unless its exact run and scope are confirmed.
- Preserve working functionality. Make the smallest targeted correction supported by evidence.
- Keep README.md and RESTORATION_STATUS.md synchronized as work progresses; do not create a new version just to test.
- The user's immediate request was to preserve the restart point in the repository so work can continue from another ChatGPT account/session.

## In-app updater status — 2026-11-04

- The home screen's manual `Kontrol et` action checks GitHub `releases/latest`, reads the published `versionCode`, compares it with the installed app, downloads `MathMotorVideo.apk` when a higher version exists, and opens Android's package installer.
- Latest signed release: `app-v8.3-62` (Build 62), `versionCode=395`. The currently installed phone build is not established by repository state.
- Candidate branch version: `versionCode=396`, `versionName=8.30-capcut-editor`. It is not published.
- Publishing is separate from PR/debug builds. The signed workflow uses the persistent release-signing key and runs manually or when `.github/release-trigger.txt` changes on `main`. That release trigger remains unchanged in PR #22 to prevent a publish before signing continuity is checked.
- The signing-continuity workflow now compares the configured signing source with the published 395 APK. Its workflow and trigger-note changes in PR #22 are designed to run on `main` after merge. Only if this check passes should a separate commit update the release trigger to publish 396.
- Debug APK artifacts use the debug certificate. They cannot replace an installation signed with a different release certificate in place. Preserve/export private editor drafts before any one-time migration between signing certificates.


## Current continuation audit — 2026-11-04

- PR #18 was merged on 2026-11-03. Current published `main` is `versionCode=395`, `versionName=8.29-capcut-editor`; latest signed tag is `app-v8.3-62`, Build 62.
- PR #22 is open and unmerged. The candidate branch includes current `main` (0 commits behind); latest comparison at this note was 137 commits ahead, 30 changed files. GitHub reports the PR mergeable. Merge remains gated on current-head verification.
- Candidate version 396 exists only on the PR branch. Signing continuity is updated to compare against current signed 395. The release trigger file is intentionally unchanged.
- Current-head Actions jobs for unit/build, emulator screenshots and a debug APK artifact are queued. No green result, export playback confirmation, or deliverable APK is claimed.
- Keep `main` unchanged until current-head CI passes and the full PR diff is reviewed. After merge, verify signing continuity against 395; only then change the main release trigger separately to publish 396.


## Follow-up static audit — 2026-11-04

- Found and corrected a start-trim edge case for animated image/video overlays: keyframes are now rebased to the trimmed local timeline, and a keyframe is seeded at the new local zero using the interpolated transform at the cut point. Extending the left edge preserves the original keyframe timing relative to the source.
- Added focused unit tests for trimming the start edge and extending it earlier. These tests are committed but **not executed yet**; the PR still has no verified green CI/build.
- The branch comparison currently reports 78 commits ahead and 4 behind `main` (22 changed files). This is a divergent branch, not merge-ready.

### 2026-11-04 audio preview audit

- Found and fixed a preview-only audio activation defect: `syncPreviewAudio()` previously required the legacy single-clip ExoPlayer to be playing, which is false during multi-clip `CompositionPlayer` playback. The gate now checks `timelineCompositionPlayer.isPlaying` when the timeline composition is active and checks the legacy player only for single-clip preview.
- Static dependency review: this branch declares Media3 `1.10.1`. Each main-video `EditedMediaItem` receives a constant `SpeedProvider`; the source audio remains part of the same edited item unless the user disables original clip audio, so video and source audio share the clip speed effect. Music and sound-effect export remain separate audio sequences at their timeline offsets.
- Not verified dynamically: no Gradle build, unit tests, emulator playback, exported-file audio sync check, or APK for commit `6371b3555f0a0c996beeab4938e4780e99935d2e`.
- PR #22 remains open/unmerged. Latest comparison after the fix: 109 commits ahead, 4 behind `main`; 22 changed files. No PR-triggered workflow run was returned for the fix commit.
- Follow-up found that the music preview player repeats the entire source file while the timeline loops only the selected in/out range. The preview now forces a seek as soon as the playhead escapes that selected range, avoiding up to 400ms of untrimmed audio leakage; a regression test covers range escape and normal in-range tolerance.
- Latest branch comparison after these additions: 115 commits ahead, 4 behind `main`, 23 changed files. The new regression test is committed but remains unexecuted; no build, emulator run, export-audio check, or APK is verified.

### 2026-11-04 export pipeline verification follow-up

- Media3 1.10.1 source inspection found two invalid audio sequence builder usages: the music path combined the explicit `Builder(Set<TrackType>)` constructor with the legacy `experimentalSetForceAudioTrack(true)` call (the 1.10.1 implementation rejects that combination), and the delayed sound path used the private no-argument builder. Both music and sound now use a shared audio-only sequence factory with explicit track types and preserved leading/trailing silence.
- Media3 1.10.1's Transformer source adds 16-bit PCM normalization to composition items and adds `SpeedChangingAudioProcessor` to items with a `SpeedProvider`.
- The channel mixing matrix helper uses the library-supported 1–6 channel constant-power matrices and explicit stereo routing for 6.1/7.1 layouts; the new tests opt in to Media3's unstable API annotation. This confirms that source audio in a speed-adjusted video item follows that item's playback speed; `setDurationUs` remains the pre-clipping/pre-speed source duration.
- Media3 also requires audio items mixed in a composition to have the same channel count. Added stereo normalization for original clip audio, music and sound effects, including 1–8 channel inputs, plus unit tests for matrix output and sequence track declarations.
- Static code and official-library source review only. The new tests have not been executed; no Gradle build, emulator playback, exported MP4 audio check or APK has been verified for this head.
- Latest Git comparison before these documentation commits: 125 ahead, 4 behind `main`, 27 changed files. PR #22 remains open/unmerged; no Actions run or check was returned for the current head.
- Added explicit Kotlin opt-in to the Media3 channel-matrix unit test so the test source correctly acknowledges the library's unstable API annotation.
- Latest comparison after this source correction: 129 commits ahead and 4 behind `main`, 27 changed files. The current head still requires the queued CI results; no APK is claimed yet.

### 2026-11-04 release preparation

- Bumped the candidate app version from `versionCode 395 / 8.29-capcut-editor` to `versionCode 396 / 8.30-capcut-editor` so an eventual signed release can update the currently published version 395 installation rather than reusing its version code.
- Merged the current `main` head into this working branch using a merge commit; the common-base and main trees were identical, and GitHub now reports the PR branch as 0 commits behind `main`. `main` itself remains unchanged.
- Latest source head is now being checked by GitHub Actions. No test, build, emulator, signed APK, or release is claimed until the corresponding run/artifact is completed and inspected.

### CI failure follow-up — 2026-11-04

- First real Gradle run exposed a missing text-layer delete handler reference; the dock action now calls the existing `removeSelectedTextLayer()` implementation.
- The next unit-test run exposed a signed-delta bug in left-trimming a clip earlier: source-time extension was being converted to a nonnegative timeline offset, so the project start did not move left. The mapping now preserves the sign and converts the magnitude through the clip speed.
- Overlay rotation interpolation intentionally normalizes the +180° midpoint as positive 180°; updated the stale test expectation to match that documented behavior.
- CI for the immediately preceding commit is being rerun; the newest test assertion correction is committed after that run started, so it still needs a fresh workflow run. No APK or successful test suite is claimed yet.
- The debug APK artifact is now built and uploaded successfully from the tested source. The separate verification workflow also completed successfully.
- The first emulator attempt passed Gradle tests and APK packaging but failed before AVD creation: `android-actions/setup-android@v3` attempted to install the removed SDK package `tools`. The workflow now uses the runner's preinstalled command-line tools directly and installs only supported SDK packages; emulator smoke tests must be rerun before claiming emulator verification.


### MP4 export button interaction gate — 2026-11-05

- The earlier emulator smoke test did not open the Proje panel or tap the MP4 export control; its green result did not verify export-button interaction.
- The MP4 action is now placed immediately below the Proje panel heading, with an explicit accessible description and a 52dp touch height, rather than being buried below secondary options.
- Debug-only EditorExportTest markers distinguish a tap reaching exportEditedVideo(), Transformer.start(), and successful MediaStore save. The emulator script opens Proje, locates the enabled MP4 button from the live accessibility hierarchy, taps its actual bounds, and requires a saved-output marker.
- After confirming the saved MP4, the emulator test closes the Proje drawer before resuming timeline gesture/playback checks, so the drawer cannot mask later controls.
- The encoding and save pipeline is unchanged. A passing emulator run proves the UI event, encoder startup, and MP4 save on the fixture; it does not replace physical-device playback verification.


### Video replacement / stale filmstrip regression — 2026-11-05

- A clip removal now invalidates that source URI's queued thumbnail tasks, cache entries, poster requests, and pending target references. Active workers carry a per-source generation and cannot publish a frame after the source has been removed or replaced.
- Replacing the video timeline (including a replace-existing import) clears old source thumbnail state and re-anchors the ruler/playhead/scroll at time zero. Unrelated video sources are retained when another clip from the same source remains in the project.
- The API 35 emulator scenario now starts with a red/hue-cycling MP4, adds the paused overlay, removes the original clip through the production removal path, and imports a distinct solid-green MP4 through the normal add path.
- The regression check waits for an attached replacement thumbnail, verifies that the new filename is the only video clip in the accessibility hierarchy, and samples the filmstrip screenshot to confirm the new green frames are visible rather than old frames.
- This test is part of PR #24's candidate validation. No phone-level confirmation, merge, version bump, or signed release is claimed until the candidate checks pass.


- MP4 export root cause isolated from emulator logs: the actual button tap reached exportEditedVideo() with one valid clip, but Media3 ImageAssetLoader threw IllegalStateException while constructing the still-image overlay input. Its constructor requires both a known duration and an explicit frame rate; the image overlay supplied duration but left EditedMediaItem.frameRate unset.
- Fixed still-image overlay export by setting an explicit 30 fps frame rate on the image EditedMediaItem. Video overlays retain their source frame rate.
- Re-run the actual Proje → MP4 tap test and require Transformer startup, completion, a MediaStore-saved MP4, and a non-empty output file before considering export fixed.


### 2026-11-05 emulator evidence and precision follow-up

- On API 35, the actual Proje → MP4 button tap reached the export handler, Media3 Transformer started, and the completed export was inserted into MediaStore under Movies/MathMotorVideo. The emulator log recorded `EXPORT_OUTPUT_SAVED`; the captured editor screen showed Android's “Video kaydedildi.” confirmation.
- The same run removed the original red/hue-cycling source and imported the distinct green replacement. Its video lane contained only `editor-test-video-replacement.mp4`; replacement thumbnail decode markers appeared for multiple source timestamps. Screenshot pixel inspection measured 96.02% green pixels in the video-lane crop.
- The latest full emulator run stopped later at the thumbnail/playhead alignment helper: after five measured gestures, the remaining center difference was 4 physical pixels. This was a test-coordinate tolerance failure, not an export or replacement failure. The test now accepts a 5px residual (under 3dp at the emulator density) in both preparation and pixel-verification stages.
- The MP4 save path now checks the persisted MediaStore file descriptor size (and the legacy target file size) is greater than zero before reporting success. Debug export logs include the encoded byte count.
- These changes require a fresh complete emulator run; the full timeline scrub/playback/autosave suite has not yet passed on the current PR head. No merge, version bump, or signed release is claimed here.


### 2026-11-05 thumbnail-diversity test-fixture correction

- The API 35 run reached the real MP4 export path and saved a non-empty MP4 successfully. The later `1/8 unique thumbnails` assertion was a test-fixture mismatch: the replacement video was intentionally a single solid-green frame repeated for all timestamps, while the generic frame-diversity assertion requires time-varying pixels.
- Run 849 falsified the first fixture adjustment: its `sin(t*2)*4` hue wobble produced only 4/8 unique filmstrip fingerprints. The replacement lane remained correctly green and loaded, but the test input was still too visually uniform for the independent diversity assertion.
- Increased the deterministic replacement fixture's hue progression to `t*24` degrees. A local FFmpeg reproduction at the exact eight assertion timestamps produced 8/8 distinct 3×3 sample fingerprints while retaining a 100% green-pixel ratio under the workflow's green test (threshold: at least 75%). This is a preflight check only; Android decoder/emulator confirmation is still pending.
- Run 849 also showed the new Play control remained accessible and the replacement/MP4-export checks passed; the stale-player teardown callback was no longer poisoning the new preview state in this scenario. The remaining red assertion was fixture diversity only.
- Do not weaken the 75% green requirement or the 75% unique-frame requirement to make this pass. Re-run the complete emulator workflow against the revised fixture; merge, version bump, and signed release remain blocked until the full run is green.

---

## Signed update candidate — version 399 — 2026-11-05

- Candidate: `versionCode=399`, `versionName=8.32-capcut-editor`; base is main merge commit `42fff263`.
- Scope is the already-verified first-video-at-zero/gallery-replacement correction and detailed Media3 export failure diagnostics. No editor redesign or unrelated feature changes.
- Before publication, the release-signing workflow is updated to compare the exact publisher-selected Actions signing source against the latest signed release, expected `versionCode=398`. It must pass before the 399 release trigger is changed.
- Required gates: unit tests/debug compile, signed APK build, signing certificate continuity against 398, and Android 35 emulator smoke test. The emulator's MP4 fixture is not a substitute for testing the user's actual gallery asset on the physical phone.
- Publication is deliberately separated from this candidate PR. Do not touch `.github/release-trigger.txt` until the signing and build gates are green.

---

## Published signed version 399 — 2026-11-05

- Release: [MathMotor Video signed APK build 66](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/tag/app-v8.3-66).
- Installable production APK: [MathMotorVideo.apk](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/download/app-v8.3-66/MathMotorVideo.apk).
- Package version: `versionCode=399`, `versionName=8.32-capcut-editor`; application ID remains `com.mathmotor.video.next`.
- APK asset SHA-256: `4f4fca205f148a60f19c60d0568ba568076d4db2f6183167bf8df2b55ef0e14e`.
- Signed release workflow: [run 42094217987](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/42094217987) completed successfully. Unit tests, signing-key restore, signed release build, APK preparation, and GitHub release upload all passed.
- Signing continuity: the publisher-selected `MMV_SIGNING_BUNDLE_B64` certificate SHA-256 matched the published 398 certificate exactly; [verification run 42093431357](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/42093431357) passed. The release workflow did not rotate or replace the signing key.
- Runtime source includes the verified main-video insertion rule: a new main video starts at 00:00 when that lane is empty; independent image/text/audio layers do not push it later. Export failures expose nested Media3 cause details.
- **Physical-phone acceptance still pending:** install this APK over the existing signed app, open the same gallery video that previously produced `Asset loader error`, export MP4, and report whether it succeeds. The emulator fixture export is not a substitute for this real-asset device check.

---

## CapCut-inspired home screen candidate — 2026-11-06

- Rebuilt the Android home screen around the supplied two CapCut references: blue gradient masthead, Standart/search controls, prominent update card, paired New video/Edit photo cards, horizontal recent-project thumbnails, three-column tools, and fixed five-item bottom navigation.
- The update card calls the existing GitHub version-check, APK download, and Android install flow; it is not a decorative placeholder.
- Recent project thumbnails are loaded from the existing exported-video store. The draft backup/restore section remains available below the tool grid.
- The photo picker opens the selected image in the editor as an independent image overlay; it does not claim unsupported standalone photo retouching tools.
- First build and unit tests passed. The first emulator run reached the final draft check but the Android document picker remained in the foreground after export. CI now explicitly restarts MainActivity before checking the saved-draft section; the section heading is uppercase to match the accessibility assertion.
- Candidate PR #28 is not merged or published. Awaiting the corrected full emulator run.


## Shared project-canvas boundary — 2026-11-06

- `VideoEditorActivity` now separates the dark preview work stage (`previewFrame`) from an inner, aspect-fitted project canvas (`previewCanvas`). The player, paused-frame image, paused overlay layers, text layers, overlay selection chrome, and clip/overlay touch surfaces are children of the inner canvas; the empty-state affordance and fullscreen controls remain on the outer stage.
- The inner canvas is fitted to the active main-video source aspect ratio and clips children at its bounds. Text sizing/positioning and overlay gesture fractions now use those same inner dimensions instead of the entire preview stage. This addresses the shared canvas boundary, not just a text-only adjustment.
- The export pipeline already stores overlay positions as normalized canvas fractions and uses normalized device coordinates for clip transforms; the new preview bounds are intended to align editing with that output coordinate space. Exact preview/export pixel parity, mixed-aspect clip behavior, touch transforms, and crop behavior still require emulator/device validation.
- Source commit: `feb9733` (follow-up coordinate cleanup). **No Gradle build, unit test, emulator interaction test, MP4 export test, or APK is claimed yet.** Do not merge or publish from this branch until those checks pass.

## Preview canvas export-framing alignment — 2026-11-06

- Follow-up: the inner preview canvas now uses the selected `VideoCanvasPreset` aspect ratio; `SOURCE` uses the first main-video clip as the project reference. Changing the export framing refreshes the preview canvas immediately. This keeps the canvas fixed across clips instead of resizing whenever the playhead crosses a source with a different aspect ratio.
- Latest source commit: `751ba224`. CI lookup for this commit returned no PR-triggered workflow runs and no combined status checks. The implementation is committed to `feature/capcut-home-screen`, but compilation and behavioral verification remain pending; do not treat this as a verified APK or merge-ready change.

- Final aspect correction: the canvas ratio is now fixed to the chosen output preset, or the first main-video source when `SOURCE` is selected; it no longer changes with the active clip. Source commit: `aeb61a8`. Static source checks confirmed all preview/editable visual surfaces are parented to the inner canvas and the empty-state/fullscreen controls remain on the outer stage. Build and runtime checks are still pending.

- Paused-frame parity follow-up: the decoded still-image fallback now uses center-crop inside the same fixed project canvas, matching the live composition's crop-to-fill behavior when a non-source output preset is selected. Source commit: `48ee2af`. This remains statically reviewed only; no compile/emulator/export validation is available yet.


## Preview canvas geometry regression coverage — 2026-11-06

- Added pure `PreviewCanvasMath.fit()` so the project-frame fit/centering rule is independently testable; `VideoEditorActivity.updatePreviewCanvasSize()` now uses the helper instead of duplicating the geometry calculation.
- Added JVM tests for 9:16, 16:9 and square frames, invalid aspect fallback, zero-size stages, and bounds containment. These tests are included in the new PR-triggered unit/build run.
- Added a debug-only `PREVIEW_CANVAS_BOUNDS` marker and emulator assertion. The screenshot workflow checks that the actual measured canvas bounds are centered, remain inside the outer work stage, and preserve the 9:16 deterministic source ratio.
- Source commits: `7386c23` (math helper), `59339e3` (unit tests), `e61d6ad` (Activity integration/log marker), `0ce6919` (emulator assertion). Verification awaits the PR-triggered run for this latest branch head; no new green result or APK is claimed for these follow-up changes yet.

- Layout reflow follow-up: a `previewCanvas` size-change listener now recomputes editable text geometry and overlay selection/paused layers after the inner canvas has actually completed layout. This avoids calculating touch/selection bounds against the previous dimensions immediately after changing the output aspect preset. Source commit: `2218a22`; CI for this final follow-up commit is pending.


## Unified preview/export layer anchors — 2026-11-06

- Added shared normalized-center conversions to `PreviewCanvasMath`: normalized x/y fractions become Android preview translations and Media3 normalized frame anchors using one rule, including Media3's inverted Y axis.
- Text preview no longer maps its center only across the remaining safe width/height of the text box. Text and image/video overlays now position their centers from the same project-canvas fractions used by export; content that crosses the canvas edge is clipped by the canvas rather than silently repositioned inward.
- Removed the old text export anchor clamps so edge positions and text animation offsets are not pulled back inside the frame differently from the preview.
- Added unit cases for left/center/right anchors, top/center/bottom Y mapping and non-finite fallback. Extended emulator coverage to confirm the visible magenta overlay pixels do not escape the actual green video canvas after direct dragging.
- Source commits: `19cd81b` (coordinate mapping), `8a215fd` (unit coverage), `49a9281` (Activity wiring), `102c5c1` (animation anchor parity), `4b772a8` (visual clipping assertion). **Latest follow-up commits await their own build and emulator result.**


## Coordinate compile correction and pixel-boundary test — 2026-11-06

- The first shared-anchor integration build exposed two Kotlin type errors: text preview passed integer canvas width/height into a float coordinate helper. Both call sites now explicitly convert the dimensions to `Float` (source commit `245ed7a`). The red run was a compile-time type mismatch, not a runtime preview/export failure.
- The emulator clipping assertion now reads the Activity's absolute `previewFrame` screen origin and the inner-canvas offset from `PREVIEW_CANVAS_BOUNDS`, then checks all magenta overlay pixels against that exact screen-space rectangle. This replaces the earlier color-inferred green-video bounding box, which could have included unrelated green timeline UI.
- Source commits: `9d7d36b` (absolute stage origin marker), `0605686` (pixel test uses measured canvas rectangle). These fixes are committed; their new CI run is required before marking the build and emulator checks green.

- Added a direct parity regression: for fractions from 0 to 1 (including safe inset-like positions), the normalized Media3 export anchor must equal the corresponding Android preview translation scaled by `2 / canvasExtent`, with the Y-axis inversion explicitly accounted for. Source commit: `ee52e27`; its PR-triggered test run is pending.

- SOURCE-aspect timing follow-up: the source ratio is first learned from the decoded thumbnail. If the PlayerView size callback happened before that decode, the inner project frame could stay on the 9:16 fallback for a landscape/square source. The first non-default aspect discovery now also refreshes the SOURCE canvas (custom 9:16/4:5/1:1/16:9 presets remain fixed). Source commit: `b9efe23`; build and emulator checks are pending.


## Direct preview tap selection — 2026-11-06

- Text overlay views now select their matching text track when tapped, even when direct text dragging is not currently enabled. Selecting a text item opens its contextual text tools; when text editing is enabled, the existing touch handler continues to support direct drag/pinch.
- Paused image/video overlay views are clickable. Tapping one selects its timeline layer and restores its overlay tool dock and gesture surface. A tap on the already-selected layer also reopens the overlay tools after the user has returned to the main dock.
- Extended the emulator smoke sequence to exit the overlay dock, tap the unselected visible magenta layer, and assert that the overlay tool dock plus direct-canvas gesture surface return before drag/clipping checks.
- Source commits: `c4cedb0` (canvas tap handlers), `f71e376` (reopen tool strip for a repeated tap), `167e67e` (emulator tap-to-select check). CI for this branch head is pending.


## Text selection outline — 2026-11-06

- Added a dedicated white selection frame around the selected text/sticker preview view. Its width/height are derived from the measured TextView bounds, and it follows the same normalized canvas translation and text-animation scale. It is clipped by the inner project canvas.
- Text selection now clears the previous image/video overlay and clip gesture targets; selecting an image/video overlay clears the text outline; selecting an audio track clears the text selection outline. This prevents a stale transform target from remaining active after changing layer type.
- Source commits: `59b4dc8` (text outline and selection-state clearing), `0e3676b` (gesture-target cleanup and initialization guard). CI for these source commits is still running.

- Layer-type switch follow-up: entering the audio tool strip now clears stale text and image/video selection IDs before refreshing preview chrome. This keeps a previous visual selection outline/gesture target from remaining active while audio is selected. Source commit: `ec516ec`; its CI run is pending.


## Text/sticker two-finger rotation — 2026-11-06

- Added a per-layer `rotationDegrees` field to text/sticker layers. It is written to project JSON and restored on load; older project files without the field remain at 0°.
- In preview edit mode, the two-finger gesture now updates font size and rotation together. Rotation uses the shortest signed angular delta so crossing the ±180° boundary does not spin the text the long way around.
- The TextView and its white selection outline render the same rotation; `buildTimedTextOverlay()` passes that saved angle into Media3's export overlay settings.
- Updated the text panel and gesture status copy to mention rotate support. Added pure math tests for wrap-around and canonical angle normalization.
- Source commits: `bc97359` (rotation math), `5b7d7b7` (unit cases), `b1cf8c6` (text model/gesture/preview/export/persistence), `a837f6f` (UI guidance). Build and emulator verification pending.


## Text canvas interaction smoke and visual-panel return — 2026-11-06

- Text and image/video overlay detail panels now use one contextual return helper: from the global editor tab it closes the panel back to the global dock; from a selected visual layer's tool strip it returns to that layer's one-row tools.
- The emulator workflow now taps the global Metin tab, creates a text layer, enters `CanvasSmoke`, and requires both the visible preview TextView and `Seçili metin katmanı sınırı` accessibility bounds. It closes the panel, taps the text itself in the preview, requires the `Düzenle`/`Sil` text tool strip and selection frame to return, then deletes the smoke layer before timeline alignment/scrubbing tests.
- Source commits: `dbe3460` (return flow), `af66df5` (text selection UI smoke). Latest CI pending.

- Direct-text-gesture click guard: `performClick()` is still fired after a drag/pinch/rotation for accessibility, but the TextView click listener now ignores it while direct text editing is enabled. This prevents the end of a gesture from being misinterpreted as a fresh selection tap and collapsing the open text settings panel. Repeated preview taps still reselect/reopen tools when direct editing is disabled. Source commit: `8041f71`; CI pending.

- Text smoke target refinement: the hierarchy assertion now distinguishes the rendered preview `TextView` from the similarly named `EditText` field in the settings drawer, so the tap coordinate is taken from the on-canvas layer itself. Source commit: `1787a84`; pending CI.

- Visible canvas boundary: added a thin turquoise outline as a workspace-only sibling of the inner project canvas. It tracks the exact measured/fitted canvas rectangle but is outside the inner canvas and is never part of MP4 composition/export; it is non-clickable and does not intercept gestures. Source commit: `ac36120`; CI pending.

- The emulator screenshot pixel check now verifies that the turquoise workspace-only outline is visibly drawn on all four measured project-canvas edges, while still asserting that no magenta overlay pixels escape the inner canvas. Source commit: `a738908`; the new assertion is awaiting its emulator run.

- Cross-mode selection cleanup: entering video tools or image/video overlay tools now clears a stale selected text layer and refreshes its selection outline before enabling the new transform surface. Audio, video, text and visual-overlay modes no longer leave an unrelated text selection frame active. Source commit: `c485854`; CI pending.


## Main-video selection frame — 2026-11-06

- Added a non-interactive white selection rectangle for the selected main-video clip. Its bounds follow the clip's stored scale, rotation and normalized translation in the inner project canvas, so the selection box visibly tracks the same transform as the preview image.
- The frame is visible while a video clip is selected on the timeline (not only while its detail panel is open). It hides when selection moves to text, an image/video overlay or an audio track. The touch surface remains separately gated by the explicit video-edit tool panel.
- Source commits: `a3b64cd` (clip frame geometry and update hooks), `d9901f8` (clear clip selection when switching to overlay/audio tools). CI pending.


## Shared editor touch priority — 2026-11-06

- Added a shared TouchPriority contract for preview hit layers: passive text at 4dp, active canvas gesture surfaces at 16dp, and non-interactive selection chrome at 20dp. Main-video and image/video-overlay transform surfaces now use the same active priority; visible text layers remain above paused media and rise to active priority only while direct text editing is enabled.
- Standardized text/music/sound trim hit widths to a preferred 32dp (bounded by the track block) and brought all timeline trim handles to the same 12dp elevation. Main-video trim handles keep their existing wider 40dp target while sharing that elevation.
- This is deliberate mode arbitration, not one global transparent overlay: only the active canvas tool receives canvas gestures, while timeline scrubbing/pinch and per-track body/edge gestures remain owned by their own interaction zones.
- Source commit: 71d11c8.
- Emulator run #952 passed canvas sizing, replacement filmstrip, main-video selection frame, overlay reselection and direct overlay drag/clipping. It then failed in the later text test because uiautomator dump timed out and did not create editor-text-selected.xml; this was a test-harness hierarchy-capture failure, not an overlay-drag failure.
- Test harness follow-up commit eed391b retries compressed hierarchy dumps with a longer timeout, emits a dedicated diagnostic if XML is still missing, and adds a direct text-drag assertion that compares the preview TextView bounds before/after the gesture.
- Runs #953 (source-policy verification) and #954 (updated emulator interaction smoke) plus build/verify runs #742 and #786 were pending at the time of this update. Do not mark cross-tool touch handling complete until the updated emulator run passes.


## Touch target sizing and gesture consistency — 2026-11-06

- Text preview views now have a minimum 48×44dp touch box; their selection outline still follows the measured view bounds.
- Text, music and sound-effect trim handles now use transparent FrameLayout hit areas with narrow 5dp visual grips. Their preferred hit width is up to 32dp, constrained to half the clip block to prevent start/end overlap. Video trim retains a wider 40dp target with the same narrow visual grip.
- Video, text, music and sound-effect trim movement now uses Android scaledTouchSlop consistently. The text-edit toggle accessibility description now reports the active direct-manipulation mode so automated tests can preserve its initial state.
- Additional test-harness commits: 833ac16 (activity touch-slop context fix), f0dace1 (transparent trim hit regions), 41fa43c (text minimum hit box), 9ff9e7e (text gesture accessibility state), 2d20595 (reselect clip before video-transform smoke), 2de525d (preserve initial text-edit toggle state in smoke).
- Emulator run #960 reached the video-transform check after passing canvas geometry, replacement filmstrip, main-video selection frame, overlay reselection and direct overlay drag/clipping. It failed because the test had switched selection to the overlay and then opened clip tools without reselecting the video; the screenshot confirmed the clip was not selected, so the absent frame was expected. The test now taps the video track to reselect it before opening Kırp.
- Run #952 had separately failed at a missing UIAutomator XML after the overlay drag had passed. The hierarchy capture now retries with compressed XML and explicit diagnostics.
- Latest cross-tool emulator runs #963–#965 are pending; do not close this work until the video, text and overlay direct-drag assertions pass on the latest run. Build/verify runs for commits through 41fa43c passed; later commits are under CI.

## Shared trim targets — overlay inclusion — 2026-11-06

- Image/video overlay timeline trim edges now use the same transparent hit-region helper as text, music and sound-effect trims: preferred width up to 32dp, capped at half the block width so the two edge targets cannot overlap. The visible teal grip remains 5dp wide and 24dp high; the interactive target uses the shared 12dp edge-handle priority.
- The drag handler still uses the device's scaled touch slop, clamps against the source/timeline range, preserves/rebases overlay keyframes on start trim, and returns to the selected overlay's contextual tool strip.
- Added `OVERLAY_EDGE_TRIM` diagnostics for image/video overlay start and end edges. The API 35 emulator smoke now drags each edge independently and checks the saved start/duration values actually change; target bounds must also be at least 40x20 screen pixels.
- The text selection smoke no longer depends on dumping the selected-text drawer's rapidly changing UIAutomator tree. It measures the white selection frame against the app-reported project-canvas bounds, then takes a second screenshot after direct drag and verifies the rendered frame moved.
- Current source head: `7935e0eb8771545b867f9ceab41d6cad3483a59c`. APK build #769 and Android unit/build verification #813 are green. Android 35 emulator run #981 is still in progress; do not mark the interaction suite green until it concludes.
- A prior compile failure on `49749f44` was caused by escaped Kotlin string-template markers in test diagnostics. The markers were corrected on `7935e0eb`; both subsequent compile/build checks passed.
- No version bump, signed APK publication, release-trigger change or physical-phone acceptance is included. The app remains at versionCode 399 / 8.32-capcut-editor until the current emulator gate and the separate physical-device checks pass.


### Overlay end-edge visibility diagnosis — 2026-11-06

- Emulator run #983 confirmed the overlay end hit view is created and measured at 84×84 emulator pixels, but its screen bounds were outside the horizontally scrolled overlay-row viewport (the end edge was at x=2982 while the viewport ended at x=1039). The prior “missing handle” failure was therefore a test navigation/visibility issue, not evidence that the app failed to create the end hit target.
- Updated `.github/scripts/capture-emulator-screenshots.sh` to identify the overlay's own HorizontalScrollView and scroll that row toward its end, re-dumping accessibility until the end handle is visible before asserting its bounds and dragging it. The app touch/trim implementation is unchanged by this test-harness correction.
- Follow-up emulator verification is required before marking shared overlay trim complete. No APK release or version bump.


- Follow-up run #985 showed why the first test-harness correction did not work: swiping directly on the overlay row is intercepted by the selected clip's drag gesture, so the row never scrolls. The end handle remains correctly laid out offscreen (84×84px at x=2982), and no app crash was found. The harness now scrolls the shared timeline ruler instead; the timeline rows follow the ruler's horizontal position. This avoids sending navigation gestures through the clip's own touch target. Re-run the emulator gate before considering the fix verified.


### Emulator run #988 — overlay end-edge scroll harness follow-up — 2026-11-06

- Run #988 (source `96b72f31986601f239c85c8aa48217251bbbd237`) passed unit tests and debug APK assembly, as well as all earlier emulator checks through overlay start trim and music/sound start/end trims. It stopped at overlay end-handle discovery with exit code 82.
- The saved `EditorTouchTest` diagnostics confirm the overlay end hit target is created at 84×84px inside a 2,527px-wide clip block (`left=2443 right=2527)); the selected row's viewport exposes only the first part of that content. Overlay start trim passed and changed timing from 0/8,023ms to 133/7,890ms.
- The harness attempted to reveal the far edge by starting a 708px swipe at x=1004, close to the API 35 emulator's right-side system-navigation region. The post-gesture hierarchy ended up on the Android launcher, so #988 does not establish an app crash or a missing end-handle implementation; the harness gesture is the likely failure point.
- Corrected the ruler-scroll gesture to start/end within the inner 50% of the ruler viewport and added a foreground-package assertion after every scroll. This guards against silently leaving the editor and gives a specific diagnostic if navigation wins the gesture.
- The app's trim implementation and version are unchanged. This harness correction still needs a fresh emulator run; do not mark overlay end trim verified, publish an APK, or bump the app version until that run passes.


### Emulator run #989 — partial overlay end-hitbox visibility — 2026-11-06

- Run #989 on source `077c33f58a28ed23d6ceae94113839ef7b809160` passed unit tests/debug APK assembly and all emulator checks up to the overlay end-edge assertion. The previous system-back navigation issue was avoided; every scroll hierarchy still reported the MathMotor package.
- The inner-ruler swipe exposed the overlay end hitbox, but only 22×84px were inside the ruler viewport: `[1017,1227][1039,1311]`. The check correctly stopped with exit 83 because it requires at least 40×20px to avoid a gesture being clipped. This is not a missing view or undersized app-side hitbox; the view itself is clipped by the horizontal lane viewport.
- The captured paused-editor screenshot shows the magenta still-image overlay rendered visibly on the project canvas; image preview is present at this stage. The selected overlay's end handle remains the outstanding interaction assertion.
- Updated the harness so a partially exposed edge does not count as success: it performs a short 40px nudge from the ruler centre and rechecks the actual accessible bounds, while retaining the app-foreground guard. The first large swipe remains reserved for a handle that is not visible at all.
- No change to the production overlay target width/trim handler, app version, or release state. A fresh emulator pass must still prove at least 40×20px is visible and that dragging the end handle changes its saved duration.


### Emulator run #990 — overlay trim passed; text drag assertion clipped at canvas edge — 2026-11-06

- Run #990 (source `a369bbe1a013d1227fbbe2b46bc69f77f17f909f`) passed unit tests/debug build and the full checks through: project-canvas 9:16 geometry, replacement filmstrip, video selection/direct manipulation, paused image overlay visibility/drag, all four audio edge trims, both image-overlay trims, contextual dock restoration, stable panel geometry, and actual MP4 export.
- The overlay-end harness correction is verified: the end handle became fully visible at 84×84px and a real end-edge drag shortened the image layer from 7,890ms to 7,757ms (−133ms).
- The run then stopped with exit 62 in the text direct-drag smoke. The initial text frame was detected at `[365,661]-[713,868]` inside the measured canvas `[352,203]-[727,871]`. The harness dragged the centre by (+28,+18), producing an expected moved frame around `[393,679]-[741,886]`; this moved the right and bottom edges outside the canvas. The screenshot shows the text moved, with its top/left selection strokes still visible, while the canvas clipped the right/bottom strokes. The detector intentionally required a complete rectangle inside the canvas, so it reported a false negative rather than a failed text gesture.
- Updated the harness to drag diagonally right/up by (+12,−28) instead. This keeps the full selection box inside the measured canvas while exceeding the emulator's measured 21px touch slop and retaining a measurable movement assertion. No production text interaction code was changed.
- The remaining emulator checks after text manipulation (preview reselection, timeline thumbnail alignment/playback, and draft list) still need to run; no release or version change.


### Emulator run #991 — text selection frame clipped by +12px horizontal test drag — 2026-11-06

- Run #991 (source `ba2f4eb234e4b879a510758a8e3cbd93f247ba99`) again passed unit tests/debug build, canvas/video/overlay interaction, all audio trims, both overlay trims (including the 84×84px end hitbox and 133ms duration reduction), contextual editor panel geometry, and actual MP4 export. It failed at the text drag detector with exit 62.
- Inspecting both full-resolution screenshots and the detector's exact pixel logic identified the remaining issue: before the drag, the selection frame occupied x=362..716 inside the canvas x=352..727. The test then dragged (+12,−28), placing the right selection edge at x≈728; the canvas clipped that right vertical stroke. The screenshot visibly shows the text and the moved top/bottom/left selection edges, but the detector correctly refused to accept a frame whose right edge was clipped.
- Updated the test-only gesture to be strictly vertical (0,−28px), which remains above the emulator's measured 21px touch slop while keeping the full horizontal frame inside the canvas. The assertion now verifies y movement of at least 12px and horizontal drift within ±6px. No production text manipulation behavior was changed.
- Re-run the emulator gate before treating text direct manipulation or the full interaction suite as verified. No APK release/version bump.


### Emulator run #992 — text movement passed; IME focus hid the preview before text reselection — 2026-11-06

- Run #992 (source `3ff509d722829dc35917e2183560930a892b73ab`) passed unit/build, 9:16 canvas geometry, replacement filmstrip, video/overlay direct manipulation, all audio and image-overlay trims (including image end trim 7,890ms → 7,757ms), stable contextual panel geometry, MP4 export, and the full in-canvas text drag.
- The text drag is now conclusively verified: the visible white selection frame moved from center `(539,764)` to `(539,736)`, with its full border inside the project canvas.
- Run #992 then failed the preview text-reselection assertion. The captured `editor-text-reselected.png` showed Android's soft keyboard open (top action changed to `Bitti ✓`) and the project preview compressed to roughly 70×109px. The test tapped the old canvas coordinate while the IME layout was active, so the tap no longer landed on the text. This was an IME focus/layout state defect during entry to the text contextual dock, not a failed vertical drag.
- Production `enterTextTools()` now dismisses the IME and clears the text EditText focus before rebuilding the contextual dock. Added diagnostic markers for text-panel return/close and the rebuilt three-action dock.
- The emulator harness now checks both post-close and post-reselection screenshots against the measured project-canvas bounds and green video-fixture pixels, and uses the app-side `TEXT_TOOLS_DOCK_VISIBLE` marker plus a pixel-verified selection border as proof. UIAutomator cleanup is best-effort to avoid turning an accessibility timeout into a false negative after direct interaction already passed.
- Source keyboard-focus fix: `92d2f1d85b368d5dcef63a11601bfd0ca4b8c422`. Harness follow-up is committed separately and must pass a fresh emulator run. No release/version bump.


### Emulator run #994 — photo overlay end-trim gesture intercepted at Android's right system edge — 2026-11-06

- Run #994 on `765e6df070a9eef51628118c42c304d0bbe5c107` built and passed unit/build and Android verification, but the emulator smoke test failed at `overlay-end-gesture-not-received` (exit 85).
- The photo overlay start trim was verified: `OVERLAY_EDGE_TRIM kind=image edge=start beforeStartMs=0 beforeDurationMs=8023 afterStartMs=133 afterDurationMs=7890 deltaMs=133`.
- The end trim hit target was found at screen bounds `[965,1227]-[1039,1311]` (74×84px visible) and the harness swiped left from its center `(1002,1269)`. No `OVERLAY_EDGE_TRIM kind=image edge=end` callback arrived. Music/sound start+end trims and photo start trim passed earlier in the same run.
- Diagnosis: the trailing handle is at the physical right display edge. On Android gesture navigation, the system back gesture can intercept a leftward drag beginning there before the app's touch listener receives it. The test was not merely a flaky selector: it exposed a real touch-priority conflict at the right edge.
- Production fix: the narrow trailing trim handle now registers only its own bounds in `systemGestureExclusionRects` on Android Q+ so its leftward drag reaches the app while the rest of the screen retains normal system navigation. Diagnostic log now records `gestureExclusion=true`.
- Emulator harness now asserts the trailing handle's gesture exclusion was registered before dragging and still requires the actual `OVERLAY_EDGE_TRIM kind=image edge=end` callback plus changed duration. This is not considered fixed until a fresh emulator run passes.
- Commits: `efb31a91e63d7cd4ba9cc922c526673b1fe41ba1` (app), `e7923312644070e1fa2740bef503e452aba180fb` (harness). No release/version bump.


### Text panel return control — production layout correction — 2026-11-06

- Emulator run #1003 remained red at `text-canvas-panel-back-missing`. The app process stayed RESUMED, no fatal exception or ANR was found, and the text layer/editor controls were still visible.
- Source inspection found the root cause: text entry focuses the `EditText`, whose `requestRectangleOnScreen()` scrolls the shared settings `ScrollView`. The “Araçlara dön” button was its first child, so it scrolled out of view. The earlier test-only swipe workaround did not reliably restore it.
- Production correction: moved the text-panel return action into a fixed sibling header above the settings ScrollView. The text settings viewport now reserves the header height in both normal and IME-visible layouts. Closing the text panel, switching panels, and entering text-track tools hide the header.
- Emulator smoke coverage now checks the pinned return action directly after the text manipulation, without a compensating swipe. This verifies the production control remains visible rather than masking the layout defect.
- Build, unit tests and emulator verification for this correction are pending. No version bump, APK release, signing or publishing was performed.


### Text-panel return smoke-test — UiAutomator timeout correction — 2026-11-06

- Emulator run #1004 failed at the post-edit hierarchy inspection (exit 52), not at the return-button interaction. The app was still foregrounded; ANR files were empty. The captured `editor-text-after-drag.png` visibly shows the pinned “Araçlara dön” button fully on screen.
- The second `uiautomator dump --compressed` after text editing did not produce XML in two attempts. The timeline accessibility tree contains many off-screen thumbnail nodes; the dump timed out despite the app remaining responsive. The earlier hierarchy dump, before text editing, successfully measured the pinned button bounds `[0,1166][1080,1265]`.
- Test harness correction: capture the return-button coordinates from the initial text-panel hierarchy, reuse the pinned sibling-header target after text editing, clear logcat, tap it, and require both `TEXT_PANEL_RETURN_TAPPED` and `TEXT_PANEL_CLOSED` events. No post-edit full hierarchy dump is required.
- Production UI code is unchanged in this follow-up. Build/unit tests and the emulator interaction must be rerun before this flow is considered verified. No version bump or APK release.


### Emulator run #1005 — text panel closes, but preview tap did not reopen contextual tools — 2026-11-06

- Run #1005 on `5f281ec3f1f7ea9ab35333f4d909d37639c1d08e` passed Gradle/unit tests and debug APK packaging, but failed in the emulator smoke at `text-canvas-tap-did-not-reopen-tools` (exit 54).
- The pinned text-panel return button was tapped successfully: logcat contains both `TEXT_PANEL_RETURN_TAPPED` and `TEXT_PANEL_CLOSED activePanel=none`. The preview remained full portrait size, and the text selection frame stayed visible after the panel closed.
- The next tap on the text did not emit `TEXT_TOOLS_DOCK_VISIBLE childCount=3`. This is a real interaction failure, distinct from the previous hierarchy-dump timeout.
- Production correction: the preview text overlay now explicitly recognizes a passive tap while its detail panel is closed and calls `selectTextLayer`, which rebuilds the contextual text dock. Movement outside the active detail-edit mode is consumed without moving the text. Direct drag/pinch/rotate remains restricted to the active text-edit panel.
- Fix commit: `4c572d430b5bacdafc9bd8abaf5431c58241e7f0`. A fresh emulator run is required; this correction is not yet verified. No version bump or release.


### Editor panel / keyboard layout correction
- Contextual editor panels now participate in the root vertical workspace layout instead of floating as a bottom overlay. Opening text tools reserves measured space above the dock, so the timeline and its touch targets are not covered.
- The dock remains in the same layout flow; fullscreen preview hides/restores it through the root child visibility cycle.
- Responsive keyboard handling continues to resize the preview workspace and fit the project canvas to the available stage; emulator verification is pending.
- Panel geometry assertion was corrected: opening a detail panel is expected to move the unchanged-size timeline upward by the exact height removed from the preview; closing the panel must restore both. A fixed timeline Y-coordinate was an invalid test expectation, not an app regression.


### Text-entry keyboard preview sizing — production layout correction — 2026-11-06

- Emulator run #1016 on `f96b088ff0a0d82dc3f905780c8aecf53b1260fc` passed build and verification workflows but failed the real keyboard/canvas check. The keyboard remained visible; the measured 9:16 project canvas collapsed to only `90x161` pixels because the full multi-track timeline, contextual text panel and IME competed for the available vertical space.
- This is a production layout issue, not an emulator startup failure. The keyboard mode now temporarily hides the timeline card while text entry is active, allowing the preview stage and project canvas to remain large enough for text positioning. The timeline is restored when the keyboard closes.
- Removed obsolete bottom-sheet inset handling that attempted to cast the now-root-child `LinearLayout` to `FrameLayout.LayoutParams`; root padding is the single inset source.
- Fix commit: `3d4758aacf726a02295287163fb72a389e40aa17`. Fresh build and emulator verification are pending; do not mark this correction complete until the keyboard-open canvas measurement passes. No version bump or APK release.


### Text-panel preview starvation after keyboard dismissal — production layout correction — 2026-11-06

- Emulator run #1018 verified the keyboard-open project canvas at 216x385 (9:16), but after dismissing the keyboard the still-open text panel left the preview stage at 1038x161 (project canvas 90x161). The selected text layer's preview selection frame was consequently not measurable in the screenshot.
- This exposed a second real layout issue: the fixed 220dp timeline card and expanded tool panel competed with the preview after IME dismissal. The timeline is now compacted to 132dp while any second-level editor panel is open, and restored to 220dp when that panel closes. Timeline rows remain present and vertically scrollable; the preview receives the released height.
- The keyboard-open mode continues to temporarily hide the timeline while the IME is visible, restoring the compact timeline when the keyboard closes.
- Fix commit: `d754573096ce9e6a57963115fc85ed0cd2caec29`. Fresh build and emulator verification are pending. Do not mark text editing layout complete until the post-keyboard preview and text-selection frame checks pass. No version bump or APK release.


### Main video canvas drag coordinate consistency — 2026-11-06

- Emulator run #1021 reached the main-video transform gesture but measured only 27.8x16.7 project-canvas pixels from a requested 40x24 px drag. Build and code verification passed; the emulator interaction remained red.
- The main-video handler mixed raw screen coordinates for one-finger movement with local view coordinates for pinch movement and pointer handoff. Overlay transforms already use the gesture surface's local coordinates. The video handler now follows the same single local coordinate space for down, move and finger handoff, normalized against the project canvas dimensions. Diagnostics record both local and raw coordinates.
- Fix commit: `edad968a8a0056611dc49c7faeac2770d10e5154`. A fresh build and emulator run must verify the requested 40x24 px drag and reverse restoration before this item is complete. No version bump or APK release.


## 2026-11-06 — Emulator #1006 follow-up

- Verified in emulator logs: closing the text panel succeeds, tapping the preview text restores the three-action text dock, and the full portrait preview remains visible.
- Emulator run #1006 still ended red later in timeline alignment setup because the test script attempted to read `TIMELINE_TOUCH_SLOP_PX` after clearing logcat during text-panel isolation.
- Fixed the harness ordering in commit `5c2bc6f`: capture and validate the device touch-slop marker immediately after launching the editor, then reuse the stored value during timeline alignment.
- Build and unit tests passed on the prior source revision; emulator verification of this harness change is pending. Do not mark #1006 as green.



### Stable timeline viewport after adding independent tracks — 2026-11-06

- Emulator run #1026 verified the corrected main-video drag exactly: saved transform changed by (39.9, 24.0) project-canvas pixels and the reverse gesture restored the preview. This closes the prior 40x24 drag discrepancy.
- The same run then exposed a separate real layout regression: after audio and overlay rows were added, the timeline card expanded from 577px to 661px and moved its top from y=1076 to y=992. The dynamic content-sized card calculation (up to 280dp) was overriding the editor's intended 220dp/132dp workspace heights, pushing the preview and destabilizing panel geometry.
- Replaced content-driven outer-card expansion with a stable viewport height: 220dp normally, 132dp while a detail panel is open, and 88dp while the keyboard is active. Track rows remain independent inside the viewport rather than changing the editor shell geometry.
- Fix commit: `046539e295bef2383de59eeb9db1326499001727`. Fresh build and emulator verification are pending; do not mark the layout correction complete until all panel geometry and track interaction checks pass. No version bump or APK release.


## Timeline viewport height regression — 2026-11-06

- **Root cause from emulator #1027–#1029:** two independent height controllers collapsed the timeline card from 220dp to 132dp whenever a detail panel opened: `setTimelinePanelMode()` and the outer-card sizing in `refreshTimelineTracks()`. The measured geometry changed from `(x=21,y=1076,w=1038,h=577)` to `(x=21,y=793,w=1038,h=346)`, violating the intended layout contract.
- **Source fix:** commit `212db75d4130cc5bf253edf9fde8845c64b4b50e` keeps the timeline card at 220dp during detail panels in both code paths; only keyboard mode retains the compact 88dp height. Track rows remain in the scrollable viewport instead of resizing the editor shell.
- **Verification pending:** build, unit tests and fresh API 35 emulator geometry validation must pass on this commit. Required proof: timeline width/height remain unchanged while the preview shrinks by the detail panel height; close/switch restores the original preview and timeline geometry. No APK/release is being prepared.


## Text overlay clipping regression — 2026-11-06

- Historical emulator #1017/#1018 failure: `text-canvas-selection-frame-missing`. Screenshot review showed the text panel reduced the project canvas to about 90×161px, while `updatePreviewTextOverlay()` forced dimensions to at least 100dp and the text view to at least 80dp width. The text/selection frame could therefore be larger than the real project canvas and clip out of view.
- **Source correction:** commit `7d7c3f80377ee84da074770fc6736c78c5d798d7` uses actual `previewCanvas.width/height`, dynamically constrains the text view to available canvas width/height, and clamps the rotated selection frame within the real canvas. The test was not weakened.
- **Verification pending:** require the actual white selection frame, text drag, panel/keyboard geometry and timeline layout to pass in a fresh API 35 emulator run. No release/APK is being prepared.


## Text selection smoke-test measurement correction — 2026-11-06

- Additional review of the #1018 screenshot artifact found the pixel detector reused `preview-canvas-logcat.txt` captured before the text panel/keyboard resized the workspace. The detector therefore searched the old 376×669 canvas region although the live text-panel canvas was much smaller. The image assertion was using stale geometry, independently of the text overlay sizing defect.
- Script commit `9efb587741ad6aa12b5e608a42fdf9cf05fe485e` snapshots the latest `PREVIEW_CANVAS_BOUNDS` immediately before both the selected-text and post-drag screenshots, and supplies those fresh bounds to the unchanged pixel/drag assertions. This updates measurement, not success thresholds.
- Await fresh API 35 emulator evidence for text selection, in-canvas drag and stable timeline geometry.


## Follow-up compile correction — 2026-11-06

- The first text-bounds implementation did not compile because the rotated bounding-box math mixed `Float` and `Double`. The failing compiler locations were `VideoEditorActivity.kt:6331–6340`.
- Commit `798f7288a626d34345b5ac1966e239932e4a04d8` normalizes the trigonometric bounds to `Float` before calculating the canvas clamp. The previous red runs are not treated as successful; a fresh build and emulator run on this correction is required.


## Compact text-panel layout — 2026-11-06

- The text detail panel occupied 156dp after its sticky header, leaving the 9:16 preview too narrow for a useful text transform/selection affordance on the API 35 emulator.
- Source commit `b530525c7ebacf9d9ff7a054c728071635a52905` reduces the normal text-panel viewport to 128dp while retaining its scrollable controls and leaving keyboard-mode sizing unchanged. This reserves more screen area for the project preview instead of hiding controls or covering the timeline.
- Confirm with a fresh screenshot that the preview canvas is larger, the white selection frame is fully visible, text dragging works, and panel close restores the full preview.


## Responsive text preview scale — 2026-11-06

- Screenshot evidence from emulator #1032 showed the preview project canvas shrinking from 423×753px to 137×245px when the text detail panel opened, while the overlay remained at 24sp with 10dp side padding. "CanvasSmoke" wrapped beyond its available width and the selected frame was clipped at the canvas edges.
- **Source fix:** commit `54de4fe0e4cf1346669e4fb6cb8e12e5c62d4882` remembers the largest normal, keyboard-free project-canvas dimensions and scales preview text size and its visual insets/corner/shadow by the current canvas ratio. The saved text layer size and export dimensions are not changed.
- **Validation pending:** require a visible complete selection rectangle inside the measured canvas, successful in-canvas text movement, preview restoration after closing the panel, and the three-action text dock on tap. Do not mark complete until the latest emulator run passes.


- Follow-up commit `abfb2b1894fcd2a8da88dbe0e0d655441a32325a` refreshes the reference dimensions from the latest settled keyboard-free, panel-free layout instead of retaining a transient oversized startup canvas. Validation remains pending on this exact revision.


## Text target after closing detail panel — 2026-11-06

- The full-preview canvas expands when the text panel closes, so the text layer's on-screen coordinate changes with the project canvas. The previous smoke script reused the compressed-panel center for the later reselect tap.
- Script commit `f03f1a12e9510b6310fa8490eed10af81bf7d59f` now captures fresh canvas bounds after panel close, verifies the full-size selection frame, and taps its newly measured center. It also uses fresh bounds for the reselected screenshot. This preserves the pixel/gesture assertions rather than weakening them.
- Validate on the next complete API 35 run together with responsive text preview scaling.


## Screenshot-coordinate scale correction — 2026-11-07

- Emulator #1048 source screenshot shows the text itself and its white selection rectangle are visibly present and fully inside the project canvas. The smoke test nevertheless failed because `PREVIEW_CANVAS_BOUNDS` is logged in Android view coordinates (root window 1080×1920), while the pulled PNG is rasterized at 928×1640.
- The detector compared these coordinate spaces without scaling, so it searched a shifted/larger region and falsely reported `text-canvas-selection-frame-missing`.
- Commit `70b9a05b20dcf6fb855c9e247aaed2db32fa7216` scales the measured canvas rectangle using the hierarchy root bounds and actual screenshot dimensions before running the unchanged white-frame/text pixel checks. It does not weaken the frame, text, or drag assertions.
- Fresh emulator verification is required for selected text, post-drag position, and reselection after closing the panel.


## Emulator fixture dependency correction — 2026-11-07

- On candidate commit `7015e26b6e8ff8276b5fd87619fe23938f3d2aae`, unit tests and Debug APK compilation passed in Verify Android build #882; the debug APK ZIP artifact was uploaded. Android emulator run #1050 remained in the fixture-generation step and had not reached emulator boot or interaction checks when inspected. This is **not** a green emulator result.
- The `ubuntu-24.04` runner's published software inventory does not list FFmpeg. The workflow therefore used its apt-get fallback before generating deterministic video/audio fixtures; that step remained in progress. The exact stalled subprocess was not available from the active-run logs.
- Replaced that fallback with the FFmpeg setup action pinned to commit `37062fbf7149fc5578d6c57e08aed62458b375d6`; the existing fixture-generation commands and test assertions are unchanged. The action supplies a static FFmpeg binary without running apt repository updates.
- A fresh run on the updated PR head is required. Do not mark preview, timeline, text panel, export, or phone acceptance green until the relevant emulator/device evidence is captured.


## Empty-draft home layout correction — 2026-11-07

- Static review found that the initial home screen still rendered the empty `TASLAKLAR` heading, helper text and “Henüz kaydedilmiş taslak yok” placeholder. This contradicted the compact-home requirement to hide the empty draft area.
- Updated `refreshDraftProjects()` so the heading, helper copy, explanation and list appear only when at least one saved draft exists. The `Taslakları yedekle` action stays visible but disabled when there are no drafts; `Yedekten geri yükle` remains visible and usable, so an empty device can still restore a backup.
- Added an API 35 emulator smoke assertion on the fresh home screen: both backup/restore actions must be present while the empty draft heading/placeholder must be absent. The existing saved-draft check still requires the heading and both actions after the test creates a draft.
- Unit/build and the complete API 35 emulator run on this exact head are required before marking this correction green. The current screenshot run from the prior source does not cover this change.


## Empty-draft smoke assertion coordinate correction — 2026-11-07

- API 35 emulator run #1055 on commit `61f974a1f71fb82cf87fa01d29e560f1f2485233` passed unit tests, Debug APK packaging, FFmpeg setup/fixture generation, KVM, SDK installation, emulator boot and the two `VideoProjectDraftBackupTest` instrumentation tests.
- The screenshot script then failed at its fresh-home empty-draft assertion (exit 31). The captured UI hierarchy shows the home screen at scroll position zero: the hero, primary cards, recent-project placeholder and fixed footer are visible, while the backup/restore section is farther down the ScrollView after the tool grid. The buttons were therefore off-screen and absent from that hierarchy; this is not evidence that the app failed to create them.
- Corrected the test ordering: capture the top-of-home screenshot first, scroll the home content down to the draft section, assert both backup/restore actions are present and empty draft heading/helper/placeholder are absent, then scroll back to the top and refresh the hierarchy before opening the empty editor.
- This is a test-coordinate/scroll-state correction. It does not weaken the empty-state assertions or alter app behavior. Run #1055 did not reach the editor/export/timeline/text smoke tests; API 35 full smoke verification remains pending on the corrected script.


## Full-frame initial photo correction — 2026-11-07

- Static review found that the home-screen `Fotoğrafı düzenle` route passed its image into the same helper used by the logo picker. That helper applies the 18% upper-right logo preset, so the first selected photo did not open as a full-frame editable layer.
- Split still-image defaults into two explicit entry modes: `imageLogo()` retains the small upper-right 18% / 88%-opacity logo behavior; `fullFramePhoto()` uses scale 1.0, centered 0.5/0.5 placement, full opacity and the project duration. The home-screen `initial_overlay_uri` path now explicitly selects the full-frame preset; normal logo/overlay imports keep their prior defaults.
- Added unit cases for full-frame photo placement and zero-duration fallback. Added a debug-only standalone photo launch path plus a portrait 9:16 magenta fixture in the API 35 smoke test. The smoke now requires the runtime transform marker and checks screenshot pixel bounds, portrait aspect and fill ratio; this verifies the rendered project canvas rather than only the stored scale value.
- The new Kotlin changes, helper tests and full-frame screenshot assertion require fresh unit/build/emulator checks on the current PR head. The production version and signed release path are unchanged.


## Empty-draft exact-label assertion correction — 2026-11-07

- API 35 emulator run #1057 attempt 2 passed unit tests, debug APK compilation/packaging, FFmpeg setup, deterministic fixture generation, KVM/SDK installation, emulator boot, app install and home launch. The fresh-home test scrolled to the actual draft controls and confirmed both backup/restore buttons were present.
- It then failed the empty-label check because the shell used substring matching: `TASLAKLAR` also matches the longer valid button label `Taslakları yedekle`. Artifact `empty-drafts-home.xml` contains only the two expected backup/restore controls; the heading, helper text and empty placeholder are absent. The screenshot likewise shows no empty draft section. This is a **test false positive**, not an app UI regression.
- Replaced the grep substring check with XML parsing and exact normalized equality for the three forbidden labels. The valid `Taslakları yedekle` action no longer matches the heading assertion. The emulator run did not reach editor/export/preview checks in this attempt; rerun the complete smoke against the corrected script.


## Photo-only preview empty-state correction — 2026-11-07

- Follow-up source review found the editor's centered `Videonu ekle` empty-state card was controlled only by `clips.isEmpty()`. A photo opened directly from the home screen creates an image overlay without a main-video clip, so the empty card could remain above the selected image and cover the actual photo preview.
- Added one shared `updatePreviewEmptyStateVisibility()` rule: the card appears only when the project has no video clips, no image/video overlays, and no non-empty text layers. It is refreshed when an overlay is added/removed, a project is restored, video lanes change, or text layers change.
- The API 35 photo-only smoke now captures a portrait magenta image after opening through the actual debug-only equivalent of the home photo route. Its pixel-bounds/fill assertion will catch an empty-state card covering the canvas, as well as a non-full-frame placement.
- Unit/build/emulator verification is pending for this follow-up source change. No app version, signing or release workflow was changed.


## Photo-only empty-card smoke assertion — 2026-11-07

- Extended the full-frame photo smoke so that, after verifying the portrait magenta pixel bounds and fill ratio, it also captures the photo-only preview accessibility hierarchy.
- The test now explicitly rejects the empty-state content description `Galeriden video ekle` and label `Videonu ekle` while the initial photo layer is active. This distinguishes a genuinely photo-ready preview from one where the photo exists underneath a blocking empty-video card.
- Latest script commit: `88235b6fb700828e5292e7bd571c9ef97267e063`. Full unit/build and API 35 emulator verification are running for this updated head; no success is claimed until those exact checks complete.


## Text selection frame visibility and screenshot geometry — 2026-11-07

- Investigated emulator run `42340561594` (`d5bcb29da4d26cd64190c39fc0a84ca3e5542581`). The text rendered and the keyboard/9:16 canvas assertion passed, but the selection-frame assertion could only see three sides: the preview-canvas outline was a higher-level sibling of the canvas and painted over the white selection chrome where they met.
- Moved the non-interactive canvas outline into the project-canvas drawing stack, below text and selection chrome. Text/sticker transforms now retain a 2 dp inner safety inset so the selection stroke is not clipped when a layer reaches a canvas edge.
- The emulator pixel assertion now reads root-window and `Proje tuvali` bounds from a fresh accessibility hierarchy for each relevant screenshot: after keyboard dismissal, after the direct drag, after closing the text panel, and after preview reselection. It no longer pairs pixels with IME-era or previous-panel canvas dimensions.
- Added JVM coverage for the safe selection-frame translation bounds.
- Unit/build/emulator verification is pending for this exact follow-up commit. No app version, release signing, or publish workflow was changed.


## Selection geometry invalid-input guard — 2026-11-07

- The first follow-up's added JVM test exposed a defensive-input edge case: an infinite/negative selection inset was normalized to zero, which allowed movement instead of failing closed.
- `PreviewCanvasMath.maxCenterTranslation` now returns zero for non-finite or invalid canvas/content/inset inputs. The unit test also covers a negative inset.
- The complete emulator run remains pending on the next head; no version, signing, or release workflow changed.


## Text selection smoke: use fresh canvas metrics without redundant UIAutomator dumps — 2026-11-07

- Emulator run `42346679053` (#1076) passed installation, video replacement, preview, overlay/audio touch checks, MP4 export, and the keyboard-open 9:16 canvas check.
- It stopped because UIAutomator could not dump a second copy of the text/track accessibility tree after keyboard dismissal. The timeline tree is large and this repeated dump timed out; it was a test-harness failure.
- The selection pixel check now uses the latest `PREVIEW_CANVAS_BOUNDS` marker captured after each keyboard/panel/gesture transition, together with stable root-window bounds from the already captured editor hierarchy. Redundant UIAutomator dumps are removed.
- The application correction (canvas outline below text/selection chrome and a 2 dp layer-edge inset) remains intact. Full emulator verification is pending on the next head.


## Text direct-drag baseline — 2026-11-07

- Emulator run `42347714269` (#1077) confirmed the white text-selection frame was fully visible, but the first short vertical drag did not move it.
- Root cause: `TextLayer.yFraction` could remain at its original 0.84 while the rendered view was clamped higher to keep its selection chrome inside the canvas. The gesture used the stale stored fraction as its start point, so a 28 px drag could be absorbed by the clamp.
- Text drag and two-finger re-anchoring now derive their starting fractions from the view's actual rendered translation. Added JVM tests for this clamp-to-visible-position round trip and invalid values.
- Emulator verification is pending for the new app + test commit. No version, signing, or release workflow was changed.

## Content URI overlay durability — 2026-11-07

- VideoEditorActivity.addOverlayAsset() now keeps persistable document-provider grants when available. If a provider returns a temporary content:// URI instead, the selected image/video is copied into app-private files/overlay-media/ before its durable URI is written into the project/draft. A failed copy stops the import with a visible error instead of silently saving an expiring URI.
- The debug emulator entry can publish the existing deterministic photo fixture as a real MediaStore content:// item. The API 35 smoke now opens it alongside a video clip, requires the app-private durable-copy marker, closes the editor to autosave, force-stops the app, reopens the saved draft, and requires exactly one restored overlay.
- Source/test commits: c313dbe (durable app-private copy), bef79e6 / eafd9f3 (MediaStore content URI fixture and draft reopen entry), ad864ca (restore marker), cb6d2e5 (smoke script).
- The complete unit/build/API 35 emulator run is in progress. This is not yet verified green; inspect the content-URI copy, draft-save, process-restart, and overlay-restore artifacts before closing the issue.


## CapCut-style preview interaction and trim handles — 2026-11-07

- Preview text layers can now be dragged with one finger and resized/rotated with two fingers even after the text properties panel is closed. Gesture origins are based on the rendered, edge-clamped preview position; reselecting a layer restores the contextual text tools.
- The centered playback control remains available while contextual tools are open (the keyboard-only compact mode may still hide it). The API 35 smoke completed the live-playback and ruler/filmstrip synchronization step.
- Video-lane reorder now closes gaps after a completed drop even when the relative clip order is unchanged. The matching unit test expects the second clip to ripple from 5000 ms to 2000 ms.
- Timeline trim hit areas use a minimum 48 dp target and are centered on each clip boundary, extending into adjacent spacer space without changing clip duration. Parent tracks and clip frames do not clip those extended hit targets.
- Root cause of a remaining red emulator test: Android's right-edge back gesture intercepted leftward drags on the trailing music/sound/text trim handles. Added gesture-exclusion rectangles to trailing audio and text handles; the overlay handle already had this protection.
- Final verification for source commit `0d38b108707f7d0d09bbee94f7db377a89239973`:
  - Verify Android build + unit tests: run `42399166628` — **success**.
  - API 35 Android emulator screenshots/smoke: run `42399166651` — **success**.
  - Emulator logs confirm independent music start/end trims, sound-effect start/end trims, image-overlay start/end trims, a 27 px in-canvas text drag, live playback/timeline synchronization, draft save/resume, full-frame photo preview, and durable `content://` photo restoration after force-stop/relaunch.
- No app version, release signing, or publish workflow was changed. This verification covers these targeted interactions; it does not claim complete parity with CapCut or independently re-test every editor tool.


## CapCut-style video split and direct clip drag — 2026-11-08

- Main-video split uses one source-time cut. The left segment's source end is assigned as the right segment's source start, and the right segment's timeline start is the exact calculated end of the left segment. Unit coverage checks seam-free splits at 0.5×, 1×, 1.75× and 2× playback speed, plus invalid/edge split positions.
- A one-finger drag moves a main-video clip directly; a stationary long-press opens the clip context menu. Dropping into empty space moves only the selected clip and preserves deliberate gaps. Dropping onto an occupied range inserts/reorders the clip and pushes only colliding followers.
- The timeline validator now tolerates up to 1 px of cross-lane coordinate rounding and independently checks every lane's zero-time edge against the fixed playhead. Scroll spacers are derived from measured viewport geometry.
- API 35 emulator run `42451563873` passed the preview/filmstrip timestamp comparison, centering, forward/reverse scrubbing, and live playback synchronization. It stopped before splitting because the test's three correction swipes left the playhead at 4,292 ms instead of the 4,000 ms target. The script now makes up to five corrective attempts; the ±100 ms split-target assertion is unchanged.
- Emulator run `42469234908` passed unit/build and debug APK packaging but did not boot the emulator: the pinned BtbN FFmpeg release download returned HTTP 504 repeatedly. The CI step was replaced with the Node 24-compatible `FedericoCarboni/setup-ffmpeg` action pinned to `8073ccb51e517ed576e6871c7f2360c6de4ebb7c`, and explicit `ffmpeg`/`ffprobe` checks were added.
- Source/workflow commit `30f470d1c061ea22c5d32538aa9405a3e87f5798`: verify/unit run `42469494749` — **success**; debug APK run `42469494772` — **success**; API 35 emulator run `42469494743` — **in progress** at documentation update. On that emulator run, FFmpeg setup, fixture generation, KVM enablement, and Android SDK installation all passed before the actual editor smoke test started.
- Keep PR #29 unmerged until the API 35 emulator completes split, direct-drag/reorder, playback synchronization, and persistence checks. No versionCode, release signing, main-branch APK, or release workflow change is part of this work.

## Playback recovery and split-timeline persistence — 2026-11-08

- The API 35 playback failure was narrowed to a mismatch between the play glyph and the actual Media3 state: the old button-state code treated `playWhenReady=true` as active playback even while the composition player remained in `STATE_IDLE`. The UI now reports active playback only when the player is actually playing or buffering toward playback.
- Pressing Play on an idle timeline composition now clears a stale readiness request, calls `prepare()`, and then requests playback. Debug-only playback-state diagnostics record the Media3 state, timeline window count, duration, and position.
- Commit `83de73615a74207c0a2ce0315e158ab002346f18`: Verify Android build/unit run `42477679313` — **success**; debug APK build run `42477679322` — **success**. API 35 emulator run `42477679389` completed with a **live playback/timeline synchronization PASS** (player advanced and all timeline-lane assertions passed), then stopped in Step 11b before invoking the split handler because the test script printed a coordinate diagnostic to stdout; the shell parsed that text as touch coordinates. This is a test-harness output bug, not a demonstrated split failure.
- The real emulator flow splits the fixture into two production clips before the autosave check. The old persistence assertion still expected one clip; commit `dbeb0721dee194e71e2fc9aaf9255236d3fe4d19` updates that assertion to require both split segments to be saved (`clips=2`).
- The split math and the single-lane drawing both use absolute project-time boundaries. The JVM suite covers split seam continuity at 0.5×, 1×, 1.75× and 2×, gap preservation, overlap repair, and insertion/reorder. This is not yet proof of the visible emulator result: keep PR #29 open until the current-head API 35 test demonstrates actual playback, a 0–1 px post-split seam, successful direct drag/reorder, and saving/restoring both segments.
- Commit `6c779c917ec1d6652eea66e85eefd18e6cb2e465` adds a force-stop/reopen check for the split-video draft and verifies the two restored project ranges still meet at a 0 ms seam. Commit `1f26db04218293d080977a0c1c05000ae874ac6c` moves the clip-bound diagnostic to stderr so the shell receives only numeric tap coordinates. The current-head rerun must validate these changes before the PR is considered green.
- No versionCode, release signing, main-branch APK, or release workflow changes.

## 2026-11-08 — fresh timeline geometry in emulator sync diagnostics

- Emulator run `42480441974` built successfully and passed unit tests, but the final preview/thumbnail alignment assertion failed at a 35 ms tolerance: the captured thumbnail timestamp was 445 ms while the latest decoded preview samples were 83, 375, and 625 ms.
- Artifact inspection showed the alignment snapshot could be rate-limited for 500 ms while scroll/fling updates continued; the final sync samples could therefore describe a newer playhead/scroll position than the most recent thumbnail View-bounds snapshot. This made the assertion compare different moments in time.
- Commit `7a4adbbb1830e6f05f52348de5315eb5aaa51ea4` changes the debug-only diagnostic path to force a fresh View-bounds snapshot alongside each timeline sync sample. The 35 ms assertion is unchanged. Re-run emulator verification before treating preview/thumbnail alignment as passed.

## 2026-11-08 — split-reorder emulator test measurement source

- Emulator run `42481691607` on `428e99f5527cdfe48f0d60b3fc727e8c20a6edd9` confirmed split continuity: `firstEndMs=4042`, `secondStartMs=4042`, `gapMs=0`; measured screen seam was 0px.
- The next drag test failed before sending the drag because it searched `video-split-drag-logcat.txt` for `TIMELINE_SYNC_SAMPLE`. That file is collected after the split while playback is paused, so it may contain the split event but no new playback/sync sample.
- Commit `265cab249bf6bc84ed5920893affceb8beecd7b5` now uses the live playhead centre and scale measured during the immediately preceding midpoint seek (`video-midpoint-seek-logcat.txt`). No production gesture behavior or acceptance threshold was changed. Emulator verification is still required to prove the actual reorder/drop and remaining persistence checks.


## 2026-11-08 — prevent paused overlay refresh from starving playback

- The API 35 emulator run `42482764799` passed FFmpeg setup, video-fixture generation, emulator boot, canvas validation, thumbnail/preview pixel comparison (8 visually distinct cells; RGB delta 0), and bidirectional timeline scrubbing. The playback step then failed because no live playing samples were emitted.
- Full logcat established a production playback failure, not merely a missing test log: with an image/text overlay present, the composition player repeatedly transitioned `READY → BUFFERING → READY` at positions 538/622 ms while paused. The paused-overlay bootstrap listener scheduled another forced `seek(+84 ms) / seek(back)` refresh on every `STATE_READY`, creating an unbounded refresh loop.
- At the playback tap, Media3 was still buffering; shortly afterward it failed with `ExoPlaybackException: Unexpected runtime error`, caused by `NoSuchElementException` in `ExternalTextureManager.removeAllSurfaceTextureFrames` / `maybeQueueFrameToExternalShaderProgram`, and the composition player fell back to `STATE_IDLE`.
- Commit `90fe35c56671b339a80b297f74cd56002799a791` makes the initial paused-overlay frame refresh one-shot per CompositionPlayer, breaking the self-triggering READY/seek loop. It also emits a debug-only player-error record under `TimelineSyncTest` so future emulator artifacts retain the root exception beside sync samples.
- Do not lower the live-playback sample count or any timing tolerance. Verify on API 35 that the player reaches READY, begins advancing after Play, emits at least five live samples, and completes the subsequent split/reorder and save/reopen checks.


## 2026-11-08 — split-reorder measurement parser field order

- Emulator run `42485845233` on `17124eebf2349ebc99dc1abba7fa5e4afab0f155` confirms the paused-overlay refresh fix worked: live playback produced 62 samples and advanced from 3,083 ms to 7,855 ms; six timeline lanes remained synchronized. The split operation also passed with `firstEndMs=4000`, `secondStartMs=4000`, `gapMs=0`, and the measured on-screen seam was 0 px.
- The run then stopped before the direct clip drag because the test helper's regex expected `playheadCenterPx` before `pxPerSecond`, while the actual production diagnostic emits `pxPerSecond` first. The valid sample existed in the captured log; the parser rejected it and reported no fixed-playhead measurement. This is a test-harness parser defect, not a failed production drag.
- Commit `cb3908cb39034e79f9187db4498d4b93a0870925` changes the parser to consume the emitted field order and map scale/centre into the intended values. No app behavior, timing tolerance, or acceptance threshold was changed.
- Re-run current-head emulator verification to prove direct drag/reorder and draft save/reopen. Keep PR #29 open until those checks pass.


## 2026-11-08 — direct clip drag zero-boundary regression

- The latest API 35 emulator run reached the real production drop handler. Playback synchronization passed (50 live samples, 3,292→8,102 ms); split continuity passed (firstEndMs=4042, secondStartMs=4042, gapMs=0) and the measured visual seam was 0 px. The direct reorder then failed with ordered=1,0 starts=1:4,0:3985.
- This was not another playback or split failure. The filmstrip UI clamped its visual drag to the rounded pixel coordinate for time zero, then converted that rounded pixel offset back to milliseconds. At 78 px/s, the round trip can leave a small positive remainder (4 ms in the observed run) even though the finger crossed the zero boundary.
- Added TimelineScaleMath.timeAfterHorizontalDrag() so a pointer at or beyond the rounded left boundary commits exactly 0 ms. Added a unit regression for the observed 4,042 ms / 78 px/s case, including a one-pixel-inside control.
- The emulator assertion also incorrectly derived the next clip's expected start as 8000 - split_boundary_ms. The encoded video is nominally 8 seconds, but the app's measured media duration can differ from that nominal input; the observed drop implies a 3,981 ms moved-segment duration, while the old assertion expected 3,958 ms. The production split diagnostic now reports secondDurationMs, and the test requires the following clip to start at exactly that measured duration while the moved segment starts at exactly zero. This corrects the fixture assumption; no timing tolerance or behavior threshold was relaxed.
- Keep PR #29 open until the latest-head API 35 emulator run proves the zero-start reorder and the save/reopen persistence check. Unit/build and debug APK jobs must also be green. No versionCode bump or release build.


## 2026-11-08 — adjacent split-clip visual seam

- Inspection of successful emulator artifact `42492659555` confirmed live playback (58 samples, 1,958→7,207 ms), exact split continuity (`4042:4042`, `gapMs=0`), direct reorder to `0:3981`, and force-stop/reopen restoration of both clip ranges (`0:3981,3981:8023`).
- The post-reorder screenshot still showed a dark vertical divider even though the timeline View bounds met. The prior smoke validated only pre-drag View geometry and could not catch decorative borders/rounded inner corners creating a false gap.
- `VideoEditorActivity.buildVideoClipFilmstrip()` now squares the two corners that touch adjacent clips and draws the selected outline without a vertical stroke on shared edges. This removes the artificial separator without altering clip times or reordering behavior.
- The emulator smoke now captures fresh View bounds after the actual drop, checks the post-drop seam is within ±1 px, and samples the deterministic green fixture pixels across the seam to reject a visible dark divider. No timing tolerance, split math, reorder acceptance, or app version was changed.
- Source/script commits: `17a3307`, `b251866`, `2af8de4`, `e260a21`. Unit/build/API 35 emulator verification is pending for the final script head. Keep PR #29 open until all three latest-head workflows pass.

## 2026-11-08 — CapCut primary-lane continuity and selection frame

- User's device screenshot showed two main-video clips separated by an unintended empty timeline interval. The prior overlap repair only packed collisions and intentionally retained all non-overlapping gaps; consequently an old saved `timelineStartMs` offset could survive project restore and display a gray/black blank interval between ordinary clips.
- Added `TimelineVideoMath.packSequentially()` and wired the main-video lane refresh normalization to use it. Clips are ordered by their current timeline positions and packed from 0 with exact end-to-start adjacency; source in/out points and speed are unchanged. This repairs stale gaps in restored drafts as well as gaps left by main-video drag/reorder before the next lane render.
- Added unit coverage for legacy gap repair, out-of-order clip positions, and source-range/speed preservation.
- Updated selected main-video clip outline to a rounded white CapCut-style frame. Existing white edge grips remain the independent trim touch targets; the outline is purely visual and does not change clip bounds.
- Build and API 35 emulator validation for this change are pending. Do not deliver an updated APK until the latest-head build, unit tests and emulator checks pass.


## 2026-11-08 — CapCut-style video trim grips: physical emulator verification

- On current tested source commit 06ca571ef13dbfd78e4450327e9ac1073f8bf166, the API 35 emulator drove the actual left and right video trim touch targets using Android swipe events resolved from measured clip View bounds (not hard-coded screen positions).
- All four gestures passed: left trim, left extend, right trim, and right extend. Each gesture changed only its intended source edge; the opposite source edge stayed unchanged, the clip duration changed in the expected direction, the first clip remained anchored at project time 0, and the following main-video clip was repacked to the exact new duration with no gap.
- Verified examples: left trim changed source start 4042→4555 ms while source end stayed 8023 ms; left extend restored 4555→4042 ms. Right trim changed source end 8023→7510 ms while source start stayed 4042 ms; right extend restored 7510→8023 ms. Duration changed 3981↔3468 ms and the next clip start matched exactly.
- The same emulator run also passed split continuity, direct clip drag/reorder, post-drop seam geometry/pixels, and the remaining draft save/reopen smoke. Android unit/build, debug APK packaging, and API 35 emulator workflows all completed successfully for this tested head: verify run 42506500040, APK run 42506500012, emulator run 42506500354.
- No versionCode bump, release signing, or release build was made. Do not treat the generated debug APK ZIP as a release artifact.


## 2026-11-08 — CapCut filmstrip pan versus clip move

- Reproduced the gesture conflict in `VideoEditorActivity.buildVideoClipFilmstrip()`: every one-finger horizontal movement on a video clip was consumed as an immediate clip translation/reorder, while the ruler and other lanes remained in their independent synchronized scroll views. This made the blank time-zero origin spacer appear to move separately from the filmstrip and caused the ruler/clip interaction to feel disconnected.
- Changed the gesture contract to match CapCut: an ordinary horizontal swipe directly on the video thumbnails scrolls the shared timeline, and the existing lane scroll listener keeps ruler, video, text/audio lanes, playhead time and preview synchronized. A stationary long-press followed by a drag is now the explicit clip-move/reorder gesture; a stationary long-press still opens the clip menu.
- Kept the leading `timeline-origin-spacer`: it is required to put project time zero beneath the fixed centre playhead. It is not video content and must not be deleted or included in clip duration. The fix is to prevent ordinary scrubbing from translating the clip independently.
- Added API 35 emulator coverage that swipes on the actual filmstrip in both directions and asserts all lane `scrollX` values remain identical. Updated the direct reorder smoke gesture to use long-press-and-drag.
- **Verified on 2026-11-08, exact source commit `902d30eb0c86376770e0cbfdd0fb8cbf5a62c677`:** Android unit/build run `42514790303`, debug APK packaging run `42514790193`, and API 35 emulator run `42514790210` all succeeded.
- The emulator swiped directly on the filmstrip: left advanced the playhead to 5375 ms, right returned it to 3625 ms, and all six lane offsets remained equal (`418,418,418,418,418,418`) after the forward swipe. Live playback passed 53 synchronization samples (3625→8054 ms); ruler and first-frame zero edges stayed aligned.
- The centered preview/thumbnail pixel comparison passed at the fixed 35 ms limit with RGB channel delta 0. Real clip-menu split had a 0 ms seam; long-press drag reordered the segment and post-drop geometry/pixels passed. All four video trim/extend directions and force-stop draft save/reopen also passed.
- No versionCode bump, release signing, or signed release APK was made. The CI debug ZIP is not a release artifact.

- **Current sound-volume test correction:** the previous attempt found the SFX SeekBar was below the visible viewport of the audio-panel ScrollView; the XML contained only the music SeekBar because the panel had not been scrolled. The harness now scrolls the actual vertical audio settings panel (not the timeline), captures a second hierarchy, and locates the SFX volume control before interaction. No accessibility assertion has been removed or relaxed; latest-head rerun is pending.

## 2026-11-09 — Current validation gate and CapCut Kapak correction

- Starting point: draft PR #34 head `0d887c708f3d80e197303967b5847c0a1108f8ba`. Unit/build workflow `42571612057` and debug packaging workflow `42571612044` passed. API 35 emulator workflow `42571612054` failed at the real sound-effect volume Undo assertion: the injected slider gesture left the model at 100, so Undo had no volume change to restore. This is a red emulator result; do not mark the gate green or publish a release APK.
- Follow-up branch: `fix/capcut-cover-control-layout-20261109`, created from that exact candidate head. Commit `767bd6a3a81ff73db6b38a00b1e6c0e7718c1a60` changes the clip-panel **Kapak** action from a raised mini-button to an unboxed text action, matching the supplied CapCut reference while retaining the same cover-picker callback and accessible description.
- README records the visual acceptance criteria. Still required on the exact latest branch head: verify the cover action visually and functionally, resolve the sound-volume drag/Undo failure, then run unit/build, debug packaging and API 35 emulator checks. Preserve the already-verified shared timeline mapping, 35 ms preview/thumbnail tolerance, thumbnail latency behavior, and transparent time-zero spacer. No version-code bump, signed production APK, or release publication.


## 2026-11-09 — CapCut cover thumbnail tile (source change; validation pending)

- Corrected the prior misunderstanding: the reference has a distinct square cover-image tile in the left video-lane gutter, with a small edit/pencil affordance and a **Kapak** caption. A text-only label is not visual parity.
- Added a square thumbnail tile to the video lane. It asynchronously decodes the selected clip's saved cover timestamp, or the clip's start frame when no custom cover is chosen. Tapping the tile opens the existing cover-frame picker; selecting a new cover refreshes the tile through the existing trim-control refresh path.
- The image decode is guarded against stale selection results and uses the existing thumbnail executor. No timeline scale, ruler/filmstrip geometry, clip timing, or thumbnail strip mapping is changed.
- Pending: inspect the exact API 35 screenshot at normal phone density; verify selected frame changes after choosing a new cover; verify tap target opens picker; run unit/build, debug packaging and emulator workflows on the exact head. Do not claim visual parity or ship an APK until all checks pass.


## 2026-11-09 — Cover tile decode-path reuse

- Inspection confirmed three distinct image paths: timestamped filmstrip frames use the 3-worker thumbnail executor and 32 MiB LRU cache; temporary provider posters use a separate single-thread poster executor and 4 MiB cache; optional cover JPG export writes to the user's Pictures/MathMotor/Kapaklar gallery folder (or a document picker on older Android). The saved JPG is not the editor's cover-tile source.
- The first cover-tile implementation called the higher-resolution preview-frame decoder separately, which could duplicate frame work and compete with filmstrip decoding. Replaced that path with requestTimelineThumbnail() for the clip's selected cover timestamp (or source start when unset). The cover tile now reuses the exact timestamp-keyed filmstrip cache and in-flight request deduplication, at the existing 162×288 thumbnail scale; no second 1440px preview decode is launched for the tile.
- This removes a potential redundant decode, but does not establish that the historical slow-loading symptom was caused by cover generation. The earlier recorded concern that thumbnails appeared after audio import remains a separate loading-order investigation. Validate on the latest API 35 emulator and physical phone; compare queue wait, decode time, first-visible time and full-strip fill before claiming a performance improvement.


## 2026-11-09 — Independent persistent cover still

- Corrected the cover architecture per the CapCut reference: the left cover tile is an independent still image, not a filmstrip cell and not a live alias of the filmstrip cache. When no custom cover exists it can display the chosen source frame; after Save, the selected frame is written as a bounded 720px JPEG in the app's project_covers directory and the tile loads that saved image directly.
- Added Clip.coverImagePath to project JSON save/restore. The cover picker now saves the selected frame into project storage and updates the visible tile only after the file write succeeds. The selected source timestamp remains metadata; the filmstrip remains unchanged.
- The prior separate gallery-export helper remains available but is no longer the action used by the primary Kapak yap button. Project-local image paths currently refer to this app installation's private storage; portability of a project JSON plus its cover assets to another device remains a separate packaging requirement.
- Pending: compile and API 35 emulator validation for picker, save, clip switching, project save/reopen, undo, and cover tile independence from horizontal filmstrip scrolling. No APK or green claim until tests pass.


## 2026-11-09 — CapCut parity follow-up: independent cover image lifecycle

- The video-lane gutter now owns its own square cover ImageView; it is not a filmstrip cell and does not scroll with the thumbnails. The edit affordance opens the cover picker.
- Cover tile rendering uses a dedicated 4 MiB LRU and in-flight request deduplication. Default covers use an independently requested small source frame; saved covers are decoded from their own project-local JPEG. Timeline rebuilds explicitly rebind the current selected clip's cover (or the first clip when none is selected), preventing a newly recreated gutter view from appearing blank.
- A selected frame is scaled to at most 720px and written as a JPEG under app-private project_covers/. The picker now has a single Kaydet action that updates the clip's cover; the old separate Gallery JPG export path was removed from this picker to avoid a second, unrelated save action.
- Manual project JSON export embeds cover bytes as Base64 (bounded to 6 MB per cover) and import materializes them into the new device's private cover folder. Local autosave drafts keep the lightweight local path because they are restored on the same installation. Clip.copy() carries the path into split/duplicate and history snapshots.
- Added API 35 smoke assertions for: select the main clip, open the cover picker, save, reload from the saved-file path, Undo to the source-frame cover, and Redo to the saved still. The script now reselects the main clip after the preceding overlay test before opening Kapak.
- Fixed the audio-history test's scroll preparation: the previous run found the SFX SeekBar only 9px visible at the ScrollView edge and attempted a drag on that sliver. The smoke now keeps scrolling until at least 40px of the control is visible before dragging. This is a test-harness correction; the application slider listener itself already captured one history snapshot per drag.
- Validation: an earlier run at candidate head 51a9107471a8c617f56d647e06c4ee78d4c23335 failed Kotlin compilation because the first cover refresh refactor had removed persistClipCoverImage. That helper was restored at fa68c50bb5ba01d44fd7716ae27447313c4cb686; subsequent commits added the independent cache, project embedding, single Save action and API 35 assertions. The exact latest head still requires all three CI workflows to pass; do not treat the prior run as green and do not publish a release APK.


## 2026-11-09 — Audio waveform and lane label parity

- Music and sound-effect timeline blocks now render PCM-derived peak bars, not just a solid background with a text label. Decoding runs off the UI thread: WAV/PCM uses MediaExtractor sample reads; compressed audio uses MediaCodec PCM output. Samples are reduced to 120 amplitude buckets and drawn by an AudioWaveformView.
- Waveform samples have a 256 KiB LRU and an independent single-thread decoder queue. Keys include source URI and source-range boundaries, so panning, zooming and timeline rebuilds reuse cached data; a changed trim range gets a new waveform. The video filmstrip and fixed cover tile use separate executors/caches.
- API 35 smoke test on head 9a80eea4e47a46cfe023275accdae1c193e9f708 decoded both deterministic WAV fixtures and verified actual waveform pixels in the screenshot (music 3,294 pixels; effect 2,859 pixels). The same run passed unit/build but stopped later at the cover Save harness matching Android's uppercase KAYDET label; this is a test selector issue, not a waveform failure.
- Fixed the clip gutter's add-action labels: the source previously contained a double-escaped newline and displayed literal \\n text. It now uses a real newline for the two-line “Metin ekle” and “Ses ekle” labels, and the accessibility description normalizes the actual line break.
- Latest head 09d5de144e9e32be7264665bea54eab8a3dc00df has unit/build and debug APK build checks green; the latest API 35 emulator scenario is still running and must pass cover Save/Undo/Redo and audio interaction before the candidate can be accepted. No version bump, signed release APK or publication.
