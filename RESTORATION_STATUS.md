# MathMotor Video — current handoff status

**Last known baseline:** versionCode 394 / build 61 (`app-v8.3-61`).  
**Active branch:** `fix/editor-395-gate`. **Draft PR:** [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8).  
**Release status:** blocked; no version bump, signed APK publication, or update offer without verified gates and explicit user authorization.

## Latest verified result

[Workflow #40199353737 (#152)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40199353737) passed on source/test commit `515df8035d31508acb03f5e9fe59cf0e78879508`: 6 instrumentation tests, 0 failures/errors/skips. The verified behaviors are the fixed-playhead scroll/pinch path, left/right trim and restoration, distinct filmstrip frames, text input after keyboard resize plus live text size/timing controls, two-clip export to one new 11,029 ms MP4 with a green second-clip frame at 9.5 s, and the home/editor/text-panel smoke capture. Both source video hashes remained unchanged.

The keyboard assertion is inside `textTimingAndSizeControlsUpdatePreview` and passed in run #152. It is no longer an open blocker for the tested emulator configuration. Two-clip import/export is also no longer open.

## New work in progress

The new `savedDraftReopensTextLayerAndClipState` check writes a draft through the real `VideoProjectDraftStore`, then checks restored trim duration, preview text, editable text value and 32 sp size. First attempt [#40201158067 (#154)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40201158067) failed because the debug-only test bridge refused to start without a video URI and the assertion waited for the wrong status text. Those were fixed by [`e7820c1`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/e7820c1202a09f7bd0b1de6100c5e36bb73b4f0a), [`288e21d`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/288e21da2a9971c89c9e9914eefa9c6fcfe50dec), and [`147a0d5`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/147a0d57094330d4e13408ba6ae4e4162ced914d). Run [#40202008562 (#156)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202008562) completed with six tests passing and this new test failing on a stale UI assertion: it expected `Kesit 00:07.02`, but the actual label is `Klip 00:01.00 / 00:07.02` (playhead position / clip duration). The assertion was corrected in [`55c69c2`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/55c69c27cd6b370a2f1eb41fa335028bd81cc4cc). Run [#40202911219 (#157)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202911219) failed again at the duration assertion. The full formatted label selector was brittle; the next test-only change [`47b0e6f`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/47b0e6fd119f27573956d4065e109da9da1618e5) searches for the distinctive `00:07.02` duration text. Wait for the new run and keep the restore gate open. Intermediate run [#40202001975 (#155)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202001975) was cancelled by the subsequent push as configured by branch concurrency.

## Remaining real blockers

1. **Undo/Redo:** source history uses clip-only snapshots. Text and audio/music edits do not yet share a global history.
2. **Project round-trip:** local draft test above is pending CI; separately verify manual JSON Save As → close → Open with clips, trims, timeline position, text, music and sound settings restored.
3. **Text strips/export:** dragging text-strip edges has not been validated; output-vs-preview parity for text timing/placement/size/style is unverified.
4. **Audio/export:** original audio level, background music loop/volume, timed sound effect and final MP4 A/V sync lack end-to-end test evidence.
5. **Timeline edge cases:** split-at-playhead/export boundary, trim-versus-scroll conflicts and multi-clip editing at multiple zoom levels are still unverified. The previously passing single-clip start/end/zoom checks and two-clip export do not need to be rerun without a relevant code change.
6. **Physical phone:** emulator screenshots were reviewed, but thumbnail density, compact tap targets, keyboard/panel feel and overall layout still need a physical-device check.
7. **Release/install:** signed update/install verification remains last, after all previous items pass.

## Source-of-truth rule

The canonical checklist lives in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md). This file is only the short handoff: update both after meaningful progress, but do not maintain duplicate historical unchecked lists. Older entries saying keyboard accessibility or two-clip export are unverified are superseded by run #152 above.

No signed release APK was produced by run #152. Keep version metadata unchanged and do not release version 395.


## Latest CI handoff — 2026-10-25

- Run [#158](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40203831760) failed in `savedDraftReopensTextLayerAndClipState` with `Could not find restored text-size label`. Build/unit tests passed; six? The run reports one failing emulator test among seven, so the draft round-trip is still not green.
- The clip duration check now passed; failure is at the text-size label query after switching to the Metin panel. This alone does not prove text-size data was lost: the size control is late in a vertically scrollable panel and may not be present in the visible accessibility tree.
- Test-only commit [`eeda257`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/eeda2577ec86f9371fea6a00087abc9843de6ccb) scrolls the panel before asserting the restored size. Wait for the resulting CI run and inspect any next failure rather than weakening the assertion.
- Keep version 395 unreleased. Baseline remains versionCode 394 / build 61 until all gates pass and user explicitly authorizes release.


## Latest CI handoff — 2026-10-25 (run #159)

- [Run #159](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40204548336) failed in the draft round-trip test at the scroll helper, not at the 32 sp comparison. The helper `scrollTextIntoView("Yazı boyutu:")` uses exact text matching; the real label includes the size value, so the selector could not match it.
- Test-only fix [`1e0f987`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/1e0f987a90d37e8547621950ce93d4edb5ae24d7) uses a `textContains` selector with `scrollIntoView`. The check still verifies the restored value includes 32. Draft round-trip remains unverified until a new emulator run passes.


## Latest CI handoff — 2026-10-25 (run #160)

- Run [#160](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40205495392) failed before reaching interaction tests, during initial home-screen capture. Emulator diagnostics explicitly report a system ANR for `com.google.android.apps.nexuslauncher` (Pixel launcher); ADB then became unavailable and the capture script exited 8. `dumpsys activity` showed `com.mathmotor.video.next/.MainActivity` resumed. Treat this as emulator/launcher infrastructure failure, not a verdict on draft restore.
- The [`1e0f987`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/1e0f987a90d37e8547621950ce93d4edb5ae24d7) substring-selector correction remains unverified because the editor suite did not run. Retried failed CI jobs on run #160. Await the retry; if the launcher ANR recurs, harden emulator workflow recovery instead of changing app assertions.
