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

## In-app updater status — 2026-11-03

- The home screen already provides a manual `Kontrol et` action. It checks GitHub `releases/latest`, reads the published `versionCode`, compares it with the installed app, downloads `MathMotorVideo.apk` when a higher version exists, and opens Android's package installer.
- The latest signed release is `app-v8.3-61`, with `versionCode=394`; Gradle is also set to 394. No signed release was published by merging PR #18.
- Publishing is separate from PR builds. The signed workflow runs manually or when `.github/release-trigger.txt` changes on `main`; it uses the persistent release-signing key. PR #18 did not touch that marker.
- Run #754's debug APK uses the debug certificate, while public releases use the persistent release certificate. When the installed app has the debug certificate, Android will not accept the release certificate as an in-place update. A one-time signed-install migration is needed first. Uninstalling erases private editor drafts, so back up important projects before migration.
- Before publishing a higher version, verify that the release certificate matches the currently published signed 394 APK and test a controlled upgrade. Keep 394 unchanged until that check is ready.


## Current continuation audit — 2026-11-04

- Correction to the older updater notes above: PR #18 **was merged** on 2026-11-03. The current `main` Gradle configuration is `versionCode=395`, `versionName=8.29-capcut-editor`.
- The latest signed GitHub release is `app-v8.3-62` (Build 62), published 2026-11-04, with `versionCode=395`. This supersedes the older 394 / Build 61 release note above. Do not assume which build is installed on the phone without checking the device.
- PR #22 remains open. Its head is 73 commits ahead and 4 commits behind `main`; it changes 22 files, including a large `VideoEditorActivity.kt` diff. Do not merge it as a routine small fix; reconcile it with current `main` and review the full diff first.
- The PR branch contains clip-speed timing/export work, overlay transform keyframes, persistence, and focused unit tests. Direct touch gestures were adjusted to write the interpolated transform at the current playhead, not only mutate the base transform.
- The PR debug workflow artifact was renamed to generic `MathMotor-debug.apk/.zip` so a debug artifact cannot be mistaken for a specific signed release/build.
- **Verification is still pending:** GitHub reports no Actions runs or check-runs for the current PR head. No build/test result or new APK is claimed. The local environment could not clone the repository, so an independent local Gradle build was not available.
- Keep `main` as the canonical source. Do not merge PR #22, publish a release, or change the signed release marker until the branch is rebased/reconciled, CI is green, and the editor/export behavior is tested on the emulator and phone.


## Follow-up static audit — 2026-11-04

- Found and corrected a start-trim edge case for animated image/video overlays: keyframes are now rebased to the trimmed local timeline, and a keyframe is seeded at the new local zero using the interpolated transform at the cut point. Extending the left edge preserves the original keyframe timing relative to the source.
- Added focused unit tests for trimming the start edge and extending it earlier. These tests are committed but **not executed yet**; the PR still has no verified green CI/build.
- The branch comparison currently reports 78 commits ahead and 4 behind `main` (22 changed files). This is a divergent branch, not merge-ready.
